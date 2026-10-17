package com.seeloggyplus.features.search.presentation;

import com.seeloggyplus.shared.ui.AppTheme;
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
    void navigatorStatusReflectsCountsAndVisibility() throws Exception {
        SearchNavigator navigator = new SearchNavigator();
        Label status = (Label) privateField(navigator, "statusLabel");

        navigator.setMatchCount(-1);
        assertEquals("Scanning...", status.getText());
        assertTrue(navigator.isVisible());

        navigator.setMatchCount(0);
        assertEquals("No matches", status.getText());

        navigator.setMatchCount(7);
        assertEquals("7 matches", status.getText());

        navigator.updateStatus(2, 5);
        assertEquals("2 / 5", status.getText());

        navigator.updateStatus(0, 0);
        assertEquals("No matches", status.getText());

        navigator.clear();
        assertEquals("", status.getText());
        assertFalse(navigator.isVisible());
    }

    @Test
    void navigatorStatusMarksTruncatedCounts() throws Exception {
        SearchNavigator navigator = new SearchNavigator();
        Label status = (Label) privateField(navigator, "statusLabel");

        navigator.setMatchCount(50000, true);
        assertEquals("50000+ matches", status.getText());
        assertNotNull(status.getTooltip(), "a truncated count must explain the scan limit");
        assertTrue(status.getTooltip().getText().contains("50000"));

        navigator.setMatchCount(7, false);
        assertEquals("7 matches", status.getText());
        assertNull(status.getTooltip(), "an exact count must not keep the scan-limit tooltip");
    }

    @Test
    void navigatorButtonsInvokeTheirHandlers() throws Exception {
        SearchNavigator navigator = new SearchNavigator();
        javafx.scene.control.Button prev = (javafx.scene.control.Button) privateField(navigator, "prevButton");
        javafx.scene.control.Button next = (javafx.scene.control.Button) privateField(navigator, "nextButton");
        java.util.List<String> calls = new java.util.ArrayList<>();
        navigator.setOnPrevious(() -> calls.add("prev"));
        navigator.setOnNext(() -> calls.add("next"));

        prev.fire();
        next.fire();

        assertEquals(java.util.List.of("prev", "next"), calls);
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
            assertEquals(Color.web("#1f2329"), paletteField(panel, "textColor"),
                    "search text must use the theme text token");
            assertEquals(Color.web("#6c757d"), paletteField(panel, "lineNumColor"));
            assertEquals(Color.web("#e6f0ff"), paletteField(panel, "hoverColor"));

            AppTheme.setDark(true);
            root.applyCss();
            assertEquals(Color.web("#1e2226"), backgroundFill(panel),
                    "dark theme search panel background must be the dark surface");
            assertEquals(Color.web("#d7dbe0"), paletteField(panel, "textColor"));
            assertEquals(Color.web("#8b939c"), paletteField(panel, "lineNumColor"));
            assertEquals(Color.web("#2a3036"), paletteField(panel, "hoverColor"));

            AppTheme.setDark(false);
        });
    }

    private static Object paletteField(SearchResultPanel panel, String name) {
        try {
            return privateField(panel, name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
