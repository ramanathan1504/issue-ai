package org.apache.issueai.cli;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.issueai.model.AiAnalysisResult;
import org.apache.issueai.analyzer.IssueAnalysis;
import org.apache.issueai.analyzer.SeverityAnalyzer;
import org.apache.issueai.model.Issue;
import org.apache.issueai.model.IssueEmbedding;
import org.apache.issueai.model.JiraBridgeLink;
import org.apache.issueai.model.Label;
import org.apache.issueai.storage.SqliteStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "triage",
        description = "Perform a consolidated automated triage audit on a specific issue"
)
public class TriageCommand implements Callable<Integer> {

    @Parameters(
            index = "0",
            description = "The issue or PR number to triage"
    )
    private long issueNumber;

    @Option(
            names = {"-r", "--repo"},
            description = "The target GitHub repository (owner/name)",
            defaultValue = "apache/logging-log4j2"
    )
    private String repository;

    @Override
    public Integer call() throws Exception {
        // 1. Load Datasets
        List<Issue> issues = SqliteStorage.loadIssues(repository);
        List<Issue> prs = SqliteStorage.loadPullRequests(repository);
        List<AiAnalysisResult> aiResults = SqliteStorage.loadAiAnalysis(repository);
        List<IssueEmbedding> embeddings = SqliteStorage.loadEmbeddings(repository);
        List<JiraBridgeLink> jiraBridges = SqliteStorage.loadJiraBridges(repository);

        // Find target issue or pull request
        Issue target = null;
        for (Issue i : issues) {
            if (i.number() == issueNumber) {
                target = i;
                break;
            }
        }
        if (target == null) {
            for (Issue p : prs) {
                if (p.number() == issueNumber) {
                    target = p;
                    break;
                }
            }
        }

        if (target == null) {
            System.err.printf("Issue #%d not found in local data for '%s'. Please run 'sync' first.%n", issueNumber, repository);
            return 1;
        }

        System.out.println("==================================================");
        System.out.printf("Triage Report: %s #%d (%s)%n",
                target.isPullRequest() ? "PR" : "Issue",
                issueNumber,
                repository);
        System.out.println("==================================================");

        // A. Metadata Output
        String labelsStr = target.labels() == null || target.labels().isEmpty()
                ? "(none)"
                : target.labels().stream().map(Label::name).collect(Collectors.joining(", "));

        String authorName = target.user() != null ? target.user().login() : "unknown";
        String memberBadge = target.isOrgMember() ? " [Member]" : "";

        System.out.printf("[METADATA]%n");
        System.out.printf("  Title:      %s%n", target.title());
        System.out.printf("  Author:     %s%s%n", authorName, memberBadge);
        System.out.printf("  Labels:     %s%n", labelsStr);
        System.out.printf("  Comments:   %d%n%n", target.comments());

        // B. Severity Assessments
        SeverityAnalyzer severityAnalyzer = new SeverityAnalyzer();
        IssueAnalysis v1Analysis = severityAnalyzer.analyze(target);

        AiAnalysisResult targetAi = null;
        for (AiAnalysisResult ai : aiResults) {
            if (ai.issueNumber() == issueNumber) {
                targetAi = ai;
                break;
            }
        }

        System.out.printf("[SEVERITY ASSESSMENT]%n");
        System.out.printf("  • V1 Rule Score: %d (%s)%n", v1Analysis.score(), v1Analysis.severity());
        if (targetAi != null) {
            System.out.printf("  • AI Severity:   %s (Confidence: %.2f)%n", targetAi.severity(), targetAi.confidence());
            System.out.printf("  • AI Reason:     %s%n%n", targetAi.reason());
        } else {
            System.out.println("  • AI Severity:   (No AI evaluation found. Run 'analyze' first.)\n");
        }

        // C. Backlog Overlap & Duplicates (Semantic Similarity)
        double[] targetVector = null;
        for (IssueEmbedding emb : embeddings) {
            if (emb.issueNumber() == issueNumber) {
                targetVector = emb.vector();
                break;
            }
        }

        System.out.printf("[BACKLOG OVERLAP]%n");
        if (targetVector == null) {
            System.out.println("  No vector embedding found. Run 'duplicates' first to check for overlaps.\n");
        } else {
            List<String> similarIssues = new ArrayList<>();
            for (IssueEmbedding emb : embeddings) {
                if (emb.issueNumber() != issueNumber) {
                    double sim = cosineSimilarity(targetVector, emb.vector());
                    if (sim >= 0.70) {
                        // Find title of similar issue
                        String title = "Unknown Title";
                        for (Issue i : issues) {
                            if (i.number() == emb.issueNumber()) { title = i.title(); break; }
                        }
                        for (Issue p : prs) {
                            if (p.number() == emb.issueNumber()) { title = p.title(); break; }
                        }
                        similarIssues.add(String.format("#%d - %s (%.2f Similarity)", emb.issueNumber(), title, sim));
                    }
                }
            }
            if (similarIssues.isEmpty()) {
                System.out.println("  No duplicate groups detected above the 70% threshold.\n");
            } else {
                System.out.println("  Potential duplicates/related issues detected:");
                for (String line : similarIssues) {
                    System.out.println("    - " + line);
                }
                System.out.println();
            }
        }

        // D. Ecosystem / JIRA Bridges
        System.out.printf("[ECOSYSTEM / JIRA BRIDGES]%n");
        List<JiraBridgeLink> filteredBridges = jiraBridges.stream()
                .filter(b -> b.localNumber() == issueNumber)
                .toList();

        if (filteredBridges.isEmpty()) {
            System.out.println("  No ecosystem connections or JIRA bridge matches found.\n");
        } else {
            for (JiraBridgeLink b : filteredBridges) {
                System.out.printf("  • Connection: Matches %s#%d via JIRA Key [%s]%n",
                        b.externalRepo(),
                        b.externalNumber(),
                        b.jiraKey());
            }
            System.out.println();
        }

        // E. Action Log & Recommendation Logic
        System.out.printf("[RECOMMENDED ACTION LOG]%n");
        List<String> actions = new ArrayList<>();

        // Logic check: Hidden Critical
        if (targetAi != null) {
            boolean isCriticalAi = "Critical".equalsIgnoreCase(targetAi.severity());
            boolean isHighAi = "High".equalsIgnoreCase(targetAi.severity()) || isCriticalAi;
            boolean hasSecurityLabel = target.hasLabel("security") || target.hasLabel("security-label");
            boolean hasBugLabel = target.hasLabel("bug") || target.hasLabel("bug-label");

            if (!hasSecurityLabel && isCriticalAi) {
                actions.add("⚠ ACTION: Escalate and add SECURITY label (flagged as HIDDEN CRITICAL; AI predicted Critical but lacks security tags).");
            } else if (target.comments() > 15 && hasBugLabel && isHighAi) {
                actions.add(String.format("⚠ ACTION: Address user noise (has %d comments, bug label, and high AI prediction).", target.comments()));
            }
        }

        // Logic check: Stale
        long daysSinceUpdate = ChronoUnit.DAYS.between(Instant.parse(target.updated_at()), Instant.now());
        if (daysSinceUpdate > 30) {
            actions.add(String.format("⚠ ACTION: Check stale status (last activity was %d days ago).", daysSinceUpdate));
        }

        // Logic check: Missing reviewer (Review needed)
        if (target.isPullRequest() && target.comments() == 0) {
            actions.add("⚠ ACTION: Request code review (PR has had zero comments or interactions).");
        }

        if (actions.isEmpty()) {
            System.out.println("  ✔ No immediate actions required. Backlog state is clean.");
        } else {
            for (String act : actions) {
                System.out.println("  " + act);
            }
        }
        System.out.println("==================================================");

        return 0;
    }

    private double cosineSimilarity(double[] vecA, double[] vecB) {
        if (vecA.length != vecB.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}