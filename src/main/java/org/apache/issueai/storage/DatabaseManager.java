package org.apache.issueai.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.issueai.AppPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabaseManager {

    private static final Logger LOGGER = LogManager.getLogger(DatabaseManager.class);
    // private static final String DB_URL = "jdbc:sqlite:data/issue_intelligence.db";
    private static final int CURRENT_SCHEMA_VERSION = 6;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // Interface representing a single, isolated database migration step
    private interface Migration {
        int getTargetVersion();

        void execute(Connection conn) throws SQLException;
    }

    // Static registry of all sequential database schema migrations
    private static final Migration[] MIGRATIONS = new Migration[] {
        // Migration 1: Fresh Database Schema Initialization
        new Migration() {
            @Override
            public int getTargetVersion() {
                return 1;
            }

            @Override
            public void execute(Connection conn) throws SQLException {
                LOGGER.info("Initializing fresh SQLite database schema...");
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
                    stmt.execute(getCreateConfigTableSql());
                    stmt.execute(getSeedConfigTableSql());
                    stmt.execute(getCreatePersonalCodeFootprintTableSql());
                    stmt.execute(getCreatePersonalPrMemoryTableSql());
                    stmt.execute(getCreatePersonalChatMemoryTableSql());
                }
            }
        },
        // Migration 2: Upgrade V1 Legacy tables to composite-key schemas
        new Migration() {
            @Override
            public int getTargetVersion() {
                return 2;
            }

            @Override
            public void execute(Connection conn) throws SQLException {
                LOGGER.info("Upgrading database schema from Version 1 to Version 2...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = OFF;");
                }

                migrateTable(
                        conn,
                        "issues",
                        "number, title, body, state, comments, created_at, updated_at, is_pull_request, author, author_association",
                        getCreateIssuesTableSql());

                migrateTable(conn, "labels", "issue_number, label_name", getCreateLabelsTableSql());

                migrateTable(
                        conn,
                        "ai_analysis",
                        "issue_number, severity, confidence, reason",
                        getCreateAiAnalysisTableSql());

                migrateTable(conn, "embeddings", "issue_number, vector", getCreateEmbeddingsTableSql());

                migrateTable(
                        conn,
                        "snapshots",
                        "date, critical_issues, high_priority, stale_prs, duplicate_clusters",
                        getCreateSnapshotsTableSql());

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(getCreateCrossRepoLinksTableSql());
                    stmt.execute(getCreateJiraMentionsTableSql());
                    stmt.execute(getCreateMonitoredTableSql());
                    stmt.execute(getSeedMonitoredTableSql());
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
            }
        },
        // Migration 3: Add last_synced_at to monitored repositories
        new Migration() {
            @Override
            public int getTargetVersion() {
                return 3;
            }

            @Override
            public void execute(Connection conn) throws SQLException {
                LOGGER.info("Upgrading database schema to Version 3 (Adding delta-sync tracking)...");
                if (!columnExists(conn, "monitored_repositories", "last_synced_at")) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE monitored_repositories ADD COLUMN last_synced_at TEXT;");
                    }
                }
            }
        },
        // Migration 4: Create system_config table and seed defaults
        new Migration() {
            @Override
            public int getTargetVersion() {
                return 4;
            }

            @Override
            public void execute(Connection conn) throws SQLException {
                LOGGER.info("Upgrading database schema to Version 4 (Adding system configuration)...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(getCreateConfigTableSql());
                    stmt.execute(getSeedConfigTableSql());
                }
            }
        },
        new Migration() {
            @Override
            public int getTargetVersion() {
                return 5;
            }

            @Override
            public void execute(Connection conn) throws SQLException {
                LOGGER.info("Upgrading database schema to Version 5 (Adding personal code footprint)...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(getCreatePersonalCodeFootprintTableSql());
                }
            }
        },
        // Migration 6: Create personal_pr_memory and personal_chat_memory tables
        new Migration() {
            @Override
            public int getTargetVersion() {
                return 6;
            }

            @Override
            public void execute(Connection conn) throws SQLException {
                LOGGER.info("Upgrading database schema to Version 6 (Adding personal memory registries)...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(getCreatePersonalPrMemoryTableSql());
                    stmt.execute(getCreatePersonalChatMemoryTableSql());
                }
            }
        }
    };

    public static Connection getConnection() throws SQLException {
        try {
            Files.createDirectories(AppPaths.DATA_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create database directory structure: {}", e.getMessage());
            throw new SQLException("Failed to create database directory structure", e);
        }

        int attempts = 0;
        while (true) {
            try {
                return DriverManager.getConnection(AppPaths.DB_URL);
            } catch (SQLException e) {
                attempts++;
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    LOGGER.fatal("Failed to connect to SQLite after {} attempts.", attempts, e);
                    throw e;
                }
                LOGGER.warn(
                        "Database busy or connection locked. Retrying attempt {}/{}...", attempts, MAX_RETRY_ATTEMPTS);
                try {
                    Thread.sleep(100 + (long) (Math.random() * 400));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Database connection attempt interrupted", ie);
                }
            }
        }
    }

    public static void initializeSchema() {
        LOGGER.info("Initializing local SQLite database connection...");

        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            // Ensure version tracking table exists
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY);");
            }

            // Read current version
            int currentVersion = 0;
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version;")) {
                if (rs.next()) {
                    currentVersion = rs.getInt("version");
                }
            }

            // Handle unversioned or legacy database migrations
            if (currentVersion == 0) {
                if (!tableExists(conn, "issues")) {
                    // Fresh Database - Build the complete fresh schema natively to latest version
                    MIGRATIONS[0].execute(conn);
                    setVersion(conn, CURRENT_SCHEMA_VERSION);
                    currentVersion = CURRENT_SCHEMA_VERSION;
                } else if (!columnExists(conn, "issues", "repository")) {
                    // Old unversioned single-repo DB (V1)
                    MIGRATIONS[1].execute(conn);
                    setVersion(conn, 2);
                    currentVersion = 2;
                } else {
                    setVersion(conn, 2);
                    currentVersion = 2;
                }
            }

            // Sequentially execute any remaining migrations registered in the array
            for (Migration migration : MIGRATIONS) {
                if (migration.getTargetVersion() > currentVersion
                        && migration.getTargetVersion() <= CURRENT_SCHEMA_VERSION) {
                    migration.execute(conn);
                    setVersion(conn, migration.getTargetVersion());
                    currentVersion = migration.getTargetVersion();
                }
            }

        } catch (SQLException e) {
            LOGGER.error("Database schema initialization failed critically: {}", e.getMessage(), e);
        }
    }

    private static void migrateTable(Connection conn, String tableName, String fields, String createTableSql)
            throws SQLException {
        if (tableExists(conn, tableName)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE " + tableName + " RENAME TO old_" + tableName + ";");
                stmt.execute(createTableSql);
                stmt.execute("INSERT INTO " + tableName + " (repository, " + fields + ") "
                        + "SELECT 'apache/logging-log4j2', " + fields + " FROM old_" + tableName + ";");
                stmt.execute("DROP TABLE old_" + tableName + ";");
            }
        } else {
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
        return "CREATE TABLE IF NOT EXISTS issues (repository TEXT, number INTEGER, title TEXT, body TEXT, state TEXT, comments INTEGER, created_at TEXT, updated_at TEXT, is_pull_request BOOLEAN, author TEXT, author_association TEXT, PRIMARY KEY (repository, number));";
    }

    private static String getCreateLabelsTableSql() {
        return "CREATE TABLE IF NOT EXISTS labels (repository TEXT, issue_number INTEGER, label_name TEXT, PRIMARY KEY (repository, issue_number, label_name), FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE);";
    }

    private static String getCreateAiAnalysisTableSql() {
        return "CREATE TABLE IF NOT EXISTS ai_analysis (repository TEXT, issue_number INTEGER, severity TEXT, confidence REAL, reason TEXT, PRIMARY KEY (repository, issue_number), FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE);";
    }

    private static String getCreateEmbeddingsTableSql() {
        return "CREATE TABLE IF NOT EXISTS embeddings (repository TEXT, issue_number INTEGER, vector TEXT, PRIMARY KEY (repository, issue_number), FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE);";
    }

    private static String getCreateCrossRepoLinksTableSql() {
        return "CREATE TABLE IF NOT EXISTS cross_repo_links (source_repo TEXT, source_number INTEGER, target_repo TEXT, target_number INTEGER, link_type TEXT, PRIMARY KEY (source_repo, source_number, target_repo, target_number), FOREIGN KEY (source_repo, source_number) REFERENCES issues(repository, number) ON DELETE CASCADE);";
    }

    private static String getCreateSnapshotsTableSql() {
        return "CREATE TABLE IF NOT EXISTS snapshots (repository TEXT, date TEXT, critical_issues INTEGER, high_priority INTEGER, stale_prs INTEGER, duplicate_clusters INTEGER, PRIMARY KEY (repository, date));";
    }

    private static String getCreateJiraMentionsTableSql() {
        return "CREATE TABLE IF NOT EXISTS jira_mentions (repository TEXT, issue_number INTEGER, jira_key TEXT, PRIMARY KEY (repository, issue_number, jira_key), FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE);";
    }

    private static String getCreateMonitoredTableSql() {
        return "CREATE TABLE IF NOT EXISTS monitored_repositories (repository TEXT PRIMARY KEY, enabled BOOLEAN DEFAULT 1, last_synced_at TEXT);";
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

    private static String getCreateConfigTableSql() {
        return "CREATE TABLE IF NOT EXISTS system_config (key TEXT PRIMARY KEY, value TEXT);";
    }

    private static String getSeedConfigTableSql() {
        return """
                INSERT OR IGNORE INTO system_config (key, value) VALUES
                ('ollama.model.triage', 'qwen2.5:0.5b'),
                ('ollama.model.embedding', 'all-minilm'),
                ('ollama.model.guidance', 'qwen2.5:7b'),
                ('ollama.url', 'http://localhost:11434'),
                ('github.username', 'ramanathan1504'),
                ('drive.paths', '');
                """;
    }

    private static String getCreatePersonalCodeFootprintTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS personal_code_footprint (
                    repository TEXT,
                    issue_number INTEGER,
                    file_path TEXT,
                    PRIMARY KEY (repository, issue_number, file_path),
                    FOREIGN KEY (repository, issue_number) REFERENCES issues(repository, number) ON DELETE CASCADE
                );
                """;
    }

    private static String getCreatePersonalPrMemoryTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS personal_pr_memory (
                    repository TEXT,
                    pr_number INTEGER,
                    files_changed TEXT,
                    generated_story TEXT,
                    vector TEXT,
                    PRIMARY KEY (repository, pr_number)
                );
                """;
    }

    private static String getCreatePersonalChatMemoryTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS personal_chat_memory (
                    file_path TEXT PRIMARY KEY,
                    file_name TEXT,
                    last_modified INTEGER,
                    content TEXT,
                    vector TEXT
                );
                """;
    }
}
