package com.seeloggyplus.util;

import com.seeloggyplus.update.UpdateLayout;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Restarts the application after a staged update.
 * <p>
 * On Windows the app is started directly with {@code javaw} so no console window
 * is created: the root {@code launcher.bat} of legacy installs runs
 * {@code java.exe} in the foreground, which would keep a terminal open for the
 * whole session. The platform launcher script remains the fallback.
 */
public final class UpdateRelauncher {

    private static final Logger logger = LoggerFactory.getLogger(UpdateRelauncher.class);

    /** Packaged default of {@code max.memory.gb} in launcher.properties. */
    static final int DEFAULT_MAX_MEMORY_GB = 4;

    private UpdateRelauncher() {
    }

    public static void relaunch() {
        try {
            Path root = UpdateLayout.installationRoot();
            ProcessBuilder builder = isWindows() ? windowsRelaunch(root) : null;
            if (builder == null) {
                builder = scriptRelaunch(root);
            }
            if (builder != null) {
                builder.start();
            } else {
                logger.warn("No usable launcher found under {}; restart the application manually", root);
            }
        } catch (Exception e) {
            logger.warn("Failed to relaunch application: {}", e.getMessage());
        }
        Platform.exit();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Hidden Windows restart: {@code javaw -Xmx<mem>g -jar <active jar>} from the install root. */
    static ProcessBuilder windowsRelaunch(Path root) {
        return windowsCommand(root, System.getProperty("java.home"))
                .map(command -> new ProcessBuilder(command).directory(root.toFile()))
                .orElse(null);
    }

    /** Builds the invisible Windows command, or empty when no jar/JVM can be found. */
    static Optional<List<String>> windowsCommand(Path root, String javaHome) {
        Path jar = activeJar(root);
        Path java = resolveJavaw(javaHome);
        if (jar == null || java == null) {
            return Optional.empty();
        }
        List<String> command = new ArrayList<>(4);
        command.add(java.toString());
        command.add("-Xmx" + maxMemoryGb(root) + "g");
        command.add("-jar");
        command.add(jar.toString());
        return Optional.of(command);
    }

    /** The jar the launcher would start: {@code current} when staged, else the root jar. */
    static Path activeJar(Path root) {
        try {
            UpdateLayout layout = new UpdateLayout(root);
            Optional<String> current = layout.currentVersion();
            if (current.isPresent() && layout.isStaged(current.get())) {
                return layout.jarFor(current.get());
            }
        } catch (IOException e) {
            logger.debug("Could not read the active version: {}", e.getMessage());
        }
        Path rootJar = root.resolve(UpdateLayout.APP_JAR_NAME);
        return Files.isRegularFile(rootJar) ? rootJar : null;
    }

    /** {@code javaw.exe} from the running JVM; falls back to {@code java.exe}. */
    static Path resolveJavaw(String javaHome) {
        if (javaHome == null || javaHome.isBlank()) {
            return null;
        }
        Path bin = Path.of(javaHome, "bin");
        Path javaw = bin.resolve("javaw.exe");
        if (Files.isRegularFile(javaw)) {
            return javaw;
        }
        Path java = bin.resolve("java.exe");
        return Files.isRegularFile(java) ? java : null;
    }

    /** {@code max.memory.gb} from launcher.properties, with a safe default. */
    static int maxMemoryGb(Path root) {
        Path propertiesFile = root.resolve("launcher.properties");
        if (!Files.isRegularFile(propertiesFile)) {
            return DEFAULT_MAX_MEMORY_GB;
        }
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            Properties properties = new Properties();
            properties.load(input);
            int configured = Integer.parseInt(properties.getProperty("max.memory.gb", "").trim());
            return configured > 0 ? configured : DEFAULT_MAX_MEMORY_GB;
        } catch (Exception e) {
            return DEFAULT_MAX_MEMORY_GB;
        }
    }

    private static ProcessBuilder scriptRelaunch(Path root) {
        String script = isWindows() ? "launcher.bat" : "launcher.sh";
        Path launcher = root.resolve(script);
        if (!Files.isRegularFile(launcher)) {
            return null;
        }
        return new ProcessBuilder(launcher.toString()).directory(root.toFile());
    }
}
