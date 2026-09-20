package com.seeloggyplus.ui.canvas;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
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
 * The canvas log viewer ignores CSS and switches its palette programmatically
 * based on the scene theme. This verifies the light/dark switch (Phase 7).
 */
@ExtendWith(ApplicationExtension.class)
class CanvasThemeTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void resetTheme() {
        AppTheme.setDark(false);
    }

    @Test
    void canvasPaletteFollowsTheme() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            StackPane root = new StackPane(viewer);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();

            assertEquals(Color.web("#ffffff"), backgroundFill(viewer),
                    "light theme canvas background must be white");

            AppTheme.setDark(true);
            root.applyCss();
            assertEquals(Color.web("#1e2226"), backgroundFill(viewer),
                    "dark theme canvas background must be the dark surface");

            AppTheme.setDark(false);
            assertEquals(Color.web("#ffffff"), backgroundFill(viewer),
                    "canvas must return to the light palette");
        });
    }

    private static Color backgroundFill(Region region) {
        Background bg = region.getBackground();
        if (bg == null || bg.getFills().isEmpty()) {
            return null;
        }
        Paint paint = bg.getFills().get(0).getFill();
        return paint instanceof Color color ? color : null;
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
