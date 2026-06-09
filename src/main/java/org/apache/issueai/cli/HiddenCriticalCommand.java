package org.apache.issueai.cli;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.issueai.model.AiAnalysisResult;
import org.apache.issueai.model.Issue;
import org.apache.issueai.storage.SqliteStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "hidden-critical",
        description = "Find critical issues that maintainers may have underestimated"
)
public class HiddenCriticalCommand implements Callable<Integer> {

    @Option(
            names = {"-r", "--repo"},
            description = "The target GitHub repository to analyze (owner/name)",
            defaultValue = "apache/logging-log4j2"
    )
    private String repository;

    @Override
    public Integer call() throws Exception {
        // Load issues and AI analysis results specifically for this repository context
        List<Issue> issues = SqliteStorage.loadIssues(repository);
        List<AiAnalysisResult> aiResults = SqliteStorage.loadAiAnalysis(repository);

        if (issues.isEmpty() || aiResults.isEmpty()) {
            System.err.printf("Required local data is missing for '%s'. Please run 'sync' followed by 'analyze' first.%n", repository);
            return 1;
        }

        // Map AI results by issue number for fast lookup
        Map<Long, AiAnalysisResult> aiMap = aiResults.stream()
                .collect(Collectors.toMap(AiAnalysisResult::issueNumber, result -> result));

        System.out.printf("Hidden Critical Issues Report for '%s'%n", repository);
        System.out.println("====================================================\n");

        int count = 0;

        for (Issue issue : issues) {
            AiAnalysisResult aiResult = aiMap.get(issue.number());
            if (aiResult == null) {
                continue;
            }

            boolean isCriticalAi = "Critical".equalsIgnoreCase(aiResult.severity());
            boolean isHighAi = "High".equalsIgnoreCase(aiResult.severity()) || isCriticalAi;

            boolean hasSecurityLabel = issue.hasLabel("security") || issue.hasLabel("security-label");
            boolean hasBugLabel = issue.hasLabel("bug") || issue.hasLabel("bug-label");

            boolean isHiddenCritical = false;
            String detectionReason = "";

            // Rule 1: Label != Security AND AI Severity == Critical
            if (!hasSecurityLabel && isCriticalAi) {
                isHiddenCritical = true;
                detectionReason = "Predicted as Critical by AI but lacks security labeling.";
            }
            // Rule 2: Many comments (> 15) + Bug label + High AI severity
            else if (issue.comments() > 15 && hasBugLabel && isHighAi) {
                isHiddenCritical = true;
                detectionReason = String.format(
                        "Has %d comments, bug label, and predicted severity is %s.",
                        issue.comments(),
                        aiResult.severity());
            }

            if (isHiddenCritical) {
                count++;

                String labelsStr = issue.labels() == null || issue.labels().isEmpty()
                        ? "[]"
                        : issue.labels().stream()
                        .map(label -> label.name())
                        .collect(Collectors.joining(", "));

                System.out.printf("Issue #%d%n", issue.number());
                System.out.printf("  Title: %s%n", issue.title());
                System.out.printf("  Current Labels: %s%n", labelsStr);
                System.out.printf("  AI Severity: %s (Confidence: %.2f)%n", aiResult.severity(), aiResult.confidence());
                System.out.printf("  AI Reason: %s%n", aiResult.reason());
                System.out.printf("  Detection Rule: %s%n%n", detectionReason);
            }
        }

        if (count == 0) {
            System.out.printf("No hidden critical issues detected in this database snapshot for '%s'.%n", repository);
        } else {
            System.out.printf("Detection completed. Found %d potential hidden critical issues for '%s'.%n", count, repository);
        }

        return 0;
    }
}