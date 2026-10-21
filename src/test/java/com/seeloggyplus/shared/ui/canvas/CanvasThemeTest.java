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
        onFxThread(() -> AppTheme.setDark(false));
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
            assertEquals(Color.web("#5f6269"), palette(viewer, "lineNumColor"));
            assertEquals(Color.web("#ffff00"), palette(viewer, "highlightBg"));

            AppTheme.setDark(true);
            root.applyCss();
            assertEquals(Color.web("#181c22"), backgroundFill(viewer),
                    "dark theme canvas background must be the dark surface");
            assertEquals(Color.web("#ff8a80"), palette(viewer, "fatalText"));
            assertEquals(Color.web("#9fa2a8"), palette(viewer, "lineNumColor"));
            assertEquals(Color.web("rgba(250,204,21,0.35)"), palette(viewer, "highlightBg"));

            AppTheme.setTheme(AppTheme.Theme.LIGHT);
            root.applyCss();
            assertEquals(Color.web("#426588"), palette(viewer, "infoText"),
                    "light theme canvas must use the light level-info token");

            AppTheme.setDark(false);
            assertEquals(Color.web("#ffffff"), backgroundFill(viewer),
                    "canvas must return to the light palette");
        });
    }

    @Test
    void severityColorsOnlyTheWholeLineTextWithoutTintOrGutterStripe() {
        onFxThread(() -> {
            var viewer = new CanvasLogViewer();
            var root = new StackPane(viewer);
            stage.setScene(AppTheme.scene(root));
            stage.setWidth(700); stage.setHeight(250); stage.show();
            String[] levels = {"FATAL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE"};
            var entries = new java.util.ArrayList<com.seeloggyplus.shared.model.LogEntry>();
            for (int i = 0; i < levels.length; i++) {
                entries.add(new com.seeloggyplus.shared.model.LogEntry(i + 1, levels[i] + "  MMMMMMMM payload"));
            }
            viewer.setTailBuffer(entries);
            for (var theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                root.applyCss(); root.layout(); viewer.redraw();
                var image = viewer.snapshot(null, null);
                var pixels = image.getPixelReader();
                Color surface = Color.web(theme == AppTheme.Theme.LIGHT ? "#ffffff" : "#181c22");
                for (int i = 0; i < levels.length; i++) {
                    int top = 4 + i * 18;
                    assertEquals(surface, pixels.getColor(600, top + 9), "Row background must stay neutral for " + levels[i]);
                    assertEquals(surface, pixels.getColor(77, top + 9), "Gutter must have no severity stripe");
                    Color foreground = com.seeloggyplus.shared.ui.ThemePalette.color(
                            "-sl-level-" + levels[i].toLowerCase(java.util.Locale.ROOT) + "-fg", theme);
                    double x = luminance(foreground), y = luminance(surface);
                    assertTrue((Math.max(x, y) + .05) / (Math.min(x, y) + .05) >= 4.5);
                    int coloredPayloadPixels = 0;
                    for (int py = top; py < top + 18; py++) {
                        for (int px = 145; px < 200; px++) {
                            if (pixels.getColor(px, py).equals(foreground)) coloredPayloadPixels++;
                        }
                    }
                    assertTrue(coloredPayloadPixels >= 5, "Payload text must use the " + levels[i] + " foreground in " + theme);
                }
            }
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

    @Test void mutedInfoRemainsReadableInEveryTheme() {
        onFxThread(() -> {
            var viewer = new CanvasLogViewer();
            var root = new StackPane(viewer);
            stage.setScene(AppTheme.scene(root));
            stage.show();
            for (var theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                root.applyCss();
                Color info = palette(viewer, "infoText");
                assertEquals(Color.web(theme == AppTheme.Theme.DARK ? "#8eabc4" : "#426588"), info);
                double a = luminance(info), b = luminance(backgroundFill(viewer));
                assertTrue((Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5, theme.name());
            }
        });
    }

    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }

    @Test void selectedDarkLevelTextUsesReadableEditorForeground() {
        onFxThread(() -> {
            var viewer = new CanvasLogViewer();
            var root = new StackPane(viewer);
            stage.setScene(AppTheme.scene(root));
            stage.setWidth(500); stage.setHeight(150); stage.show();
            AppTheme.setTheme(AppTheme.Theme.DARK);
            root.applyCss(); root.layout();
            viewer.setTailBuffer(java.util.List.of(new com.seeloggyplus.shared.model.LogEntry(1, "INFO")));
            viewer.selectLine(0);
            viewer.requestCanvasFocus();
            viewer.redraw();
            var pixels = viewer.snapshot(null, null).getPixelReader();
            double brightest = 0;
            for (int y = 4; y < 22; y++) {
                for (int x = 80; x < 112; x++) brightest = Math.max(brightest, luminance(pixels.getColor(x, y)));
            }
            assertTrue(brightest >= luminance(Color.web("#d6dbe3")) * .9,
                    "Selected INFO must use readable editor text instead of low-contrast blue on blue");
        });
    }

    private static double linear(double value) {
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
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
