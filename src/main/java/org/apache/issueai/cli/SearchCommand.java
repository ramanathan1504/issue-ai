package org.apache.issueai.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.issueai.llm.OllamaClient;
import org.apache.issueai.model.Issue;
import org.apache.issueai.model.IssueEmbedding;
import org.apache.issueai.model.RepoIssue;
import org.apache.issueai.storage.SqliteStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "search",
        description = "Search the local issue backlog semantically using vector embeddings"
)
public class SearchCommand implements Callable<Integer> {

    @Parameters(
            index = "0",
            description = "The plain text search query (wrap in quotes if it contains spaces)"
    )
    private String query;

    @Option(
            names = {"-r", "--repo"},
            description = "The target GitHub repository to analyze (owner/name)",
            defaultValue = "apache/logging-log4j2"
    )
    private String repository;

    @Option(
            names = {"-g", "--global"},
            description = "Perform a global search across all repositories in the database"
    )
    private boolean global;

    @Option(
            names = {"-m", "--model"},
            description = "Ollama embedding model to use",
            defaultValue = "all-minilm"
    )
    private String modelName;

    @Option(
            names = {"-n", "--limit"},
            description = "Number of top search results to return",
            defaultValue = "5"
    )
    private int limit;

    @Override
    public Integer call() throws Exception {
        Map<String, Issue> issueMap = new HashMap<>();
        List<IssueEmbedding> embeddings;

        if (global) {
            System.out.println("Loading global issues and pull requests from SQLite...");
            List<RepoIssue> allIssues = SqliteStorage.loadAllIssues();
            List<RepoIssue> allPrs = SqliteStorage.loadAllPullRequests();
            embeddings = SqliteStorage.loadAllEmbeddings();

            // Map using composite key: repository + "_" + issue_number
            allIssues.forEach(ri -> issueMap.put(ri.repository() + "_" + ri.issue().number(), ri.issue()));
            allPrs.forEach(ri -> issueMap.put(ri.repository() + "_" + ri.issue().number(), ri.issue()));
        } else {
            List<Issue> issues = SqliteStorage.loadIssues(repository);
            List<Issue> prs = SqliteStorage.loadPullRequests(repository);
            embeddings = SqliteStorage.loadEmbeddings(repository);

            issues.forEach(i -> issueMap.put(repository + "_" + i.number(), i));
            prs.forEach(p -> issueMap.put(repository + "_" + p.number(), p));
        }

        if (embeddings.isEmpty()) {
            System.err.println("No vector embeddings found in the database. Please run 'duplicates' first to generate them.");
            return 1;
        }

        System.out.printf("Generating semantic vector for query: \"%s\"...%n", query);
        OllamaClient client = new OllamaClient(modelName);
        double[] queryVector;

        try {
            queryVector = client.generateEmbedding(query);
        } catch (IOException | InterruptedException e) {
            System.err.printf("  ↳ [Error] Failed to generate embedding: %s%n", e.getMessage());
            return 1;
        }

        System.out.println("Scanning vectors and calculating cosine similarity...\n");
        List<SearchResult> results = new ArrayList<>();

        for (IssueEmbedding emb : embeddings) {
            String compositeKey = emb.repository() + "_" + emb.issueNumber();
            Issue matchedIssue = issueMap.get(compositeKey);
            if (matchedIssue != null) {
                double similarity = cosineSimilarity(queryVector, emb.vector());
                results.add(new SearchResult(emb.repository(), matchedIssue, similarity));
            }
        }

        results.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));

        System.out.printf("Top %d Global Semantic Search Results:%n", Math.min(limit, results.size()));
        System.out.println("==========================================================================");

        for (int i = 0; i < Math.min(limit, results.size()); i++) {
            SearchResult res = results.get(i);
            String typeBadge = res.issue().isPullRequest() ? "[PR]" : "[Issue]";
            System.out.printf(
                    "%d. %s %s#%d  Similarity: %.2f%n",
                    i + 1,
                    typeBadge,
                    res.repoName(),
                    res.issue().number(),
                    res.similarity());
            System.out.printf("   Title: %s%n%n", res.issue().title());
        }

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

    private static record SearchResult(String repoName, Issue issue, double similarity) {}
}