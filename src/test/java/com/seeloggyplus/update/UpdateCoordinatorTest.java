package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCoordinatorTest {

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

    static byte[] zipWithJar(byte[] jarContent) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("SeeloggyPlus/seeloggyplus.jar"));
            zip.write(jarContent);
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }

    static UpdateDownloader offlineDownloader(byte[] zip) {
        return new UpdateDownloader((url, offset) ->
                new ByteArrayInputStream(zip, (int) offset, zip.length - (int) offset));
    }

    @Test
    void downloadsStagesAndActivatesVersion() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-root");
        Path staging = Files.createTempDirectory("seeloggy-staging");
        try {
            byte[] zip = zipWithJar("jar-bytes".getBytes());
            UpdateCoordinator coordinator = new UpdateCoordinator(new UpdateLayout(root), staging,
                    offlineDownloader(zip), new UpdateInstaller());
            UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", zip.length, Hashing.sha256(zip));

            UpdateCoordinator.InstallResult result = coordinator.install(asset, "0.3.0", null, () -> false);

            assertTrue(result.success(), result.message());
            UpdateLayout layout = new UpdateLayout(root);
            assertEquals(Optional.of("0.3.0"), layout.currentVersion());
            assertTrue(Files.exists(layout.jarFor("0.3.0")));
            try (Stream<Path> leftovers = Files.list(staging)) {
                assertTrue(leftovers.findAny().isEmpty(), "Downloaded zip must be cleaned up");
            }
        } finally {
            deleteRecursively(root);
            deleteRecursively(staging);
        }
    }

    @Test
    void cancellationLeavesNoActiveVersion() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-root");
        Path staging = Files.createTempDirectory("seeloggy-staging");
        try {
            byte[] zip = zipWithJar("jar".getBytes());
            UpdateCoordinator coordinator = new UpdateCoordinator(new UpdateLayout(root), staging,
                    offlineDownloader(zip), new UpdateInstaller());
            UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", zip.length, Hashing.sha256(zip));

            UpdateCoordinator.InstallResult result = coordinator.install(asset, "0.3.0", null, () -> true);

            assertTrue(result.cancelled());
            assertFalse(result.success());
            assertTrue(new UpdateLayout(root).currentVersion().isEmpty());
        } finally {
            deleteRecursively(root);
            deleteRecursively(staging);
        }
    }

    @Test
    void failedChecksumReportsFailureWithoutActivating() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-root");
        Path staging = Files.createTempDirectory("seeloggy-staging");
        try {
            byte[] zip = zipWithJar("jar".getBytes());
            UpdateCoordinator coordinator = new UpdateCoordinator(new UpdateLayout(root), staging,
                    offlineDownloader(zip), new UpdateInstaller());
            UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", zip.length, "deadbeef");

            UpdateCoordinator.InstallResult result = coordinator.install(asset, "0.3.0", null, () -> false);

            assertFalse(result.success());
            assertFalse(result.cancelled());
            assertTrue(new UpdateLayout(root).currentVersion().isEmpty());
        } finally {
            deleteRecursively(root);
            deleteRecursively(staging);
        }
    }
}
