package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
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
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;

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

    @Test
    @DisplayName("toolbar/status buttons keep readable text on hover, pressed and selected")
    void toolbarInteractiveStatesKeepReadableText() {
        PseudoClass hover = PseudoClass.getPseudoClass("hover");
        PseudoClass pressed = PseudoClass.getPseudoClass("pressed");
        onFxThread(() -> {
            Button toolbarButton = new Button("Find in Files");
            ToggleButton toolbarToggle = new ToggleButton("Tail");
            ToolBar toolbar = new ToolBar(toolbarButton, toolbarToggle);
            toolbar.getStyleClass().add("app-toolbar");
            Button statusButton = new Button("Status");
            VBox status = new VBox(statusButton);
            status.getStyleClass().add("status-bar");
            VBox root = new VBox(toolbar, status);

            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();

            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                useTheme(theme);
                root.applyCss();
                root.layout();
                WaitForAsyncUtils.waitForFxEvents();
                String mode = theme.name();

                toolbarButton.pseudoClassStateChanged(hover, true);
                root.applyCss();
                root.layout();
                assertReadable(toolbarButton, "toolbar hover " + mode);

                toolbarButton.pseudoClassStateChanged(hover, false);
                toolbarButton.pseudoClassStateChanged(pressed, true);
                root.applyCss();
                root.layout();
                assertReadable(toolbarButton, "toolbar pressed " + mode);
                toolbarButton.pseudoClassStateChanged(pressed, false);

                toolbarToggle.setSelected(true);
                root.applyCss();
                root.layout();
                assertReadable(toolbarToggle, "toolbar toggle selected " + mode);

                toolbarToggle.pseudoClassStateChanged(hover, true);
                root.applyCss();
                root.layout();
                assertReadable(toolbarToggle, "toolbar toggle selected+hover " + mode);
                toolbarToggle.pseudoClassStateChanged(hover, false);
                toolbarToggle.setSelected(false);

                statusButton.pseudoClassStateChanged(hover, true);
                root.applyCss();
                root.layout();
                assertReadable(statusButton, "status hover " + mode);
                statusButton.pseudoClassStateChanged(hover, false);
            }
        });
    }

    @Test
    @DisplayName("focused buttons and dropdowns keep readable text in every theme")
    void focusedControlsKeepReadableText() {
        onFxThread(() -> {
            PseudoClass focused = PseudoClass.getPseudoClass("focused");
            Button plain = new Button("Close");
            Button defaultButton = new Button("Open & Jump");
            defaultButton.setDefaultButton(true);
            Button primary = new Button("Search");
            primary.getStyleClass().add("btn-primary");
            ToggleButton toggle = new ToggleButton("Regex");
            ComboBox<String> combo = new ComboBox<>();
            combo.getItems().addAll("1", "3", "5", "10");
            combo.getSelectionModel().selectFirst();
            VBox root = new VBox(6, plain, defaultButton, primary, toggle, combo);

            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            WaitForAsyncUtils.waitForFxEvents();

            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                useTheme(theme);
                for (Control control : new Control[]{
                        plain, defaultButton, primary, toggle, combo}) {
                    control.pseudoClassStateChanged(focused, true);
                }
                root.applyCss();
                root.layout();
                WaitForAsyncUtils.waitForFxEvents();
                String mode = theme.name();

                assertReadable(plain, "plain focused " + mode);
                assertReadable(defaultButton, "default focused " + mode);
                assertReadable(primary, "primary focused " + mode);
                assertReadable(toggle, "toggle focused " + mode);
                assertReadableCombo(combo, "dropdown focused " + mode);

                for (Control control : new Control[]{
                        plain, defaultButton, primary, toggle, combo}) {
                    control.pseudoClassStateChanged(focused, false);
                }
            }
        });
    }

    private static void assertReadable(Labeled control, String label) {
        assertNotNull(control.getBackground(), label + ": background must resolve");
        Color background = toColor(control.getBackground().getFills().get(0).getFill());
        Color text = control.getTextFill() instanceof Color c ? c : null;
        assertNotNull(background, label + ": background must be a colour");
        assertNotNull(text, label + ": text fill must be a colour");
        assertTrue(contrast(text, background) >= 4.5,
                String.format("%s: text contrast too low: %.2f:1 (fg=%s bg=%s)",
                        label, contrast(text, background), text, background));
    }

    private static void assertReadableCombo(ComboBox<?> combo, String label) {
        ListCell<?> cell = (ListCell<?>) combo.lookup(".list-cell");
        assertNotNull(cell, label + ": dropdown value cell must exist");
        assertNotNull(combo.getBackground(), label + ": background must resolve");
        Color background = toColor(combo.getBackground().getFills().get(0).getFill());
        Color text = cell.getTextFill() instanceof Color c ? c : null;
        assertNotNull(background, label + ": background must be a colour");
        assertNotNull(text, label + ": text fill must be a colour");
        assertTrue(contrast(text, background) >= 4.5,
                String.format("%s: text contrast too low: %.2f:1 (fg=%s bg=%s)",
                        label, contrast(text, background), text, background));
    }

    @Test
    @DisplayName("dark-filled controls (selected toggle, primary/empty-state button, memory track) draw no outline")
    void darkFilledControlsHaveNoOutline() {
        onFxThread(() -> {
            Button primary = new Button("Open File...");
            primary.getStyleClass().add("empty-state-button");
            ToggleButton selectedToggle = new ToggleButton();
            selectedToggle.setSelected(true);
            ProgressBar memory = new ProgressBar(0.5);
            memory.getStyleClass().add("status-memory-bar");
            HBox statusBar = new HBox(memory);
            statusBar.getStyleClass().add("status-bar");
            VBox root = new VBox(6, primary, selectedToggle, statusBar);

            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            WaitForAsyncUtils.waitForFxEvents();

            assertNoVisibleOutline(primary, "empty-state button");
            assertNoVisibleOutline(selectedToggle, "selected toggle");
            assertTrue(luminance(toColor(primary.getBackground().getFills().get(0).getFill())) < 0.3,
                    "empty-state button must be a dark, filled button");

            Node track = memory.lookup(".track");
            assertNotNull(track, "memory bar track must exist");
            assertNoVisibleOutline((Region) track, "memory bar track");
            Color trackFill = toColor(((Region) track).getBackground().getFills().get(0).getFill());
            assertNotNull(trackFill);
            assertTrue(luminance(trackFill) < 0.4, "memory bar track must stay a flat dark tone");
        });
    }

    private static void assertNoVisibleOutline(Region region, String label) {
        Border border = region.getBorder();
        if (border == null) {
            return;
        }
        for (BorderStroke stroke : border.getStrokes()) {
            for (Paint paint : new Paint[]{
                    stroke.getTopStroke(), stroke.getBottomStroke(),
                    stroke.getLeftStroke(), stroke.getRightStroke()}) {
                assertTrue(isTransparentOrNull(paint),
                        label + " must not draw a visible outline, found " + paint);
            }
        }
    }

    private static boolean isTransparentOrNull(Paint paint) {
        if (paint == null) {
            return true;
        }
        return paint instanceof Color c && c.getOpacity() <= 0.05;
    }

    private void useTheme(AppTheme.Theme theme) {
        AppTheme.setTheme(AppTheme.Theme.DARK);
        AppTheme.setTheme(theme);
    }

    private static Color toColor(Paint paint) {
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
