#!/bin/zsh

# Exit immediately if a command fails
set -e

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "❌ Error: Please provide a version number."
    echo "Usage: ./release.sh 1.0.1"
    exit 1
fi

echo "========================================"
echo "🚀 Starting Automated Release for v$VERSION"
echo "========================================"

echo "1. Compiling fresh project..."
mvn clean package -DskipTests

# The Shade plugin outputs the fat JAR directly to this name
RELEASE_JAR="target/issue-ai-${VERSION}.jar"

if [ ! -f "$RELEASE_JAR" ]; then
    echo "❌ Error: Could not find $RELEASE_JAR!"
    exit 1
fi

echo "2. Uploading to GitHub Releases..."
gh release create "v$VERSION" "$RELEASE_JAR" --title "Issue-AI v$VERSION" --generate-notes

echo "3. Calculating SHA-256 Hash..."
JAR_SHA=$(shasum -a 256 "$RELEASE_JAR" | awk '{print $1}')
echo "   ↳ SHA256: $JAR_SHA"

echo "4. Updating Homebrew Tap..."
# Clone the tap into a temporary hidden folder
TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"

git clone https://github.com/ramanathan1504/homebrew-issue-ai.git
cd homebrew-issue-ai

# Safely replace the old URL and SHA with the brand new ones
sed -i '' -e "s|url \".*\"|url \"https://github.com/ramanathan1504/issue-ai/releases/download/v${VERSION}/issue-ai-${VERSION}.jar\"|" issue-ai.rb
sed -i '' -e "s|sha256 \".*\"|sha256 \"${JAR_SHA}\"|" issue-ai.rb

# Commit and push the updated formula back to GitHub
git add issue-ai.rb
git commit -m "Bump version to v$VERSION"
git push origin main

# Cleanup
rm -rf "$TEMP_DIR"

echo "========================================"
echo "✅ Release v$VERSION Published Successfully!"
echo "Global users can now run: brew upgrade issue-ai"
echo "========================================"