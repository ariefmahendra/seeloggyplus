package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateDownloaderTest {

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

    private static UpdateDownloader openerFor(byte[] content) {
        return new UpdateDownloader((url, offset) -> new UpdateDownloader.Opened(
                new ByteArrayInputStream(content, (int) offset, content.length - (int) offset), offset));
    }

    @Test
    void downloadsAndVerifiesContent() throws Exception {
        byte[] content = "hello update".getBytes();
        UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", content.length,
                Hashing.sha256(content));
        Path dir = Files.createTempDirectory("seeloggy-dl");
        try {
            Path out = openerFor(content).download(asset, dir, null, () -> false);
            assertArrayEquals(content, Files.readAllBytes(out));
            assertFalse(Files.exists(dir.resolve("app.zip.partial")), "Partial must be removed on success");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void reportsProgress() throws Exception {
        byte[] content = new byte[600 * 1024];
        UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", content.length, null);
        Path dir = Files.createTempDirectory("seeloggy-dl");
        AtomicLong lastDownloaded = new AtomicLong();
        try {
            openerFor(content).download(asset, dir, (done, total) -> lastDownloaded.set(done), () -> false);
            assertEquals(content.length, lastDownloaded.get());
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void resumesFromExistingPartialFile() throws Exception {
        byte[] content = "resume-me-please".getBytes();
        UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", content.length, null);
        Path dir = Files.createTempDirectory("seeloggy-dl");
        Path partial = dir.resolve("app.zip.partial");
        Files.write(partial, Arrays.copyOf(content, 7));
        AtomicLong requestedOffset = new AtomicLong(-1);
        try {
            UpdateDownloader downloader = new UpdateDownloader((url, offset) -> {
                requestedOffset.set(offset);
                return new UpdateDownloader.Opened(
                        new ByteArrayInputStream(content, (int) offset, content.length - (int) offset), offset);
            });
            Path out = downloader.download(asset, dir, null, () -> false);
            assertEquals(7, requestedOffset.get(), "Opener must be asked for the remaining bytes");
            assertArrayEquals(content, Files.readAllBytes(out));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void restartsCleanlyWhenServerIgnoresRange() throws Exception {
        byte[] content = "fresh-full-download".getBytes();
        UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", content.length, Hashing.sha256(content));
        Path dir = Files.createTempDirectory("seeloggy-dl");
        Path partial = dir.resolve("app.zip.partial");
        Files.write(partial, "leftover-error-page".getBytes()); // stale 31-byte partial
        try {
            UpdateDownloader downloader = new UpdateDownloader((url, offset) ->
                    // Server ignored the Range header: body starts at 0.
                    new UpdateDownloader.Opened(new ByteArrayInputStream(content), 0));
            Path out = downloader.download(asset, dir, null, () -> false);
            assertArrayEquals(content, Files.readAllBytes(out),
                    "Stale partial must be truncated, not appended, when Range is ignored");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void rejectsChecksumMismatchAndRemovesPartial() throws Exception {
        byte[] content = "tampered".getBytes();
        UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", content.length, "deadbeef");
        Path dir = Files.createTempDirectory("seeloggy-dl");
        try {
            assertThrows(IOException.class, () -> openerFor(content).download(asset, dir, null, () -> false));
            assertFalse(Files.exists(dir.resolve("app.zip.partial")));
            assertFalse(Files.exists(dir.resolve("app.zip")));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void rejectsIncompleteDownload() throws Exception {
        byte[] content = "short".getBytes();
        UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", content.length + 100, null);
        Path dir = Files.createTempDirectory("seeloggy-dl");
        try {
            assertThrows(IOException.class, () -> openerFor(content).download(asset, dir, null, () -> false));
            assertFalse(Files.exists(dir.resolve("app.zip.partial")));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void cancellationRemovesPartialFile() throws Exception {
        byte[] content = new byte[1024 * 1024];
        UpdateAsset asset = new UpdateAsset("https://example.com/app.zip", content.length, null);
        Path dir = Files.createTempDirectory("seeloggy-dl");
        try {
            assertThrows(CancellationException.class,
                    () -> openerFor(content).download(asset, dir, null, () -> true));
            assertFalse(Files.exists(dir.resolve("app.zip.partial")));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void derivesFileNameFromUrl() {
        assertEquals("app.zip", UpdateDownloader.fileName("https://example.com/dir/app.zip?token=1"));
        assertEquals("update.zip", UpdateDownloader.fileName("https://example.com/"));
    }
}
