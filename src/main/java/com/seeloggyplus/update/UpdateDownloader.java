package com.seeloggyplus.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Downloads an update asset with streaming, resume, cancellation and integrity checks.
 * The stream opener is injectable so tests run offline.
 */
public class UpdateDownloader {

    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    @FunctionalInterface
    public interface StreamOpener {
        InputStream open(String url, long offset) throws IOException;
    }

    private static final int BUFFER_SIZE = 256 * 1024;

    private final StreamOpener opener;

    public UpdateDownloader() {
        this(UpdateDownloader::httpOpen);
    }

    public UpdateDownloader(StreamOpener opener) {
        this.opener = opener;
    }

    /**
     * Downloads {@code asset} into {@code targetDir}, resuming from an existing
     * {@code .partial} file when present.
     *
     * @throws CancellationException when {@code cancelled} becomes true (partial removed)
     * @throws IOException           on I/O error, size mismatch or checksum mismatch
     */
    public Path download(UpdateAsset asset, Path targetDir, ProgressListener progress, BooleanSupplier cancelled)
            throws IOException {
        if (asset == null || asset.url() == null || asset.url().isBlank()) {
            throw new IOException("Missing asset URL");
        }
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(fileName(asset.url()));
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        long offset = Files.exists(partial) ? Files.size(partial) : 0;
        long total = asset.size() > 0 ? asset.size() : -1;

        try (InputStream input = opener.open(asset.url(), offset);
             RandomAccessFile output = new RandomAccessFile(partial.toFile(), "rw")) {
            output.seek(offset);
            byte[] buffer = new byte[BUFFER_SIZE];
            long downloaded = offset;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new CancellationException("Download cancelled");
                }
                output.write(buffer, 0, read);
                downloaded += read;
                if (progress != null) {
                    progress.onProgress(downloaded, total);
                }
            }
            if (total > 0 && downloaded != total) {
                throw new IOException("Incomplete download (" + downloaded + "/" + total + " bytes)");
            }
        } catch (CancellationException | IOException e) {
            deleteQuietly(partial);
            throw e;
        }

        verifyChecksum(asset, partial);
        moveIntoPlace(partial, target);
        return target;
    }

    private void verifyChecksum(UpdateAsset asset, Path partial) throws IOException {
        if (asset.sha256() == null || asset.sha256().isBlank()) {
            return;
        }
        String actual = Hashing.sha256(partial);
        if (!actual.equalsIgnoreCase(asset.sha256())) {
            deleteQuietly(partial);
            throw new IOException("Checksum mismatch for " + partial.getFileName());
        }
    }

    private static void moveIntoPlace(Path partial, Path target) throws IOException {
        try {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String fileName(String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isBlank() ? "update.zip" : name;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static InputStream httpOpen(String url, long offset) throws IOException {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(30))
                    .GET();
            if (offset > 0) {
                builder.header("Range", "bytes=" + offset + "-");
            }
            HttpResponse<InputStream> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            int code = response.statusCode();
            if (code != 200 && code != 206) {
                response.body().close();
                throw new IOException("HTTP " + code);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Download failed: " + e.getMessage(), e);
        }
    }
}
