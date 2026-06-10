package org.apache.issueai.analyzer;

import org.apache.issueai.model.Issue;

public record IssueAnalysis(Issue issue, Severity severity, int score, String reason) {}
