package org.apache.issueai.cli;

import java.util.Scanner;
import java.util.concurrent.Callable;
import org.apache.issueai.storage.SqliteStorage;
import picocli.CommandLine.Command;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Command(
        name = "setup",
        description = "Interactive wizard to configure local system settings, models, and paths"
)
public class SetupCommand implements Callable<Integer> {

    private static final Logger LOGGER = LogManager.getLogger(SetupCommand.class);

    @Override
    public Integer call() throws Exception {
        Scanner scanner = new Scanner(System.in);

        LOGGER.info("==================================================");
        LOGGER.info("          issue-ai Interactive Setup Wizard       ");
        LOGGER.info("==================================================");

        // 1. Configure GitHub Username
        String currentUsername = SqliteStorage.loadConfig("github.username");
        LOGGER.info("Current GitHub Username: [ {} ]", currentUsername == null ? "(none)" : currentUsername);
        LOGGER.info("Enter new Username (or press Enter to keep current):");
        String inputUsername = scanner.nextLine().trim();
        if (!inputUsername.isEmpty()) {
            SqliteStorage.saveConfig("github.username", inputUsername);
            currentUsername = inputUsername;
            LOGGER.info("  ↳ Updated GitHub Username to: {}", currentUsername);
        }

        // 2. Configure Triage Model
        String currentTriageModel = SqliteStorage.loadConfig("ollama.model.triage");
        LOGGER.info("Current AI Triage Model: [ {} ]", currentTriageModel == null ? "(none)" : currentTriageModel);
        LOGGER.info("Enter new Triage Model (or press Enter to keep current):");
        String inputTriage = scanner.nextLine().trim();
        if (!inputTriage.isEmpty()) {
            SqliteStorage.saveConfig("ollama.model.triage", inputTriage);
            currentTriageModel = inputTriage;
            LOGGER.info("  ↳ Updated AI Triage Model to: {}", currentTriageModel);
        }

        // 3. Configure Embedding Model
        String currentEmbeddingModel = SqliteStorage.loadConfig("ollama.model.embedding");
        LOGGER.info("Current Vector Embedding Model: [ {} ]", currentEmbeddingModel == null ? "(none)" : currentEmbeddingModel);
        LOGGER.info("Enter new Embedding Model (or press Enter to keep current):");
        String inputEmbedding = scanner.nextLine().trim();
        if (!inputEmbedding.isEmpty()) {
            SqliteStorage.saveConfig("ollama.model.embedding", inputEmbedding);
            currentEmbeddingModel = inputEmbedding;
            LOGGER.info("  ↳ Updated Vector Embedding Model to: {}", currentEmbeddingModel);
        }

        // 4. Configure Guidance Model
        String currentGuidanceModel = SqliteStorage.loadConfig("ollama.model.guidance");
        LOGGER.info("Current Deep Guidance Model: [ {} ]", currentGuidanceModel == null ? "(none)" : currentGuidanceModel);
        LOGGER.info("Enter new Guidance Model (or press Enter to keep current):");
        String inputGuidance = scanner.nextLine().trim();
        if (!inputGuidance.isEmpty()) {
            SqliteStorage.saveConfig("ollama.model.guidance", inputGuidance);
            currentGuidanceModel = inputGuidance;
            LOGGER.info("  ↳ Updated Deep Guidance Model to: {}", currentGuidanceModel);
        }

        // 5. Configure Google Drive Locations
        String currentDrivePaths = SqliteStorage.loadConfig("drive.paths");
        LOGGER.info("Current Google Drive Paths: [ {} ]", currentDrivePaths == null ? "(none)" : currentDrivePaths);
        LOGGER.info("Enter new Google Drive Paths (comma-separated, or press Enter to keep current):");
        String inputDrive = scanner.nextLine().trim();
        if (!inputDrive.isEmpty()) {
            SqliteStorage.saveConfig("drive.paths", inputDrive);
            currentDrivePaths = inputDrive;
            LOGGER.info("  ↳ Updated Google Drive Paths to: {}", currentDrivePaths);
        }

        // 6. Security Credential Check
        LOGGER.info("Checking secure credentials on this host...");
        String token = System.getenv("GITHUB_TOKEN");
        boolean hasKeychain = false;

        // Attempt best-effort secure keychain check on macOS
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "sh", "-c", "security find-generic-password -s github_token -w 2>/dev/null || true"
                });
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        hasKeychain = true;
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignore execution errors on non-macOS systems
        }

        if (token != null && !token.trim().isEmpty()) {
            LOGGER.info("  ✔ GITHUB_TOKEN detected in active environment variables.");
        } else if (hasKeychain) {
            LOGGER.info("  ✔ GITHUB_TOKEN detected securely inside macOS Keychain.");
        } else {
            LOGGER.warn("  ⚠ WARNING: No GITHUB_TOKEN found! Ensure it is set in env or run:");
            LOGGER.warn("    security add-generic-password -a \"$USER\" -s github_token -w \"<YOUR_TOKEN>\" -U");
        }

        LOGGER.info("==================================================");
        LOGGER.info("Configuration successfully updated in local SQLite!");
        LOGGER.info("==================================================");

        return 0;
    }
}