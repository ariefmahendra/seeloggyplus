package com.seeloggyplus.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts a downloaded update zip into a new versioned staging directory.
 * Applies zip-slip protection, a content whitelist and a size cap, then verifies
 * the staged jar checksum. Does not activate the version (that is the bootstrapper's job).
 */
public class UpdateInstaller {

    public record StagedUpdate(Path directory, Path jar, String version) {
    }

    private static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024; // 2 GB
    private static final Set<String> ALLOWED_FILES = Set.of(
            "seeloggyplus.jar",
            "launcher.bat",
            "launcher.sh",
            "launcher.properties",
            "launcher_readme.md",
            "version.properties");

    public StagedUpdate stage(Path zipFile, Path versionsRoot, String version, String expectedJarSha256)
            throws UpdateException {
        if (version == null || version.isBlank()) {
            throw new UpdateException("Version is required");
        }
        Path target = versionsRoot.resolve(version);
        if (Files.exists(target)) {
            throw new UpdateException("Version already staged: " + version);
        }

        Path temp = null;
        try {
            Files.createDirectories(versionsRoot);
            temp = Files.createTempDirectory(versionsRoot, version + ".staging-");
            extractZip(zipFile, temp);

            Path contentRoot = resolveContentRoot(temp);
            if (contentRoot.equals(temp)) {
                move(contentRoot, target);
                temp = null;
            } else {
                move(contentRoot, target);
                // The wrapper folder was moved out; remove the now-empty staging directory.
                deleteQuietly(temp);
                temp = null;
            }

            // The package layout is known (the optional single wrapper folder is
            // stripped above), so the jar always ends up directly inside the version
            // directory. Do not walk the tree to look for it.
            Path jar = target.resolve(UpdateLayout.APP_JAR_NAME);
            if (!Files.isRegularFile(jar)) {
                throw new UpdateException("seeloggyplus.jar not found in update package");
            }
            if (expectedJarSha256 != null && !expectedJarSha256.isBlank()) {
                String actual = Hashing.sha256(jar);
                if (!actual.equalsIgnoreCase(expectedJarSha256)) {
                    throw new UpdateException("Staged jar checksum mismatch");
                }
            }
            return new StagedUpdate(target, jar, version);
        } catch (UpdateException e) {
            deleteQuietly(temp);
            deleteQuietly(target);
            throw e;
        } catch (IOException e) {
            deleteQuietly(temp);
            deleteQuietly(target);
            throw new UpdateException("Failed to stage update: " + e.getMessage(), e);
        }
    }

    private void extractZip(Path zipFile, Path destination) throws IOException, UpdateException {
        Path normalizedRoot = destination.toAbsolutePath().normalize();
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.isBlank() || name.startsWith("/")) {
                    continue;
                }
                String relative = name.startsWith("./") ? name.substring(2) : name;
                Path out = normalizedRoot.resolve(relative).normalize();
                if (!out.startsWith(normalizedRoot)) {
                    throw new UpdateException("Update entry escapes the target directory: " + relative);
                }
                String normalizedRelative = normalizedRoot.relativize(out).toString().replace('\\', '/');
                if (normalizedRelative.isBlank()) {
                    continue;
                }
                // Directories (including the single wrapper folder) are always allowed;
                // only files are subject to the content whitelist.
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                if (!isAllowed(normalizedRelative)) {
                    throw new UpdateException("Update package contains a disallowed entry: " + relative);
                }
                Files.createDirectories(out.getParent());
                totalBytes += copyEntry(zip, out);
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new UpdateException("Update package exceeds the maximum allowed size");
                }
            }
        }
    }

    private long copyEntry(InputStream input, Path out) throws IOException {
        long written = 0;
        byte[] buffer = new byte[64 * 1024];
        try (var output = Files.newOutputStream(out)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                written += read;
            }
        }
        return written;
    }

    static boolean isAllowed(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        if (ALLOWED_FILES.contains(name)) {
            return true;
        }
        return lower.contains("help/") || lower.contains("jre/") || lower.contains("lib/");
    }

    private static Path resolveContentRoot(Path temp) throws IOException {
        try (Stream<Path> entries = Files.list(temp)) {
            List<Path> list = entries.toList();
            if (list.size() == 1 && Files.isDirectory(list.get(0))) {
                return list.get(0);
            }
        }
        return temp;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static void deleteQuietly(Path path) {
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
        } catch (IOException ignored) {
            // best effort
        }
    }
}
