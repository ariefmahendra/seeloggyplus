package com.seeloggyplus.controller;

import com.seeloggyplus.update.UpdateAsset;
import com.seeloggyplus.update.UpdateCheckResult;
import com.seeloggyplus.update.UpdateCoordinator;
import com.seeloggyplus.update.UpdateLayout;
import com.seeloggyplus.update.UpdateService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Shows the result of an update check and lets the user open the download or
 * release notes. Actual in-app download/install lands in a later phase.
 */
public class UpdateDialogController {

    private static final Logger logger = LoggerFactory.getLogger(UpdateDialogController.class);

    @FXML
    private Label titleLabel;
    @FXML
    private Label versionLabel;
    @FXML
    private TextArea notesArea;
    @FXML
    private Label statusLabel;
    @FXML
    private Button closeButton;
    @FXML
    private Button skipButton;
    @FXML
    private Button laterButton;
    @FXML
    private Button releaseNotesButton;
    @FXML
    private Button downloadButton;
    @FXML
    private ProgressBar progressBar;

    private UpdateCheckResult result;
    private boolean skipped;
    private Consumer<String> browser = UpdateDialogController::openInBrowser;
    private UpdateCoordinator coordinator;
    private Runnable restartAction = this::defaultRelaunch;
    private volatile boolean installing;
    private volatile boolean installed;
    private volatile String installedVersion;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    public void setResult(UpdateCheckResult result) {
        this.result = result;
        applyResult();
    }

    /** Injected for tests to avoid opening a real browser. */
    void setBrowser(Consumer<String> browser) {
        this.browser = browser != null ? browser : UpdateDialogController::openInBrowser;
    }

    /** Injected for tests to use temp directories and an offline downloader. */
    void setCoordinator(UpdateCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /** Injected for tests to observe the restart request. */
    void setRestartAction(Runnable restartAction) {
        this.restartAction = restartAction != null ? restartAction : this::defaultRelaunch;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public boolean isInstalled() {
        return installed;
    }

    public String getInstalledVersion() {
        return installedVersion;
    }

    private void applyResult() {
        if (result == null) {
            return;
        }
        installing = false;
        installed = false;
        installedVersion = null;
        if (downloadButton != null) {
            downloadButton.setText("Download");
        }
        setVisible(progressBar, false);
        boolean actionable = result.manifest() != null && result.hasUpdate();
        switch (result.status()) {
            case UPDATE_AVAILABLE, FORCED -> titleLabel.setText(
                    result.isForced() ? "Update Required" : "Update Available");
            case UP_TO_DATE -> titleLabel.setText("You're up to date");
            case ERROR -> titleLabel.setText("Update Check Failed");
        }

        if (actionable) {
            versionLabel.setText(result.currentVersion() + "  \u2192  " + result.manifest().latest());
            notesArea.setText(describeAssets(result));
            boolean hasNotes = result.manifest().releaseNotesUrl() != null
                    && !result.manifest().releaseNotesUrl().isBlank();
            releaseNotesButton.setDisable(!hasNotes);
            UpdateAsset asset = result.manifest().assetFor(UpdateService.DEFAULT_ASSET_KEY);
            downloadButton.setDisable(asset == null);
            setVisible(skipButton, !result.isForced());
            setVisible(laterButton, !result.isForced());
        } else {
            versionLabel.setText("Current version: " + result.currentVersion());
            notesArea.clear();
            setVisible(releaseNotesButton, false);
            setVisible(downloadButton, false);
            setVisible(skipButton, false);
            setVisible(laterButton, false);
        }
        statusLabel.setText(result.message() == null ? "" : result.message());
    }

    private String describeAssets(UpdateCheckResult checkResult) {
        StringBuilder builder = new StringBuilder();
        checkResult.manifest().assets().forEach((key, asset) -> {
            builder.append(key).append('\n');
            builder.append("  url    : ").append(asset.url()).append('\n');
            if (asset.size() > 0) {
                builder.append("  size   : ").append(asset.size()).append(" bytes\n");
            }
            if (asset.sha256() != null && !asset.sha256().isBlank()) {
                builder.append("  sha256 : ").append(asset.sha256()).append('\n');
            }
            builder.append('\n');
        });
        return builder.toString().stripTrailing();
    }

    private void setVisible(javafx.scene.Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    @FXML
    private void handleDownload() {
        if (installed) {
            restartAction.run();
            return;
        }
        if (installing) {
            cancelRequested.set(true);
            statusLabel.setText("Cancelling...");
            return;
        }
        if (result == null || result.manifest() == null) {
            return;
        }
        UpdateAsset asset = result.manifest().assetFor(UpdateService.DEFAULT_ASSET_KEY);
        if (asset != null) {
            startInstall(asset, result.manifest().latest());
        }
    }

    private void startInstall(UpdateAsset asset, String version) {
        installing = true;
        installed = false;
        cancelRequested.set(false);
        downloadButton.setText("Cancel");
        skipButton.setDisable(true);
        laterButton.setDisable(true);
        releaseNotesButton.setDisable(true);
        closeButton.setDisable(true);
        progressBar.setProgress(0);
        setVisible(progressBar, true);
        statusLabel.setText("Downloading... 0%");

        UpdateCoordinator activeCoordinator = activeCoordinator();
        Thread.ofVirtual().start(() -> {
            UpdateCoordinator.InstallResult installResult = activeCoordinator.install(asset, version,
                    (stage, done, total) -> Platform.runLater(() -> {
                        if ("Downloading".equals(stage) && total > 0) {
                            double progress = Math.min(1.0, (double) done / total);
                            progressBar.setProgress(progress);
                            statusLabel.setText(String.format("Downloading... %.0f%%", progress * 100));
                        } else {
                            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                            statusLabel.setText(stage + "...");
                        }
                    }),
                    cancelRequested::get);
            Platform.runLater(() -> onInstallFinished(installResult));
        });
    }

    private void onInstallFinished(UpdateCoordinator.InstallResult installResult) {
        installing = false;
        setVisible(progressBar, false);
        skipButton.setDisable(false);
        laterButton.setDisable(false);
        releaseNotesButton.setDisable(false);
        closeButton.setDisable(false);

        if (installResult.success()) {
            installed = true;
            installedVersion = installResult.version();
            statusLabel.setText("Version " + installResult.version() + " installed. Restart to apply.");
            downloadButton.setText("Restart Now");
            setVisible(skipButton, false);
            setVisible(laterButton, false);
        } else if (installResult.cancelled()) {
            statusLabel.setText("Update cancelled.");
            downloadButton.setText("Download");
        } else {
            statusLabel.setText("Update failed: " + installResult.message());
            downloadButton.setText("Download");
        }
    }

    private UpdateCoordinator activeCoordinator() {
        if (coordinator == null) {
            Path staging = Path.of(System.getProperty("java.io.tmpdir"), "seeloggyplus-update");
            coordinator = new UpdateCoordinator(UpdateLayout.installationRoot(), staging);
        }
        return coordinator;
    }

    private void defaultRelaunch() {
        try {
            Path root = UpdateLayout.installationRoot();
            String script = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "launcher.bat" : "launcher.sh";
            Path launcher = root.resolve(script);
            if (Files.exists(launcher)) {
                new ProcessBuilder(launcher.toString()).directory(root.toFile()).start();
            } else {
                logger.warn("Launcher script not found at {}; restart manually", launcher);
            }
        } catch (Exception e) {
            logger.warn("Failed to relaunch application: {}", e.getMessage());
        }
        Platform.exit();
    }

    @FXML
    private void handleReleaseNotes() {
        if (result != null && result.manifest() != null) {
            browser.accept(result.manifest().releaseNotesUrl());
        }
    }

    @FXML
    private void handleSkip() {
        skipped = true;
        closeDialog();
    }

    @FXML
    private void handleLater() {
        closeDialog();
    }

    @FXML
    private void handleClose() {
        closeDialog();
    }

    private void closeDialog() {
        if (closeButton != null && closeButton.getScene() != null
                && closeButton.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }

    private static void openInBrowser(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            logger.warn("Failed to open URL {}: {}", url, e.getMessage());
        }
    }
}
