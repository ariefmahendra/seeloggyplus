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
        boolean legacyParser = false;
        if (Files.isRegularFile(target)) {
            String existing = Files.readString(target, StandardCharsets.UTF_8);
            legacyParser = existing.contains("set VALUE=!VALUE: =!");
            if (isConsoleFree(existing) && !legacyParser) return false;
        }
        Optional<Path> staged = stagedLauncher(root);
        if (staged.isEmpty()) {
            return false;
        }
        String packaged = Files.readString(staged.get(), StandardCharsets.UTF_8);
        if (!isConsoleFree(packaged)) {
            return false;
        }
        if (legacyParser && !packaged.contains("if defined VALUE")) return false;
        Files.createDirectories(root);
        Files.write(target, packaged.getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private static boolean isConsoleFree(String content) {
        return content != null && content.toLowerCase(Locale.ROOT).contains(JAVAW);
    }

    private static Optional<Path> stagedLauncher(Path root) throws IOException {
        return stagedLauncher(root, FILE_NAME);
    }

    public static boolean healUnix(Path root) throws IOException {
        Path target = root.resolve("launcher.sh");
        if (Files.isRegularFile(target)) {
            String existing = Files.readString(target, StandardCharsets.UTF_8);
            if (existing.contains("cd \"$SCRIPT_DIR\"")) return false;
            if (!existing.contains("SCRIPT_DIR=") || !existing.contains("APP_JAR=")) return false;
        }
        Optional<Path> staged = stagedLauncher(root, "launcher.sh");
        if (staged.isEmpty()) return false;
        String packaged = Files.readString(staged.get(), StandardCharsets.UTF_8);
        if (!packaged.contains("cd \"$SCRIPT_DIR\"")) return false;
        Files.createDirectories(root);
        Files.writeString(target, packaged, StandardCharsets.UTF_8);
        if (Files.getFileAttributeView(target, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            var permissions = Files.getPosixFilePermissions(target);
            permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(target, permissions);
        }
        return true;
    }

    private static Optional<Path> stagedLauncher(Path root, String name) throws IOException {
        UpdateLayout layout = new UpdateLayout(root);
        Optional<String> current = layout.currentVersion();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        Path candidate = layout.versionDir(current.get()).resolve(name);
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }
}
