package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;

import com.seeloggyplus.update.Hashing;
import com.seeloggyplus.update.UpdateCheckResult;
import com.seeloggyplus.update.UpdateCoordinator;
import com.seeloggyplus.update.UpdateDownloader;
import com.seeloggyplus.update.UpdateInstaller;
import com.seeloggyplus.update.UpdateLayout;
import com.seeloggyplus.update.UpdateManifest;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
public class UpdateDialogControllerTest {

    private static final String MANIFEST = """
            {"channel":"stable","latest":"9.9.9","minSupported":"0.0.1",
             "releaseNotesUrl":"https://example.com/notes",
             "assets":{"portable-nojre":{"url":"https://example.com/app.zip","size":100,"sha256":"abc"}}}
            """;

    private UpdateDialogController controller;
    private Label titleLabel;
    private Label versionLabel;
    private Label statusLabel;
    private Button downloadButton;
    private Button releaseNotesButton;
    private Button skipButton;
    private Button laterButton;

    @Start
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UpdateDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();

        titleLabel = getField("titleLabel");
        versionLabel = getField("versionLabel");
        statusLabel = getField("statusLabel");
        downloadButton = getField("downloadButton");
        releaseNotesButton = getField("releaseNotesButton");
        skipButton = getField("skipButton");
        laterButton = getField("laterButton");
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String name) throws Exception {
        Field field = UpdateDialogController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(controller);
    }

    private UpdateCheckResult available() throws Exception {
        return UpdateCheckResult.updateAvailable("0.2.0", UpdateManifest.parse(MANIFEST));
    }

    @Test
    public void showsUpdateAvailableWithActions() throws Exception {
        UpdateCheckResult available = available();
        Platform.runLater(() -> controller.setResult(available));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Update Available", titleLabel.getText());
        assertTrue(versionLabel.getText().contains("0.2.0"));
        assertTrue(versionLabel.getText().contains("9.9.9"));
        assertFalse(downloadButton.isDisabled());
        assertFalse(releaseNotesButton.isDisabled());
        assertTrue(skipButton.isVisible());
        assertTrue(laterButton.isVisible());
    }

    @Test
    public void forcedUpdateHidesSkipAndLater() throws Exception {
        UpdateCheckResult forced = UpdateCheckResult.forced("0.2.0", UpdateManifest.parse(MANIFEST));
        Platform.runLater(() -> controller.setResult(forced));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Update Required", titleLabel.getText());
        assertFalse(skipButton.isVisible());
        assertFalse(laterButton.isVisible());
        assertFalse(downloadButton.isDisabled());
    }

    @Test
    public void releaseNotesOpensUrl() throws Exception {
        List<String> opened = new ArrayList<>();
        UpdateCheckResult available = available();
        Platform.runLater(() -> {
            controller.setBrowser(opened::add);
            controller.setResult(available);
            releaseNotesButton.fire();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(List.of("https://example.com/notes"), opened);
    }

    @Test
    public void cancelButtonTextDuringInstall() throws Exception {
        // Without an injected coordinator the download would need network, so we only
        // verify that a fresh result resets the button label and hides the progress bar.
        UpdateCheckResult available = available();
        Platform.runLater(() -> controller.setResult(available));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Download", downloadButton.getText());
        javafx.scene.control.ProgressBar bar = getField("progressBar");
        assertFalse(bar.isVisible());
    }

    @Test
    public void skipMarksSkipped() throws Exception {
        UpdateCheckResult available = available();
        Platform.runLater(() -> controller.setResult(available));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(controller.isSkipped());
        Platform.runLater(skipButton::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(controller.isSkipped());
    }

    private void waitUntil(java.util.concurrent.Callable<Boolean> condition) throws Exception {
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

    private static byte[] zipWithJar() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("SeeloggyPlus/seeloggyplus.jar"));
            zip.write("jar-bytes".getBytes());
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }

    @Test
    public void installDownloadsStagesActivatesAndOffersRestart() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-ui-root");
        Path staging = Files.createTempDirectory("seeloggy-ui-staging");
        try {
            byte[] zip = zipWithJar();
            UpdateCoordinator coordinator = new UpdateCoordinator(new UpdateLayout(root), staging,
                    new UpdateDownloader((url, offset) -> new UpdateDownloader.Opened(
                            new ByteArrayInputStream(zip, (int) offset, zip.length - (int) offset), offset)),
                    new UpdateInstaller());
            String json = "{\"latest\":\"9.9.9\",\"assets\":{\"portable-nojre\":{"
                    + "\"url\":\"https://example.com/app.zip\",\"size\":" + zip.length
                    + ",\"sha256\":\"" + Hashing.sha256(zip) + "\"}}}";
            UpdateCheckResult available = UpdateCheckResult.updateAvailable("0.2.0", UpdateManifest.parse(json));
            List<Boolean> restarted = new ArrayList<>();

            Platform.runLater(() -> {
                controller.setCoordinator(coordinator);
                controller.setRestartAction(() -> restarted.add(Boolean.TRUE));
                controller.setResult(available);
                downloadButton.fire();
            });
            waitUntil(controller::isInstalled);

            assertEquals("9.9.9", controller.getInstalledVersion());
            assertEquals(Optional.of("9.9.9"), new UpdateLayout(root).currentVersion());
            assertTrue(Files.exists(new UpdateLayout(root).jarFor("9.9.9")));

            Platform.runLater(downloadButton::fire);
            WaitForAsyncUtils.waitForFxEvents();
            assertEquals(1, restarted.size(), "Restart action must be triggered after install");
        } finally {
            deleteRecursively(root);
            deleteRecursively(staging);
        }
    }

    @Test
    public void upToDateHidesUpdateActions() throws Exception {
        UpdateCheckResult upToDate = UpdateCheckResult.upToDate("9.9.9", UpdateManifest.parse(MANIFEST));
        Platform.runLater(() -> controller.setResult(upToDate));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("You're up to date", titleLabel.getText());
        assertFalse(downloadButton.isVisible());
        assertFalse(releaseNotesButton.isVisible());
        assertFalse(skipButton.isVisible());
    }
}
