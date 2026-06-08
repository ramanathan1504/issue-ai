# CLI Command Reference (`COMMANDS.md`)

This guide outlines the purpose, configuration options, and execution output for all subcommands in the `issue-ai` CLI platform.

---

## 1. `sync`
Fetches all open issues and pull requests from GitHub, maps the author's organizational status, and stores them in the local `data/` cache.

### Usage
```bash
export GITHUB_TOKEN="your_personal_access_token"
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar sync
```

### Outputs
*   Saves `data/issues.json`
*   Saves `data/prs.json`
*   Prints the top 10 most commented, oldest, and recently updated issues in the terminal.

---

## 2. `analyze`
Performs batch AI Severity Analysis on open issues using a local Ollama reasoning model.

### Options
*   `-m`, `--model` : The Ollama model name to run (Default: `qwen3:8b`).

### Usage (Mobile Hotspot Friendly)
```bash
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar analyze -m qwen2.5:0.5b
```

### Outputs
*   Saves `data/ai-analysis.json` containing severity classifications (`Critical`, `High`, `Medium`, `Low`), confidence ratings, and reasoning text.

---

## 3. `hidden-critical`
Cross-references raw issue metadata against AI evaluations to detect issues that maintainers may have underestimated.

### Detection Rules
1.  **Missed Security:** Issue lacks a `security` label, but the AI flagged it as `Critical`.
2.  **Overlooked Bug:** Issue is labeled `bug`, has over 15 comments, and the AI flagged it as `Critical` or `High`.

### Usage
```bash
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar hidden-critical
```

---

## 4. `duplicates`
Identifies potential duplicate issues using local vector embeddings.

### Options
*   `-m`, `--model` : The Ollama embedding model to use (Default: `all-minilm`).
*   `-t`, `--threshold` : Cosine similarity threshold from `0.0` to `1.0` (Default: `0.80`).

### Usage
```bash
# Find closely matching issues (broad clustering)
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar duplicates -t 0.70
```

### Outputs
*   Caches vectors to `data/embeddings.json` (subsequent runs are instantaneous).
*   Groups duplicates into clusters based on a graph Connected Components BFS algorithm.

---

## 5. `prs`
Analyzes pull requests stored in `prs.json` for maintenance metrics.

### Detection Categories
*   **Stale PRs:** No update activity for over 30 days.
*   **Reviews Needed:** PR has 0 comments (used as a proxy for no active reviews).
*   **Critical Fixes:** PR matches issue numbers (e.g. `#1234`) referencing an issue flagged as `Critical` by the severity analyzer.

### Usage
```bash
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar prs
```

---

## 6. `report`
Compiles all cached databases into a unified, highly actionable, timestamped **Weekly Health Report** in Markdown format.

### Usage
```bash
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar report
```

### Outputs
*   Generates `reports/log4j-health-report-YYYYMMDD-HHmmss.md` detailing:
    *   Metrics Summary (Open counts, Stale counts, Hidden counts).
    *   Concise lists of Critical and Hidden Critical issues with author logins and organization membership badges (`[Member]`).
    *   Lists of duplicate clusters and stale pull requests.

---

## 7. `trend`
Tracks and displays project health metrics across multiple snapshot intervals over time.

### Options
*   `-s`, `--save` : Gathers current metrics and saves them as a new snapshot in the `history/` directory.

### Usage
```bash
# Save today's snapshot
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar trend --save

# Display the trend dashboard
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar trend
```

### Outputs
*   Saves `history/YYYY-MM-DD.json`.
*   Outputs a chronological matrix in the terminal showing the change direction of issues and PRs.