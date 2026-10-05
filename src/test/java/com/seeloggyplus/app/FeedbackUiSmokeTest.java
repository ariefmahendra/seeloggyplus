package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
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
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.features.servers.infrastructure.ServerManagementServiceImpl;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.TreeView;
import org.testfx.util.WaitForAsyncUtils;
@ExtendWith(ApplicationExtension.class)
class FeedbackUiSmokeTest {
    Stage stage;
    @Start void start(Stage stage) { this.stage = stage; }
    @Test void renderFeedbackDialogs(FxRobot robot) throws Exception {
        var service = new ServerManagementServiceImpl();
        var preferences = new PreferenceServiceImpl();
        var sample = new SSHServerModel("Feedback UI sample", "127.0.0.1", 22, "user");
        sample.setGroupName("Production"); sample.setFavorite(true); service.saveServer(sample);
        AtomicReference<Parent> loaded = new AtomicReference<>();
        try {
            preferences.saveOrUpdatePreferences(new Preference("file_manager_last_location", "local"));
            for (String name : new String[]{"ServerManagementDialog", "PreferencesDialog", "UnifiedFileManagerDialog"}) {
                robot.interact(() -> {
                    try {
                        AppTheme.setTheme(AppTheme.Theme.GRAPHITE);
                        Parent root = FXMLLoader.load(getClass().getResource("/fxml/" + name + ".fxml"));
                        loaded.set(root);
                        stage.setScene(AppTheme.scene(root)); stage.sizeToScene(); stage.show();
                    } catch (Exception e) { throw new AssertionError(e); }
                });
                WaitForAsyncUtils.waitForFxEvents();
                if (name.equals("UnifiedFileManagerDialog")) {
                    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                        () -> WaitForAsyncUtils.asyncFx(() ->
                            ((TreeView<?>) loaded.get().lookup("#locationTree")).getRoot() != null).get());
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
