package org.apache.issueai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Issue(
        long number,
        String title,
        String body,
        String state,
        int comments,
        String created_at,
        String updated_at,
        PullRequestMarker pull_request,
        List<Label> labels
) {
    public boolean isPullRequest() {
        return pull_request != null;
    }
    public boolean hasLabel(String labelName) {
        return labels != null &&
                labels.stream()
                        .anyMatch(label ->
                                label.name().equalsIgnoreCase(labelName));
    }
}
