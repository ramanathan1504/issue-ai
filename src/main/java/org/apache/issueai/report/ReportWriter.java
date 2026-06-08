package org.apache.issueai.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.issueai.analyzer.IssueAnalysis;

public interface ReportWriter {
    void write(Path outputPath, List<IssueAnalysis> analyses) throws IOException;
}