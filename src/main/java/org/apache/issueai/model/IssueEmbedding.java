package org.apache.issueai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IssueEmbedding(
        String repository,
        long issueNumber,
        double[] vector
) {}