# Log4j Issue Intelligence CLI

## Vision

Build an AI-powered CLI tool that helps Apache Log4j maintainers identify:

- Critical issues
- Hidden critical issues
- Duplicate issues
- Stale PRs
- High-value PRs
- Weekly project health reports

Initial scope:

- ~160 Open Issues
- ~54 Open Pull Requests

Repository:

- apache/logging-log4j2

---

# Success Criteria

The project succeeds if maintainers can discover issues they would otherwise miss.

Target outcome:

- 70%+ useful Critical issue predictions
- Hidden Critical issues validated by maintainers
- Faster issue triage

---

# Architecture

text GitHub API     ↓ Collector     ↓ JSON Storage     ↓ Analysis Engine     ↓ Local LLM     ↓ Reports

---

# Tech Stack

## Core

- Java 21
- Maven
- Picocli
- Jackson

## AI

- Ollama
- Qwen3 4B / 8B

## Future

- PostgreSQL
- pgvector

---

# Phase 1: Data Collection

## Command

bash issue-ai sync

## Collect

### Issues

- Number
- Title
- Body
- Labels
- Comments
- Author
- Assignees
- Created Date
- Updated Date

### Pull Requests

- Number
- Title
- Body
- Reviews
- Review Requests
- Comments
- Created Date
- Updated Date

## Storage Structure

text data/ ├── issues.json ├── prs.json └── cache/

---

# Phase 2: Feature Extraction

Convert raw GitHub data into signals.

## Features

- days_open
- comment_count
- label_count
- last_activity_days
- has_stacktrace
- has_reproducer
- mentions_deadlock
- mentions_memory_leak
- mentions_security
- mentions_data_loss
- mentions_regression
- mentions_crash

Example:

json {   "issue":1234,   "commentCount":32,   "daysOpen":250,   "mentionsDeadlock":true }

---

# Phase 3: Severity Engine V1

No AI yet.

## Critical Keywords

text security vulnerability rce remote code execution deadlock hang crash memory leak data corruption data loss

## High Keywords

text performance regression thread leak incorrect output

## Scoring

text Critical = 100 High = 70 Medium = 40 Low = 10

Additional boosts:

text +20 comments > 20 +10 issue age > 180 days +10 activity within last 7 days

## Command

bash issue-ai rank

---

# Phase 4: AI Severity Analysis

Use Ollama locally.

## Model

text qwen3:8b

## Prompt

text You are an Apache Log4j maintainer.  Classify:  Critical High Medium Low  Determine: - Severity - Confidence - Impact  Explain your reasoning.

## Output

json {   "severity":"Critical",   "confidence":0.91,   "reason":"Potential deadlock affecting production systems." }

## Command

bash issue-ai analyze

---

# Phase 5: Hidden Critical Detection

Find issues maintainers may have underestimated.

## Rules

text Label != Security AND AI Severity == Critical

or

text Many comments + Bug label + High AI score

## Command

bash issue-ai hidden-critical

## Example

text Issue #4321  Current Label: bug  AI Severity: Critical  Reason: Multiple reports of application hangs in production.

---

# Phase 6: Duplicate Detection

Generate embeddings from:

- Title
- Description
- Top comments

## Goal

Identify issues discussing the same root cause.

## Output

text Cluster A  #1234 #2233 #4455  Similarity: 0.94

## Command

bash issue-ai duplicates

---

# Phase 7: PR Intelligence

Analyze all open PRs.

## Detect

### Stale PRs

text No activity > 30 days

### Review Needed

text No reviews

### Critical Fixes

text PR linked to Critical issue

## Command

bash issue-ai prs

## Example

text PR #876  Fixes: Issue #1234  Severity: Critical  Waiting: 21 days

---

# Phase 8: Weekly Report Generator

## Command

bash issue-ai report

## Example Report

markdown # Log4j Weekly Health Report  Open Issues: 160 Open PRs: 54  ## Critical Issues 1. #1234 2. #5678  ## Hidden Critical 1. #8888  ## Duplicate Groups 3  ## Stale PRs 12

## Output Directory

text reports/

---

# Phase 9: Historical Trends

Store weekly snapshots.

text history/ ├── week_01.json ├── week_02.json ├── week_03.json

Track:

- Critical issue count
- High priority count
- Stale PR count
- Duplicate count

## Command

bash issue-ai trend

Example:

text Critical Issues  Week 1: 12 Week 2: 10 Week 3: 8

---

# CLI Commands

bash issue-ai sync  issue-ai rank  issue-ai analyze  issue-ai hidden-critical  issue-ai duplicates  issue-ai prs  issue-ai report  issue-ai trend

---

# Package Structure

text org.apache.issueai  ├── cli ├── github ├── model ├── storage ├── llm  ├── analyzer │   ├── severity │   ├── duplicate │   ├── hidden │   └── pr  └── report

---

# Development Timeline

## Week 1

- GitHub API integration
- JSON storage
- sync command

## Week 2

- Severity scoring
- rank command

## Week 3

- Ollama integration
- AI analysis

## Week 4

- Hidden Critical detection

## Week 5

- Duplicate detection

## Week 6

- PR intelligence

## Week 7

- Weekly report generation

## Week 8

- Historical trend analysis

---

# Future Expansion

After Log4j validation:

bash issue-ai analyze apache/kafka  issue-ai analyze apache/camel  issue-ai analyze apache/flink

Eventually:

bash issue-ai apache-top-100

Generate a ranked list of the most critical issues across major Apache projects.

---

# MVP Goal

A maintainer should be able to run:

bash issue-ai report

and immediately receive:

- Top Critical Issues
- Hidden Critical Issues
- Duplicate Clusters
- Stale PRs
- Project Health Summary

without opening GitHub manually.