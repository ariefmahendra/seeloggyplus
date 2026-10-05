package com.seeloggyplus.features.update.presentation;

import com.seeloggyplus.app.MainController;

import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.features.update.presentation.UpdateFeature;
import com.seeloggyplus.features.update.domain.UpdateAsset;
import com.seeloggyplus.features.update.domain.UpdateAwareness;
import com.seeloggyplus.features.update.domain.UpdateCheckResult;
import com.seeloggyplus.features.update.infrastructure.UpdateCoordinator;
import com.seeloggyplus.features.update.infrastructure.UpdateDownloader;
import com.seeloggyplus.features.update.infrastructure.UpdateInstaller;
import com.seeloggyplus.features.update.infrastructure.UpdateLayout;
import com.seeloggyplus.features.update.domain.UpdateManifest;
import com.seeloggyplus.features.update.domain.UpdatePreferences;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.Callable;

/**
 * Update awareness in the status bar: state texts, the background auto-download
 * flow ending in "Restart to update", and the menu preferences.
 */
@ExtendWith(ApplicationExtension.class)
class UpdateIndicatorTest {

    private static final String MANIFEST = """
            {"channel":"stable","latest":"9.9.9","minSupported":"0.0.1",
             "releaseNotesUrl":"https://example.com/notes",
             "assets":{"portable-nojre":{"url":"https://example.com/app.zip","size":100,"sha256":"abc"}}}
            """;

    private MainController controller;
    private UpdateFeature updateFeature;
    private Label updateStatusLabel;
    private CheckMenuItem autoDownloadMenuItem;

    @Start
    void start(Stage stage) throws Exception {
        PreferenceServiceImpl prefs = new PreferenceServiceImpl();
        prefs.saveOrUpdatePreferences(new Preference(UpdatePreferences.AUTO_DOWNLOAD, "true"));
        prefs.saveOrUpdatePreferences(new Preference(UpdatePreferences.AUTO_CHECK, "true"));

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();

        updateFeature = (UpdateFeature) field("updateFeature");
        updateStatusLabel = (Label) field("updateStatusLabel");
        autoDownloadMenuItem = (CheckMenuItem) field("autoDownloadUpdatesMenuItem");
        assertNotNull(updateStatusLabel, "MainView must provide the update status label");
    }

    private Object field(String name) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private UpdateCheckResult available() throws Exception {
        return UpdateCheckResult.updateAvailable("0.6.3", UpdateManifest.parse(MANIFEST));
    }

    private void waitUntil(Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            WaitForAsyncUtils.waitForFxEvents();
            if (condition.call()) {
                return;
            }
            Thread.sleep(40);
        }
        fail("Condition was not met within timeout");
    }

    /** Coordinator that succeeds instantly without touching the network. */
    static class FakeCoordinator extends UpdateCoordinator {
        FakeCoordinator() {
            super(new UpdateLayout(Path.of("build", "test-data", "fake-install")),
                    Path.of("build", "test-data", "fake-staging"),
                    new UpdateDownloader((url, offset) -> {
                        throw new IOException("offline test");
                    }),
                    new UpdateInstaller());
        }

        @Override
        public InstallResult install(UpdateAsset asset, String version, ProgressListener progress,
                BooleanSupplier cancelled) {
            if (progress != null) {
                progress.onProgress("Downloading", 50, 100);
            }
            return new InstallResult(true, false, version, "ok");
        }
    }

    @Test
    @DisplayName("the indicator is hidden until an update is available")
    void hiddenByDefault() {
        assertFalse(updateStatusLabel.isVisible());
        assertFalse(updateStatusLabel.isManaged());
    }

    @Test
    @DisplayName("showing an update keeps it visible with the version, hiding clears it")
    void showAndHide() throws Exception {
        UpdateCheckResult result = available();

        Platform.runLater(() -> updateFeature.showUpdateIndicator(result));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(updateStatusLabel.isVisible());
        assertTrue(updateStatusLabel.getText().contains("9.9.9"));

        Platform.runLater(updateFeature::hideUpdateIndicator);
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(updateStatusLabel.isVisible());
        assertTrue(updateStatusLabel.getText() == null || updateStatusLabel.getText().isBlank());
    }

    @Test
    @DisplayName("each indicator state renders its own message")
    void indicatorStates() {
        Platform.runLater(() -> updateFeature.setUpdateIndicator(
                UpdateAwareness.IndicatorState.DOWNLOADING, "9.9.9", 42));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(updateStatusLabel.getText().contains("42%"));

        Platform.runLater(() -> updateFeature.setUpdateIndicator(
                UpdateAwareness.IndicatorState.READY, "9.9.9", 100));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(updateStatusLabel.getText().toLowerCase().contains("restart"));

        Platform.runLater(() -> updateFeature.setUpdateIndicator(
                UpdateAwareness.IndicatorState.FAILED, "9.9.9", 0));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(updateStatusLabel.getText().toLowerCase().contains("failed"));
    }

    @Test
    @DisplayName("background download reaches 'Restart to update' and clicking restarts")
    void backgroundDownloadReachesReady() throws Exception {
        AtomicBoolean restarted = new AtomicBoolean();
        UpdateCheckResult result = available();

        Platform.runLater(() -> {
            updateFeature.setUpdateCoordinatorFactory(FakeCoordinator::new);
            updateFeature.setUpdateRestartAction(() -> restarted.set(true));
            updateFeature.startBackgroundUpdate(result);
        });

        waitUntil(() -> updateStatusLabel.isVisible()
                && updateStatusLabel.getText() != null
                && updateStatusLabel.getText().toLowerCase().contains("restart"));

        Platform.runLater(updateFeature::handleUpdateIndicatorClick);
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(restarted.get(), "clicking a ready indicator must trigger the restart");
    }

    @Test
    @DisplayName("auto-download defaults to on and persists the menu toggle")
    void autoDownloadMenuPersists() throws Exception {
        assertTrue(autoDownloadMenuItem.isSelected(), "auto-download should default to on");

        // A real menu click toggles the check state and then fires the action.
        Platform.runLater(() -> {
            autoDownloadMenuItem.setSelected(false);
            autoDownloadMenuItem.fire();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(autoDownloadMenuItem.isSelected());
        assertEquals("false", new PreferenceServiceImpl()
                .getPreferencesByCode(UpdatePreferences.AUTO_DOWNLOAD).orElse(null));

        // Restore for other tests sharing the database.
        Platform.runLater(() -> {
            autoDownloadMenuItem.setSelected(true);
            autoDownloadMenuItem.fire();
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(autoDownloadMenuItem.isSelected());
    }
}
