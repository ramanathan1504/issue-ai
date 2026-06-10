package org.apache.issueai.model;

public record ChatMemory(
        String fileName,
        String content,
        double[] vector
) {}