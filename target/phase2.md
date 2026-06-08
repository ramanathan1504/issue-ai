# Phase 2 Development Plan (`phase_2_plan.md`)

## Vision
Transform the existing single-repository CLI prototype into a multi-repository, database-driven, and highly queryable issue intelligence platform.

---

## 🏛️ The Four Pillars of Phase 2

```
┌────────────────────────────────────────────────────────────────────────┐
│                                PHASE 2                                 │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    ▼                               ▼                               ▼
┌───────────────┐               ┌───────────────┐               ┌───────────────┐
│   Pillar 1    │               │   Pillar 2    │               │   Pillar 3    │
│ SQLite Local  │               │  Multi-Repo   │               │   Semantic    │
│   Database    │               │    Scaling    │               │    Search     │
└───────────────┘               └───────────────┘               └───────────────┘
                                    │
                                    ▼
                            ┌───────────────┐
                            │   Pillar 4    │
                            │  Automation   │
                            │  Integration  │
                            └───────────────┘
```

---

## 📅 Milestone Timeline & Deliverables

### Milestone 1: Persistent SQLite Migration (Pillar 1)
*   **Objective:** Eliminate flat JSON files (`issues.json`, `prs.json`, `ai-analysis.json`, `embeddings.json`) and replace them with a unified local database file (`data/issue_intelligence.db`).
*   **Deliverables:**
    *   Maven integration of `sqlite-jdbc` [2].
    *   `DatabaseManager` class to automatically initialize the schema (Tables: `issues`, `labels`, `ai_analysis`, `embeddings`, `snapshots`) on start-up.
    *   Refactoring of `JsonStorage` to a database-backed repository layer (`SqliteStorage` / `IssueRepository`).
    *   Verification that all existing commands (`sync`, `analyze`, `hidden-critical`, `prs`, `report`, `trend`) load cleanly from the database.

### Milestone 2: Multi-Repository and Multi-Org Scaling (Pillar 2)
*   **Objective:** Remove hardcoded references to `apache/logging-log4j2` and allow analysis of any repository.
*   **Deliverables:**
    *   Database schema update: Add a `repository` column (indexing `owner/name`) to the `issues`, `embeddings`, and `snapshots` tables.
    *   CLI update: Introduce options like `-r` or `--repo` (e.g., `issue-ai sync --repo apache/kafka`).
    *   Update report-writing and trends to dynamically isolate data on a per-repository basis.

### Milestone 3: Semantic Search & Vector Querying (Pillar 3)
*   **Objective:** Enable active querying of the local backlog using semantic vectors.
*   **Deliverables:**
    *   New CLI Command: `issue-ai search <query>`.
    *   Under the hood:
        1.  Calls `all-minilm` to embed the user's plain-text search query.
        2.  Performs a fast mathematical Cosine Similarity scan across all saved issue vectors in the database.
        3.  Returns the top 5 most conceptually related issues, ordered by similarity score.

### Milestone 4: Triage Automation Engine (Pillar 4)
*   **Objective:** Automate local issues triage so they can run as manual checkups or inside lightweight local shell scripts.
*   **Deliverables:**
    *   New CLI Command: `issue-ai triage [issue_number]`.
    *   Under the hood:
        1.  Executes the severity ranking rules and local AI assessment on a newly synced issue.
        2.  Suggests tags, detects potential duplicates, and outputs a formatted log indicating the action to be taken (e.g., flag as critical, assign a developer, link to a duplicate).

---

## 🔄 Proposed Step-by-Step Execution
To maintain stability, it is recommended to execute these milestones sequentially.
