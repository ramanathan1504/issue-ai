package org.apache.issueai.model;

public record RepoIssue(
        String repository,
        Issue issue
) {}