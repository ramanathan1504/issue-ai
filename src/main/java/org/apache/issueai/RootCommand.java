package org.apache.issueai;
import org.apache.issueai.cli.CriticalCommand;
import org.apache.issueai.cli.SyncCommand;
import picocli.CommandLine.Command;

@Command(
        name = "issue-ai",
        mixinStandardHelpOptions = true,
        version = "0.1",
        subcommands = {
                SyncCommand.class,
                CriticalCommand.class
        }
)
public class RootCommand {
}
