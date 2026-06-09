# CLI Command Reference (`COMMANDS.md`)

This guide outlines the purpose, configuration options, and execution output for all subcommands in the `issue-ai` CLI platform.

---

## Global Options

All subcommands support the dynamic repository targeting flag:
*   `-r`, `--repo` : The target GitHub repository to analyze in the format `owner/name` (Default: `apache/logging-log4j2`).

---

## 1. `sync`
Fetches open issues and pull requests from GitHub, maps the author's organizational status, and stores them in the local SQLite database.

### Options
*   `-a`, `--all` : Sequentially synchronizes all active repositories seeded in the local SQLite registry [1, 2].
*   `--add` : Add a new owner/repository string (e.g. `elastic/logstash`) to the database watchlist [1].
*   `--remove` : Remove/disable a repository from the database watchlist.

### Usage
```bash
export GITHUB_TOKEN="your_personal_access_token"

# Sync all active, enabled repositories in your registry sequentially (All 14+ projects)
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar sync --all

# Register a new custom repository to your local database watchlist
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar sync --add elastic/logstash

# Sync a single repository manually
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar sync -r apache/kafka
```

### Outputs
*   Saves records directly to the `issues` and `labels` database tables.
*   **Background Ecosystem Parser:** Automatically scans the title and description text for cross-project dependencies (e.g., `apache/kafka#1234`) and JIRA keys (e.g., `KAFKA-20362`), writing them to the `cross_repo_links` and `jira_mentions` tables [1, 2].

---

## 2. `analyze`
Performs batch AI Severity Analysis on open issues using a local Ollama reasoning model.

### Options
*   `-m`, `--model` : The Ollama model name to run (Default: `qwen3:8b`).

### Usage (Mobile Hotspot Friendly)
```bash
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar analyze -m qwen2.5:0.5b -r apache/logging-log4j2
```

### Outputs
*   Writes results to the `ai_analysis` database table for the specified repository namespace, saving predicted severity classifications (`Critical`, `High`, `Medium`, `Low`), confidence ratings, and reasoning.

---

## 3. `hidden-critical`
Cross-references raw issue metadata in SQLite against AI evaluations to detect issues that maintainers may have underestimated.

### Detection Rules
1.  **Missed Security:** Issue lacks a `security` label, but the AI flagged it as `Critical`.
2.  **Overlooked Bug:** Issue is labeled `bug`, has over 15 comments, and the AI flagged it as `Critical` or `High`.

### Usage
```bash
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar hidden-critical -r apache/logging-log4j2
```

---

## 4. `duplicates`
Identifies potential duplicate issues using local vector embeddings.

### Options
*   `-m`, `--model` : The Ollama embedding model to use (Default: `all-minilm`).
*   `-t`, `--threshold` : Cosine similarity threshold from `0.0` to `1.0` (Default: `0.80`).

### Usage
```bash
# Find duplicate clusters at 70% similarity threshold
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar duplicates -t 0.70 -r apache/logging-log4j2
```

### Outputs
*   Writes vectors to the `embeddings` database table under the repository namespace.
*   Groups duplicates into clusters based on a graph Connected Components BFS algorithm.

---

## 5. `search`
Performs an offline semantic vector lookup on your backlog. It vectors your search query and returns issues that conceptually match, even if they use completely different terminology [1].

### Parameters
*   `[0]` (Positional) : The plain-text search query (wrap in quotes if it contains spaces).

### Options
*   `-g`, `--global` : Run a global search across all repositories stored in the database [1].
*   `-m`, `--model` : The Ollama embedding model to use (Default: `all-minilm`).
*   `-n`, `--limit` : The maximum number of search results to return (Default: `5`).

### Usage
```bash
# Search across all 14+ open-source databases in SQLite at once!
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar search "deadlock in Kafka appender" --global
```

---

## 6. `prs`
Analyzes pull requests stored in `prs` table for maintenance metrics.

### Detection Categories
*   **Stale PRs:** No update activity for over 30 days.
*   **Reviews Needed:** PR has 0 comments (used as a proxy for no active reviews).
*   **Critical Fixes:** PR matches issue numbers referencing an issue flagged as `Critical` by the severity analyzer.

### Usage
```bash
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar prs -r apache/logging-log4j2
```

---

## 7. `triage`
Executes a consolidated, automated triage audit on a specific issue or pull request, returning a complete action log indicating critical warnings, duplicates, and stale statuses.

### Parameters
*   `[0]` (Positional) : The issue or PR number to triage.

### Usage
```bash
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar triage 4088 -r apache/logging-log4j2
```

### Outputs
Prints a terminal dashboard containing:
*   Author metadata with organization member status badges (`[Member]`).
*   V1 score vs. local AI severity assessment.
*   Semantic duplicates matching above a 70% threshold.
*   Any resolved ecosystem JIRA-key overlaps [1.1.1].
*   Immediate recommended actions.

---

## 8. `report`
Compiles all SQLite databases into a unified, highly actionable, timestamped **Weekly Health Report** in Markdown format.

### Usage
```bash
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar report -r apache/logging-log4j2
```

### Outputs
Generates a customized file `reports/<repo_name>-issues-report-YYYYMMDD-HHmmss.md` detailing:
*   Summary metrics (Open, Critical, Stale, Hidden, and Duplicates).
*   Critical and Hidden Critical issues with author and member badges.
*   **Ecosystem Connections:** Lists mutual cross-project JIRA bridge overlaps, inbound downstream links, and outbound upstream dependencies [1.1.1, 2].

---

## 9. `trend`
Tracks and displays project health metrics across multiple snapshot intervals over time.

### Options
*   `-s`, `--save` : Gathers current metrics and saves them as a new snapshot in the database.

### Usage
```bash
# Save today's snapshot for the specific repository
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar trend --save -r apache/logging-log4j2

# Display the trend dashboard
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar trend -r apache/logging-log4j2
```

### Outputs
*   Saves a record to the `snapshots` database table under the specific repository namespace.
*   Outputs a chronological matrix in the terminal showing the change direction of issues and PRs.