package org.apache.issueai;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppPaths {
    // Dynamically resolves to /Users/ramanathan (or equivalent on Linux/Windows)
    public static final String HOME_DIR = System.getProperty("user.home");

    // The master global hidden directory: ~/.issue-ai
    public static final Path BASE_DIR = Paths.get(HOME_DIR, ".issue-ai");

    public static final Path DATA_DIR = BASE_DIR.resolve("data");
    public static final Path REPORTS_DIR = BASE_DIR.resolve("reports");
    public static final Path BACKUPS_DIR = BASE_DIR.resolve("backups");

    // Absolute JDBC URL targeting ~/.issue-ai/data/issue_intelligence.db
    public static final String DB_URL = "jdbc:sqlite:"
            + DATA_DIR.resolve("issue_intelligence.db").toAbsolutePath().toString();
}
