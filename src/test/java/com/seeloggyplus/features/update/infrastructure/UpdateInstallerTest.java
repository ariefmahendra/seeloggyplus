package com.seeloggyplus.features.update.infrastructure;

import com.seeloggyplus.features.update.domain.UpdateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateInstallerTest {

    @Test
    void doesNotChangePermissionsOutsideInstallationThroughSymbolicLinks() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-link-permissions");
        try {
            if (Files.getFileAttributeView(work, java.nio.file.attribute.PosixFileAttributeView.class) == null) return;
            Path outside = Files.writeString(work.resolve("outside.sh"), "outside");
            var original = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(outside, original);
            Path root = Files.createDirectory(work.resolve("installation"));
            Files.createSymbolicLink(root.resolve("launcher.sh"), outside);
            assertThrows(IOException.class, () -> UpdateInstaller.ensureUnixExecutables(root));
            assertEquals(original, Files.getPosixFilePermissions(outside));
        } finally { deleteRecursively(work); }
    }

    @Test
    void rejectsLinkedRuntimeDirectoriesWithoutChangingTheirFiles() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-runtime-link");
        try {
            if (Files.getFileAttributeView(work, java.nio.file.attribute.PosixFileAttributeView.class) == null) return;
            Path runtime = Files.createDirectories(work.resolve("outside/bin"));
            Path executable = Files.writeString(runtime.resolve("java"), "outside");
            var original = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(executable, original);
            Path root = Files.createDirectory(work.resolve("installation"));
            Files.createSymbolicLink(root.resolve("jre"), runtime.getParent());
            assertThrows(IOException.class, () -> UpdateInstaller.ensureUnixExecutables(root));
            assertEquals(original, Files.getPosixFilePermissions(executable));
        } finally { deleteRecursively(work); }
    }

    @Test void restoresUnixLauncherAndRuntimeExecutePermissions() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-permissions");
        try {
            Path zip = createZip(work, new String[] {"seeloggyplus/seeloggyplus.jar", "seeloggyplus/launcher.sh",
                    "seeloggyplus/jre/bin/java", "seeloggyplus/jre/lib/jspawnhelper"}, "jar".getBytes());
            Path staged = new UpdateInstaller().stage(zip, work.resolve("versions"), "0.8.0", null).directory();
            for (String name : new String[] {"launcher.sh", "jre/bin/java", "jre/lib/jspawnhelper"}) {
                Path file = staged.resolve(name);
                assertTrue(Files.isRegularFile(file));
                if (Files.getFileAttributeView(file, java.nio.file.attribute.PosixFileAttributeView.class) != null)
                    assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"),
                            Files.getPosixFilePermissions(file), name + " must be executable without public write access");
            }
        } finally { deleteRecursively(work); }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static Path createZip(Path dir, String[] entryNames, byte[] jarContent) throws IOException {
        Path zip = dir.resolve("update-" + System.nanoTime() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : entryNames) {
                out.putNextEntry(new ZipEntry(name));
                if (name.endsWith("seeloggyplus.jar")) {
                    out.write(jarContent);
                } else {
                    out.write(("<content of " + name + ">").getBytes());
                }
                out.closeEntry();
            }
        }
        return zip;
    }

    @Test
    void stagesUpdateAndStripsWrapperFolder() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-stage");
        try {
            Path zip = createZip(work, new String[] {
                    "SeeloggyPlus/seeloggyplus.jar",
                    "SeeloggyPlus/help/index.html",
                    "SeeloggyPlus/launcher.bat",
                    "SeeloggyPlus/SeeLoggyPlus.vbs"
            }, "jar-bytes".getBytes());
            Path versions = work.resolve("versions");

            UpdateInstaller.StagedUpdate staged = new UpdateInstaller()
                    .stage(zip, versions, "0.3.0", null);

            assertEquals("0.3.0", staged.version());
            assertEquals(versions.resolve("0.3.0"), staged.directory());
            assertTrue(Files.exists(staged.jar()));
            assertTrue(Files.exists(staged.directory().resolve("SeeLoggyPlus.vbs")));
            assertTrue(Files.exists(versions.resolve("0.3.0/help/index.html")),
                    "Wrapper folder must be stripped into the version directory");
            try (Stream<Path> leftovers = Files.list(versions)) {
                assertTrue(leftovers.noneMatch(p -> p.getFileName().toString().contains(".staging-")),
                        "No staging directory may remain after a successful stage");
            }
        } finally {
            deleteRecursively(work);
        }
    }

    @Test
    void acceptsExplicitDirectoryEntriesAndWrapperFolder() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-stage");
        try {
            Path zip = work.resolve("with-dirs.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry("SeeloggyPlus/"));
                out.closeEntry();
                out.putNextEntry(new ZipEntry("SeeloggyPlus/help/"));
                out.closeEntry();
                out.putNextEntry(new ZipEntry("SeeloggyPlus/seeloggyplus.jar"));
                out.write("jar-bytes".getBytes());
                out.closeEntry();
            }
            Path versions = work.resolve("versions");

            UpdateInstaller.StagedUpdate staged = new UpdateInstaller()
                    .stage(zip, versions, "0.3.0", null);

            assertTrue(Files.exists(staged.jar()));
            assertTrue(Files.exists(versions.resolve("0.3.0/help")));
        } finally {
            deleteRecursively(work);
        }
    }

    @Test
    void doesNotSearchRecursivelyForTheJar() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-stage");
        try {
            Path zip = createZip(work, new String[] {
                    "SeeloggyPlus/nested/seeloggyplus.jar"
            }, "jar-bytes".getBytes());
            Path versions = work.resolve("versions");

            assertThrows(UpdateException.class,
                    () -> new UpdateInstaller().stage(zip, versions, "0.3.0", null));
            assertNoLeftovers(versions);
        } finally {
            deleteRecursively(work);
        }
    }

    @Test
    void verifiesStagedJarChecksum() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-stage");
        try {
            byte[] jarContent = "jar-bytes".getBytes();
            String sha = Hashing.sha256(jarContent);
            Path zip = createZip(work, new String[] {"SeeloggyPlus/seeloggyplus.jar"}, jarContent);
            Path versions = work.resolve("versions");

            UpdateInstaller installer = new UpdateInstaller();
            assertNotNull(installer.stage(zip, versions.resolve("ok"), "0.3.0", sha));
            assertThrows(UpdateException.class,
                    () -> installer.stage(zip, versions.resolve("bad"), "0.4.0", "deadbeef"));
        } finally {
            deleteRecursively(work);
        }
    }

    @Test
    void rejectsDisallowedEntry() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-stage");
        try {
            Path zip = createZip(work, new String[] {
                    "SeeloggyPlus/seeloggyplus.jar",
                    "SeeloggyPlus/evil.exe"
            }, "jar".getBytes());
            Path versions = work.resolve("versions");

            assertThrows(UpdateException.class,
                    () -> new UpdateInstaller().stage(zip, versions, "0.3.0", null));
            assertNoLeftovers(versions);
        } finally {
            deleteRecursively(work);
        }
    }

    @Test
    void rejectsZipSlipEntry() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-stage");
        try {
            Path zip = createZip(work, new String[] {
                    "SeeloggyPlus/seeloggyplus.jar",
                    "SeeloggyPlus/jre/../../../../evil.txt"
            }, "jar".getBytes());
            Path versions = work.resolve("versions");

            assertThrows(UpdateException.class,
                    () -> new UpdateInstaller().stage(zip, versions, "0.3.0", null));
            assertNoLeftovers(versions);
        } finally {
            deleteRecursively(work);
        }
    }

    @Test
    void rejectsWhitelistBypassViaParentSegments() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-stage");
        try {
            Path zip = createZip(work, new String[] {
                    "SeeloggyPlus/seeloggyplus.jar",
                    "SeeloggyPlus/jre/../../evil.txt"
            }, "jar".getBytes());
            Path versions = work.resolve("versions");

            assertThrows(UpdateException.class,
                    () -> new UpdateInstaller().stage(zip, versions, "0.3.0", null));
            assertNoLeftovers(versions);
        } finally {
            deleteRecursively(work);
        }
    }

    @Test
    void rejectsMissingJarAndAlreadyStagedVersion() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-stage");
        try {
            Path zipNoJar = createZip(work, new String[] {"SeeloggyPlus/help/index.html"}, "x".getBytes());
            Path versions = work.resolve("versions");
            UpdateInstaller installer = new UpdateInstaller();

            assertThrows(UpdateException.class, () -> installer.stage(zipNoJar, versions, "0.3.0", null));
            assertNoLeftovers(versions);

            Files.createDirectories(versions.resolve("0.5.0"));
            Path zip = createZip(work, new String[] {"SeeloggyPlus/seeloggyplus.jar"}, "jar".getBytes());
            assertThrows(UpdateException.class, () -> installer.stage(zip, versions, "0.5.0", null));
        } finally {
            deleteRecursively(work);
        }
    }

    private static void assertNoLeftovers(Path versionsRoot) throws IOException {
        if (!Files.exists(versionsRoot)) {
            return;
        }
        try (Stream<Path> entries = Files.list(versionsRoot)) {
            assertTrue(entries.findAny().isEmpty(), "No staging leftovers must remain after a failure");
        }
    }
}
