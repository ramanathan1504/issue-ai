package org.apache.issueai.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:data/issue_intelligence.db";

    public static Connection getConnection() throws SQLException {
        try {
            Files.createDirectories(Paths.get("data"));
        } catch (IOException e) {
            throw new SQLException("Failed to create database directory structure", e);
        }
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeSchema() {
        String createIssuesTable = """
                CREATE TABLE IF NOT EXISTS issues (
                    repository TEXT,
                    number INTEGER,
                    title TEXT,
                    body TEXT,
                    state TEXT,
                    comments INTEGER,
                    created_at TEXT,
                    updated_at TEXT,
                    is_pull_request BOOLEAN,
                    author TEXT,
                    author_association TEXT,
                    PRIMARY KEY (repository, number)
                );
                """;

        String createLabelsTable = """
                CREATE TABLE IF NOT EXISTS labels (
                    repository TEXT,
                    issue_number INTEGER,
                    label_name TEXT,
                    PRIMARY KEY (repository, issue_number, label_name),
                    FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE
                );
                """;

        String createAiAnalysisTable = """
                CREATE TABLE IF NOT EXISTS ai_analysis (
                    repository TEXT,
                    issue_number INTEGER,
                    severity TEXT,
                    confidence REAL,
                    reason TEXT,
                    PRIMARY KEY (repository, issue_number),
                    FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE
                );
                """;

        String createEmbeddingsTable = """
                CREATE TABLE IF NOT EXISTS embeddings (
                    repository TEXT,
                    issue_number INTEGER,
                    vector TEXT,
                    PRIMARY KEY (repository, issue_number),
                    FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE
                );
                """;

        String createCrossRepoLinksTable = """
                CREATE TABLE IF NOT EXISTS cross_repo_links (
                    source_repo TEXT,
                    source_number INTEGER,
                    target_repo TEXT,
                    target_number INTEGER,
                    link_type TEXT,
                    PRIMARY KEY (source_repo, source_number, target_repo, target_number),
                    FOREIGN KEY (source_repo, source_number) REFERENCES issues(repository, number) ON DELETE CASCADE
                );
                """;

        String createSnapshotsTable = """
                CREATE TABLE IF NOT EXISTS snapshots (
                    repository TEXT,
                    date TEXT,
                    critical_issues INTEGER,
                    high_priority INTEGER,
                    stale_prs INTEGER,
                    duplicate_clusters INTEGER,
                    PRIMARY KEY (repository, date)
                );
                """;
        String createJiraMentionsTable = """
                CREATE TABLE IF NOT EXISTS jira_mentions (
                    repository TEXT,
                    issue_number INTEGER,
                    jira_key TEXT,
                    PRIMARY KEY (repository, issue_number, jira_key),
                    FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE
                );
                """;

        // Add this line inside the try block:
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");

            stmt.execute(createIssuesTable);
            stmt.execute(createLabelsTable);
            stmt.execute(createAiAnalysisTable);
            stmt.execute(createEmbeddingsTable);
            stmt.execute(createCrossRepoLinksTable);
            stmt.execute(createSnapshotsTable);
            stmt.execute(createJiraMentionsTable);


        } catch (SQLException e) {
            System.err.println("Failed to initialize database schema: " + e.getMessage());
            e.printStackTrace();
        }
    }
}