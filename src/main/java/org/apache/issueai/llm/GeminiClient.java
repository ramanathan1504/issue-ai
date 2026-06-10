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

public class GeminiClient {

    private static final Logger LOGGER = LogManager.getLogger(GeminiClient.class);
    // Using Gemini 1.5 Flash for high-speed, large-context RAG generation
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public GeminiClient(String model) {
        this(org.apache.issueai.util.CredentialManager.getGeminiKey(), model);
    }

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model == null || model.isEmpty() ? "gemini-1.5-flash" : model;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public String generateText(String prompt) throws IOException, InterruptedException {
        String url = String.format(GEMINI_URL, model, apiKey);

        // Construct the Gemini API JSON payload structure
        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        String jsonPayload = MAPPER.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(60)) // Give Gemini 60 seconds to reason
                .build();

        try {
            LOGGER.info("Sending context and prompt to Google Gemini API (Model: {})...", model);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.error("Gemini API failed with status code {}: {}", response.statusCode(), response.body());
                throw new IOException("Gemini returned unexpected HTTP status: " + response.statusCode());
            }

            // Parse Gemini's nested response structure
            Map<?, ?> responseMap = MAPPER.readValue(response.body(), Map.class);
            List<?> candidates = (List<?>) responseMap.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                Map<?, ?> contentMap = (Map<?, ?>) firstCandidate.get("content");
                List<?> parts = (List<?>) contentMap.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                    return (String) firstPart.get("text");
                }
            }
            throw new IOException("Failed to parse expected Gemini API response structure.");

        } catch (IOException e) {
            LOGGER.error("Failed to connect or communicate with Gemini API: {}", e.getMessage());
            throw e;
        }
    }
}
