package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
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
 * Icons must be contrasty against the active theme in every interactive state
 * (default, hover, pressed, focused, selected). WCAG non-text minimum is 3:1.
 */
@ExtendWith(ApplicationExtension.class)
class IconContrastTest {

    private static final double MIN_ICON_CONTRAST = 3.0;
    private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");
    private static final PseudoClass PRESSED = PseudoClass.getPseudoClass("pressed");
    private static final PseudoClass ARMED = PseudoClass.getPseudoClass("armed");
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

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
    void toolbarIconContrastsInAllStates() {
        onFxThread(() -> {
            ToggleButton toolbarToggle = new ToggleButton();
            FontAwesomeIconView toggleIcon = new FontAwesomeIconView(FontAwesomeIcon.EYE);
            toolbarToggle.setGraphic(toggleIcon);

            Button toolbarButton = new Button();
            FontAwesomeIconView buttonIcon = new FontAwesomeIconView(FontAwesomeIcon.SEARCH);
            toolbarButton.setGraphic(buttonIcon);

            ToolBar toolbar = new ToolBar(toolbarToggle, toolbarButton);
            toolbar.getStyleClass().add("app-toolbar");

            VBox root = new VBox(toolbar);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.setWidth(700);
            stage.setHeight(220);
            stage.show();

            for (boolean dark : new boolean[] {false, true}) {
                AppTheme.setDark(dark);
                String mode = dark ? "dark" : "light";

                reset(toolbarToggle);
                assertContrast(toolbarButton, buttonIcon, "toolbar button default " + mode);
                assertContrast(toolbarToggle, toggleIcon, "toolbar toggle default " + mode);

                reset(toolbarButton);
                set(toolbarButton, HOVER, true);
                assertContrast(toolbarButton, buttonIcon, "toolbar button hover " + mode);

                set(toolbarButton, HOVER, false);
                set(toolbarButton, PRESSED, true);
                assertContrast(toolbarButton, buttonIcon, "toolbar button pressed " + mode);

                set(toolbarButton, PRESSED, false);
                set(toolbarButton, FOCUSED, true);
                assertContrast(toolbarButton, buttonIcon, "toolbar button focused " + mode);

                reset(toolbarToggle);
                toolbarToggle.setSelected(true);
                assertContrast(toolbarToggle, toggleIcon, "toolbar toggle selected " + mode);

                reset(toolbarToggle);
                toolbarToggle.setSelected(true);
                set(toolbarToggle, HOVER, true);
                assertContrast(toolbarToggle, toggleIcon, "toolbar toggle selected+hover " + mode);
                toolbarToggle.setSelected(false);
            }

            AppTheme.setDark(false);
        });
    }

    @Test
    void surfaceIconContrastsInAllStates() {
        onFxThread(() -> {
            ToggleButton surfaceToggle = new ToggleButton();
            FontAwesomeIconView toggleIcon = new FontAwesomeIconView(FontAwesomeIcon.INDENT);
            surfaceToggle.setGraphic(toggleIcon);

            FontAwesomeIconView emptyIcon = new FontAwesomeIconView(FontAwesomeIcon.FILE_TEXT_ALT);
            emptyIcon.getStyleClass().add("empty-state-icon");

            VBox root = new VBox(surfaceToggle, emptyIcon);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();

            for (boolean dark : new boolean[] {false, true}) {
                AppTheme.setDark(dark);
                String mode = dark ? "dark" : "light";

                reset(surfaceToggle);
                assertContrast(surfaceToggle, toggleIcon, "surface toggle default " + mode);

                set(surfaceToggle, HOVER, true);
                assertContrast(surfaceToggle, toggleIcon, "surface toggle hover " + mode);
                set(surfaceToggle, HOVER, false);

                surfaceToggle.setSelected(true);
                assertContrast(surfaceToggle, toggleIcon, "surface toggle selected " + mode);
                surfaceToggle.setSelected(false);

                assertContrast(null, emptyIcon, "empty-state icon " + mode);
            }

            AppTheme.setDark(false);
        });
    }

    @Test
    void coloredFileIconsContrastInBothThemes() {
        onFxThread(() -> {
            FontAwesomeIconView folder = styledIcon(FontAwesomeIcon.FOLDER, "file-icon-folder");
            FontAwesomeIconView log = styledIcon(FontAwesomeIcon.FILE_TEXT_ALT, "file-icon-log");
            FontAwesomeIconView file = styledIcon(FontAwesomeIcon.FILE_ALT, "file-icon-file");
            FontAwesomeIconView star = styledIcon(FontAwesomeIcon.STAR, "favorite-star");

            VBox root = new VBox(4, folder, log, file, star);
            root.getStyleClass().add("empty-state-pane");
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();

            for (boolean dark : new boolean[] {false, true}) {
                AppTheme.setDark(dark);
                root.applyCss();
                root.layout();
                String mode = dark ? "dark" : "light";
                assertContrast(null, folder, "file folder icon " + mode);
                assertContrast(null, log, "file log icon " + mode);
                assertContrast(null, file, "file icon " + mode);
                assertContrast(null, star, "favorite star icon " + mode);
            }

            AppTheme.setDark(false);
        });
    }

    private static FontAwesomeIconView styledIcon(FontAwesomeIcon glyph, String styleClass) {
        FontAwesomeIconView icon = new FontAwesomeIconView(glyph);
        icon.getStyleClass().add(styleClass);
        return icon;
    }

    private void assertContrast(ButtonBase control, FontAwesomeIconView icon, String label) {
        if (control != null) {
            control.applyCss();
        }
        Scene scene = icon.getScene();
        if (scene != null) {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        }
        WaitForAsyncUtils.waitForFxEvents();

        Color fg = icon.getFill() instanceof Color c ? c : null;
        assertNotNull(fg, label + ": icon fill must be a colour");

        Color bg = effectiveBackground(icon);
        assertNotNull(bg, label + ": background must be resolvable");

        double ratio = contrast(fg, bg);
        assertTrue(ratio >= MIN_ICON_CONTRAST,
                String.format("%s: icon contrast too low: %.2f:1 (fg=%s bg=%s)", label, ratio, fg, bg));
    }

    private static Color effectiveBackground(Node node) {
        Node cur = node.getParent();
        while (cur != null) {
            if (cur instanceof Region region) {
                Background bg = region.getBackground();
                if (bg != null) {
                    var fills = bg.getFills();
                    for (int i = fills.size() - 1; i >= 0; i--) {
                        Color c = toColor(fills.get(i).getFill());
                        if (c != null && c.getOpacity() > 0.5) {
                            return c;
                        }
                    }
                }
            }
            cur = cur.getParent();
        }
        return null;
    }

    private static Color toColor(Paint paint) {
        if (paint instanceof Color c) {
            return c;
        }
        if (paint instanceof LinearGradient gradient && !gradient.getStops().isEmpty()) {
            for (Stop stop : gradient.getStops()) {
                if (stop.getColor().getOpacity() > 0.5) {
                    return stop.getColor();
                }
            }
        }
        return null;
    }

    private static void reset(ButtonBase control) {
        control.pseudoClassStateChanged(HOVER, false);
        control.pseudoClassStateChanged(PRESSED, false);
        control.pseudoClassStateChanged(ARMED, false);
        control.pseudoClassStateChanged(FOCUSED, false);
        if (control instanceof ToggleButton toggle) {
            toggle.setSelected(false);
        }
    }

    private static void set(ButtonBase control, PseudoClass state, boolean value) {
        control.pseudoClassStateChanged(state, value);
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
