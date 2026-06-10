package org.apache.issueai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrendSnapshot(String date, int criticalIssues, int highPriority, int stalePrs, int duplicateClusters) {}
