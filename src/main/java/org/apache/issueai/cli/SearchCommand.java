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
        // 1. Load issues and PRs to map matches back to titles
        List<Issue> issues = SqliteStorage.loadIssues(repository);
        List<Issue> prs = SqliteStorage.loadPullRequests(repository);

        if (issues.isEmpty() && prs.isEmpty()) {
            System.err.printf("No local data found for '%s'. Please run 'sync' first.%n", repository);
            return 1;
        }

        // Combine both collections into a single lookup map
        Map<Long, Issue> issueMap = new HashMap<>();
        issues.forEach(i -> issueMap.put(i.number(), i));
        prs.forEach(p -> issueMap.put(p.number(), p));

        // 2. Load cached vector embeddings from SQLite
        List<IssueEmbedding> embeddings = SqliteStorage.loadEmbeddings(repository);
        if (embeddings.isEmpty()) {
            System.err.printf("No vector embeddings found for '%s'. Please run 'duplicates' first to generate them.%n", repository);
            return 1;
        }

        System.out.printf("Generating semantic vector for query: \"%s\"...%n", query);
        OllamaClient client = new OllamaClient(modelName);
        double[] queryVector;

        try {
            // Generate the embedding vector for the search query
            queryVector = client.generateEmbedding(query);
        } catch (IOException | InterruptedException e) {
            System.err.printf("  ↳ [Error] Failed to generate embedding for query: %s%n", e.getMessage());
            System.err.println("Verify Ollama is running ('ollama serve') and the model is pulled ('ollama pull " + modelName + "').");
            return 1;
        }

        System.out.printf("Scanning database and calculating cosine similarity...%n%n");
        List<SearchResult> results = new ArrayList<>();

        // 3. Calculate similarity score between query and each issue vector
        for (IssueEmbedding emb : embeddings) {
            Issue matchedIssue = issueMap.get(emb.issueNumber());
            if (matchedIssue != null) {
                double similarity = cosineSimilarity(queryVector, emb.vector());
                results.add(new SearchResult(matchedIssue, similarity));
            }
        }

        // Sort results by similarity score descending
        results.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));

        // 4. Output the top matched search results
        System.out.printf("Top %d Semantic Search Results for '%s':%n", Math.min(limit, results.size()), repository);
        System.out.println("==========================================================================");

        for (int i = 0; i < Math.min(limit, results.size()); i++) {
            SearchResult res = results.get(i);
            String typeBadge = res.issue().isPullRequest() ? "[PR]" : "[Issue]";
            System.out.printf(
                    "%d. %s #%d  Similarity: %.2f%n",
                    i + 1,
                    typeBadge,
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

    // Helper class to encapsulate sorted results
    private static record SearchResult(Issue issue, double similarity) {}
}