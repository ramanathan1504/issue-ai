package org.apache.issueai.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import org.apache.issueai.report.MarkdownReportWriter;
import picocli.CommandLine.Command;

@Command(
        name = "report",
        description = "Generate a consolidated weekly health report"
)
public class ReportCommand implements Callable<Integer> {

    private static final DateTimeFormatter FILE_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    public Integer call() throws Exception {
        String timestamp = FILE_NAME_FORMATTER.format(LocalDateTime.now());
        Path reportPath = Paths.get("reports", "log4j-health-report-" + timestamp + ".md");

        MarkdownReportWriter writer = new MarkdownReportWriter();
        writer.write(reportPath);

        System.out.println("Master Weekly Health Report successfully generated offline at:");
        System.out.println("  " + reportPath.toAbsolutePath());

        return 0;
    }
}