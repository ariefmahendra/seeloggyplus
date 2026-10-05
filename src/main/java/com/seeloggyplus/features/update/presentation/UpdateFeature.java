package com.seeloggyplus.features.update.presentation;

import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.ui.AppIcons;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.features.update.infrastructure.UpdateRelauncher;

import com.seeloggyplus.features.update.domain.AppVersion;
import com.seeloggyplus.features.update.domain.ReleaseNotes;
import com.seeloggyplus.features.update.domain.UpdateAsset;
import com.seeloggyplus.features.update.infrastructure.UpdateAssetKeys;
import com.seeloggyplus.features.update.domain.UpdateAwareness;
import com.seeloggyplus.features.update.infrastructure.UpdateBootstrapper;
import com.seeloggyplus.features.update.domain.UpdateCheckResult;
import com.seeloggyplus.features.update.infrastructure.UpdateCoordinator;
import com.seeloggyplus.features.update.infrastructure.UpdateLayout;
import com.seeloggyplus.features.update.domain.UpdatePreferences;
import com.seeloggyplus.features.update.application.UpdateServiceImpl;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class UpdateFeature {

    private static final Logger logger = LoggerFactory.getLogger(UpdateFeature.class);

    private final PreferenceService preferenceService;

    private Label updateStatusLabel;
    private CheckMenuItem autoCheckUpdatesMenuItem;
    private CheckMenuItem autoDownloadUpdatesMenuItem;
    private Supplier<Window> ownerSupplier = () -> null;
    private BiConsumer<String, String> errorReporter = (title, message) -> { };

    private UpdateCheckResult availableUpdate;
    private Timeline updateCheckTimeline;
    private volatile String downloadingVersion;
    private volatile String readyToRestartVersion;
    private volatile boolean updateInstallRunning;
    private Runnable updateRestartAction = UpdateRelauncher::relaunch;
    private Supplier<UpdateCoordinator> updateCoordinatorFactory =
            () -> new UpdateCoordinator(UpdateLayout.installationRoot(),
                    UpdateDialogController.stagingDirectory());

    public UpdateFeature(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    public void initialize(Label updateStatusLabel, CheckMenuItem autoCheckUpdatesMenuItem,
            CheckMenuItem autoDownloadUpdatesMenuItem) {
        this.updateStatusLabel = updateStatusLabel;
        this.autoCheckUpdatesMenuItem = autoCheckUpdatesMenuItem;
        this.autoDownloadUpdatesMenuItem = autoDownloadUpdatesMenuItem;
        if (updateStatusLabel != null) {
            updateStatusLabel.setOnMouseClicked(e -> handleUpdateIndicatorClick());
        }
        setupUpdateMenuItems();
    }

    public void setOwnerSupplier(Supplier<Window> ownerSupplier) {
        this.ownerSupplier = ownerSupplier != null ? ownerSupplier : () -> null;
    }

    public void setErrorReporter(BiConsumer<String, String> errorReporter) {
        this.errorReporter = errorReporter != null ? errorReporter : (title, message) -> { };
    }

    public void handleCheckForUpdates() {
        runUpdateCheck(true, currentUpdateChannel());
    }

    public void reconcileUpdateStartup() {
        if (Boolean.getBoolean("seeloggyplus.disableUpdateCheck")) {
            return;
        }
        try {
            UpdateBootstrapper bootstrapper = new UpdateBootstrapper(
                    new UpdateLayout(UpdateLayout.installationRoot()));
            bootstrapper.confirmStartup(AppVersion.current()).ifPresent(version -> logger.warn(
                    "Update failed to start; rolled back to version {}", version));
        } catch (Exception e) {
            logger.debug("Update startup reconciliation skipped: {}", e.getMessage());
        }
    }

    public void scheduleAutoUpdateCheck() {
        if (Boolean.getBoolean("seeloggyplus.disableUpdateCheck")) {
            return;
        }
        Platform.runLater(this::maybeAutoUpdateCheck);
        updateCheckTimeline = new Timeline(
                new KeyFrame(Duration.minutes(30), e -> maybeAutoUpdateCheck()));
        updateCheckTimeline.setCycleCount(Animation.INDEFINITE);
        updateCheckTimeline.play();
    }

    public void showWhatsNewIfNeeded() {
        if (Boolean.getBoolean("seeloggyplus.disableUpdateCheck")
                || Boolean.getBoolean("seeloggyplus.dev")) {
            return;
        }
        try {
            String current = AppVersion.current();
            String seen = preferenceService.getPreferencesByCode(UpdatePreferences.SEEN_VERSION).orElse(null);
            if (!ReleaseNotes.shouldShow(current, seen)) {
                return;
            }
            preferenceService.saveOrUpdatePreferences(new Preference(UpdatePreferences.SEEN_VERSION, current));
            String text = ReleaseNotes.load().map(ReleaseNotes::toPlainText).orElse("");
            Platform.runLater(() -> WhatsNewDialog.show(current, text));
        } catch (Exception e) {
            logger.debug("What's New dialog skipped: {}", e.getMessage());
        }
    }

    public void showUpdateIndicator(UpdateCheckResult result) {
        if (result == null || result.manifest() == null) {
            return;
        }
        availableUpdate = result;
        setUpdateIndicator(UpdateAwareness.IndicatorState.AVAILABLE, result.manifest().latest(), 0);
    }

    public void setUpdateIndicator(UpdateAwareness.IndicatorState state, String version, int percent) {
        if (updateStatusLabel == null) {
            return;
        }
        String text = UpdateAwareness.indicatorText(state, version, percent);
        boolean visible = state != null
                && state != UpdateAwareness.IndicatorState.NONE
                && !text.isBlank();
        updateStatusLabel.setText(text);
        updateStatusLabel.setVisible(visible);
        updateStatusLabel.setManaged(visible);
    }

    public void hideUpdateIndicator() {
        availableUpdate = null;
        setUpdateIndicator(UpdateAwareness.IndicatorState.NONE, null, 0);
    }

    public void markUpdateReady(String version) {
        readyToRestartVersion = version;
        setUpdateIndicator(UpdateAwareness.IndicatorState.READY, version, 100);
    }

    public void handleUpdateIndicatorClick() {
        if (readyToRestartVersion != null) {
            updateRestartAction.run();
            return;
        }
        if (availableUpdate != null) {
            showUpdateDialog(availableUpdate, currentUpdateChannel());
        }
    }

    public void setUpdateRestartAction(Runnable action) {
        this.updateRestartAction = action != null ? action : UpdateRelauncher::relaunch;
    }

    public void setUpdateCoordinatorFactory(Supplier<UpdateCoordinator> factory) {
        if (factory != null) {
            this.updateCoordinatorFactory = factory;
        }
    }

    public void startBackgroundUpdate(UpdateCheckResult result) {
        if (result == null || result.manifest() == null || !result.hasUpdate()) {
            return;
        }
        String latest = result.manifest().latest();
        if (updateInstallRunning || latest.equals(readyToRestartVersion) || latest.equals(downloadingVersion)) {
            return;
        }
        UpdateAsset asset = result.manifest().assetFor(UpdateAssetKeys.preferred());
        if (asset == null) {
            return;
        }
        updateInstallRunning = true;
        downloadingVersion = latest;
        setUpdateIndicator(UpdateAwareness.IndicatorState.DOWNLOADING, latest, 0);

        UpdateCoordinator coordinator = updateCoordinatorFactory.get();
        Thread.ofVirtual().name("update-download").start(() -> {
            UpdateCoordinator.InstallResult install = coordinator.install(asset, latest,
                    (stage, done, total) -> {
                        if ("Downloading".equals(stage) && total > 0) {
                            int percent = (int) Math.min(100, done * 100 / total);
                            Platform.runLater(() -> setUpdateIndicator(
                                    UpdateAwareness.IndicatorState.DOWNLOADING, latest, percent));
                        }
                    },
                    () -> false);
            Platform.runLater(() -> {
                updateInstallRunning = false;
                downloadingVersion = null;
                if (install.success()) {
                    markUpdateReady(latest);
                } else {
                    setUpdateIndicator(UpdateAwareness.IndicatorState.FAILED, latest, 0);
                }
            });
        });
    }

    private void maybeAutoUpdateCheck() {
        try {
            boolean auto = !"false".equalsIgnoreCase(
                    preferenceService.getPreferencesByCode(UpdatePreferences.AUTO_CHECK).orElse("true"));
            if (!auto || "DEV".equalsIgnoreCase(AppVersion.current())) {
                return;
            }
            long now = System.currentTimeMillis();
            long last = readLongPreference(UpdatePreferences.LAST_CHECK, 0);
            long snoozeUntil = readLongPreference(UpdatePreferences.SNOOZE_UNTIL, 0);
            if (UpdateAwareness.isCheckDue(now, last, snoozeUntil, UpdateAwareness.CHECK_INTERVAL_MS)) {
                runUpdateCheck(false, currentUpdateChannel());
            }
        } catch (Exception e) {
            logger.debug("Auto update check skipped: {}", e.getMessage());
        }
    }

    private void runUpdateCheck(boolean interactive, String channel) {
        Thread.ofVirtual().start(() -> {
            UpdateCheckResult result;
            try {
                result = new UpdateServiceImpl(updateManifestUrl()).check(channel);
            } catch (Exception e) {
                result = UpdateCheckResult.error(AppVersion.current(),
                        e.getMessage() == null ? "Update check failed" : e.getMessage());
            }
            final UpdateCheckResult checkResult = result;
            if (checkResult.status() != UpdateCheckResult.Status.ERROR) {
                try {
                    preferenceService.saveOrUpdatePreferences(
                            new Preference(UpdatePreferences.LAST_CHECK, String.valueOf(System.currentTimeMillis())));
                } catch (Exception ignored) {
                }
            }
            Platform.runLater(() -> {
                if (checkResult.hasUpdate() && checkResult.manifest() != null) {
                    String skip = null;
                    try {
                        skip = preferenceService.getPreferencesByCode(UpdatePreferences.skipKey(channel)).orElse(null);
                    } catch (Exception ignored) {
                    }
                    if (!checkResult.manifest().latest().equals(skip)) {
                        showUpdateIndicator(checkResult);
                        if (!interactive) {
                            maybeStartBackgroundUpdate(checkResult);
                        }
                    }
                } else if (checkResult.status() == UpdateCheckResult.Status.UP_TO_DATE
                        && readyToRestartVersion == null) {
                    hideUpdateIndicator();
                }

                if (interactive) {
                    showUpdateDialog(checkResult, channel);
                } else if (checkResult.hasUpdate() && checkResult.manifest() != null) {
                    String skip = null;
                    try {
                        skip = preferenceService.getPreferencesByCode(UpdatePreferences.skipKey(channel)).orElse(null);
                    } catch (Exception ignored) {
                    }
                    long snoozeUntil = readLongPreference(UpdatePreferences.SNOOZE_UNTIL, 0);
                    if (UpdateAwareness.shouldPopup(
                            System.currentTimeMillis(), snoozeUntil,
                            checkResult.manifest().latest(), skip)) {
                        showUpdateDialog(checkResult, channel);
                    }
                }
            });
        });
    }

    private String currentUpdateChannel() {
        String override = System.getProperty("seeloggyplus.updateChannel");
        if (override != null && !override.isBlank()) {
            return override;
        }
        try {
            return preferenceService.getPreferencesByCode(UpdatePreferences.CHANNEL)
                    .filter(channel -> !channel.isBlank())
                    .orElse(UpdatePreferences.DEFAULT_CHANNEL);
        } catch (Exception e) {
            return UpdatePreferences.DEFAULT_CHANNEL;
        }
    }

    private String updateManifestUrl() {
        String override = System.getProperty("seeloggyplus.updateManifestUrl");
        if (override != null && !override.isBlank()) {
            return override;
        }
        try {
            return preferenceService.getPreferencesByCode(UpdatePreferences.MANIFEST_URL)
                    .filter(url -> !url.isBlank())
                    .orElse(UpdateServiceImpl.DEFAULT_MANIFEST_URL);
        } catch (Exception e) {
            return UpdateServiceImpl.DEFAULT_MANIFEST_URL;
        }
    }

    private long readLongPreference(String code, long fallback) {
        try {
            return Long.parseLong(preferenceService.getPreferencesByCode(code).orElse(String.valueOf(fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean isAutoDownloadEnabled() {
        try {
            return !"false".equalsIgnoreCase(
                    preferenceService.getPreferencesByCode(UpdatePreferences.AUTO_DOWNLOAD).orElse("true"));
        } catch (Exception e) {
            return true;
        }
    }

    private void setupUpdateMenuItems() {
        if (autoCheckUpdatesMenuItem != null) {
            boolean auto = !"false".equalsIgnoreCase(
                    preferenceService.getPreferencesByCode(UpdatePreferences.AUTO_CHECK).orElse("true"));
            autoCheckUpdatesMenuItem.setSelected(auto);
            autoCheckUpdatesMenuItem.setOnAction(e -> preferenceService.saveOrUpdatePreferences(
                    new Preference(UpdatePreferences.AUTO_CHECK, String.valueOf(autoCheckUpdatesMenuItem.isSelected()))));
        }
        if (autoDownloadUpdatesMenuItem != null) {
            autoDownloadUpdatesMenuItem.setSelected(isAutoDownloadEnabled());
            autoDownloadUpdatesMenuItem.setOnAction(e -> preferenceService.saveOrUpdatePreferences(
                    new Preference(UpdatePreferences.AUTO_DOWNLOAD,
                            String.valueOf(autoDownloadUpdatesMenuItem.isSelected()))));
        }
    }

    private void maybeStartBackgroundUpdate(UpdateCheckResult result) {
        if (!isAutoDownloadEnabled()) {
            return;
        }
        startBackgroundUpdate(result);
    }

    private void showUpdateDialog(UpdateCheckResult result, String channel) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UpdateDialog.fxml"));
            Parent root = loader.load();
            UpdateDialogController updateController = loader.getController();
            updateController.setResult(result);
            updateController.setOnSkipped(this::hideUpdateIndicator);
            if (result.manifest() != null && result.manifest().latest().equals(readyToRestartVersion)) {
                updateController.markInstalled(readyToRestartVersion);
            }

            Stage dialog = new Stage();
            dialog.setTitle("Software Update");
            AppIcons.apply(dialog);
            dialog.initModality(Modality.WINDOW_MODAL);
            Window owner = ownerSupplier.get();
            if (owner != null) {
                dialog.initOwner(owner);
            }
            dialog.setScene(AppTheme.scene(root));
            dialog.showAndWait();

            if (updateController.isSkipped() && result.manifest() != null) {
                try {
                    preferenceService.saveOrUpdatePreferences(
                            new Preference(UpdatePreferences.skipKey(channel), result.manifest().latest()));
                } catch (Exception ignored) {
                }
                hideUpdateIndicator();
            } else if (updateController.getSnoozeMillis() > 0) {
                try {
                    preferenceService.saveOrUpdatePreferences(new Preference(
                            UpdatePreferences.SNOOZE_UNTIL,
                            String.valueOf(System.currentTimeMillis() + updateController.getSnoozeMillis())));
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            logger.error("Failed to open update dialog", e);
            errorReporter.accept("Update", "Could not open update dialog: " + e.getMessage());
        }
    }
}
