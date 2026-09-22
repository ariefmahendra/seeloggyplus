package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the newly added fully-light theme: the chrome (toolbar / menus /
 * status bar) is light too, unlike the default graphite theme whose chrome is
 * dark. This is the "some people like an all-light UI" feedback item.
 */
@ExtendWith(ApplicationExtension.class)
class LightThemeTest {

    private Stage stage;
    private StackPane root;
    private HBox toolbar;
    private Label toolbarLabel;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void reset() {
        onFx(() -> {
            AppTheme.setDark(false);
            if (stage != null) {
                stage.close();
            }
        });
    }

    private void buildToolbarScene() {
        toolbarLabel = new Label("Toolbar");
        toolbar = new HBox(toolbarLabel);
        toolbar.getStyleClass().add("app-toolbar");
        root = new StackPane(toolbar);
        Scene scene = AppTheme.scene(root);
        stage.setScene(scene);
        stage.show();
        root.applyCss();
        root.layout();
    }

    @Test
    @DisplayName("Light theme makes the chrome light (all-light UI)")
    void lightThemeHasLightChrome() {
        onFx(() -> {
            buildToolbarScene();

            AppTheme.setTheme(AppTheme.Theme.LIGHT);
            root.applyCss();
            root.layout();

            Color chrome = backgroundFill(toolbar);
            assertNotNull(chrome, "toolbar must have a theme background");
            assertTrue(luminance(chrome) > 0.7,
                    "light theme chrome must be light, was " + chrome);
            Color text = (Color) toolbarLabel.getTextFill();
            assertTrue(luminance(text) < 0.35,
                    "light theme chrome text must be dark, was " + text);
        });
    }

    @Test
    @DisplayName("Graphite theme keeps dark chrome (unchanged default)")
    void graphiteThemeKeepsDarkChrome() {
        onFx(() -> {
            buildToolbarScene();
            AppTheme.setTheme(AppTheme.Theme.GRAPHITE);
            root.applyCss();
            root.layout();

            Color chrome = backgroundFill(toolbar);
            assertNotNull(chrome);
            assertTrue(luminance(chrome) < 0.35,
                    "graphite theme chrome must stay dark, was " + chrome);
        });
    }

    @Test
    @DisplayName("Theme enum maps persisted preference values, including legacy 'light'")
    void preferenceMapping() {
        assertEquals(AppTheme.Theme.DARK, AppTheme.Theme.fromPreference("dark"));
        assertEquals(AppTheme.Theme.LIGHT, AppTheme.Theme.fromPreference("light"));
        assertEquals(AppTheme.Theme.GRAPHITE, AppTheme.Theme.fromPreference("graphite"));
        assertEquals(AppTheme.Theme.GRAPHITE, AppTheme.Theme.fromPreference(""));
        assertEquals(AppTheme.Theme.GRAPHITE, AppTheme.Theme.fromPreference(null));
        assertEquals(AppTheme.Theme.GRAPHITE, AppTheme.Theme.fromPreference("unknown-value"));
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
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

    private static void onFx(Runnable action) {
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
