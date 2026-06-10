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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClaudeClient {
    private static final Logger LOGGER = LogManager.getLogger(ClaudeClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public ClaudeClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model == null || model.isEmpty() ? "claude-3-5-sonnet-20240620" : model;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public ClaudeClient(String model) {
        this(org.apache.issueai.util.CredentialManager.getClaudeKey(), model);
    }

    public String generateText(String prompt) throws IOException, InterruptedException {
        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> requestBody = Map.of("model", model, "max_tokens", 4096, "messages", List.of(message));

        String jsonPayload = MAPPER.writeValueAsString(requestBody);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(60))
                .build();

        LOGGER.info("Sending request to Anthropic Claude (Model: {})...", model);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Anthropic API failed: " + response.body());
        }

        Map<?, ?> responseMap = MAPPER.readValue(response.body(), Map.class);
        List<?> content = (List<?>) responseMap.get("content");
        Map<?, ?> firstBlock = (Map<?, ?>) content.get(0);
        return (String) firstBlock.get("text");
    }
}
