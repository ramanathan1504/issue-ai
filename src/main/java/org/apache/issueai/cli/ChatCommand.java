package org.apache.issueai.cli;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import org.apache.issueai.llm.GeminiClient;
import org.apache.issueai.llm.OllamaClient;
import org.apache.issueai.model.ChatMemory;
import org.apache.issueai.model.Issue;
import org.apache.issueai.model.IssueEmbedding;
import org.apache.issueai.model.PrMemory;
import org.apache.issueai.storage.SqliteStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "chat", description = "Open an interactive REPL chat session with local alignment and Gemini escalation")
public class ChatCommand implements Callable<Integer> {

    private static final Logger LOGGER = LogManager.getLogger(ChatCommand.class);

    @Parameters(index = "0", description = "The issue or PR number to chat about")
    private long issueNumber;

    @Option(names = {"-r", "--repo"}, defaultValue = "apache/logging-log4j2")
    private String repository;

    @Override
    public Integer call() throws Exception {
        // 1. Resolve Local Configs
        String modelName = SqliteStorage.loadConfig("ollama.model.guidance");
        if (modelName == null) modelName = "qwen2.5:7b";

        String geminiKey = retrieveGeminiKey();
        String geminiModel = SqliteStorage.loadConfig("gemini.model");
        if (geminiModel == null) geminiModel = "gemini-1.5-flash-latest";

        // 2. Load Issue Context
        List<Issue> issues = SqliteStorage.loadIssues(repository);
        List<Issue> prs = SqliteStorage.loadPullRequests(repository);
        Issue target = null;
        for (Issue i : issues) { if (i.number() == issueNumber) { target = i; break; } }
        if (target == null) {
            for (Issue p : prs) { if (p.number() == issueNumber) { target = p; break; } }
        }
        if (target == null) {
            LOGGER.error("Issue #{} not found in local data for '{}'.", issueNumber, repository);
            return 1;
        }

        // 3. Load Personal Memory Context
        List<IssueEmbedding> embeddings = SqliteStorage.loadEmbeddings(repository);
        double[] targetVector = null;
        for (IssueEmbedding emb : embeddings) {
            if (emb.issueNumber() == issueNumber) { targetVector = emb.vector(); break; }
        }

        StringBuilder memoryContext = new StringBuilder();
        if (targetVector != null) {
            for (PrMemory prMem : SqliteStorage.loadAllPersonalPrMemories()) {
                if (prMem.vector() != null && cosineSimilarity(targetVector, prMem.vector()) >= 0.35) {
                    memoryContext.append("PR #").append(prMem.prNumber()).append(" Story:\n").append(prMem.generatedStory()).append("\n");
                }
            }
            for (ChatMemory chatMem : SqliteStorage.loadAllPersonalChatMemories()) {
                if (chatMem.vector() != null && cosineSimilarity(targetVector, chatMem.vector()) >= 0.35) {
                    memoryContext.append("Past Chat: ").append(chatMem.content()).append("\n");
                }
            }
        }

        OllamaClient localClient = new OllamaClient(modelName);
        if (!localClient.isModelAvailable()) {
            LOGGER.error("Ollama model '{}' is not available. Please start Ollama.", modelName);
            return 1;
        }

        Scanner scanner = new Scanner(System.in);
        StringBuilder conversationHistory = new StringBuilder();
        String lastUserPrompt = "";

        System.out.println("==================================================");
        System.out.printf(" INTERACTIVE COPILOT SESSION: Issue #%d%n", issueNumber);
        System.out.println(" Backend: Local First (" + modelName + ") -> Gemini Escalation");
        System.out.println(" Type your questions below. Type 'exit' to end.");
        System.out.println("==================================================\n");

        while (true) {
            System.out.print("\n> You (or type 'y' to escalate your last prompt to Gemini): ");
            String userInput = scanner.nextLine().trim();

            if (userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("quit")) {
                LOGGER.info("Ending chat session. Saving and indexing memory...");
                saveAndIndexChatHistory(conversationHistory.toString(), target);
                LOGGER.info("Goodbye!");
                break;
            }
            if (userInput.isEmpty()) continue;

            // --- ESCALATION & ALIGNMENT FLOW ---
            if (userInput.equalsIgnoreCase("y") && !lastUserPrompt.isEmpty()) {
                if (geminiKey == null || geminiKey.trim().isEmpty()) {
                    LOGGER.error("GEMINI_API_KEY is missing. Cannot escalate to cloud.");
                    continue;
                }

                LOGGER.info("Bridging request to Google Gemini (Cloud)...");
                GeminiClient cloudClient = new GeminiClient(geminiKey, geminiModel);

                String geminiPrompt = String.format("""
                        You are an expert Apache Log4j maintainer.
                        We are actively resolving Issue #%d: %s
                        Body: %s
                        
                        --- CONVERSATION HISTORY ---
                        %s
                        
                        --- NEW PROMPT ---
                        %s
                        
                        Provide a highly technical, expert code resolution for the new prompt.
                        """, issueNumber, target.title(), target.body(), conversationHistory.toString(), lastUserPrompt);

                try {
                    String geminiOutput = cloudClient.generateText(geminiPrompt);
                    LOGGER.info("Received Cloud Response. Aligning with your local memory profile...");

                    String alignmentPrompt = String.format("""
                            You are a personal developer copilot.
                            An online expert AI provided this code solution:
                            %s
                            
                            Here is my personal development memory (my past PRs and edits):
                            %s
                            
                            Compare the expert solution with my memory. Output a final response that includes:
                            1. The expert solution.
                            2. ALIGNMENT CHECK: Does this solution match my past coding patterns? 
                            3. Which specific local files from my past PRs should I apply this to?
                            """, geminiOutput, memoryContext.toString().isEmpty() ? "(No local memory found)" : memoryContext.toString());

                    String finalAlignedOutput = localClient.generateText(alignmentPrompt);

                    System.out.println("\n================ EXPERT ALIGNED RESPONSE ================");
                    System.out.println(finalAlignedOutput);
                    System.out.println("=========================================================\n");

                    conversationHistory.append("User escalated to Gemini.\nAI (Hybrid): ").append(finalAlignedOutput).append("\n\n");

                } catch (Exception e) {
                    LOGGER.error("Cloud escalation failed: {}", e.getMessage());
                }

            }
            // --- LOCAL OFFLINE FLOW ---
            else {
                lastUserPrompt = userInput;
                conversationHistory.append("User: ").append(userInput).append("\n");

                LOGGER.info("Thinking locally...");
                String localPrompt = String.format("""
                        You are an Apache Log4j maintainer acting as a live coding pair-programmer.
                        We are actively resolving Issue #%d: %s
                        
                        --- RELEVANT PAST EXPERIENCES ---
                        %s
                        
                        --- CONVERSATION HISTORY ---
                        %s
                        
                        Please respond directly to the User's last message. Provide code snippets if requested.
                        """,
                        issueNumber, target.title(),
                        memoryContext.toString().isEmpty() ? "(None)" : memoryContext.toString(),
                        conversationHistory.toString());

                try {
                    String localOutput = localClient.generateText(localPrompt);

                    System.out.println("\n================ LOCAL RESPONSE ================");
                    System.out.println(localOutput);
                    System.out.println("================================================\n");

                    conversationHistory.append("AI (Local): ").append(localOutput).append("\n\n");

                } catch (Exception e) {
                    LOGGER.error("Local generation failed: {}", e.getMessage());
                }
            }
        }

        return 0;
    }

    private String retrieveGeminiKey() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key != null && !key.trim().isEmpty()) return key;

        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "sh", "-c", "security find-generic-password -s gemini_api_key -w 2>/dev/null || true"
            });
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) return line.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private double cosineSimilarity(double[] vecA, double[] vecB) {
        if (vecA.length != vecB.length) return 0.0;
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    private void saveAndIndexChatHistory(String chatContent, Issue target) {
        try {
            String drivePathsStr = SqliteStorage.loadConfig("drive.paths");
            if (drivePathsStr == null || drivePathsStr.trim().isEmpty()) {
                LOGGER.warn("No Google Drive paths configured in SQLite. Chat history will not be saved.");
                return;
            }

            // Grab the first configured directory path to save the session
            String targetDir = drivePathsStr.split(",")[0].trim();
            java.nio.file.Path dirPath = java.nio.file.Paths.get(targetDir);

            if (!java.nio.file.Files.exists(dirPath)) {
                LOGGER.warn("Configured Drive path does not exist locally: {}", dirPath.toAbsolutePath());
                return;
            }

            // Generate a clean Markdown file
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("Issue_%d_Resolution_Session_%s.md", issueNumber, timestamp);
            java.nio.file.Path filePath = dirPath.resolve(fileName);
            String absolutePath = filePath.toAbsolutePath().toString();

            StringBuilder fileContent = new StringBuilder();
            fileContent.append("# Resolution Session: Issue #").append(issueNumber).append("\n");
            fileContent.append("**Title:** ").append(target.title()).append("\n");
            fileContent.append("**Date:** ").append(java.time.LocalDate.now()).append("\n\n");
            fileContent.append("## Conversation Transcript\n\n");
            fileContent.append(chatContent);

            String finalContent = fileContent.toString();

            // 1. Save physical backup to Google Drive
            java.nio.file.Files.writeString(filePath, finalContent, java.nio.charset.StandardCharsets.UTF_8);
            long lastModified = java.nio.file.Files.getLastModifiedTime(filePath).toMillis();
            LOGGER.info("✔ Chat history successfully saved to Google Drive: {}", absolutePath);

            // 2. Real-Time Intelligence: Instantly Vectorize and Index into SQLite
            LOGGER.info("  ↳ Instantly vectorizing conversation for your Second Brain...");
            String embedModel = SqliteStorage.loadConfig("ollama.model.embedding");
            if (embedModel == null) {
                embedModel = "all-minilm";
            }

            OllamaClient embedOllama = new OllamaClient(embedModel);
            double[] chatVector = embedOllama.generateEmbedding(finalContent);

            // Inject directly into the live memory table
            SqliteStorage.savePersonalChatMemory(absolutePath, fileName, lastModified, finalContent, chatVector);
            LOGGER.info("  ✔ Session embedded and injected into active memory. Your Copilot is instantly smarter!");

        } catch (Exception e) {
            LOGGER.error("Failed to save and index chat history: {}", e.getMessage());
        }
    }
}