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

if [ -z "$GITHUB_TOKEN" ]; then
  echo "Error: GITHUB_TOKEN is not set and was not found in macOS Keychain." >&2
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
issue-ai sync --all

echo ""
echo "=================================================="
echo "Phase 2b: Syncing Personal Profile & Google Drive"
echo "=================================================="
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
echo "Phase 6: Advanced macOS Alert & Notification Engine"
echo "=================================================="

# Helper function for beautiful macOS notifications
function send_mac_alert() {
    local icon="$1"
    local title="$2"
    local subtitle="$3"
    local message="$4"
    # Uses the 'Glass' system sound for a crisp notification chime
    osascript -e "tell application \"System Events\" to display notification \"$message\" with title \"$icon $title\" subtitle \"$subtitle\" sound name \"Glass\""
}

# Helper function to track state and only notify on NEW items
function check_and_notify() {
    local state_file="$1"
    local current_data="$2"
    local icon="$3"
    local title="$4"
    local subtitle="$5"

    if [ -f "$state_file" ]; then
        OLD_DATA=$(cat "$state_file")
    else
        OLD_DATA=""
    fi

    NEW_ITEMS=""
    for item in $current_data; do
        if [[ ! " $OLD_DATA " =~ " $item " ]]; then
            NEW_ITEMS="$NEW_ITEMS #$item"
        fi
    done

    echo "$current_data" > "$state_file"

    if [ ! -z "$NEW_ITEMS" ]; then
        TRIMMED_ITEMS=$(echo "$NEW_ITEMS" | xargs)
        send_mac_alert "$icon" "$title" "$subtitle" "$TRIMMED_ITEMS"
        echo "  ↳ [$title] Alert sent for: $TRIMMED_ITEMS"
    else
        echo "  ↳ [$title] No new updates."
    fi
}

# --- QUERY 1: Global Hidden Criticals (Security Risks) ---
CURRENT_HIDDEN=$(sqlite3 data/issue_intelligence.db "
SELECT i.number FROM issues i JOIN ai_analysis a ON i.number = a.issue_number AND i.repository = a.repository
WHERE i.repository = 'apache/logging-log4j2' AND a.severity = 'Critical'
AND i.number NOT IN (SELECT issue_number FROM labels WHERE label_name LIKE '%security%') ORDER BY i.number ASC;")

check_and_notify "data/state_hidden.txt" "$CURRENT_HIDDEN" "🚨" "Log4j Security Alert" "New Hidden Criticals Found!"

# --- QUERY 2: Brand New Critical Issues (Last 24 Hours) ---
NEW_CRITICALS=$(sqlite3 data/issue_intelligence.db "
SELECT i.number FROM issues i JOIN ai_analysis a ON i.number = a.issue_number AND i.repository = a.repository
WHERE i.repository = 'apache/logging-log4j2' AND a.severity = 'Critical'
AND julianday('now') - julianday(i.created_at) <= 1 ORDER BY i.number ASC;")

check_and_notify "data/state_new_criticals.txt" "$NEW_CRITICALS" "🛡️" "Log4j Triage Alert" "New Critical Bugs Reported Today!"

# --- QUERY 3: My Personal Stale PRs (Inactivity > 30 Days) ---
MY_USERNAME=$(sqlite3 data/issue_intelligence.db "SELECT value FROM system_config WHERE key = 'github.username';")
MY_STALE_PRS=$(sqlite3 data/issue_intelligence.db "
SELECT number FROM issues
WHERE repository = 'apache/logging-log4j2' AND is_pull_request = 1
AND author = '$MY_USERNAME' AND julianday('now') - julianday(updated_at) > 30 ORDER BY number ASC;")

check_and_notify "data/state_my_stale.txt" "$MY_STALE_PRS" "👤" "Personal Developer Alert" "Your PRs are going stale!"

echo ""
echo "=================================================="
echo "Phase 7: Automated Vault Backup & Preservation"
echo "=================================================="
# Compresses your entire database securely and auto-rotates old files
issue-ai backup

echo ""
echo "=================================================="
echo "Master pipeline execution completed successfully!"
echo "=================================================="