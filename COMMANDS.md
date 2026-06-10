# CLI Command Reference (`COMMANDS.md`)

This guide outlines all subcommands available in the `issue-ai` CLI platform.

## Global Options
*   `-r`, `--repo` : Target GitHub repository to analyze (Default: `owner/reponame`).

---

## 🛠 Configuration & Setup

### `setup`
Interactive wizard to configure your secure environment, API keys, Google Drive paths, and AI models.
```bash
issue-ai setup
```
*   **Security:** Checks active environment variables and the macOS Keychain for `GITHUB_TOKEN`, `GEMINI_API_KEY`, `OPENAI_API_KEY`, and `ANTHROPIC_API_KEY`.
*   **Storage:** Saves all configurations to the local SQLite `system_config` table.

---

## 🔄 Data Synchronization

### `sync`
Fetches issues, pull requests, author profiles, and ecosystem dependencies from GitHub into SQLite.
*   `-a`, `--all` : Sequentially synchronize all active repositories in your database registry.
*   `--add <repo>` : Register a new repository to your local database watchlist.
*   `--remove <repo>` : Remove a repository from the database watchlist.
*   `--me` : **Personal Sync.** Fetches your 1-year PR history, creates your Developer Expertise Vector, and recursively crawls your Google Drive (automatically parsing ChatGPT/Claude `.json` exports and `.md` files) to index your conversational memory.

```bash
issue-ai sync --all
issue-ai sync --me
```

---

## 🤖 The Personal Copilot

### `chat`
Opens a live, interactive REPL (Read-Eval-Print Loop) to act as your pair-programmer.
*   **Context Aware:** Loads your personal SQLite memory (past PR stories, AI Studio chats, and ChatGPT/Claude JSON exports) automatically.
*   **Omni-Cloud Escalation:** Evaluates locally via Ollama. Type `y` to seamlessly escalate a prompt to Google Gemini, OpenAI GPT-4o, or Anthropic Claude for expert cloud resolution.
*   **Real-Time Memory:** Upon typing `exit`, the chat transcript is automatically saved to your Google Drive as a Markdown file and instantly embedded back into SQLite memory.

**Usage Options:**
```bash
issue-ai chat 1666            # Escalates to Gemini (Default)
issue-ai chat 1666 --openai   # Escalates to OpenAI GPT-4o
issue-ai chat 1666 --claude   # Escalates to Anthropic Claude 3.5
```

### `guide`
Generates a structured, step-by-step resolution blueprint for a specific issue using local RAG (Retrieval-Augmented Generation).
*   `--gemini` : Bypass the local model and route immediately to the Gemini API.
```bash
issue-ai guide 1666
```

### `triage`
Executes an automated triage audit on a specific issue.
*   Outputs V1 severity, local AI severity, semantic duplicate overlaps, JIRA-bridge dependencies, and immediate recommended actions.
```bash
issue-ai triage 4088
```

---

## 📊 Analytics & Reporting

### `search`
Performs an offline semantic vector lookup on your backlog.
*   `-g`, `--global` : Run the search across all synced repositories simultaneously.
```bash
issue-ai search "deadlock in network appender" --global
```

### `report`
Compiles SQLite data into a unified Weekly Health Report in Markdown format.
*   `--me` : Generates a highly personalized roadmap tailored to your Developer Expertise Vector, including Regression Guard alerts and your specific stale PRs.
```bash
issue-ai report --me
issue-ai report -r apache/kafka
```

### `trend`
Tracks project health metrics over time.
*   `-s`, `--save` : Save today's metrics as a new historical snapshot.
```bash
issue-ai trend --save
```

### `analyze`
Performs batch AI Severity Analysis on all open issues using local Ollama.
```bash
issue-ai analyze
```

### `duplicates`
Identifies duplicate issue clusters using local vector embeddings (Cosine Similarity).
```bash
issue-ai duplicates -t 0.85
```

### `hidden-critical`
Cross-references raw metadata against AI evaluations to detect underestimated security threats.
```bash
issue-ai hidden-critical
```