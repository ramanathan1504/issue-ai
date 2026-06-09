package org.apache.issueai.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:data/issue_intelligence.db";
    private static final int CURRENT_SCHEMA_VERSION = 3;

    public static Connection getConnection() throws SQLException {
        try {
            Files.createDirectories(Paths.get("data"));
        } catch (IOException e) {
            throw new SQLException("Failed to create database directory structure", e);
        }
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");

            // 1. Ensure version tracking table exists
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY);");

            // 2. Read current version
            int version = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version;")) {
                if (rs.next()) {
                    version = rs.getInt("version");
                }
            }

            // 3. Run migrations sequentially
            if (version == 0) {
                if (!tableExists(conn, "issues")) {
                    // Fresh Database - Build everything from scratch
                    System.out.println("Initializing fresh SQLite database schema...");
                    buildFreshSchema(conn);
                    setVersion(conn, CURRENT_SCHEMA_VERSION);
                } else if (!columnExists(conn, "issues", "repository")) {
                    // Legay DB - Upgrade from single-key (V1) to composite-key (V2)
                    System.out.println("Upgrading database schema from Version 1 to Version 2...");
                    migrateV1toV2(conn);
                    setVersion(conn, CURRENT_SCHEMA_VERSION);
                    System.out.println("Database schema upgrade completed successfully.");
                } else {
                    // Database is already V2 but did not have the version row registered
                    setVersion(conn, CURRENT_SCHEMA_VERSION);
                }
            } else if (version < CURRENT_SCHEMA_VERSION) {
                System.out.println("Upgrading database schema to Version 3 (Adding delta-sync tracking)...");
                try (Statement migrationStmt = conn.createStatement()) {
                    migrationStmt.execute("ALTER TABLE monitored_repositories ADD COLUMN last_synced_at TEXT;");
                }
                setVersion(conn, 3);
                System.out.println("Database schema upgraded to Version 3 successfully.");
                version = 3; // Update local variable for any subsequent checks
            }

        } catch (SQLException e) {
            System.err.println("Database schema initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void buildFreshSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(getCreateIssuesTableSql());
            stmt.execute(getCreateLabelsTableSql());
            stmt.execute(getCreateAiAnalysisTableSql());
            stmt.execute(getCreateEmbeddingsTableSql());
            stmt.execute(getCreateCrossRepoLinksTableSql());
            stmt.execute(getCreateSnapshotsTableSql());
            stmt.execute(getCreateJiraMentionsTableSql());
            stmt.execute(getCreateMonitoredTableSql());
            stmt.execute(getSeedMonitoredTableSql());
        }
    }

    private static void migrateV1toV2(Connection conn) throws SQLException {
        // Safe migration: Rename V1 tables, create V2 schemas, copy data, drop V1 tables
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF;"); // Temporarily disable FK checks for renames
        }

        migrateTable(conn, "issues",
                "number, title, body, state, comments, created_at, updated_at, is_pull_request, author, author_association",
                getCreateIssuesTableSql());

        migrateTable(conn, "labels",
                "issue_number, label_name",
                getCreateLabelsTableSql());

        migrateTable(conn, "ai_analysis",
                "issue_number, severity, confidence, reason",
                getCreateAiAnalysisTableSql());

        migrateTable(conn, "embeddings",
                "issue_number, vector",
                getCreateEmbeddingsTableSql());

        migrateTable(conn, "snapshots",
                "date, critical_issues, high_priority, stale_prs, duplicate_clusters",
                getCreateSnapshotsTableSql());

        // Create new tables that were not present in Version 1
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(getCreateCrossRepoLinksTableSql());
            stmt.execute(getCreateJiraMentionsTableSql());
            stmt.execute(getCreateMonitoredTableSql());
            stmt.execute(getSeedMonitoredTableSql());
            stmt.execute("PRAGMA foreign_keys = ON;"); // Re-enable foreign key constraints
        }
    }

    private static void migrateTable(Connection conn, String tableName, String fields, String createTableSql) throws SQLException {
        if (tableExists(conn, tableName)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE " + tableName + " RENAME TO old_" + tableName + ";");
                stmt.execute(createTableSql);

                // Copy older data into the new table, seeding 'apache/logging-log4j2' as the default repository
                stmt.execute("INSERT INTO " + tableName + " (repository, " + fields + ") " +
                        "SELECT 'apache/logging-log4j2', " + fields + " FROM old_" + tableName + ";");

                stmt.execute("DROP TABLE old_" + tableName + ";");
            }
        } else {
            // If the table did not exist previously, simply create it freshly
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
            }
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(" + tableName + ");";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void setVersion(Connection conn, int version) throws SQLException {
        String sql = "INSERT OR REPLACE INTO schema_version (version) VALUES (?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
    }

    // ==========================================
    // Table Creation SQL Strings
    // ==========================================

    private static String getCreateIssuesTableSql() {
        return """
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
    }

    private static String getCreateLabelsTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS labels (
                    repository TEXT,
                    issue_number INTEGER,
                    label_name TEXT,
                    PRIMARY KEY (repository, issue_number, label_name),
                    FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE
                );
                """;
    }

    private static String getCreateAiAnalysisTableSql() {
        return """
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
    }

    private static String getCreateEmbeddingsTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS embeddings (
                    repository TEXT,
                    issue_number INTEGER,
                    vector TEXT,
                    PRIMARY KEY (repository, issue_number),
                    FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE
                );
                """;
    }

    private static String getCreateCrossRepoLinksTableSql() {
        return """
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
    }

    private static String getCreateSnapshotsTableSql() {
        return """
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
    }

    private static String getCreateJiraMentionsTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS jira_mentions (
                    repository TEXT,
                    issue_number INTEGER,
                    jira_key TEXT,
                    PRIMARY KEY (repository, issue_number, jira_key),
                    FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE
                );
                """;
    }

    private static String getCreateMonitoredTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS monitored_repositories (
                    repository TEXT PRIMARY KEY,
                    enabled BOOLEAN DEFAULT 1,
                    last_synced_at TEXT
                );
                """;
    }

    private static String getSeedMonitoredTableSql() {
        return """
                INSERT OR IGNORE INTO monitored_repositories (repository, enabled) VALUES 
                ('apache/logging-log4j2', 1),
                ('apache/kafka', 1),
                ('apache/spark', 1),
                ('apache/flink', 1),
                ('apache/ignite', 1),
                ('quarkusio/quarkus', 1),
                ('elastic/elasticsearch', 1),
                ('opensearch-project/OpenSearch', 1),
                ('open-telemetry/opentelemetry-java-instrumentation', 1),
                ('spring-projects/spring-boot', 1),
                ('apache/solr', 1),
                ('apache/camel', 1),
                ('apache/tomcat', 1),
                ('apache/cassandra', 1);
                """;
    }
}