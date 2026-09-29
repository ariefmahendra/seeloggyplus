package com.seeloggyplus.controller;
import com.seeloggyplus.util.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.api.FxRobot;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(ApplicationExtension.class)
class FeedbackUiSmokeTest {
    Stage stage;
    @Start void start(Stage stage) { this.stage = stage; }
    @Test void renderFeedbackDialogs(FxRobot robot) throws Exception {
        var service = new com.seeloggyplus.service.impl.ServerManagementServiceImpl();
        var preferences = new com.seeloggyplus.service.impl.PreferenceServiceImpl();
        var sample = new com.seeloggyplus.model.SSHServerModel("Feedback UI sample", "127.0.0.1", 22, "user");
        sample.setGroupName("Production"); sample.setFavorite(true); service.saveServer(sample);
        java.util.concurrent.atomic.AtomicReference<Parent> loaded = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            preferences.saveOrUpdatePreferences(new com.seeloggyplus.model.Preference("file_manager_last_location", "local"));
            for (String name : new String[]{"ServerManagementDialog", "PreferencesDialog", "UnifiedFileManagerDialog"}) {
                robot.interact(() -> {
                    try {
                        AppTheme.setTheme(AppTheme.Theme.GRAPHITE);
                        Parent root = FXMLLoader.load(getClass().getResource("/fxml/" + name + ".fxml"));
                        loaded.set(root);
                        stage.setScene(AppTheme.scene(root)); stage.sizeToScene(); stage.show();
                    } catch (Exception e) { throw new AssertionError(e); }
                });
                org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
                if (name.equals("UnifiedFileManagerDialog")) {
                    org.testfx.util.WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS,
                        () -> org.testfx.util.WaitForAsyncUtils.asyncFx(() ->
                            ((javafx.scene.control.TreeView<?>) loaded.get().lookup("#locationTree")).getRoot() != null).get());
                }
                robot.interact(() -> {
                    try {
                        Parent root = loaded.get(); root.applyCss(); root.layout();
                        var image = root.snapshot(null, null);
                        assertTrue(image.getWidth() > 400);
                        BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++)
                            png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                        Path path = Path.of("build", "feedback-ui", name + ".png"); Files.createDirectories(path.getParent());
                        ImageIO.write(png, "png", path.toFile());
                        stage.hide();
                    } catch (Exception e) { throw new AssertionError(e); }
                });
            }
        } finally { service.deleteServer(sample.getId()); }
    }
}
