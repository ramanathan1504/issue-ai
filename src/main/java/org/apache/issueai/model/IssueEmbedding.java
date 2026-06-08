package org.apache.issueai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IssueEmbedding(
        long issueNumber,
        double[] vector
) {}