package org.apache.issueai.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;
import org.apache.issueai.analyzer.IssueAnalysis;
import org.apache.issueai.analyzer.Severity;
import org.apache.issueai.analyzer.SeverityAnalyzer;
import org.apache.issueai.model.AiAnalysisResult;
import org.apache.issueai.model.Issue;
import org.apache.issueai.model.IssueEmbedding;
import org.apache.issueai.model.JiraBridgeLink;
import org.apache.issueai.storage.SqliteStorage;

public class MarkdownReportWriter implements ReportWriter {

    @Override
    public void write(Path outputPath, String repository) throws IOException, SQLException {
        List<Issue> issues = SqliteStorage.loadIssues(repository);
        List<Issue> prs = SqliteStorage.loadPullRequests(repository);
        List<AiAnalysisResult> aiResults = SqliteStorage.loadAiAnalysis(repository);
        List<IssueEmbedding> embeddings = SqliteStorage.loadEmbeddings(repository);

        // Query Ecosystem Connections from SQLite
        List<String> inboundLinks = SqliteStorage.loadInboundLinks(repository);
        List<String> outboundLinks = SqliteStorage.loadOutboundLinks(repository);
        List<JiraBridgeLink> jiraBridges = SqliteStorage.loadJiraBridges(repository);

        Instant now = Instant.now();

        // 1. Gather and calculate Stale PRs detailed data (with Author & Org Member checks)
        List<String> stalePrLines = new ArrayList<>();
        for (Issue pr : prs) {
            try {
                long daysSinceUpdate = ChronoUnit.DAYS.between(Instant.parse(pr.updated_at()), now);
                if (daysSinceUpdate > 30) {
                    String authorName = pr.user() != null ? pr.user().login() : "unknown";
                    String orgStatus = pr.isOrgMember() ? " [Member]" : "";

                    stalePrLines.add(String.format(
                            "#%d - %s (Author: %s%s, %d days inactive)",
                            pr.number(),
                            pr.title(),
                            authorName,
                            orgStatus,
                            daysSinceUpdate));
                }
            } catch (Exception ignored) {
                // Ignore parsing errors
            }
        }

        // 2. Map AI results for fast hidden-critical lookup
        Map<Long, AiAnalysisResult> aiMap = aiResults.stream()
                .collect(Collectors.toMap(AiAnalysisResult::issueNumber, result -> result));

        List<Issue> hiddenCriticals = new ArrayList<>();
        for (Issue issue : issues) {
            AiAnalysisResult aiResult = aiMap.get(issue.number());
            if (aiResult != null) {
                boolean isCriticalAi = "Critical".equalsIgnoreCase(aiResult.severity());
                boolean isHighAi = "High".equalsIgnoreCase(aiResult.severity()) || isCriticalAi;

                boolean hasSecurityLabel = issue.hasLabel("security") || issue.hasLabel("security-label");
                boolean hasBugLabel = issue.hasLabel("bug") || issue.hasLabel("bug-label");

                if (!hasSecurityLabel && isCriticalAi) {
                    hiddenCriticals.add(issue);
                } else if (issue.comments() > 15 && hasBugLabel && isHighAi) {
                    hiddenCriticals.add(issue);
                }
            }
        }

        // 3. Analyze Critical Issues (V1 severity)
        SeverityAnalyzer severityAnalyzer = new SeverityAnalyzer();
        List<IssueAnalysis> criticalAnalyses = issues.stream()
                .map(severityAnalyzer::analyze)
                .filter(a -> a.severity() == Severity.CRITICAL)
                .toList();

        // 4. Calculate detailed duplicate groups
        List<List<Issue>> duplicateClusters = getDuplicateClusters(issues, embeddings);

        // 5. Construct Markdown Output
        StringBuilder md = new StringBuilder();
        md.append("# Apache Log4j Weekly Health Report\n\n");
        md.append("Generated: ").append(LocalDate.now().toString()).append("\n\n");

        md.append("## Summary\n\n");
        md.append("- Open Issues: ").append(issues.size()).append("\n");
        md.append("- Open PRs: ").append(prs.size()).append("\n");
        md.append("- Critical Issues Count: ").append(criticalAnalyses.size()).append("\n");
        md.append("- Hidden Critical Count: ").append(hiddenCriticals.size()).append("\n");
        md.append("- Duplicate Clusters Count: ").append(duplicateClusters.size()).append("\n");
        md.append("- Stale PRs Count: ").append(stalePrLines.size()).append("\n\n");

        md.append("## Critical Issues\n");
        if (criticalAnalyses.isEmpty()) {
            md.append("(none)\n");
        } else {
            int i = 1;
            for (IssueAnalysis a : criticalAnalyses) {
                String authorName = a.issue().user() != null ? a.issue().user().login() : "unknown";
                String orgStatus = a.issue().isOrgMember() ? " [Member]" : "";
                md.append(String.format(
                        "%d. #%d - %s (Author: %s%s)%n",
                        i++,
                        a.issue().number(),
                        a.issue().title(),
                        authorName,
                        orgStatus));
            }
        }
        md.append("\n");

        md.append("## Hidden Critical\n");
        if (hiddenCriticals.isEmpty()) {
            md.append("(none)\n");
        } else {
            int i = 1;
            for (Issue issue : hiddenCriticals) {
                String authorName = issue.user() != null ? issue.user().login() : "unknown";
                String orgStatus = issue.isOrgMember() ? " [Member]" : "";
                md.append(String.format(
                        "%d. #%d - %s (Author: %s%s)%n",
                        i++,
                        issue.number(),
                        issue.title(),
                        authorName,
                        orgStatus));
            }
        }
        md.append("\n");

        md.append("## Duplicate Groups\n");
        if (duplicateClusters.isEmpty()) {
            md.append("No duplicate groups detected.\n");
        } else {
            md.append(duplicateClusters.size()).append(" potential duplicate groups detected using semantic indexing:\n");
            for (int k = 0; k < duplicateClusters.size(); k++) {
                List<Issue> cluster = duplicateClusters.get(k);
                String issueNumbersStr = cluster.stream()
                        .map(issue -> "#" + issue.number())
                        .collect(Collectors.joining(", "));
                md.append(String.format("- **Group %d:** %s%n", k + 1, issueNumbersStr));
            }
        }
        md.append("\n");

        // Ecosystem Connections & Downstream Impact Section
        md.append("## Ecosystem Connections & Downstream Impact\n\n");

        md.append("### JIRA Bridge Cross-Project Overlaps\n");
        if (jiraBridges.isEmpty()) {
            md.append("(none)\n\n");
        } else {
            md.append("The following issues discuss identical JIRA keys across repositories:\n");
            for (JiraBridgeLink bridge : jiraBridges) {
                md.append(String.format(
                        "- Our Issue **#%d** matches **%s#%d** via JIRA Key **%s**%n",
                        bridge.localNumber(),
                        bridge.externalRepo(),
                        bridge.externalNumber(),
                        bridge.jiraKey()
                ));
            }
            md.append("\n");
        }

        md.append("### Downstream References (Other projects linking to us)\n");
        if (inboundLinks.isEmpty()) {
            md.append("(none)\n\n");
        } else {
            for (String link : inboundLinks) {
                md.append("- ").append(link).append("\n");
            }
            md.append("\n");
        }

        md.append("### Upstream Dependencies (We are linking to other projects)\n");
        if (outboundLinks.isEmpty()) {
            md.append("(none)\n\n");
        } else {
            for (String link : outboundLinks) {
                md.append("- ").append(link).append("\n");
            }
            md.append("\n");
        }

        md.append("## Stale PRs\n");
        if (stalePrLines.isEmpty()) {
            md.append("No stale pull requests detected.\n");
        } else {
            md.append(stalePrLines.size()).append(" pull requests have had no activity for over 30 days:\n");
            int i = 1;
            for (String line : stalePrLines) {
                md.append(String.format("%d. %s%n", i++, line));
            }
        }

        // Ensure directories exist and save the file
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, md.toString().trim() + "\n", StandardCharsets.UTF_8);
    }

    private List<List<Issue>> getDuplicateClusters(List<Issue> issues, List<IssueEmbedding> embeddings) {
        List<List<Issue>> clusters = new ArrayList<>();
        if (embeddings.isEmpty()) {
            return clusters;
        }

        Map<Long, double[]> vectorMap = new HashMap<>();
        for (IssueEmbedding emb : embeddings) {
            vectorMap.put(emb.issueNumber(), emb.vector());
        }

        int size = issues.size();
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            adj.add(new ArrayList<>());
        }

        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                double[] vecA = vectorMap.get(issues.get(i).number());
                double[] vecB = vectorMap.get(issues.get(j).number());
                if (vecA != null && vecB != null) {
                    double sim = cosineSimilarity(vecA, vecB);
                    if (sim >= 0.70) {
                        adj.get(i).add(j);
                        adj.get(j).add(i);
                    }
                }
            }
        }

        boolean[] visited = new boolean[size];
        for (int i = 0; i < size; i++) {
            if (!visited[i]) {
                List<Issue> cluster = new ArrayList<>();
                Queue<Integer> queue = new LinkedList<>();

                visited[i] = true;
                queue.add(i);

                while (!queue.isEmpty()) {
                    int current = queue.poll();
                    cluster.add(issues.get(current));
                    for (int neighbor : adj.get(current)) {
                        if (!visited[neighbor]) {
                            visited[neighbor] = true;
                            queue.add(neighbor);
                        }
                    }
                }

                if (cluster.size() > 1) {
                    clusters.add(cluster);
                }
            }
        }
        return clusters;
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