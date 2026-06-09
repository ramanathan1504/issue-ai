package org.apache.issueai.model;

import org.apache.issueai.analyzer.Severity;

public record PersonalRecommendation(
        Issue issue,
        double personalScore,
        double similarity,
        Severity baseSeverity
) {}
