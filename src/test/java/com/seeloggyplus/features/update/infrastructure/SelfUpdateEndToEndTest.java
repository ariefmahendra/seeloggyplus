package com.seeloggyplus.features.update.infrastructure;


import com.seeloggyplus.features.update.domain.UpdateAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end self-update coverage: a realistic portable package is downloaded
 * (stream injected), staged, activated, health-checked and rolled back using the
 * real coordinator/installer/bootstrapper. It also pins the package layout to the
 * whitelist of older installers so future file additions cannot break updates.
 */
class SelfUpdateEndToEndTest {

    private static final String OLD_VERSION = "0.5.1";
    private static final String NEW_VERSION = "0.6.3";

    /** Entries produced by the portable package tasks (wrapper folder included). */
    private static final List<String> PACKAGE_ENTRIES = List.of(
            "seeloggyplus/seeloggyplus.jar",
            "seeloggyplus/launcher.bat",
            "seeloggyplus/launcher.sh",
            "seeloggyplus/launcher.properties",
            "seeloggyplus/LAUNCHER_README.md",
            "seeloggyplus/version.properties",
            "seeloggyplus/help/index.html");

    /** Content whitelist of the 0.5.1 installer; packages must still pass it. */
    private static final Set<String> LEGACY_ALLOWED_FILES = Set.of(
            "seeloggyplus.jar",
            "launcher.bat",
            "launcher.sh",
            "launcher.properties",
            "launcher_readme.md",
            "version.properties");

    @TempDir
    Path tempDir;

    private static boolean legacyAllows(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        return LEGACY_ALLOWED_FILES.contains(name)
                || lower.contains("help/") || lower.contains("jre/") || lower.contains("lib/");
    }

    private Path buildPackage(byte[] jarBytes) throws IOException {
        Path zip = tempDir.resolve("seeloggyplus-Portable-win.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String entry : PACKAGE_ENTRIES) {
                out.putNextEntry(new ZipEntry(entry));
                out.write(entry.endsWith("seeloggyplus.jar") ? jarBytes : ("content of " + entry).getBytes());
                out.closeEntry();
            }
        }
        return zip;
    }

    private static List<String> zipEntries(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }

    private Path createExistingInstall() throws IOException {
        Path root = tempDir.resolve("install");
        Files.createDirectories(root.resolve("versions").resolve(OLD_VERSION));
        Files.write(root.resolve("versions").resolve(OLD_VERSION).resolve("seeloggyplus.jar"),
                "old-jar".getBytes());
        Files.writeString(root.resolve("current"), OLD_VERSION);
        return root;
    }

    @Test
    @DisplayName("download -> stage -> activate -> health -> rollback works end to end")
    void fullSelfUpdateRoundTrip() throws Exception {
        Path root = createExistingInstall();
        Path packageZip = buildPackage("new-jar".getBytes());
        byte[] zipBytes = Files.readAllBytes(packageZip);
        UpdateAsset asset = new UpdateAsset("https://example.test/seeloggyplus-Portable-win.zip",
                zipBytes.length, Hashing.sha256(packageZip));

        UpdateDownloader downloader = new UpdateDownloader(
                (url, offset) -> new UpdateDownloader.Opened(new ByteArrayInputStream(zipBytes), 0));
        List<String> stages = new ArrayList<>();
        UpdateCoordinator coordinator = new UpdateCoordinator(new UpdateLayout(root),
                tempDir.resolve("staging"), downloader, new UpdateInstaller());

        UpdateCoordinator.InstallResult result = coordinator.install(asset, NEW_VERSION,
                (stage, done, total) -> stages.add(stage), () -> false);

        assertTrue(result.success(), "update must succeed: " + result.message());
        assertEquals(List.of("Downloading", "Installing", "Activating"), stages);

        UpdateLayout layout = new UpdateLayout(root);
        assertEquals(NEW_VERSION, layout.currentVersion().orElseThrow());
        assertEquals(OLD_VERSION, layout.previousVersion().orElseThrow());
        assertTrue(Files.isRegularFile(layout.jarFor(NEW_VERSION)));
        assertEquals("new-jar", Files.readString(layout.jarFor(NEW_VERSION)));
        assertTrue(Files.isRegularFile(layout.versionDir(NEW_VERSION).resolve("launcher.bat")),
                "launcher files must be staged with the new version");

        try (var files = Files.list(tempDir.resolve("staging"))) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".zip")),
                    "the downloaded package must be cleaned up after staging");
        }

        // The silent launcher is not part of the package; the new app heals it.
        assertFalse(zipEntries(packageZip).contains("seeloggyplus/SeeLoggyPlus.vbs"));
        assertTrue(Files.isRegularFile(SilentLauncherInstaller.ensure(root)));

        UpdateBootstrapper bootstrapper = new UpdateBootstrapper(layout);
        assertTrue(bootstrapper.shouldRollback(), "a freshly activated version is unhealthy until it starts");
        assertTrue(bootstrapper.confirmStartup(NEW_VERSION).isEmpty(),
                "the newly running version must be confirmed instead of rolled back");
        assertEquals(NEW_VERSION, layout.currentVersion().orElseThrow(),
                "confirming startup must keep the update active for future launcher runs");
        assertFalse(bootstrapper.shouldRollback(), "a healthy version must not roll back");
        assertEquals(OLD_VERSION, bootstrapper.rollback().orElseThrow());
        assertEquals(OLD_VERSION, layout.currentVersion().orElseThrow(),
                "rollback must point current back at the previous version");
    }

    @Test
    @DisplayName("a failed download leaves the current installation untouched")
    void failedDownloadDoesNotTouchTheInstallation() throws Exception {
        Path root = createExistingInstall();
        UpdateDownloader failing = new UpdateDownloader((url, offset) -> {
            throw new IOException("network down");
        });
        UpdateCoordinator coordinator = new UpdateCoordinator(new UpdateLayout(root),
                tempDir.resolve("staging"), failing, new UpdateInstaller());

        UpdateCoordinator.InstallResult result = coordinator.install(
                new UpdateAsset("https://example.test/pkg.zip", 0, null), NEW_VERSION, null, () -> false);

        assertFalse(result.success());
        assertTrue(result.message().contains("network down"));
        UpdateLayout layout = new UpdateLayout(root);
        assertEquals(OLD_VERSION, layout.currentVersion().orElseThrow());
        assertFalse(Files.exists(layout.versionDir(NEW_VERSION)), "nothing may be staged on failure");
    }

    @Test
    @DisplayName("portable packages stay installable by older (0.5.x) whitelists")
    void packageRemainsCompatibleWithLegacyWhitelist() throws Exception {
        Path zip = buildPackage("x".getBytes());

        for (String entry : zipEntries(zip)) {
            assertFalse(entry.toLowerCase(Locale.ROOT).endsWith(".vbs"),
                    "the silent launcher must not be packaged (older installers reject it): " + entry);
            String relative = entry.replaceFirst("^seeloggyplus/", "");
            assertTrue(legacyAllows(relative),
                    "the 0.5.1 installer would reject this entry: " + entry);
        }
    }
}
