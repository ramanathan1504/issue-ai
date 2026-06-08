package org.apache.issueai.cli;

import java.util.List;
import java.util.concurrent.Callable;
import org.apache.issueai.analyzer.IssueAnalysis;
import org.apache.issueai.analyzer.Severity;
import org.apache.issueai.analyzer.SeverityAnalyzer;
import org.apache.issueai.model.Issue;
import org.apache.issueai.model.Label;
import org.apache.issueai.storage.JsonStorage;
import picocli.CommandLine.Command;

@Command(
        name = "critical",
        description = "Find critical issues using local data"
)
public class CriticalCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        List<Issue> issues = JsonStorage.loadIssues();
        if (issues.isEmpty()) {
            System.err.println("No local data found. Please run the 'sync' command first.");
            return 1;
        }

        SeverityAnalyzer analyzer = new SeverityAnalyzer();

        List<IssueAnalysis> analyses = issues.stream()
                .filter(issue -> !issue.isPullRequest())
                .map(analyzer::analyze)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .toList();

        long critical = analyses.stream().filter(a -> a.severity() == Severity.CRITICAL).count();
        long high = analyses.stream().filter(a -> a.severity() == Severity.HIGH).count();
        long medium = analyses.stream().filter(a -> a.severity() == Severity.MEDIUM).count();
        long low = analyses.stream().filter(a -> a.severity() == Severity.LOW).count();

        System.out.println("Repository: apache/logging-log4j2 (Offline Mode)");
        System.out.println();

        System.out.println("Critical: " + critical);
        System.out.println("High: " + high);
        System.out.println("Medium: " + medium);
        System.out.println("Low: " + low);

        System.out.println();
        System.out.println("CRITICAL");
        System.out.println("========");
        analyses.stream()
                .filter(a -> a.severity() == Severity.CRITICAL)
                .forEach(this::printIssue);

        System.out.println();
        System.out.println("HIGH");
        System.out.println("====");
        analyses.stream()
                .filter(a -> a.severity() == Severity.HIGH)
                .limit(10)
                .forEach(this::printIssue);

        System.out.println();
        System.out.println("MEDIUM");
        System.out.println("====");
        analyses.stream()
                .filter(a -> a.severity() == Severity.MEDIUM)
                .limit(10)
                .forEach(this::printIssue);

        return 0;
    }

    private void printIssue(IssueAnalysis a) {
        String labels = a.issue().labels() == null
                ? "[]"
                : a.issue().labels().stream()
                .map(Label::name)
                .toList()
                .toString();

        System.out.printf(
                "#%d Score=%d Labels=%s [%s] %s%n",
                a.issue().number(),
                a.score(),
                labels,
                a.reason(),
                a.issue().title());
    }
}