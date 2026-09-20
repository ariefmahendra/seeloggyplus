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
 * Every label (including labels that accompany input fields) must have readable
 * contrast against its effective background in BOTH light and dark mode.
 */
@ExtendWith(ApplicationExtension.class)
class LabelContrastTest {

    private static final double MIN_TEXT_CONTRAST = 4.5;

    private static final String[] FXMLS = {
            "/fxml/MainView.fxml",
            "/fxml/PreferencesDialog.fxml",
            "/fxml/ServerEditDialog.fxml",
            "/fxml/UnifiedFileManagerDialog.fxml",
            "/fxml/RemoteLogSearchDialog.fxml",
            "/fxml/LogPreviewDialog.fxml",
            "/fxml/UpdateDialog.fxml"
    };

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
    void allLabelsContrastInBothThemes() {
        onFxThread(() -> {
            for (String fxml : FXMLS) {
                assertLabelsContrast(fxml, false);
                assertLabelsContrast(fxml, true);
            }
            AppTheme.setDark(false);
        });
    }

    private void assertLabelsContrast(String fxml, boolean dark) {
        try {
            AppTheme.setDark(dark);
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();

            String mode = dark ? "dark" : "light";
            for (Node node : root.lookupAll(".label")) {
                if (!(node instanceof Label label)) {
                    continue;
                }
                if (label.getText() == null || label.getText().isBlank()) {
                    continue;
                }
                Color fg = label.getTextFill() instanceof Color c ? c : null;
                if (fg == null) {
                    continue;
                }
                Color bg = effectiveBackground(label, dark);
                double ratio = contrast(fg, bg);
                assertTrue(ratio >= MIN_TEXT_CONTRAST,
                        String.format("%s [%s] label '%s' contrast too low: %.2f:1 (fg=%s bg=%s, classes=%s)",
                                fxml, mode, label.getText(), ratio, fg, bg, label.getStyleClass()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Color effectiveBackground(Node node, boolean dark) {
        Node cur = node.getParent();
        while (cur != null) {
            if (cur instanceof Region region) {
                Background background = region.getBackground();
                if (background != null) {
                    var fills = background.getFills();
                    // Last opaque fill is the content background; earlier layers are borders.
                    for (int i = fills.size() - 1; i >= 0; i--) {
                        Color color = toColor(fills.get(i).getFill());
                        if (color != null && color.getOpacity() > 0.5) {
                            return color;
                        }
                    }
                }
            }
            cur = cur.getParent();
        }
        return dark ? Color.web("#1e2226") : Color.WHITE;
    }

    private static Color toColor(Paint paint) {
        if (paint instanceof Color color) {
            return color;
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
