package org.apache.issueai.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.issueai.llm.OllamaClient;
import org.apache.issueai.model.AiAnalysisResult;
import org.apache.issueai.model.Issue;
import org.apache.issueai.storage.JsonStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "analyze",
        description = "Perform batch AI Severity Analysis on open issues via local Ollama"
)
public class AnalyzeCommand implements Callable<Integer> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Option(
            names = {"-m", "--model"},
            description = "Ollama model name to use",
            defaultValue = "qwen3:8b"
    )
    private String modelName;

    @Override
    public Integer call() throws Exception {
        List<Issue> issues = JsonStorage.loadIssues();
        if (issues.isEmpty()) {
            System.err.println("No local issues found. Please run 'sync' first.");
            return 1;
        }

        System.out.printf("Starting AI Severity Analysis using model '%s'...%n", modelName);
        System.out.println("Connecting to local Ollama service...");

        OllamaClient client = new OllamaClient(modelName);
        List<AiAnalysisResult> results = new ArrayList<>();

        for (Issue issue : issues) {
            System.out.printf("Analyzing Issue #%d: %s%n", issue.number(), issue.title());

            String prompt = String.format("""
                    You are an Apache Log4j maintainer.
                    Classify the severity of the following GitHub issue.
                    
                    Issue Title: %s
                    Issue Body: %s
                    
                    Classify: Critical, High, Medium, Low
                    Determine:
                    - Severity
                    - Confidence (0.0 to 1.0)
                    - Impact
                    
                    You MUST respond ONLY with a valid JSON object matching this exact schema:
                    {
                      "severity": "Critical",
                      "confidence": 0.91,
                      "reason": "Potential deadlock affecting production systems."
                    }
                    """, issue.title(), issue.body());

            try {
                String rawJson = client.generateJson(prompt);
                AiAnalysisResult rawResult = MAPPER.readValue(rawJson, AiAnalysisResult.class);

                AiAnalysisResult finalResult = new AiAnalysisResult(
                        issue.number(),
                        rawResult.severity(),
                        rawResult.confidence(),
                        rawResult.reason()
                );

                results.add(finalResult);
                System.out.printf("  ↳ Predicted: %s (Confidence: %.2f)%n", finalResult.severity(), finalResult.confidence());

            } catch (IOException | InterruptedException e) {
                System.err.printf("  ↳ [Error] Failed to analyze #%d: %s%n", issue.number(), e.getMessage());
                System.err.println("Verify Ollama is running ('ollama serve') and model is pulled ('ollama pull " + modelName + "').");
                return 1;
            }
        }

        JsonStorage.saveAiAnalysis(results);
        System.out.println("\nAI Analysis completed successfully. Results saved to 'data/ai-analysis.json'.");
        return 0;
    }
}