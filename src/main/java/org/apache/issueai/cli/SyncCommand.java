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

@Command(
        name = "sync",
        description = "Pull live GitHub issues and PRs and save to local SQLite tables"
)
public class SyncCommand implements Callable<Integer> {

    @Option(
            names = {"-r", "--repo"},
            description = "The target GitHub repository to analyze (owner/name)",
            defaultValue = "apache/logging-log4j2"
    )
    private String repository;

    @Option(
            names = {"-a", "--all"},
            description = "Sequentially synchronize all active repositories seeded in SQLite"
    )
    private boolean all;

    @Option(
            names = {"--add"},
            description = "Add a new GitHub repository to the local monitoring database"
    )
    private String addRepo;

    @Option(
            names = {"--remove"},
            description = "Remove a GitHub repository from the local monitoring database"
    )
    private String removeRepo;

    @Override
    public Integer call() throws Exception {
        // A. Handle Registry: Add Repository
        if (addRepo != null) {
            SqliteStorage.saveMonitoredRepository(addRepo, true);
            System.out.printf("Successfully registered '%s' in SQLite. You can now sync it anytime!%n", addRepo);
            return 0;
        }

        // B. Handle Registry: Remove Repository
        if (removeRepo != null) {
            SqliteStorage.deleteMonitoredRepository(removeRepo);
            System.out.printf("Successfully removed '%s' from SQLite monitoring database.%n", removeRepo);
            return 0;
        }

        // C. Batch Sync All Enabled Repositories
        if (all) {
            List<String> activeRepos = SqliteStorage.loadMonitoredRepositories();
            if (activeRepos.isEmpty()) {
                System.out.println("No active monitored repositories found in your local SQLite registry.");
                return 0;
            }
            System.out.printf("Starting batch sync for %d active repositories...%n%n", activeRepos.size());
            for (String repo : activeRepos) {
                System.out.println("==================================================");
                System.out.printf("Syncing: %s%n", repo);
                System.out.println("==================================================");
                try {
                    syncRepository(repo);
                } catch (Exception e) {
                    System.err.printf("  ↳ [Error] Failed to sync '%s': %s%n%n", repo, e.getMessage());
                }
            }
            System.out.println("==================================================");
            System.out.println("Batch synchronization completed successfully.");
            System.out.println("==================================================");
            return 0;
        }

        // D. Fallback to standard single sync
        return syncRepository(repository);
    }

    private int syncRepository(String targetRepo) throws Exception {
        String[] parts = targetRepo.split("/");
        if (parts.length != 2) {
            System.err.printf("Invalid repository format '%s'. Please use 'owner/name'.%n", targetRepo);
            return 1;
        }
        String owner = parts[0];
        String repoName = parts[1];

        // 1. Check SQLite for previous sync time
        String since = SqliteStorage.loadLastSyncedAt(targetRepo);
        Instant startRunTime = Instant.now();

        if (since != null) {
            System.out.printf("  ↳ Performing delta sync (fetching changes since %s)...%n", since);
        } else {
            System.out.println("  ↳ Performing full sync...");
        }

        GitHubClient client = new GitHubClient(System.getenv("GITHUB_TOKEN"));

        // 2. Query GitHub passing the dynamic "since" timestamp
        List<Issue> allIssues = client.getOpenIssues(owner, repoName, since);

        List<Issue> realIssues = allIssues.stream()
                .filter(issue -> !issue.isPullRequest())
                .toList();

        List<Issue> pullRequests = allIssues.stream()
                .filter(Issue::isPullRequest)
                .toList();

        // 3. Save delta records (new issues insert; modified issues overwrite automatically!)
        SqliteStorage.saveIssues(targetRepo, realIssues);
        SqliteStorage.saveIssues(targetRepo, pullRequests);

        // 4. Update the sync timestamp in SQLite
        SqliteStorage.updateLastSyncedAt(targetRepo, startRunTime.toString());

        System.out.println("Repository: " + targetRepo);
        System.out.println("Open Issues Saved: " + realIssues.size());
        System.out.println("Open PRs Saved: " + pullRequests.size());

        printMostCommented(realIssues);
        printOldest(realIssues);
        printRecentlyUpdated(realIssues);
        System.out.println();

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