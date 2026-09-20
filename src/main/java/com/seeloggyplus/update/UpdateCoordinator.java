package com.seeloggyplus.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Orchestrates a full update: download the asset, stage the new version and activate it.
 * UI-free so it can be tested with injected downloaders and temp directories.
 */
public class UpdateCoordinator {

    public interface ProgressListener {
        void onProgress(String stage, long done, long total);
    }

    public record InstallResult(boolean success, boolean cancelled, String version, String message) {
    }

    private final UpdateLayout layout;
    private final Path stagingDir;
    private final UpdateDownloader downloader;
    private final UpdateInstaller installer;
    private final UpdateBootstrapper bootstrapper;

    public UpdateCoordinator(Path installRoot, Path stagingDir) {
        this(new UpdateLayout(installRoot), stagingDir, new UpdateDownloader(), new UpdateInstaller());
    }

    public UpdateCoordinator(UpdateLayout layout, Path stagingDir, UpdateDownloader downloader,
            UpdateInstaller installer) {
        this.layout = layout;
        this.stagingDir = stagingDir;
        this.downloader = downloader;
        this.installer = installer;
        this.bootstrapper = new UpdateBootstrapper(layout);
    }

    public InstallResult install(UpdateAsset asset, String version, ProgressListener progress,
            BooleanSupplier cancelled) {
        if (version == null || version.isBlank()) {
            return new InstallResult(false, false, version, "Missing version");
        }
        if (cancelled != null && cancelled.getAsBoolean()) {
            return new InstallResult(false, true, version, "Cancelled");
        }
        Path zip = null;
        try {
            zip = downloader.download(asset, stagingDir, (done, total) -> {
                if (progress != null) {
                    progress.onProgress("Downloading", done, total);
                }
            }, cancelled);

            if (progress != null) {
                progress.onProgress("Installing", 0, 0);
            }
            installer.stage(zip, layout.versionsRoot(), version, null);

            if (progress != null) {
                progress.onProgress("Activating", 1, 1);
            }
            bootstrapper.activate(version);
            return new InstallResult(true, false, version, "Update installed");
        } catch (CancellationException e) {
            return new InstallResult(false, true, version, "Cancelled");
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Update failed" : e.getMessage();
            return new InstallResult(false, false, version, message);
        } finally {
            if (zip != null) {
                try {
                    Files.deleteIfExists(zip);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }
}
