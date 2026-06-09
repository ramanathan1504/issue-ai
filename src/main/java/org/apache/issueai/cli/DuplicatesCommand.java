package org.apache.issueai.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import org.apache.issueai.llm.OllamaClient;
import org.apache.issueai.model.Issue;
import org.apache.issueai.model.IssueEmbedding;
import org.apache.issueai.storage.SqliteStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "duplicates",
        description = "Identify potential duplicate issues using local vector embeddings"
)
public class DuplicatesCommand implements Callable<Integer> {

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
            names = {"-t", "--threshold"},
            description = "Cosine similarity threshold (0.0 to 1.0)",
            defaultValue = "0.80"
    )
    private double threshold;

    @Override
    public Integer call() throws Exception {
        // Load issues specifically for this repository
        List<Issue> issues = SqliteStorage.loadIssues(repository);
        if (issues.isEmpty()) {
            System.err.printf("No local issues found for '%s'. Please run 'sync' first.%n", repository);
            return 1;
        }

        // 1. Load any previously cached vector embeddings for this repo from the DB
        List<IssueEmbedding> cachedEmbeddings = SqliteStorage.loadEmbeddings(repository);
        Map<Long, double[]> vectorMap = new HashMap<>();
        for (IssueEmbedding emb : cachedEmbeddings) {
            vectorMap.put(emb.issueNumber(), emb.vector());
        }

        System.out.printf("Starting duplicate analysis for '%s' (Model: %s, Threshold: %.2f)...%n", repository, modelName, threshold);
        OllamaClient client = new OllamaClient(modelName);
        boolean cacheUpdated = false;

        // 2. Compute missing embeddings
        for (Issue issue : issues) {
            if (!vectorMap.containsKey(issue.number())) {
                System.out.printf("  Generating embedding vector for Issue #%d...%n", issue.number());
                String content = "Title: " + issue.title() + "\nBody: " + (issue.body() == null ? "" : issue.body());
                try {
                    double[] vector = client.generateEmbedding(content);
                    vectorMap.put(issue.number(), vector);
                    cacheUpdated = true;
                } catch (IOException | InterruptedException e) {
                    System.err.printf("  ↳ [Error] Failed to generate embedding for #%d: %s%n", issue.number(), e.getMessage());
                    System.err.println("Verify Ollama is running ('ollama serve') and model is pulled ('ollama pull " + modelName + "').");
                    return 1;
                }
            }
        }

        // Save embeddings cache if we generated new ones
        if (cacheUpdated) {
            List<IssueEmbedding> newCacheList = new ArrayList<>();
            // Passed 'repository' as the first argument
            vectorMap.forEach((k, v) -> newCacheList.add(new IssueEmbedding(repository, k, v)));
            SqliteStorage.saveEmbeddings(repository, newCacheList);
            System.out.println("  ↳ Local embeddings database updated.");
        }

        // 3. Build similarity graph (Adjacency List)
        int size = issues.size();
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            adj.add(new ArrayList<>());
        }

        System.out.println("Analyzing similarities and clustering issues...");
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                double[] vecA = vectorMap.get(issues.get(i).number());
                double[] vecB = vectorMap.get(issues.get(j).number());
                if (vecA != null && vecB != null) {
                    double sim = cosineSimilarity(vecA, vecB);
                    if (sim >= threshold) {
                        adj.get(i).add(j);
                        adj.get(j).add(i);
                    }
                }
            }
        }

        // 4. Cluster similar issues using Connected Components (BFS)
        boolean[] visited = new boolean[size];
        List<List<Issue>> clusters = new ArrayList<>();

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

                // We only care about components with more than 1 issue (groups of duplicates)
                if (cluster.size() > 1) {
                    clusters.add(cluster);
                }
            }
        }

        // 5. Output Report
        System.out.println("\nDuplicate Issue Clusters Report");
        System.out.println("===============================\n");

        if (clusters.isEmpty()) {
            System.out.println("No duplicate groups detected above the threshold.");
        } else {
            for (int k = 0; k < clusters.size(); k++) {
                List<Issue> cluster = clusters.get(k);
                System.out.printf("Cluster %d (Size: %d)%n", k + 1, cluster.size());

                for (Issue issue : cluster) {
                    System.out.printf("  #%d: %s%n", issue.number(), issue.title());
                }

                // Print a sample similarity for reference (first pair)
                double[] vecA = vectorMap.get(cluster.get(0).number());
                double[] vecB = vectorMap.get(cluster.get(1).number());
                if (vecA != null && vecB != null) {
                    System.out.printf("  ↳ Representative Pair Similarity: %.2f%n", cosineSimilarity(vecA, vecB));
                }
                System.out.println();
            }
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
}