package org.apache.issueai;
import org.apache.issueai.cli.*;
import picocli.CommandLine.Command;

@Command(
        name = "issue-ai",
        mixinStandardHelpOptions = true,
        version = "0.1",
        subcommands = {
                SyncCommand.class,
                CriticalCommand.class,
                AnalyzeCommand.class,
                HiddenCriticalCommand.class,
                DuplicatesCommand.class,
                SearchCommand.class,
                PrsCommand.class,
                ReportCommand.class,
                TrendCommand.class,
                TriageCommand.class,
                SetupCommand.class
        }
)
public class RootCommand {
}
