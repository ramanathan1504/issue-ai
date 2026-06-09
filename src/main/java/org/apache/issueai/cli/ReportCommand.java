package org.apache.issueai.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import org.apache.issueai.report.MarkdownReportWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "report",
        description = "Generate a consolidated weekly health report"
)
public class ReportCommand implements Callable<Integer> {

    private static final DateTimeFormatter FILE_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Option(
            names = {"-r", "--repo"},
            description = "The target GitHub repository to analyze (owner/name)",
            defaultValue = "apache/logging-log4j2"
    )
    private String repository;

    @Override
    public Integer call() throws Exception {
        String[] parts = repository.split("/");
        String repoName = parts.length == 2 ? parts[1] : "issues";

        String timestamp = FILE_NAME_FORMATTER.format(LocalDateTime.now());
        // Dynamic file output location (e.g., reports/kafka-issues-report-20260609-120000.md)
        Path reportPath = Paths.get("reports", repoName + "-issues-report-" + timestamp + ".md");

        MarkdownReportWriter writer = new MarkdownReportWriter();
        writer.write(reportPath, repository);

        System.out.println("Master Weekly Health Report successfully generated offline at:");
        System.out.println("  " + reportPath.toAbsolutePath());

        return 0;
    }
}