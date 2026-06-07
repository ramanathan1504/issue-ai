package org.apache.issueai;

import picocli.CommandLine;

public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RootCommand())
                .execute(args);
        System.exit(exitCode);
    }

}
