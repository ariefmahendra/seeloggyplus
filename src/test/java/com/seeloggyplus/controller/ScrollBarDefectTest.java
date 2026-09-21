package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
 * Defect fixes: scrollbar step buttons (up/down) must be clickable, and the
 * Find in Files scrollbars must be easy to grab.
 */
@ExtendWith(ApplicationExtension.class)
class ScrollBarDefectTest {

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
    void stepButtonsAreVisibleAndStep() {
        onFxThread(() -> {
            ScrollBar bar = new ScrollBar();
            bar.setOrientation(Orientation.VERTICAL);
            bar.setMin(0);
            bar.setMax(100);
            bar.setVisibleAmount(5);
            bar.setValue(50);
            bar.setUnitIncrement(1);

            StackPane root = new StackPane(bar);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.setWidth(120);
            stage.setHeight(260);
            stage.show();
            root.applyCss();
            root.layout();

            Region increment = (Region) bar.lookup(".increment-button");
            Region decrement = (Region) bar.lookup(".decrement-button");
            assertNotNull(increment, "increment (down/up) button must exist");
            assertNotNull(decrement, "decrement button must exist");
            assertTrue(increment.getWidth() > 0 && increment.getHeight() > 0,
                    "increment button must be visible/grabbable");
            assertTrue(decrement.getWidth() > 0 && decrement.getHeight() > 0,
                    "decrement button must be visible/grabbable");

            double before = bar.getValue();
            bar.increment();
            assertEquals(before + bar.getUnitIncrement(), bar.getValue(), 1e-6,
                    "increment must move one unit (one row)");
            bar.decrement();
            assertEquals(before, bar.getValue(), 1e-6,
                    "decrement must move back one unit");
        });
    }

    @Test
    void findInFilesScrollbarsAreWideEnough() {
        onFxThread(() -> {
            TableView<String> table = new TableView<>();
            for (int i = 0; i < 60; i++) {
                table.getItems().add("row " + i);
            }
            VBox root = new VBox(table);
            root.getStyleClass().add("remote-log-search-dialog");
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.setWidth(600);
            stage.setHeight(360);
            stage.show();
            root.applyCss();
            root.layout();

            ScrollBar vertical = findVerticalScrollBar(table);
            assertNotNull(vertical, "result table must show a vertical scrollbar");
            assertTrue(vertical.getWidth() >= 16,
                    "Find in Files vertical scrollbar must be wide enough to grab, was " + vertical.getWidth());

            Node thumb = vertical.lookup(".thumb");
            assertNotNull(thumb, "scrollbar thumb must exist");
            Region thumbRegion = (Region) thumb;
            assertTrue(thumbRegion.minHeight(-1) >= 40,
                    "scrollbar thumb min height must be at least 40px, was " + thumbRegion.minHeight(-1));
        });
    }

    private static ScrollBar findVerticalScrollBar(Region root) {
        for (Node node : root.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                return bar;
            }
        }
        return null;
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
