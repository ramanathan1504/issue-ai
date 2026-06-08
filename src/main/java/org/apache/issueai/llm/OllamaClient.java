package org.apache.issueai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class OllamaClient {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient;
    private final String model;

    public OllamaClient(String model) {
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String generateJson(String prompt) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "format", "json"
        );

        String jsonPayload = MAPPER.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Ollama returned unexpected HTTP status: " + response.statusCode());
        }

        Map<?, ?> responseMap = MAPPER.readValue(response.body(), Map.class);
        return (String) responseMap.get("response");
    }
    public double[] generateEmbedding(String text) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", text
        );

        String jsonPayload = MAPPER.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/embed"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Ollama returned unexpected HTTP status: " + response.statusCode());
        }

        Map<?, ?> responseMap = MAPPER.readValue(response.body(), Map.class);
        List<?> embeddingsList = (List<?>) responseMap.get("embeddings");
        if (embeddingsList == null || embeddingsList.isEmpty()) {
            throw new IOException("No embeddings returned by Ollama service");
        }

        List<?> vectorList = (List<?>) embeddingsList.get(0);
        double[] vector = new double[vectorList.size()];
        for (int i = 0; i < vectorList.size(); i++) {
            vector[i] = ((Number) vectorList.get(i)).doubleValue();
        }
        return vector;
    }
}