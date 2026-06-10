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

@Command(name = "guide", description = "Generate a personalized resolution blueprint using local memory and interactive Gemini escalation")
public class GuideCommand implements Callable<Integer> {

    private static final Logger LOGGER = LogManager.getLogger(GuideCommand.class);

    @Parameters(index = "0", description = "The issue or PR number to analyze")
    private long issueNumber;

    @Option(names = {"-r", "--repo"}, defaultValue = "apache/logging-log4j2")
    private String repository;

    @Option(names = {"-m", "--model"}, description = "Local Ollama model to use")
    private String modelName;

    @Option(names = {"--gemini"}, description = "Bypass local AI and route immediately to Gemini API")
    private boolean forceGemini;

    @Override
    public Integer call() throws Exception {
        // 1. Resolve configurations
        if (modelName == null) {
            modelName = SqliteStorage.loadConfig("ollama.model.guidance");
            if (modelName == null) modelName = "qwen2.5:7b";
        }

        // 2. Load the target issue details
        List<Issue> issues = SqliteStorage.loadIssues(repository);
        List<Issue> prs = SqliteStorage.loadPullRequests(repository);
        Issue target = null;

        for (Issue i : issues) {
            if (i.number() == issueNumber) { target = i; break; }
        }
        if (target == null) {
            for (Issue p : prs) {
                if (p.number() == issueNumber) { target = p; break; }
            }
        }

        if (target == null) {
            LOGGER.error("Issue #{} not found in local data for '{}'. Please run 'sync' first.", issueNumber, repository);
            return 1;
        }

        // 3. Load target issue vector and extract memory contexts
        List<IssueEmbedding> embeddings = SqliteStorage.loadEmbeddings(repository);
        double[] targetVector = null;
        for (IssueEmbedding emb : embeddings) {
            if (emb.issueNumber() == issueNumber) {
                targetVector = emb.vector();
                break;
            }
        }

        StringBuilder contextBlock = new StringBuilder();
        int matchedCount = 0;

        if (targetVector != null) {
            LOGGER.info("Retrieving memory contexts from SQLite...");
            for (PrMemory prMem : SqliteStorage.loadAllPersonalPrMemories()) {
                if (prMem.vector() != null) {
                    double similarity = cosineSimilarity(targetVector, prMem.vector());
                    if (similarity >= 0.35) {
                        matchedCount++;
                        contextBlock.append("--- REFERENCE DEVELOPMENT NOTE (PR #").append(prMem.prNumber()).append(") ---\n");
                        contextBlock.append("Files Changed: ").append(prMem.filesChanged()).append("\n");
                        contextBlock.append("Story:\n").append(prMem.generatedStory()).append("\n\n");
                    }
                }
            }

            for (ChatMemory chatMem : SqliteStorage.loadAllPersonalChatMemories()) {
                if (chatMem.vector() != null) {
                    double similarity = cosineSimilarity(targetVector, chatMem.vector());
                    if (similarity >= 0.35) {
                        matchedCount++;
                        contextBlock.append("--- REFERENCE DISCUSSION NOTE (File: ").append(chatMem.fileName()).append(") ---\n");
                        contextBlock.append("Content:\n").append(chatMem.content()).append("\n\n");
                    }
                }
            }
            LOGGER.info("  ↳ Retrieved {} semantic memory contexts.", matchedCount);
        }

        String memorySection = matchedCount > 0
                ? contextBlock.toString()
                : "No specific personal past experience found. Provide expert generic Log4j resolution.";

        // --- TIER 1: Local Ollama Generation ---
        String localOutput = "";

        if (!forceGemini) {
            OllamaClient guideOllama = new OllamaClient(modelName);
            if (!guideOllama.isModelAvailable()) {
                LOGGER.error("Ollama model '{}' is not available. Please pull it or use --gemini.", modelName);
                return 1;
            }

            LOGGER.info("Synthesizing initial blueprint using local model '{}'...", modelName);
            String localPrompt = String.format("""
                    You are an Apache Log4j maintainer.
                    Help the developer write a step-by-step code resolution plan for this new issue.
                    
                    --- REFERENCE MEMORY ---
                    %s
                    
                    --- NEW ISSUE ---
                    Title: %s
                    Body: %s
                    
                    Your output MUST be a structured markdown guide containing:
                    1. ANALYSIS: A concise technical explanation of the root cause.
                    2. HISTORICAL MATCH: How this relates to the past work provided in the reference memory.
                    3. STEP-BY-STEP PLAN: A concrete, file-by-file coding blueprint.
                    """, memorySection.trim(), target.title(), target.body());

            try {
                localOutput = guideOllama.generateText(localPrompt);
                LOGGER.info("\n==================================================");
                LOGGER.info("LOCAL RESOLUTION BLUEPRINT FOR ISSUE #{} (Ollama)", issueNumber);
                LOGGER.info("==================================================");
                LOGGER.info("\n{}\n", localOutput);
                LOGGER.info("==================================================");
            } catch (Exception e) {
                LOGGER.error("Failed to generate local blueprint: {}", e.getMessage());
                return 1;
            }
        }

        // --- TIER 2: Interactive Gemini Escalation ---
        Scanner scanner = new Scanner(System.in);
        LOGGER.info("");
        LOGGER.info("Would you like to refine this using Google Gemini? ");
        LOGGER.info("(Type your tweaks/instructions below and press Enter, or just press Enter to exit):");

        String tweak = scanner.nextLine().trim();

        if (!tweak.isEmpty() || forceGemini) {
            String geminiKey = retrieveGeminiKey();
            if (geminiKey == null || geminiKey.trim().isEmpty()) {
                LOGGER.error("GEMINI_API_KEY is missing. Please run 'setup' to store it securely.");
                return 1;
            }

            LOGGER.info("Bridging request to Cloud Agent (Gemini API)...");

            String geminiPrompt = String.format("""
                    You are an expert Apache Log4j maintainer.
                    We are resolving Issue #%d: %s
                    
                    --- ORIGINAL CONTEXT & MEMORY ---
                    %s
                    
                    --- LOCAL AI DRAFT RESOLUTION ---
                    %s
                    
                    --- DEVELOPER FEEDBACK / TWEAK ---
                    %s
                    
                    Please provide a final, expert-level Markdown resolution blueprint that incorporates the developer's feedback and improves upon the local draft.
                    """, issueNumber, target.title(), memorySection.trim(), localOutput, tweak);

            try {
                String geminiModel = SqliteStorage.loadConfig("gemini.model");
                if (geminiModel == null || geminiModel.trim().isEmpty()) {
                    geminiModel = "gemini-1.5-flash-latest"; // Universally available alias
                }
                LOGGER.info("Bridging request to Cloud Agent using model '{}'...", geminiModel);
                GeminiClient geminiClient = new GeminiClient(geminiKey, geminiModel);
                String geminiOutput = geminiClient.generateText(geminiPrompt);

                LOGGER.info("\n==================================================");
                LOGGER.info("EXPERT RESOLUTION BLUEPRINT FOR ISSUE #{} (Gemini)", issueNumber);
                LOGGER.info("==================================================");
                LOGGER.info("\n{}\n", geminiOutput);
                LOGGER.info("==================================================");
            } catch (Exception e) {
                LOGGER.error("Failed to generate Gemini blueprint: {}", e.getMessage());
                return 1;
            }
        } else {
            LOGGER.info("Copilot session completed.");
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
}