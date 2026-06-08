package org.apache.issueai.cli;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.issueai.model.AiAnalysisResult;
import org.apache.issueai.model.Issue;
import org.apache.issueai.storage.JsonStorage;
import picocli.CommandLine.Command;

@Command(
        name = "hidden-critical",
        description = "Find critical issues that maintainers may have underestimated"
)
public class HiddenCriticalCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        List<Issue> issues = JsonStorage.loadIssues();
        List<AiAnalysisResult> aiResults = JsonStorage.loadAiAnalysis();

        if (issues.isEmpty() || aiResults.isEmpty()) {
            System.err.println("Required local data is missing. Please run 'sync' followed by 'analyze' first.");
            return 1;
        }

        // Map AI results by issue number for fast lookup
        Map<Long, AiAnalysisResult> aiMap = aiResults.stream()
                .collect(Collectors.toMap(AiAnalysisResult::issueNumber, result -> result));

        System.out.println("Hidden Critical Issues Report");
        System.out.println("=============================\n");

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
            System.out.println("No hidden critical issues detected in this database snapshot.");
        } else {
            System.out.printf("Detection completed. Found %d potential hidden critical issues.%n", count);
        }

        return 0;
    }
}