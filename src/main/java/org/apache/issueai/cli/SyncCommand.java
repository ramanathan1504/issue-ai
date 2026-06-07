package org.apache.issueai.cli;

import org.apache.issueai.github.GitHubClient;
import org.apache.issueai.model.Issue;
import picocli.CommandLine.Command;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "sync")
public class SyncCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {

        GitHubClient client =
                new GitHubClient(System.getenv("GITHUB_TOKEN"));

        List<Issue> allIssues =
                client.getOpenIssues("apache", "logging-log4j2");

        List<Issue> realIssues = allIssues.stream()
                .filter(issue -> !issue.isPullRequest())
                .toList();

        System.out.println("Repository: apache/logging-log4j2");
        System.out.println("Open Issues: " + realIssues.size());

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