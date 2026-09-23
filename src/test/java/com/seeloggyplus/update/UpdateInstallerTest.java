package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateInstallerTest {

    private static void deleteRecursively(Path path) throws IOException {
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

    /** Creates a zip with the given entry names and simple content. */
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
                    "SeeloggyPlus/launcher.bat"
            }, "jar-bytes".getBytes());
            Path versions = work.resolve("versions");

            UpdateInstaller.StagedUpdate staged = new UpdateInstaller()
                    .stage(zip, versions, "0.3.0", null);

            assertEquals("0.3.0", staged.version());
            assertEquals(versions.resolve("0.3.0"), staged.directory());
            assertTrue(Files.exists(staged.jar()));
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
            // The jar is nested below the wrapper. The installer must rely on the
            // known package layout (jar directly inside the version directory) and
            // reject this instead of walking the tree to find it.
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
