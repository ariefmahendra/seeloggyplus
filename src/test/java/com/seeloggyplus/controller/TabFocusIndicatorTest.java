package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.Border;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: no visible focus ring/border must appear on the tab header when
 * the TabPane receives focus (e.g. after clicking a log line in the canvas).
 */
@ExtendWith(ApplicationExtension.class)
class TabFocusIndicatorTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @Test
    void tabHeaderHasNoVisibleFocusBorder() {
        onFxThread(() -> {
            TabPane pane = new TabPane(new Tab("One"), new Tab("Two"));
            StackPane root = new StackPane(pane);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();

            pane.getSelectionModel().select(0);
            pane.requestFocus();
            root.applyCss();
            root.layout();
            pane.applyCss();

            for (Node node : pane.lookupAll(".focus-indicator")) {
                if (node instanceof Region region && region.getBorder() != null) {
                    for (BorderStroke stroke : region.getBorder().getStrokes()) {
                        assertTrue(isInvisible(stroke.getTopStroke()), "top focus border must be invisible");
                        assertTrue(isInvisible(stroke.getRightStroke()), "right focus border must be invisible");
                        assertTrue(isInvisible(stroke.getBottomStroke()), "bottom focus border must be invisible");
                        assertTrue(isInvisible(stroke.getLeftStroke()), "left focus border must be invisible");
                    }
                }
            }
        });
    }

    private static boolean isInvisible(Paint paint) {
        return paint == null || paint.equals(Color.TRANSPARENT)
                || (paint instanceof Color color && color.getOpacity() == 0);
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
