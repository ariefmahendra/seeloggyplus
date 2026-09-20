package com.seeloggyplus.update;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Blue/green installation layout:
 * <pre>
 * root/
 *   current            -> active version (text)
 *   current.previous   -> version to roll back to (text)
 *   versions/&lt;v&gt;/seeloggyplus.jar
 *   versions/&lt;v&gt;/.ok   -> health marker written after a successful start
 * </pre>
 * The running jar is never overwritten; a new version lives in its own directory.
 */
public final class UpdateLayout {

    public static final String CURRENT_FILE = "current";
    public static final String PREVIOUS_FILE = "current.previous";
    public static final String VERSIONS_DIR = "versions";
    public static final String HEALTH_MARKER = ".ok";
    public static final String APP_JAR_NAME = "seeloggyplus.jar";

    private final Path root;

    public UpdateLayout(Path root) {
        this.root = root;
    }

    /** Directory that contains the running application (jar or classes folder). */
    public static Path installationRoot() {
        try {
            java.net.URI uri = UpdateLayout.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path location = java.nio.file.Paths.get(uri);
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (Exception e) {
            return java.nio.file.Paths.get(System.getProperty("user.dir"));
        }
    }

    public Path root() {
        return root;
    }

    public Path versionsRoot() {
        return root.resolve(VERSIONS_DIR);
    }

    public Path versionDir(String version) {
        return versionsRoot().resolve(version);
    }

    public Path jarFor(String version) {
        return versionDir(version).resolve(APP_JAR_NAME);
    }

    public Path healthMarker(String version) {
        return versionDir(version).resolve(HEALTH_MARKER);
    }

    public Optional<String> currentVersion() throws IOException {
        return readValue(root.resolve(CURRENT_FILE));
    }

    public Optional<String> previousVersion() throws IOException {
        return readValue(root.resolve(PREVIOUS_FILE));
    }

    public void writeCurrent(String version) throws IOException {
        writeAtomic(root.resolve(CURRENT_FILE), version);
    }

    public void writePrevious(String version) throws IOException {
        writeAtomic(root.resolve(PREVIOUS_FILE), version);
    }

    public boolean isStaged(String version) {
        return version != null && Files.isRegularFile(jarFor(version));
    }

    public void markHealthy(String version) throws IOException {
        Path marker = healthMarker(version);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "ok");
    }

    public boolean isHealthy(String version) {
        return version != null && Files.exists(healthMarker(version));
    }

    public List<String> listVersions() throws IOException {
        if (!Files.isDirectory(versionsRoot())) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(versionsRoot())) {
            List<String> versions = new ArrayList<>();
            entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.contains(".staging-"))
                    .forEach(versions::add);
            versions.sort(Comparator.naturalOrder());
            return versions;
        }
    }

    /**
     * Deletes old version directories, always keeping the current version, the
     * previous version and the {@code keep} newest ones.
     */
    public int cleanup(int keep) throws IOException {
        String current = currentVersion().orElse(null);
        String previous = previousVersion().orElse(null);
        List<String> versions = listVersions();
        int removed = 0;
        List<String> newest = new ArrayList<>(versions);
        newest.sort(Comparator.reverseOrder());
        List<String> protectedVersions = new ArrayList<>();
        if (current != null) {
            protectedVersions.add(current);
        }
        if (previous != null) {
            protectedVersions.add(previous);
        }
        for (int i = 0; i < Math.min(keep, newest.size()); i++) {
            protectedVersions.add(newest.get(i));
        }
        for (String version : versions) {
            if (protectedVersions.contains(version)) {
                continue;
            }
            deleteRecursively(versionDir(version));
            removed++;
        }
        return removed;
    }

    private Optional<String> readValue(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String value = Files.readString(file).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static void writeAtomic(Path file, String value) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".new");
        Files.writeString(temp, value);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        }
    }
}
