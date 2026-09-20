package com.seeloggyplus.ui.search;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
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

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the search result panel and navigator adopt theme style classes and
 * follow the light/dark palette (Phase 6 / 7 compatibility).
 */
@ExtendWith(ApplicationExtension.class)
class SearchPanelThemeTest {

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
    void resultPanelHeaderUsesThemeStyleClass() throws Exception {
        SearchResultPanel panel = new SearchResultPanel();
        Label header = (Label) privateField(panel, "headerLabel");
        assertTrue(header.getStyleClass().contains("search-results-header"),
                "header must use the search-results-header style class instead of an inline style");
        assertEquals("", header.getStyle(), "header must not rely on inline styles");
    }

    @Test
    void navigatorStatusUsesThemeStyleClass() throws Exception {
        SearchNavigator navigator = new SearchNavigator();
        Label status = (Label) privateField(navigator, "statusLabel");
        assertTrue(status.getStyleClass().contains("search-navigator-status"),
                "status label must use the search-navigator-status style class");
        assertEquals("", status.getStyle(), "status label must not rely on inline styles");
    }

    @Test
    void resultPanelPaletteFollowsTheme() {
        onFxThread(() -> {
            SearchResultPanel panel = new SearchResultPanel();
            StackPane root = new StackPane(panel);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();

            assertEquals(Color.web("#ffffff"), backgroundFill(panel),
                    "light theme search panel background must be white");

            AppTheme.setDark(true);
            root.applyCss();
            assertEquals(Color.web("#1e2226"), backgroundFill(panel),
                    "dark theme search panel background must be the dark surface");

            AppTheme.setDark(false);
        });
    }

    private static Object privateField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
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
