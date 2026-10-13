package com.seeloggyplus.shared.ui.canvas;

import com.seeloggyplus.shared.ui.AppTheme;
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
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

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
            assertEquals(Color.web("#7f1d1d"), palette(viewer, "fatalText"));
            assertEquals(Color.web("#6c757d"), palette(viewer, "lineNumColor"));
            assertEquals(Color.web("#ffff00"), palette(viewer, "highlightBg"));

            AppTheme.setDark(true);
            root.applyCss();
            assertEquals(Color.web("#1e2226"), backgroundFill(viewer),
                    "dark theme canvas background must be the dark surface");
            assertEquals(Color.web("#ff8a80"), palette(viewer, "fatalText"));
            assertEquals(Color.web("#8b939c"), palette(viewer, "lineNumColor"));
            assertEquals(Color.web("rgba(250,204,21,0.35)"), palette(viewer, "highlightBg"));

            AppTheme.setTheme(AppTheme.Theme.LIGHT);
            root.applyCss();
            assertEquals(Color.web("#1d4ed8"), palette(viewer, "infoText"),
                    "light theme canvas must use the light level-info token");

            AppTheme.setDark(false);
            assertEquals(Color.web("#ffffff"), backgroundFill(viewer),
                    "canvas must return to the light palette");
        });
    }

    private static Color palette(CanvasLogViewer viewer, String name) {
        try {
            Field field = CanvasLogViewer.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Color) field.get(viewer);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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
