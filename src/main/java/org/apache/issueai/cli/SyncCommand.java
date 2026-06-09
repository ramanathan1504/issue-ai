package org.apache.issueai.cli;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.issueai.github.GitHubClient;
import org.apache.issueai.model.Issue;
import org.apache.issueai.storage.SqliteStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "sync")
public class SyncCommand implements Callable<Integer> {

    @Option(
            names = {"-r", "--repo"},
            description = "The target GitHub repository to analyze (owner/name)",
            defaultValue = "apache/logging-log4j2"
    )
    private String repository;

    @Override
    public Integer call() throws Exception {
        String[] parts = repository.split("/");
        if (parts.length != 2) {
            System.err.println("Invalid repository format. Please use 'owner/name' (e.g., 'apache/kafka').");
            return 1;
        }
        String owner = parts[0];
        String repoName = parts[1];

        GitHubClient client = new GitHubClient(System.getenv("GITHUB_TOKEN"));

        // Dynamically fetch open issues for the requested owner/repo
        List<Issue> allIssues = client.getOpenIssues(owner, repoName);

        List<Issue> realIssues = allIssues.stream()
                .filter(issue -> !issue.isPullRequest())
                .toList();

        List<Issue> pullRequests = allIssues.stream()
                .filter(Issue::isPullRequest)
                .toList();

        // Save data to SQLite using the target repository namespace
        SqliteStorage.saveIssues(repository, realIssues);
        SqliteStorage.saveIssues(repository, pullRequests);

        System.out.println("Repository: " + repository);
        System.out.println("Open Issues Saved: " + realIssues.size());
        System.out.println("Open PRs Saved: " + pullRequests.size());

        printMostCommented(realIssues);
        printOldest(realIssues);
        printRecentlyUpdated(realIssues);

        return 0;
    }

    private void printMostCommented(List<Issue> issues) {
        System.out.println("\nTop 10 Most Commented Issues");

        issues.stream()
                .sorted(Comparator.comparingInt(Issue::comments).reversed())
                .limit(10)
                .forEach(issue ->
                        System.out.printf(
                                "#%d (%d comments) %s%n",
                                issue.number(),
                                issue.comments(),
                                issue.title()));
    }

    private void printOldest(List<Issue> issues) {
        System.out.println("\nOldest Open Issues");

        issues.stream()
                .sorted(Comparator.comparing(
                        issue -> Instant.parse(issue.created_at())))
                .limit(10)
                .forEach(issue ->
                        System.out.printf(
                                "#%d %s%n",
                                issue.number(),
                                issue.title()));
    }

    private void printRecentlyUpdated(List<Issue> issues) {
        System.out.println("\nRecently Updated");

        issues.stream()
                .sorted(Comparator.comparing(
                                (Issue issue) -> Instant.parse(issue.updated_at()))
                        .reversed())
                .limit(10)
                .forEach(issue ->
                        System.out.printf(
                                "#%d %s%n",
                                issue.number(),
                                issue.title()));
    }
}