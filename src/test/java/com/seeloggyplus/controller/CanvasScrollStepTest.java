package com.seeloggyplus.controller;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Defect fix: the log viewer must support stepping one row at a time via the
 * scrollbar up/down buttons.
 */
@ExtendWith(ApplicationExtension.class)
class CanvasScrollStepTest {

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
    void verticalScrollBarButtonsStepOneRow() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.setPrefSize(420, 220);
            List<LogEntry> lines = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                lines.add(new LogEntry(i + 1, "line " + (i + 1) + " content"));
            }
            viewer.setTailBuffer(lines);

            StackPane root = new StackPane(viewer);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.setWidth(520);
            stage.setHeight(320);
            stage.show();
            root.applyCss();
            root.layout();
            viewer.applyCss();
            viewer.layout();
            WaitForAsyncUtils.waitForFxEvents();

            ScrollBar bar = viewer.getVScrollBar();
            assertNotNull(bar, "log viewer must expose its vertical scrollbar");
            // Start from the top (the viewer auto-scrolls to bottom on load).
            viewer.setFollowTail(false);
            bar.setValue(0);
            bar.setUnitIncrement(1);
            assertTrue(bar.getMax() > 5, "scrollbar must have room to step, max=" + bar.getMax());

            Region increment = (Region) bar.lookup(".increment-button");
            Region decrement = (Region) bar.lookup(".decrement-button");
            assertNotNull(increment, "increment (down) button must exist");
            assertNotNull(decrement, "decrement (up) button must exist");
            assertTrue(increment.getWidth() > 0 && increment.getHeight() > 0,
                    "increment button must be visible/clickable, size=" + increment.getWidth() + "x" + increment.getHeight());
            assertTrue(decrement.getWidth() > 0 && decrement.getHeight() > 0,
                    "decrement button must be visible/clickable, size=" + decrement.getWidth() + "x" + decrement.getHeight());

            double start = bar.getValue();
            bar.increment();
            assertEquals(start + 1, bar.getValue(), 1e-6,
                    "clicking down must move exactly one row");
            bar.increment();
            assertEquals(start + 2, bar.getValue(), 1e-6,
                    "clicking down again must move another row");
            bar.decrement();
            assertEquals(start + 1, bar.getValue(), 1e-6,
                    "clicking up must move back one row");
        });
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
