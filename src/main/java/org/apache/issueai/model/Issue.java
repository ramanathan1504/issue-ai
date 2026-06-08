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
        List<Label> labels,
        User user,
        String author_association
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

    // Helper to determine if the author has maintainer/member permissions
    public boolean isOrgMember() {
        if (author_association == null) {
            return false;
        }
        String upper = author_association.toUpperCase();
        return "MEMBER".equals(upper) || "OWNER".equals(upper) || "COLLABORATOR".equals(upper);
    }
}