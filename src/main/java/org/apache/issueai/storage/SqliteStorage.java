package org.apache.issueai.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.issueai.model.AiAnalysisResult;
import org.apache.issueai.model.Issue;
import org.apache.issueai.model.IssueEmbedding;
import org.apache.issueai.model.Label;
import org.apache.issueai.model.PullRequestMarker;
import org.apache.issueai.model.RepoIssue;
import org.apache.issueai.model.TrendSnapshot;
import org.apache.issueai.model.User;

public class SqliteStorage {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Regex patterns for Ecosystem Dependency Analysis
    private static final Pattern CROSS_REPO_PATTERN = Pattern.compile("([a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+)#(\\d+)");
    private static final Pattern INTERNAL_REF_PATTERN = Pattern.compile("\\b#(\\d+)\\b");
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("\\b([A-Z]+-\\d+)\\b");

    // ==========================================
    // 1. Issues & Pull Requests Operations
    // ==========================================

    public static void saveIssues(String repository, List<Issue> issues) throws SQLException {
        if (issues == null || issues.isEmpty()) {
            return;
        }

        String insertIssueSql = """
                INSERT OR REPLACE INTO issues (
                    repository, number, title, body, state, comments, created_at, updated_at, is_pull_request, author, author_association
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        String deleteLabelsSql = "DELETE FROM labels WHERE repository = ? AND issue_number = ?;";
        String insertLabelSql = "INSERT INTO labels (repository, issue_number, label_name) VALUES (?, ?, ?);";

        String deleteLinksSql = "DELETE FROM cross_repo_links WHERE source_repo = ? AND source_number = ?;";
        String insertLinkSql = """
                INSERT OR REPLACE INTO cross_repo_links (
                    source_repo, source_number, target_repo, target_number, link_type
                ) VALUES (?, ?, ?, ?, ?);
                """;

        String deleteJiraSql = "DELETE FROM jira_mentions WHERE repository = ? AND issue_number = ?;";
        String insertJiraSql = "INSERT OR REPLACE INTO jira_mentions (repository, issue_number, jira_key) VALUES (?, ?, ?);";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psIssue = conn.prepareStatement(insertIssueSql);
                 PreparedStatement psDelLabels = conn.prepareStatement(deleteLabelsSql);
                 PreparedStatement psLabel = conn.prepareStatement(insertLabelSql);
                 PreparedStatement psDelLinks = conn.prepareStatement(deleteLinksSql);
                 PreparedStatement psLink = conn.prepareStatement(insertLinkSql);
                 PreparedStatement psDelJira = conn.prepareStatement(deleteJiraSql);
                 PreparedStatement psJira = conn.prepareStatement(insertJiraSql)) {

                for (Issue issue : issues) {
                    // A. Save Issue
                    psIssue.setString(1, repository);
                    psIssue.setLong(2, issue.number());
                    psIssue.setString(3, issue.title());
                    psIssue.setString(4, issue.body());
                    psIssue.setString(5, issue.state());
                    psIssue.setInt(6, issue.comments());
                    psIssue.setString(7, issue.created_at());
                    psIssue.setString(8, issue.updated_at());
                    psIssue.setBoolean(9, issue.isPullRequest());
                    psIssue.setString(10, issue.user() != null ? issue.user().login() : null);
                    psIssue.setString(11, issue.author_association());
                    psIssue.addBatch();

                    // B. Rebuild Labels
                    psDelLabels.setString(1, repository);
                    psDelLabels.setLong(2, issue.number());
                    psDelLabels.addBatch();

                    if (issue.labels() != null) {
                        for (Label label : issue.labels()) {
                            if (label != null && label.name() != null) {
                                psLabel.setString(1, repository);
                                psLabel.setLong(2, issue.number());
                                psLabel.setString(3, label.name());
                                psLabel.addBatch();
                            }
                        }
                    }

                    // C. Auto-Extract Dependency Links
                    psDelLinks.setString(1, repository);
                    psDelLinks.setLong(2, issue.number());
                    psDelLinks.addBatch();

                    String textToScan = issue.title() + " " + (issue.body() == null ? "" : issue.body());

                    // Match external links (e.g. apache/kafka#1234)
                    Matcher crossMatcher = CROSS_REPO_PATTERN.matcher(textToScan);
                    while (crossMatcher.find()) {
                        String targetRepo = crossMatcher.group(1);
                        long targetNum = Long.parseLong(crossMatcher.group(2));

                        psLink.setString(1, repository);
                        psLink.setLong(2, issue.number());
                        psLink.setString(3, targetRepo);
                        psLink.setLong(4, targetNum);
                        psLink.setString(5, "EXPLICIT_REFERENCE");
                        psLink.addBatch();
                    }

                    // Match internal links (e.g. #4567 -> referring same repo)
                    Matcher internalMatcher = INTERNAL_REF_PATTERN.matcher(textToScan);
                    while (internalMatcher.find()) {
                        long targetNum = Long.parseLong(internalMatcher.group(1));
                        if (targetNum != issue.number()) {
                            psLink.setString(1, repository);
                            psLink.setLong(2, issue.number());
                            psLink.setString(3, repository);
                            psLink.setLong(4, targetNum);
                            psLink.setString(5, "EXPLICIT_REFERENCE");
                            psLink.addBatch();
                        }
                    }

                    // D. Rebuild JIRA Mentions
                    psDelJira.setString(1, repository);
                    psDelJira.setLong(2, issue.number());
                    psDelJira.addBatch();

                    Matcher jiraMatcher = JIRA_KEY_PATTERN.matcher(textToScan);
                    while (jiraMatcher.find()) {
                        String jiraKey = jiraMatcher.group(1);
                        psJira.setString(1, repository);
                        psJira.setLong(2, issue.number());
                        psJira.setString(3, jiraKey);
                        psJira.addBatch();
                    }
                }

                psIssue.executeBatch();
                psDelLabels.executeBatch();
                psLabel.executeBatch();
                psDelLinks.executeBatch();
                psLink.executeBatch();
                psDelJira.executeBatch();
                psJira.executeBatch();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static List<Issue> loadIssues(String repository) throws SQLException {
        return loadIssuesInternal(repository, false);
    }

    public static List<Issue> loadPullRequests(String repository) throws SQLException {
        return loadIssuesInternal(repository, true);
    }

    private static List<Issue> loadIssuesInternal(String repository, boolean isPullRequest) throws SQLException {
        String queryIssuesSql = "SELECT * FROM issues WHERE repository = ? AND is_pull_request = ?;";
        String queryLabelsSql = "SELECT issue_number, label_name FROM labels WHERE repository = ?;";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement psIssues = conn.prepareStatement(queryIssuesSql);
             PreparedStatement psLabels = conn.prepareStatement(queryLabelsSql)) {

            // A. Fetch labels map
            psLabels.setString(1, repository);
            Map<Long, List<Label>> labelsMap = new HashMap<>();
            try (ResultSet rsLabels = psLabels.executeQuery()) {
                while (rsLabels.next()) {
                    long issueNum = rsLabels.getLong("issue_number");
                    String labelName = rsLabels.getString("label_name");
                    labelsMap.computeIfAbsent(issueNum, k -> new ArrayList<>()).add(new Label(labelName));
                }
            }

            // B. Fetch issues
            psIssues.setString(1, repository);
            psIssues.setBoolean(2, isPullRequest);
            List<Issue> results = new ArrayList<>();
            try (ResultSet rs = psIssues.executeQuery()) {
                while (rs.next()) {
                    long num = rs.getLong("number");
                    List<Label> labelsList = labelsMap.getOrDefault(num, List.of());

                    Issue issue = new Issue(
                            num,
                            rs.getString("title"),
                            rs.getString("body"),
                            rs.getString("state"),
                            rs.getInt("comments"),
                            rs.getString("created_at"),
                            rs.getString("updated_at"),
                            rs.getBoolean("is_pull_request") ? new PullRequestMarker() : null,
                            labelsList,
                            new User(rs.getString("author")),
                            rs.getString("author_association"),
                            repository // Pass repository string as html_url context
                    );
                    results.add(issue);
                }
            }
            return results;
        }
    }

    // ==========================================
    // 2. AI Analysis Operations
    // ==========================================

    public static void saveAiAnalysis(String repository, List<AiAnalysisResult> results) throws SQLException {
        if (results == null || results.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO ai_analysis (repository, issue_number, severity, confidence, reason) VALUES (?, ?, ?, ?, ?);";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (AiAnalysisResult r : results) {
                ps.setString(1, repository);
                ps.setLong(2, r.issueNumber());
                ps.setString(3, r.severity());
                ps.setDouble(4, r.confidence());
                ps.setString(5, r.reason());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    public static List<AiAnalysisResult> loadAiAnalysis(String repository) throws SQLException {
        String sql = "SELECT * FROM ai_analysis WHERE repository = ?;";
        List<AiAnalysisResult> results = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new AiAnalysisResult(
                            rs.getLong("issue_number"),
                            rs.getString("severity"),
                            rs.getDouble("confidence"),
                            rs.getString("reason")
                    ));
                }
            }
        }
        return results;
    }

    // ==========================================
    // 3. Vector Embeddings Operations
    // ==========================================

    public static void saveEmbeddings(String repository, List<IssueEmbedding> results) throws SQLException, IOException {
        if (results == null || results.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO embeddings (repository, issue_number, vector) VALUES (?, ?, ?);";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (IssueEmbedding emb : results) {
                ps.setString(1, repository);
                ps.setLong(2, emb.issueNumber());
                String jsonVector = MAPPER.writeValueAsString(emb.vector());
                ps.setString(3, jsonVector);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    public static List<IssueEmbedding> loadEmbeddings(String repository) throws SQLException, IOException {
        String sql = "SELECT * FROM embeddings WHERE repository = ?;";
        List<IssueEmbedding> results = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long num = rs.getLong("issue_number");
                    String jsonVector = rs.getString("vector");
                    double[] vector = MAPPER.readValue(jsonVector, double[].class);
                    results.add(new IssueEmbedding(repository, num, vector));
                }
            }
        }
        return results;
    }

    // ==========================================
    // 4. Historical Snapshots & Sync State Operations
    // ==========================================

    public static void saveTrendSnapshot(String repository, TrendSnapshot snapshot) throws SQLException {
        if (snapshot == null) {
            return;
        }

        String sql = """
                INSERT OR REPLACE INTO snapshots (
                    repository, date, critical_issues, high_priority, stale_prs, duplicate_clusters
                ) VALUES (?, ?, ?, ?, ?, ?);
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            ps.setString(2, snapshot.date());
            ps.setInt(3, snapshot.criticalIssues());
            ps.setInt(4, snapshot.highPriority());
            ps.setInt(5, snapshot.stalePrs());
            ps.setInt(6, snapshot.duplicateClusters());
            ps.executeUpdate();
        }
    }

    public static List<TrendSnapshot> loadTrendSnapshots(String repository) throws SQLException {
        String sql = "SELECT * FROM snapshots WHERE repository = ? ORDER BY date ASC;";
        List<TrendSnapshot> results = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new TrendSnapshot(
                            rs.getString("date"),
                            rs.getInt("critical_issues"),
                            rs.getInt("high_priority"),
                            rs.getInt("stale_prs"),
                            rs.getInt("duplicate_clusters")
                    ));
                }
            }
        }
        return results;
    }

    public static String loadLastSyncedAt(String repository) throws SQLException {
        String sql = "SELECT last_synced_at FROM monitored_repositories WHERE repository = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("last_synced_at");
                }
            }
        }
        return null;
    }

    public static void updateLastSyncedAt(String repository, String timestamp) throws SQLException {
        String sql = "UPDATE monitored_repositories SET last_synced_at = ? WHERE repository = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, timestamp);
            ps.setString(2, repository);
            ps.executeUpdate();
        }
    }

    // ==========================================
    // 5. Ecosystem & Downstream Links Operations
    // ==========================================

    public static List<org.apache.issueai.model.JiraBridgeLink> loadJiraBridges(String repository) throws SQLException {
        String sql = """
                SELECT a.issue_number AS local_number, b.repository AS external_repo, b.issue_number AS external_number, a.jira_key
                FROM jira_mentions a
                JOIN jira_mentions b ON a.jira_key = b.jira_key
                WHERE a.repository = ? 
                  AND b.repository != ?
                  AND a.jira_key NOT IN (
                      'UTF-8', 'UTF-16', 'JDK-17', 'JDK-21', 'JDK-8', 'JDK-11', 
                      'SHA-256', 'SHA-1', 'LICENSE-2', 'ISO-8859'
                  );
                """;
        List<org.apache.issueai.model.JiraBridgeLink> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            ps.setString(2, repository);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new org.apache.issueai.model.JiraBridgeLink(
                            rs.getLong("local_number"),
                            rs.getString("external_repo"),
                            rs.getLong("external_number"),
                            rs.getString("jira_key")
                    ));
                }
            }
        }
        return results;
    }

    public static List<String> loadInboundLinks(String repository) throws SQLException {
        String sql = """
                SELECT source_repo, source_number, target_number 
                FROM cross_repo_links 
                WHERE target_repo = ? AND source_repo != ?;
                """;
        List<String> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            ps.setString(2, repository);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(String.format(
                            "**%s#%d** references our Issue **#%d**",
                            rs.getString("source_repo"),
                            rs.getLong("source_number"),
                            rs.getLong("target_number")
                    ));
                }
            }
        }
        return results;
    }

    public static List<String> loadOutboundLinks(String repository) throws SQLException {
        String sql = """
                SELECT source_number, target_repo, target_number 
                FROM cross_repo_links 
                WHERE source_repo = ? AND target_repo != ?;
                """;
        List<String> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            ps.setString(2, repository);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(String.format(
                            "Our Issue **#%d** references **%s#%d**",
                            rs.getLong("source_number"),
                            rs.getString("target_repo"),
                            rs.getLong("target_number")
                    ));
                }
            }
        }
        return results;
    }

    // ==========================================
    // 6. Monitored Repositories Operations
    // ==========================================

    public static List<String> loadMonitoredRepositories() throws SQLException {
        String sql = "SELECT repository FROM monitored_repositories WHERE enabled = 1;";
        List<String> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(rs.getString("repository"));
            }
        }
        return results;
    }

    public static void saveMonitoredRepository(String repository, boolean enabled) throws SQLException {
        String sql = "INSERT OR REPLACE INTO monitored_repositories (repository, enabled) VALUES (?, ?);";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            ps.setBoolean(2, enabled);
            ps.executeUpdate();
        }
    }

    public static void deleteMonitoredRepository(String repository) throws SQLException {
        String sql = "DELETE FROM monitored_repositories WHERE repository = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            ps.executeUpdate();
        }
    }

    // ==========================================
    // 7. Global Multi-Repo Operations
    // ==========================================

    public static List<IssueEmbedding> loadAllEmbeddings() throws SQLException, IOException {
        String sql = "SELECT * FROM embeddings;";
        List<IssueEmbedding> results = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String repo = rs.getString("repository");
                long num = rs.getLong("issue_number");
                String jsonVector = rs.getString("vector");
                double[] vector = MAPPER.readValue(jsonVector, double[].class);
                results.add(new IssueEmbedding(repo, num, vector));
            }
        }
        return results;
    }

    public static List<RepoIssue> loadAllIssues() throws SQLException {
        return loadAllIssuesInternal(false);
    }

    public static List<RepoIssue> loadAllPullRequests() throws SQLException {
        return loadAllIssuesInternal(true);
    }

    private static List<RepoIssue> loadAllIssuesInternal(boolean isPullRequest) throws SQLException {
        String queryIssuesSql = "SELECT * FROM issues WHERE is_pull_request = ?;";
        String queryLabelsSql = "SELECT repository, issue_number, label_name FROM labels;";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement psIssues = conn.prepareStatement(queryIssuesSql);
             PreparedStatement psLabels = conn.prepareStatement(queryLabelsSql)) {

            // Fetch and group labels in memory by composite key (repository + "_" + issue_number)
            Map<String, List<Label>> labelsMap = new HashMap<>();
            try (ResultSet rsLabels = psLabels.executeQuery()) {
                while (rsLabels.next()) {
                    String repo = rsLabels.getString("repository");
                    long issueNum = rsLabels.getLong("issue_number");
                    String labelName = rsLabels.getString("label_name");
                    labelsMap.computeIfAbsent(repo + "_" + issueNum, k -> new ArrayList<>()).add(new Label(labelName));
                }
            }

            psIssues.setBoolean(1, isPullRequest);
            List<RepoIssue> results = new ArrayList<>();
            try (ResultSet rs = psIssues.executeQuery()) {
                while (rs.next()) {
                    long num = rs.getLong("number");
                    String repo = rs.getString("repository");
                    List<Label> labelsList = labelsMap.getOrDefault(repo + "_" + num, List.of());

                    // Reconstruct Issue passing the repository column as the 12th parameter (html_url)
                    Issue issue = new Issue(
                            num,
                            rs.getString("title"),
                            rs.getString("body"),
                            rs.getString("state"),
                            rs.getInt("comments"),
                            rs.getString("created_at"),
                            rs.getString("updated_at"),
                            rs.getBoolean("is_pull_request") ? new PullRequestMarker() : null,
                            labelsList,
                            new User(rs.getString("author")),
                            rs.getString("author_association"),
                            repo // Pass repository string as html_url context
                    );
                    results.add(new RepoIssue(repo, issue));
                }
            }
            return results;
        }
    }

    // ==========================================
    // 8. System Configuration Operations
    // ==========================================

    public static String loadConfig(String key) throws SQLException {
        String sql = "SELECT value FROM system_config WHERE key = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        }
        return null;
    }

    public static void saveConfig(String key, String value) throws SQLException {
        String sql = "INSERT OR REPLACE INTO system_config (key, value) VALUES (?, ?);";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    // ==========================================
    // 9. Personal Code Footprint Operations
    // ==========================================

    public static void savePersonalCodeFootprint(String repository, long issueNumber, List<String> filePaths) throws SQLException {
        String deleteSql = "DELETE FROM personal_code_footprint WHERE repository = ? AND issue_number = ?;";
        String insertSql = "INSERT OR REPLACE INTO personal_code_footprint (repository, issue_number, file_path) VALUES (?, ?, ?);";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psDel = conn.prepareStatement(deleteSql);
                 PreparedStatement psIns = conn.prepareStatement(insertSql)) {

                psDel.setString(1, repository);
                psDel.setLong(2, issueNumber);
                psDel.executeUpdate();

                for (String path : filePaths) {
                    psIns.setString(1, repository);
                    psIns.setLong(2, issueNumber);
                    psIns.setString(3, path);
                    psIns.addBatch();
                }

                psIns.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static List<String> loadPersonalCodeFootprint(String repository) throws SQLException {
        String sql = "SELECT DISTINCT file_path FROM personal_code_footprint WHERE repository = ?;";
        List<String> results = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getString("file_path"));
                }
            }
        }
        return results;
    }
    // ==========================================
    // 10. Personal Developer Memory Operations
    // ==========================================

    public static boolean hasPersonalPrMemory(String repository, long prNumber) throws SQLException {
        String sql = "SELECT 1 FROM personal_pr_memory WHERE repository = ? AND pr_number = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            ps.setLong(2, prNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void savePersonalPrMemory(String repository, long prNumber, String filesChanged, String generatedStory, double[] vector) throws SQLException, IOException {
        String sql = "INSERT OR REPLACE INTO personal_pr_memory (repository, pr_number, files_changed, generated_story, vector) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, repository);
            ps.setLong(2, prNumber);
            ps.setString(3, filesChanged);
            ps.setString(4, generatedStory);
            ps.setString(5, MAPPER.writeValueAsString(vector));
            ps.executeUpdate();
        }
    }

    public static long loadPersonalChatLastModified(String filePath) throws SQLException {
        String sql = "SELECT last_modified FROM personal_chat_memory WHERE file_path = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("last_modified");
                }
            }
        }
        return -1;
    }

    public static void savePersonalChatMemory(String filePath, String fileName, long lastModified, String content, double[] vector) throws SQLException, IOException {
        String sql = "INSERT OR REPLACE INTO personal_chat_memory (file_path, file_name, last_modified, content, vector) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.setString(2, fileName);
            ps.setLong(3, lastModified);
            ps.setString(4, content);
            ps.setString(5, MAPPER.writeValueAsString(vector));
            ps.executeUpdate();
        }
    }
    public static String loadPersonalChatContent(String filePath) throws SQLException {
        String sql = "SELECT content FROM personal_chat_memory WHERE file_path = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("content");
                }
            }
        }
        return null;
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

}