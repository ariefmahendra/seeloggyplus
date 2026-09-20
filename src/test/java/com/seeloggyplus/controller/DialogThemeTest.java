package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * About & Update dialogs must adopt the dark palette: tokens for text and a
 * dark banner/primary button that match the dark surfaces (no light/blue tones).
 */
@ExtendWith(ApplicationExtension.class)
class DialogThemeTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    @Test
    void aboutDialogTextFollowsTheme() {
        onFxThread(() -> {
            Parent root = load("/fxml/AboutDialog.fxml");
            show(root);

            Label version = (Label) lookup(root, "#versionLabel");
            Text description = (Text) lookup(root, ".about-description");
            Label copyright = (Label) lookup(root, ".about-copyright");
            assertNotNull(version);
            assertNotNull(description);
            assertNotNull(copyright);

            AppTheme.setDark(true);
            apply(root);
            assertEquals(Color.web("#9aa3ac"), version.getTextFill(), "version must use dark muted text");
            assertEquals(Color.web("#b8c0c8"), description.getFill(), "description must use dark secondary text");
            assertEquals(Color.web("#6f787f"), copyright.getTextFill(), "copyright must use dark faint text");

            AppTheme.setDark(false);
        });
    }

    @Test
    void updateDialogBannerIsDarkInDarkMode() {
        onFxThread(() -> {
            Parent root = load("/fxml/UpdateDialog.fxml");
            show(root);

            Region header = (Region) lookup(root, ".update-dialog-header");
            assertNotNull(header);

            AppTheme.setDark(true);
            apply(root);
            Color stop = firstGradientStop(header);
            assertNotNull(stop, "banner must be a gradient");
            assertTrue(luminance(stop) < 0.30,
                    "dark banner must be a dark tone, was " + stop);

            AppTheme.setDark(false);
        });
    }

    @Test
    void primaryButtonMatchesDarkMode() {
        onFxThread(() -> {
            Parent root = load("/fxml/UpdateDialog.fxml");
            show(root);
            Region download = (Region) lookup(root, "#downloadButton");
            assertNotNull(download);

            assertEquals(Color.web("#343a40"), backgroundFill(download),
                    "light primary button must be the graphite brand");

            AppTheme.setDark(true);
            apply(root);
            assertEquals(Color.web("#3a424b"), backgroundFill(download),
                    "dark primary/default button must be the darker graphite tone");

            AppTheme.setDark(false);
        });
    }

    private Parent load(String fxml) {
        try {
            return FXMLLoader.load(getClass().getResource(fxml));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void show(Parent root) {
        Scene scene = AppTheme.scene(root);
        stage.setScene(scene);
        stage.show();
        apply(root);
    }

    private void apply(Parent root) {
        root.applyCss();
        root.layout();
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Node lookup(Parent root, String selector) {
        return root.lookup(selector);
    }

    private static Color firstGradientStop(Region region) {
        Background bg = region.getBackground();
        if (bg == null || bg.getFills().isEmpty()) {
            return null;
        }
        Paint paint = bg.getFills().get(0).getFill();
        if (paint instanceof LinearGradient gradient && !gradient.getStops().isEmpty()) {
            Stop stop = gradient.getStops().get(0);
            return stop.getColor();
        }
        return paint instanceof Color color ? color : null;
    }

    private static Color backgroundFill(Region region) {
        Background bg = region.getBackground();
        if (bg == null || bg.getFills().isEmpty()) {
            return null;
        }
        Paint paint = bg.getFills().get(0).getFill();
        return paint instanceof Color color ? color : null;
    }

    private static double luminance(Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }

    private static void onFxThread(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }
}
