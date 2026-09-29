package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
 * Every button follows the active theme (no default JavaFX/Modena look):
 * a themed surface, readable text and the same border radius. Primary
 * buttons keep the brand accent with readable text.
 */
@ExtendWith(ApplicationExtension.class)
class ButtonThemeTest {

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
    @DisplayName("plain buttons use the theme surface/text/border in all three themes")
    void plainButtonsAreThemed() {
        onFxThread(() -> {
            Button dialogButton = new Button("Cancel");
            ToggleButton toggle = new ToggleButton("Tail");
            VBox root = new VBox(6, dialogButton, toggle, new Label("x"));
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();

            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                useTheme(theme);
                root.applyCss();
                root.layout();
                WaitForAsyncUtils.waitForFxEvents();
                String mode = theme.name();

                // One solid fill means our rule replaced Modena's layered gradients.
                Background background = dialogButton.getBackground();
                assertNotNull(background, mode + ": button background must be themed");
                assertEquals(1, background.getFills().size(),
                        mode + ": button must not keep the default Modena gradient");
                Color surface = toColor(background.getFills().get(0).getFill());
                assertNotNull(surface, mode + ": button background must resolve to a colour");

                Color text = (Color) dialogButton.getTextFill();
                assertNotNull(text, mode + ": button text fill must be themed");
                assertTrue(contrast(text, surface) >= 4.5,
                        mode + ": button text must be readable on its surface");
                assertTrue(text.getOpacity() > 0.9, mode + ": button text must be opaque");

                // Toggle buttons share the same base look when not selected.
                Background toggleBg = toggle.getBackground();
                assertNotNull(toggleBg, mode + ": toggle background must be themed");
                assertEquals(1, toggleBg.getFills().size(),
                        mode + ": toggle must not keep the default Modena gradient");
            }
        });
    }

    @Test
    @DisplayName("primary buttons keep the brand accent with readable text")
    void primaryButtonsUseBrandTokens() {
        onFxThread(() -> {
            Button primary = new Button("Open");
            primary.getStyleClass().add("btn-primary");
            VBox root = new VBox(primary);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();

            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                useTheme(theme);
                root.applyCss();
                root.layout();
                WaitForAsyncUtils.waitForFxEvents();

                Color background = toColor(primary.getBackground().getFills().get(0).getFill());
                Color text = (Color) primary.getTextFill();
                assertNotNull(background, theme + ": primary background must resolve");
                assertNotNull(text, theme + ": primary text must resolve");
                assertTrue(contrast(text, background) >= 4.5,
                        theme + ": primary button text must be readable on the accent");

                // Not the plain surface look (primary is a saturated dark/brand tone).
                assertTrue(luminance(background) < 0.6,
                        theme + ": primary button must not use the plain light surface");
            }
        });
    }

    private void useTheme(AppTheme.Theme theme) {
        AppTheme.setTheme(AppTheme.Theme.DARK);
        AppTheme.setTheme(theme);
    }

    private static Color toColor(javafx.scene.paint.Paint paint) {
        return paint instanceof Color c ? c : null;
    }

    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
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
