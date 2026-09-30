package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.image.Image;
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
 * Find-in-Files focus regression: when a button or the Context dropdown receives
 * focus, the rendered text must stay readable (>= 4.5:1) in every theme. The
 * default action button ("Open &amp; Jump") used to drop to 3.39:1 in Dark because
 * Modena painted its own focus layers under the light text.
 */
@ExtendWith(ApplicationExtension.class)
class FindInFilesFocusTest {

    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");
    private static final double MIN_CONTRAST = 4.5;

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void resetTheme() {
        Platform.runLater(() -> AppTheme.setDark(false));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("focused Search, Close, default Open & Jump and Context dropdown stay readable")
    void focusedControlsStayReadable() throws Exception {
        for (AppTheme.Theme theme : AppTheme.Theme.values()) {
            Parent root = loadDialog(theme);

            assertFocusedContrast(root, "#searchButton", theme, "Search");
            assertFocusedContrast(root, "#closeButton", theme, "Close");
            assertFocusedContrast(root, "#contextSelector", theme, "Context dropdown");

            // The default action is disabled until a result is picked; enable it to
            // measure the focused state the user actually interacts with.
            Button open = (Button) root.lookup("#openButton");
            assertNotNull(open, theme + ": Open & Jump must exist");
            Platform.runLater(() -> open.setDisable(false));
            WaitForAsyncUtils.waitForFxEvents();
            assertFocusedContrast(root, "#openButton", theme, "Open & Jump (default)");
        }
    }

    private Parent loadDialog(AppTheme.Theme theme) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                if (stage.getScene() != null) {
                    stage.hide();
                }
                AppTheme.setTheme(AppTheme.Theme.DARK);
                AppTheme.setTheme(theme);
                Parent root = new FXMLLoader(getClass().getResource("/fxml/RemoteLogSearchDialog.fxml")).load();
                rootRef.set(root);
                stage.setScene(AppTheme.scene(root));
                stage.setWidth(960);
                stage.setHeight(640);
                stage.show();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        Parent root = rootRef.get();
        root.applyCss();
        root.layout();
        WaitForAsyncUtils.waitForFxEvents();
        return root;
    }

    private void assertFocusedContrast(Parent root, String selector, AppTheme.Theme theme, String label)
            throws Exception {
        javafx.scene.Node node = root.lookup(selector);
        assertNotNull(node, theme + ": " + label + " must exist");
        Platform.runLater(() -> node.pseudoClassStateChanged(FOCUSED, true));
        WaitForAsyncUtils.waitForFxEvents();
        root.applyCss();
        root.layout();
        WaitForAsyncUtils.waitForFxEvents();

        final javafx.scene.Node snapshotNode = root.lookup(selector);
        Image image = WaitForAsyncUtils.asyncFx(() -> snapshotNode.snapshot(null, null)).get();
        double contrast = renderedContrast(image);
        assertTrue(contrast >= MIN_CONTRAST,
                String.format("%s focused [%s]: text contrast too low: %.2f:1 (needs >= %.1f)",
                        label, theme, contrast, MIN_CONTRAST));

        Platform.runLater(() -> node.pseudoClassStateChanged(FOCUSED, false));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Contrast between the dominant background colour and the most distinct text colour. */
    private static double renderedContrast(Image image) {
        int[] buckets = new int[64];
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = image.getPixelReader().getColor(x, y);
                if (c.getOpacity() < 0.5) {
                    continue;
                }
                buckets[luminanceBucket(c)]++;
            }
        }
        int bgBucket = 0;
        for (int i = 1; i < buckets.length; i++) {
            if (buckets[i] > buckets[bgBucket]) {
                bgBucket = i;
            }
        }
        int fgBucket = -1;
        double bestDistance = 0;
        for (int i = 0; i < buckets.length; i++) {
            if (i == bgBucket || buckets[i] < 2) {
                continue;
            }
            double distance = Math.abs(i - bgBucket);
            if (distance > bestDistance) {
                bestDistance = distance;
                fgBucket = i;
            }
        }
        if (fgBucket < 0) {
            return -1;
        }
        double bgL = (bgBucket + 0.5) / 64.0;
        double fgL = (fgBucket + 0.5) / 64.0;
        return (Math.max(bgL, fgL) + 0.05) / (Math.min(bgL, fgL) + 0.05);
    }

    private static int luminanceBucket(Color c) {
        double l = 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
        return Math.max(0, Math.min(63, (int) (l * 64)));
    }

    private static double channel(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
