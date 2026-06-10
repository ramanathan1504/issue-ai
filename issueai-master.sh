#!/bin/zsh

# Exit immediately if any compilation or execution command fails
set -e

# 1. Manually export standard macOS and Homebrew paths
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"

# 2. Dynamically locate and export JAVA_HOME on macOS
if [ -z "$JAVA_HOME" ] && [ -x /usr/libexec/java_home ]; then
    export JAVA_HOME=$(/usr/libexec/java_home)
fi

# 3. Retrieve GITHUB_TOKEN securely from macOS Keychain
if [ -z "$GITHUB_TOKEN" ]; then
  KEYCHAIN_TOKEN="$(security find-generic-password -s github_token -w 2>/dev/null || true)"
  if [ -n "$KEYCHAIN_TOKEN" ]; then
    export GITHUB_TOKEN="$KEYCHAIN_TOKEN"
  fi
fi

# Double check if we successfully retrieved a token
if [ -z "$GITHUB_TOKEN" ]; then
  echo "Error: GITHUB_TOKEN is not set and was not found in macOS Keychain." >&2
  echo "Please run: security add-generic-password -a \"\$USER\" -s github_token -w \"<YOUR_TOKEN>\" -U" >&2
  exit 1
fi

# Navigate to your project directory
cd "$HOME/apache/issue-analyzer"

echo "=================================================="
echo "Phase 1: Compiling Clean Developer Build (Maven)"
echo "=================================================="
mvn clean package

echo ""
echo "=================================================="
echo "Phase 2: Syncing Backlog Registries (SQLite)"
echo "=================================================="
# Sync all active, enabled repositories
issue-ai sync --all

echo ""
echo "=================================================="
echo "Phase 2b: Syncing Personal Profile & Google Drive"
echo "=================================================="
# Auto-ingest new Google Drive chats and merged PRs
issue-ai sync --me

echo ""
echo "=================================================="
echo "Phase 3: Running AI Severity Assessments"
echo "=================================================="
issue-ai analyze -r apache/logging-log4j2

echo ""
echo "=================================================="
echo "Phase 4: Regenerating Semantic Vector Index"
echo "=================================================="
issue-ai duplicates -t 0.85 -r apache/logging-log4j2

echo ""
echo "=================================================="
echo "Phase 5: Generating Master Weekly Health Report"
echo "=================================================="
issue-ai report -r apache/logging-log4j2
issue-ai report --me -r apache/logging-log4j2
issue-ai trend --save -r apache/logging-log4j2

echo ""
echo "=================================================="
echo "Phase 6: Running Automated Security & Link Audit"
echo "=================================================="

# A. Extract current hidden critical issue numbers
CURRENT_HIDDEN=$(sqlite3 data/issue_intelligence.db "
SELECT i.number FROM issues i
JOIN ai_analysis a ON i.number = a.issue_number AND i.repository = a.repository
WHERE i.repository = 'apache/logging-log4j2'
  AND a.severity = 'Critical'
  AND i.number NOT IN (SELECT issue_number FROM labels WHERE label_name LIKE '%security%')
ORDER BY i.number ASC;")

STATE_FILE="data/last_hidden_criticals.txt"

if [ -f "$STATE_FILE" ]; then
    OLD_HIDDEN=$(cat "$STATE_FILE")
else
    OLD_HIDDEN=""
fi

NEW_ISSUES=""
for num in $CURRENT_HIDDEN; do
    if [[ ! " $OLD_HIDDEN " =~ " $num " ]]; then
        NEW_ISSUES="$NEW_ISSUES #$num"
    fi
done

echo "$CURRENT_HIDDEN" > "$STATE_FILE"

if [ ! -z "$NEW_ISSUES" ]; then
    NEW_ISSUES_TRIMMED=$(echo "$NEW_ISSUES" | xargs)
    osascript -e "display notification \"New Hidden Critical Issues found in Log4j: $NEW_ISSUES_TRIMMED\" with title \"Issue-AI Triage Alert\""
    echo "  ↳ [Alert] Detected NEW Hidden Critical issues: $NEW_ISSUES_TRIMMED"
else
    echo "  ↳ [Status] No new hidden critical issues detected since last run."
fi

echo ""
echo "=================================================="
echo "Master pipeline execution completed successfully!"
echo "=================================================="