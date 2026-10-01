package com.seeloggyplus.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Repairs the {@code launcher.bat} in the installation root on startup.
 * <p>
 * Self-updates stage the launcher inside {@code versions/&lt;v&gt;/} but never touch
 * the root script (older installers pinned the update package to a content
 * whitelist). Installations created before 0.6.4 therefore keep a launcher that
 * runs {@code java.exe} in the foreground, which leaves a console window open for
 * the whole session. The app heals that file from the launcher staged with the
 * active version; a launcher that already starts the app with {@code javaw} is
 * left untouched so user customisations survive.
 */
public final class RootLauncherInstaller {

    public static final String FILE_NAME = "launcher.bat";

    /** Marker of a console-free Windows launcher. */
    private static final String JAVAW = "javaw";

    private RootLauncherInstaller() {
    }

    /**
     * Rewrites the root launcher when it is missing or still starts the app with a
     * visible console, copying the launcher staged with the active version.
     *
     * @return {@code true} when the root launcher was written
     */
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
            // The active version predates the javaw launcher; do not make things worse.
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
