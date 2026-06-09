package org.apache.issueai.cli;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.issueai.github.GitHubClient;
import org.apache.issueai.llm.OllamaClient;
import org.apache.issueai.model.Issue;
import org.apache.issueai.storage.SqliteStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "sync",
        description = "Pull live GitHub issues and PRs and save to local SQLite tables"
)
public class SyncCommand implements Callable<Integer> {
    private static final Logger LOGGER = LogManager.getLogger(SyncCommand.class);
    @Option(
            names = {"--me"},
            description = "Dynamically sync and build your personal contribution profile from GitHub"
    )
    private boolean me;

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
        if (me) {
            return syncPersonalProfile();
        }

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
    private int syncPersonalProfile() throws Exception {
        String username = SqliteStorage.loadConfig("github.username");
        String embedModel = SqliteStorage.loadConfig("ollama.model.embedding");
        String guidanceModel = SqliteStorage.loadConfig("ollama.model.guidance");
        String drivePathsStr = SqliteStorage.loadConfig("drive.paths");

        // Load the last personal sync time from SQLite config
        String lastSyncedMe = SqliteStorage.loadConfig("developer.last_synced_at");
        Instant startRunTime = Instant.now();

        if (username == null || username.trim().isEmpty()) {
            LOGGER.error("No GitHub username configured. Please run 'setup' first.");
            return 1;
        }
        if (embedModel == null) {
            embedModel = "all-minilm";
        }
        if (guidanceModel == null) {
            guidanceModel = "qwen2.5:7b";
        }

        // --- PRE-FLIGHT OLLAMA VERIFICATION (The Rate-Limit Shield) ---
        LOGGER.info("Performing pre-flight Ollama verification check...");
        OllamaClient embedOllama = new OllamaClient(embedModel);
        OllamaClient guideOllama = new OllamaClient(guidanceModel);

        if (!embedOllama.isModelAvailable()) {
            LOGGER.error("Error: Required embedding model '{}' is not pulled locally.", embedModel);
            LOGGER.error("Please run: ollama pull {}", embedModel);
            LOGGER.error("Or run 'setup' to choose an available model.");
            return 1;
        }
        if (!guideOllama.isModelAvailable()) {
            LOGGER.error("Error: Required guidance model '{}' is not pulled locally.", guidanceModel);
            LOGGER.error("Please run: ollama pull {}", guidanceModel);
            LOGGER.error("Or run 'setup' to choose an available model (like qwen2.5:0.5b).");
            return 1;
        }
        LOGGER.info("  ✔ Pre-flight verification successful. All local AI models are ready.");

        String searchQuery;
        if (lastSyncedMe != null && !lastSyncedMe.trim().isEmpty()) {
            // Incremental Delta Sync: Query only PRs merged since our last run
            searchQuery = String.format("author:%s type:pr is:merged merged:>=%s", username, lastSyncedMe);
            LOGGER.info("Starting incremental Personal Sync for '{}' (fetching changes merged since {})...", username, lastSyncedMe);
        } else {
            // Initial Full Sync: Query all merged PRs from the last 365 days
            String sinceDate = LocalDate.now().minusYears(1).toString() + "T00:00:00Z";
            searchQuery = String.format("author:%s type:pr is:merged created:>=%s", username, sinceDate);
            LOGGER.info("Starting initial Personal Sync for '{}' (Timeline: >= {})...", username, sinceDate);
        }

        LOGGER.info("Querying GitHub Search API for merged contributions...");

        GitHubClient client = new GitHubClient(System.getenv("GITHUB_TOKEN"));
        List<Issue> mergedPrs = client.searchIssuesAndPrs(searchQuery);

        // A. Crawl Diffs and Generate PR Summaries (Skip if empty, but DO NOT exit early)
        if (mergedPrs.isEmpty()) {
            LOGGER.info("No new merged pull requests found since the last execution.");
        } else {
            LOGGER.info("  ↳ Found {} merged pull requests on GitHub.", mergedPrs.size());
            StringBuilder experienceDoc = new StringBuilder();

            for (Issue pr : mergedPrs) {
                LOGGER.info("  Processing PR #{}...", pr.number());
                experienceDoc.append("Title: ").append(pr.title()).append("\n");

                if (pr.body() != null) {
                    String trimmedBody = pr.body().trim();
                    if (trimmedBody.length() > 500) {
                        trimmedBody = trimmedBody.substring(0, 500) + "...";
                    }
                    experienceDoc.append("Body: ").append(trimmedBody).append("\n\n");
                }

                String sourceRepo = pr.getRepositoryOwnerAndName();
                String[] repoParts = sourceRepo.split("/");
                String owner = repoParts[0];
                String repoName = repoParts[1];
                List<String> modifiedFiles = new ArrayList<>();

                try {
                    modifiedFiles = client.getPullRequestFiles(owner, repoName, pr.number());
                    SqliteStorage.savePersonalCodeFootprint(sourceRepo, pr.number(), modifiedFiles);
                    LOGGER.info("    ↳ Logged {} modified file paths in '{}'.", modifiedFiles.size(), sourceRepo);
                } catch (Exception e) {
                    LOGGER.warn("    ↳ [Warning] Could not extract changed files from {}: {}", sourceRepo, e.getMessage());
                }

                // Generate PR Story Note if it does not exist in SQLite
                try {
                    if (!SqliteStorage.hasPersonalPrMemory(sourceRepo, pr.number())) {
                        LOGGER.info("    Generating automated Development Story for PR #{} using model '{}'...", pr.number(), guidanceModel);

                        String summaryPrompt = String.format("""
                                You are an Apache Log4j maintainer.
                                Summarize the following pull request as a personal development story.
                                Explain:
                                1. What problem was solved.
                                2. What files were changed and why.
                                3. What feedback was addressed during code review.
                                
                                PR Title: %s
                                PR Description: %s
                                Files Changed: %s
                                
                                Keep the story concise and technical.
                                """, pr.title(), pr.body(), String.join(", ", modifiedFiles));

                        String generatedStory = guideOllama.generateJson(summaryPrompt);
                        double[] storyVector = embedOllama.generateEmbedding(generatedStory);

                        SqliteStorage.savePersonalPrMemory(
                                sourceRepo,
                                pr.number(),
                                String.join(", ", modifiedFiles),
                                generatedStory,
                                storyVector
                        );
                        LOGGER.info("    ↳ Saved PR #{} Development Story to local SQLite memory.", pr.number());
                    }
                } catch (Exception e) {
                    LOGGER.warn("    ↳ [Warning] Could not generate AI development story for PR #{}: {}", pr.number(), e.getMessage());
                }
            }

            // Generate Semantic Developer Expertise Vector
            LOGGER.info("Generating semantic Developer Expertise Vector using model '{}'...", embedModel);
            try {
                double[] vector = embedOllama.generateEmbedding(experienceDoc.toString().trim());
                String jsonVector = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(vector);
                SqliteStorage.saveConfig("developer.vector", jsonVector);
                LOGGER.info("  ↳ Personal Developer Expertise Vector successfully saved to SQLite.");
            } catch (Exception e) {
                LOGGER.error("  ↳ [Error] Failed to generate embedding vector: {}", e.getMessage());
                return 1;
            }
        }

        // B. Sync Google Drive AI Studio Logs (The Ingestion Engine - Always Runs)
        if (drivePathsStr != null && !drivePathsStr.trim().isEmpty()) {
            LOGGER.info("Scanning Google Drive paths recursively for AI Studio logs...");
            String[] paths = drivePathsStr.split(",");

            for (String path : paths) {
                java.nio.file.Path localPath = java.nio.file.Paths.get(path.trim());
                if (!java.nio.file.Files.exists(localPath)) {
                    LOGGER.warn("Google Drive directory does not exist locally: {}", localPath.toAbsolutePath());
                    continue;
                }

                try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(localPath)) {
                    List<java.nio.file.Path> files = stream
                            .filter(java.nio.file.Files::isRegularFile) // Exclude directories
                            .filter(p -> {
                                String name = p.toString().toLowerCase();
                                // Exclude binary files and macOS metadata system files
                                return !name.endsWith(".png")
                                        && !name.endsWith(".pdf")
                                        && !name.endsWith(".zip")
                                        && !name.endsWith(".jpg")
                                        && !name.endsWith(".jpeg")
                                        && !name.endsWith(".gif")
                                        && !name.endsWith(".jar")
                                        && !name.endsWith(".ds_store")
                                        && !name.endsWith(".docx")
                                        && !name.endsWith(".class");
                            })
                            .toList();

                    LOGGER.info("  ↳ Found {} total active discussion files inside '{}' and its subfolders.", files.size(), localPath.getFileName());

                    for (java.nio.file.Path file : files) {
                        String fileName = file.getFileName().toString();
                        String absolutePath = file.toAbsolutePath().toString();
                        long lastModified = java.nio.file.Files.getLastModifiedTime(file).toMillis();

                        // 1. Clean Content-Based Comparison: Load previously cached text
                        String cachedContent = SqliteStorage.loadPersonalChatContent(absolutePath);
                        String content = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);

                        // 2. Only ingest if the file is new or the text content actually changed!
                        if (cachedContent == null || !content.equals(cachedContent)) {
                            LOGGER.info("    Ingesting new or modified chat log: {}...", fileName);
                            try {
                                double[] chatVector = embedOllama.generateEmbedding(content);
                                SqliteStorage.savePersonalChatMemory(absolutePath, fileName, lastModified, content, chatVector);
                                LOGGER.info("    ↳ Successfully cached and indexed '{}'.", fileName);
                            } catch (Exception e) {
                                LOGGER.warn("    ↳ [Warning] Could not generate embedding for '{}': {}", fileName, e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to scan Google Drive directory recursively '{}': {}", localPath.toAbsolutePath(), e.getMessage());
                }
            }
        } else {
            LOGGER.info("No Google Drive paths configured for AI log ingestion. Skipping this step.");
        }

        // C. Update the sync timestamp in SQLite on success
        SqliteStorage.saveConfig("developer.last_synced_at", startRunTime.toString());

        LOGGER.info("Personal Sync completed successfully. Your complete developer footprint and AI logs are cached locally!");
        return 0;
    }
}