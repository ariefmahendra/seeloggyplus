package com.seeloggyplus.util;

import com.seeloggyplus.update.UpdateLayout;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Restarts the application after a staged update by starting the platform
 * launcher script in the installation root and exiting the current process.
 */
public final class UpdateRelauncher {

    private static final Logger logger = LoggerFactory.getLogger(UpdateRelauncher.class);

    private UpdateRelauncher() {
    }

    public static void relaunch() {
        try {
            Path root = UpdateLayout.installationRoot();
            String script = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "launcher.bat" : "launcher.sh";
            Path launcher = root.resolve(script);
            if (Files.exists(launcher)) {
                new ProcessBuilder(launcher.toString()).directory(root.toFile()).start();
            } else {
                logger.warn("Launcher script not found at {}; restart manually", launcher);
            }
        } catch (Exception e) {
            logger.warn("Failed to relaunch application: {}", e.getMessage());
        }
        Platform.exit();
    }
}
