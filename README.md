# Issue Intelligence CLI (`issue-ai`)

An advanced, offline-first, AI-powered Personal Copilot designed for maintainers. It manages issue triage, cross-repository dependency tracking, and provides real-time, context-aware coding guidance using a Dual-Engine Developer Memory.

---

## 🧠 The Dual-Engine Architecture

This platform separates public repository data from your private developer identity to ensure absolute security and zero context-leakage:

1.  **The Repository Engine (Public):** Syncs 14+ enterprise Java repositories (Kafka, Quarkus, Elastic, etc.) into a unified SQLite database. It automatically extracts cross-project dependencies, applies JIRA Bridge matching, and tracks ecosystem health.
2.  **The Personal Copilot (Private):** Ingests your 1-year GitHub PR footprint and your local Google Drive AI Studio/ChatGPT chat logs to build a **Developer Expertise Vector**.

### ⚡ Omni-Cloud Hybrid AI Intelligence
The CLI utilizes an Adaptive Routing pattern:
*   **Tier 1 (Local):** Executes fast, privacy-first reasoning and semantic RAG using local Ollama models (`qwen2.5:7b` & `all-minilm`).
*   **Tier 2 (Cloud):** Seamlessly bridges to **Google Gemini**, **OpenAI GPT-4o**, or **Anthropic Claude 3.5** during interactive chat sessions when deep, expert-level architectural code synthesis is required.

---

## 🛠 Prerequisites

*   **Java 17**
*   **Apache Maven**
*   **Ollama** (Local models: `qwen2.5:7b`, `qwen2.5:0.5b`, `all-minilm`)

---

## 🚀 Setup & Installation

1.  **Compile the Project:**
    ```bash
    mvn clean package
    ```

2.  **Run the Interactive Wizard:**
    This command securely registers your GitHub Token, Cloud API Keys (Gemini/OpenAI/Anthropic), Ollama models, and Google Drive paths into the SQLite `system_config` table.
    ```bash
    issue-ai setup
    ```

3.  **Install Global Command (macOS/Linux):**
    ```bash
    sudo nano /usr/local/bin/issue-ai
    # Paste: java -jar /absolute/path/to/target/issue-ai-0.1.0-SNAPSHOT.jar "$@"
    sudo chmod +x /usr/local/bin/issue-ai
    ```

---

## ⏱ Background Automation (macOS Launchd)

The project includes an hourly background daemon that automatically fetches updates, parses AI logs, chunks JSON chat exports, runs intelligence vectors, and issues native desktop notifications if a Hidden Critical security threat is detected.

1. Configure `issueai-master.sh` with your correct paths.
2. Load the macOS `.plist` scheduler:
   ```bash
   launchctl load ~/Library/LaunchAgents/issueai.plist
   ```
3. Monitor the background service logs:
   ```bash
   tail -f ~/apache/issue-analyzer/issueai_run.log
   ```

---

## 🔄 The Master Workflow

For standard manual repository analysis:

```bash
# 1. Sync all ecosystem repositories (Log4j, Kafka, Spark, Elastic, etc.)
issue-ai sync --all

# 2. Sync your personal 1-year Developer Profile & Google Drive chat logs
issue-ai sync --me

# 3. Analyze the backlog and generate semantic vector embeddings
issue-ai analyze
issue-ai duplicates -t 0.85

# 4. Generate your Personal Contribution Roadmap Report
issue-ai report --me

# 5. Open a live Omni-Chat to solve a specific issue using Claude 3.5
issue-ai chat 1666 --claude
```

---

## 💾 Database Migrations
The tool utilizes a zero-configuration SQLite database (`data/issue_intelligence.db`). It features an **automatic, non-destructive migration engine**. Any future schema modifications are safely performed on application boot without dropping or overwriting your previously synchronized data.