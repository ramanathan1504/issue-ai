package org.apache.issueai.cli;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import org.apache.issueai.analyzer.Severity;
import org.apache.issueai.analyzer.SeverityAnalyzer;
import org.apache.issueai.model.Issue;
import org.apache.issueai.model.IssueEmbedding;
import org.apache.issueai.model.TrendSnapshot;
import org.apache.issueai.storage.JsonStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "trend",
        description = "Track and visualize weekly project health trends"
)
public class TrendCommand implements Callable<Integer> {

    @Option(
            names = {"-s", "--save"},
            description = "Compute and save a new historical trend snapshot for today"
    )
    private boolean save;

    @Override
    public Integer call() throws Exception {
        if (save) {
            saveTodaySnapshot();
        }

        List<TrendSnapshot> snapshots = JsonStorage.loadTrendSnapshots();

        if (snapshots.isEmpty()) {
            System.out.println("No historical trend data found.");
            System.out.println("Run with 'trend --save' first to create your first snapshot.");
            return 0;
        }

        System.out.println("Project Health Trends Dashboard");
        System.out.println("===============================\n");

        System.out.printf("%-12s | %-15s | %-13s | %-18s | %-18s%n",
                "Date", "Critical Issues", "High Priority", "Stale PRs", "Duplicate Clusters");
        System.out.println("-------------+-----------------+---------------+--------------------+-------------------");

        for (TrendSnapshot s : snapshots) {
            System.out.printf("%-12s | %-15d | %-13d | %-18d | %-18d%n",
                    s.date(), s.criticalIssues(), s.highPriority(), s.stalePrs(), s.duplicateClusters());
        }
        System.out.println();

        if (snapshots.size() > 1) {
            TrendSnapshot first = snapshots.get(0);
            TrendSnapshot latest = snapshots.get(snapshots.size() - 1);

            System.out.println("Trend Analysis (Comparison with oldest snapshot on " + first.date() + "):");

            int criticalDiff = latest.criticalIssues() - first.criticalIssues();
            int highDiff = latest.highPriority() - first.highPriority();
            int stalePrDiff = latest.stalePrs() - first.stalePrs();
            int duplicateDiff = latest.duplicateClusters() - first.duplicateClusters();

            printTrendMessage("Critical Issues", criticalDiff);
            printTrendMessage("High Priority Issues", highDiff);
            printTrendMessage("Stale PRs", stalePrDiff);
            printTrendMessage("Duplicate Clusters", duplicateDiff);
        }

        return 0;
    }

    private void printTrendMessage(String metric, int diff) {
        if (diff < 0) {
            System.out.printf("  ✔ %s decreased by %d%n", metric, Math.abs(diff));
        } else if (diff > 0) {
            System.out.printf("  ⚠ %s increased by %d%n", metric, diff);
        } else {
            System.out.printf("  • %s remained unchanged%n", metric);
        }
    }

    private void saveTodaySnapshot() throws Exception {
        List<Issue> issues = JsonStorage.loadIssues();
        List<Issue> prs = JsonStorage.loadPullRequests();
        List<IssueEmbedding> embeddings = JsonStorage.loadEmbeddings();

        if (issues.isEmpty()) {
            System.err.println("No local issues found to save today's snapshot. Please run 'sync' first.");
            return;
        }

        SeverityAnalyzer severityAnalyzer = new SeverityAnalyzer();
        long criticalCount = issues.stream()
                .map(severityAnalyzer::analyze)
                .filter(a -> a.severity() == Severity.CRITICAL)
                .count();

        long highCount = issues.stream()
                .map(severityAnalyzer::analyze)
                .filter(a -> a.severity() == Severity.HIGH)
                .count();

        Instant now = Instant.now();
        long staleCount = prs.stream()
                .filter(pr -> {
                    try {
                        return ChronoUnit.DAYS.between(Instant.parse(pr.updated_at()), now) > 30;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();

        int dupCount = calculateDuplicateGroupsCount(issues, embeddings);

        String today = LocalDate.now().toString();
        TrendSnapshot snapshot = new TrendSnapshot(
                today,
                (int) criticalCount,
                (int) highCount,
                (int) staleCount,
                dupCount
        );

        JsonStorage.saveTrendSnapshot(snapshot);
        System.out.println("  ↳ Saved snapshot for today (" + today + "):");
        System.out.printf("    Critical: %d, High: %d, Stale PRs: %d, Duplicates: %d%n%n",
                criticalCount, highCount, staleCount, dupCount);
    }

    private int calculateDuplicateGroupsCount(List<Issue> issues, List<IssueEmbedding> embeddings) {
        if (embeddings.isEmpty()) {
            return 0;
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
        int duplicateGroupsCount = 0;

        for (int i = 0; i < size; i++) {
            if (!visited[i]) {
                List<Integer> component = new ArrayList<>();
                Queue<Integer> queue = new LinkedList<>();

                visited[i] = true;
                queue.add(i);

                while (!queue.isEmpty()) {
                    int current = queue.poll();
                    component.add(current);
                    for (int neighbor : adj.get(current)) {
                        if (!visited[neighbor]) {
                            visited[neighbor] = true;
                            queue.add(neighbor);
                        }
                    }
                }

                if (component.size() > 1) {
                    duplicateGroupsCount++;
                }
            }
        }
        return duplicateGroupsCount;
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