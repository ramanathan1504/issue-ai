package org.apache.issueai.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.issueai.model.Issue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class GitHubClient {

    private static final String GITHUB_API = "https://api.github.com";

    private final HttpClient httpClient;
    private final String token;

    public GitHubClient(String token) {
        this.httpClient = HttpClient.newHttpClient();
        this.token = token;
    }

    public List<Issue> getOpenIssues(String owner, String repo) throws IOException, InterruptedException {
        List<Issue> allIssues = new ArrayList<>();

        for (int page = 1; ; page++) {
            String url =
                    GITHUB_API +
                            "/repos/" + owner + "/" + repo +
                            "/issues?state=open&per_page=100&page=" + page;
            List<Issue> issues = fetchPage(url);
            if (issues.isEmpty()) {
                break;
            }
            allIssues.addAll(issues);
            System.out.printf(
                    "Fetched page %d (%d issues)%n",
                    page,
                    issues.size());
            if (issues.size() < 100) {
                break;
            }
        }
        return allIssues;
    }
public List<Issue> fetchPage(String url) throws IOException, InterruptedException {

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer " + token)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()
            .build();

    HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(
            response.body(),
            new TypeReference<>() {
            });
}
    public String getOpenPullRequests(String owner, String repo)
            throws IOException, InterruptedException {

        String url = GITHUB_API +
                "/repos/" + owner + "/" + repo +
                "/pulls?state=open&per_page=100";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String getLabels(String owner, String repo)
            throws IOException, InterruptedException {

        String url = GITHUB_API +
                "/repos/" + owner + "/" + repo +
                "/labels?per_page=100";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}
