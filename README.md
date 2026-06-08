# Log4j Issue Intelligence CLI (`issue-ai`)

An offline-first, AI-powered CLI tool designed to assist Apache Log4j maintainers with issue triage, duplicate identification, security audits, and pull request tracking.

By prioritizing local data structures and running lightweight models locally via Ollama, the tool runs entirely offline, making it highly data-efficient and friendly for low-bandwidth connections.

---

## Prerequisites

*   **Java 21** (or higher)
*   **Apache Maven**
*   **Ollama** (for local AI and embeddings generation)

---

## Local AI Setup (Mobile Data Friendly)

To perform AI assessments and duplicate detection without consuming large amounts of cellular data, pull these highly optimized, compact models:

```bash
# 1. Download the ultra-lightweight reasoning model (approx. 390 MB)
ollama pull qwen2.5:0.5b

# 2. Download the semantic embedding model (approx. 45 MB)
ollama pull all-minilm
```

Ensure the local Ollama background service is running:
```bash
ollama serve
```

---

## Building the Project

Compile the project and package it into an executable JAR using Maven:

```bash
mvn clean package
```

The resulting executable will be generated at:
`target/issue-ai-0.1.0-SNAPSHOT.jar`

---

## Local Data Structure

Running commands will populate your project directory with the following offline databases and directories:

```text
issue-analyzer/
├── data/
│   ├── issues.json       # Raw GitHub issues cache
│   ├── prs.json          # Raw GitHub pull requests cache
│   ├── ai-analysis.json  # Saved AI severity predictions
│   └── embeddings.json   # Cached semantic vector embeddings
├── history/
│   └── 2026-06-09.json   # Historical weekly snapshots
└── reports/
    └── log4j-health-report-20260609-235400.md  # Generated markdown reports
```

---

## Recommended Workflow Loop

For standard repository analysis, execute the following workflow:

```bash
# 1. Sync live GitHub data to local cache
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar sync

# 2. Run local LLM to predict priorities
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar analyze -m qwen2.5:0.5b

# 3. Cluster duplicate issues semantically
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar duplicates -t 0.70

# 4. Compile the consolidated weekly report
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar report

# 5. Capture a snapshot to track project trends
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar trend --save
```