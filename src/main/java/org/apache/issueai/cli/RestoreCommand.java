package org.apache.issueai.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
        name = "restore",
        description = "Import and restore your AI memory and database from a backup archive"
)
public class RestoreCommand implements Callable<Integer> {

    private static final Logger LOGGER = LogManager.getLogger(RestoreCommand.class);

    @Parameters(index = "0", description = "The path to the backup .zip file")
    private String backupFilePath;

    @Override
    public Integer call() throws Exception {
        Path zipPath = Paths.get(backupFilePath);
        if (!Files.exists(zipPath) || !backupFilePath.endsWith(".zip")) {
            LOGGER.error("Invalid backup file: {}. Please provide a valid .zip backup archive.", zipPath.toAbsolutePath());
            return 1;
        }

        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        LOGGER.info("Starting restoration of your AI Memory into '{}'...", dataDir.toAbsolutePath());

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(dataDir.toFile(), zipEntry.getName());

                // Prevent Zip Slip vulnerability
                if (!newFile.getCanonicalPath().startsWith(dataDir.toFile().getCanonicalPath())) {
                    throw new IOException("Security Error: Bad zip entry targeting outside data directory.");
                }

                LOGGER.info("  ↳ Restoring: {}", zipEntry.getName());
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();

            LOGGER.info("==================================================");
            LOGGER.info("✔ Restoration completed successfully!");
            LOGGER.info("  1. Your database, vectors, and memory are fully restored.");
            LOGGER.info("  2. Please run 'issue-ai setup' to register your API Keys to this machine's Keychain.");
            LOGGER.info("  3. Ensure your Ollama models are pulled (e.g., 'ollama pull all-minilm').");
            LOGGER.info("==================================================");

        } catch (IOException e) {
            LOGGER.error("Failed to restore backup archive: {}", e.getMessage());
            return 1;
        }

        return 0;
    }
}