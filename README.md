# Log4j Issue Intelligence CLI (`issue-ai`)

An offline-first, AI-powered CLI tool designed to assist Apache Log4j maintainers with issue triage, duplicate identification, security audits, and pull request tracking across multiple repositories.

By migrating to a central SQLite database and running compact models locally via Ollama, the tool runs entirely offline, making it highly data-efficient and friendly for low-bandwidth connections.

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

On its first boot, the program initializes a single, zero-configuration database file. All data is managed relationally:

```text
issue-analyzer/
├── data/
│   └── issue_intelligence.db  # SQLite database storing issues, labels, AI evaluations, embeddings, and snapshots
└── reports/
    └── logging-log4j2-issues-report-20260609-143834.md  # Generated weekly reports
```

---

## Multi-Repository & Ecosystem Tracking

Because the database utilizes **composite keys** (combining `repository` and `number`), you can sync and track multiple repositories side-by-side [3].

Additionally, the system automatically tracks **Ecosystem Connections**:
*   **JIRA Bridge Pattern:** Automatically detects where developers across different projects discuss identical JIRA issues (e.g., `KAFKA-XXXX`, `LOG4J2-XXXX`) and links them as cross-repo overlaps [1.1.1].
*   **Downstream Links:** Detects when other synced repositories (like Kafka or Spark) link to your core project's issues [2].

---

## Recommended Workflow Loop

For standard repository analysis, execute the following workflow (using Apache Log4j as an example):

```bash
# 1. Sync live GitHub data to local SQLite tables
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar sync -r apache/logging-log4j2

# 2. Run local LLM to predict priorities
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar analyze -m qwen2.5:0.5b -r apache/logging-log4j2

# 3. Cache semantic duplicate index
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar duplicates -t 0.70 -r apache/logging-log4j2

# 4. Search backlog offline semantically
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar search "PatternLayout truncation deadlock" -r apache/logging-log4j2

# 5. Run a consolidated triage audit on a specific issue
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar triage 4088 -r apache/logging-log4j2

# 6. Compile the weekly health report
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar report -r apache/logging-log4j2

# 7. Capture a snapshot to track project trends
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar trend --save -r apache/logging-log4j2
```