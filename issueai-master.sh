#!/bin/zsh

# Exit immediately if any compilation or execution command fails
set -e

# 1. Manually export standard macOS and Homebrew paths (Bypassing interactive .zshrc)
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"

# 2. Dynamically locate and export JAVA_HOME on macOS
if [ -z "$JAVA_HOME" ] && [ -x /usr/libexec/java_home ]; then
    export JAVA_HOME=$(/usr/libexec/java_home)
fi

# 3. Retrieve GITHUB_TOKEN securely from macOS Keychain if not already exported in the env
if [ -z "$GITHUB_TOKEN" ]; then
  KEYCHAIN_TOKEN="$(security find-generic-password -s github_token -w 2>/dev/null || true)"
  if [ -n "$KEYCHAIN_TOKEN" ]; then
    export GITHUB_TOKEN="$KEYCHAIN_TOKEN"
  fi
fi

# Double check if we successfully retrieved a token
if [ -z "$GITHUB_TOKEN" ]; then
  System.err.println "Error: GITHUB_TOKEN is not set and was not found in macOS Keychain."
  System.err.println "Please run: security add-generic-password -a \"\$USER\" -s github_token -w \"<YOUR_TOKEN>\" -U"
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
# Sync all active, enabled repositories in your SQLite registry
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar sync --all

echo ""
echo "=================================================="
echo "Phase 3: Running AI Severity Assessments"
echo "=================================================="
# Predict priorities using the local Qwen model on Log4j (or others)
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar analyze -m qwen2.5:0.5b -r apache/logging-log4j2

echo ""
echo "=================================================="
echo "Phase 4: Regenerating Semantic Vector Index"
echo "=================================================="
# Rebuild duplicate vector mappings
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar duplicates -t 0.70 -r apache/logging-log4j2

echo ""
echo "=================================================="
echo "Phase 5: Generating Master Weekly Health Report"
echo "=================================================="
# Compile the updated Markdown report with JIRA and ecosystem links
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar report -r apache/logging-log4j2

# Save today's historical snapshot
java -jar target/issue-ai-0.1.0-SNAPSHOT.jar trend --save -r apache/logging-log4j2

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

# B. Read previous state if exists
if [ -f "$STATE_FILE" ]; then
    OLD_HIDDEN=$(cat "$STATE_FILE")
else
    OLD_HIDDEN=""
fi

# C. Detect if any NEW issues have arrived
NEW_ISSUES=""
for num in $CURRENT_HIDDEN; do
    if [[ ! " $OLD_HIDDEN " =~ " $num " ]]; then
        NEW_ISSUES="$NEW_ISSUES #$num"
    fi
done

# D. Save the current state for the next hour's run
echo "$CURRENT_HIDDEN" > "$STATE_FILE"

# E. Only trigger macOS notification if we found actually NEW hidden criticals
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