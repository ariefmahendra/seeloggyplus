package com.seeloggyplus.features.update.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class RootLauncherInstaller {

    public static final String FILE_NAME = "launcher.bat";

    private static final String JAVAW = "javaw";

    private RootLauncherInstaller() {
    }

    public static boolean heal(Path root) throws IOException {
        Path target = root.resolve(FILE_NAME);
        if (Files.isRegularFile(target) && isConsoleFree(Files.readString(target, StandardCharsets.UTF_8))) {
            return false;
        }
        Optional<Path> staged = stagedLauncher(root);
        if (staged.isEmpty()) {
            return false;
        }
        String packaged = Files.readString(staged.get(), StandardCharsets.UTF_8);
        if (!isConsoleFree(packaged)) {
            return false;
        }
        Files.createDirectories(root);
        Files.write(target, packaged.getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private static boolean isConsoleFree(String content) {
        return content != null && content.toLowerCase(Locale.ROOT).contains(JAVAW);
    }

    private static Optional<Path> stagedLauncher(Path root) throws IOException {
        UpdateLayout layout = new UpdateLayout(root);
        Optional<String> current = layout.currentVersion();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        Path candidate = layout.versionDir(current.get()).resolve(FILE_NAME);
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }
}
