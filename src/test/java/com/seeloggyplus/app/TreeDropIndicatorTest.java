package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
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
 * The drag-and-drop target highlight in the location tree must not change the
 * cell size (no border that makes rows jump while dragging) yet must stay
 * clearly visible.
 */
@ExtendWith(ApplicationExtension.class)
class TreeDropIndicatorTest {

    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");

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
    @DisplayName("drop-target highlight keeps the exact same cell height")
    void highlightDoesNotResizeTheCell() {
        onFxThread(() -> {
            AppTheme.setTheme(AppTheme.Theme.GRAPHITE);
            TreeView<String> tree = new TreeView<>();
            TreeItem<String> root = new TreeItem<>("root");
            TreeItem<String> group = new TreeItem<>("Production");
            group.getChildren().add(new TreeItem<>("server-1"));
            root.getChildren().add(group);
            root.setExpanded(true);
            group.setExpanded(true);
            tree.setRoot(root);
            tree.setShowRoot(false);

            VBox box = new VBox(tree);
            Scene scene = AppTheme.scene(box);
            stage.setScene(scene);
            stage.setWidth(360);
            stage.setHeight(260);
            stage.show();
            box.applyCss();
            box.layout();
            WaitForAsyncUtils.waitForFxEvents();

            TreeCell<?> cell = (TreeCell<?>) tree.lookup(".tree-cell");
            assertNotNull(cell, "the tree must have rendered cells");

            double heightBefore = cell.getHeight();
            double prefBefore = cell.prefHeight(-1);
            Background backgroundBefore = cell.getBackground();

            cell.pseudoClassStateChanged(DROP_TARGET, true);
            box.applyCss();
            box.layout();
            WaitForAsyncUtils.waitForFxEvents();

            assertEquals(heightBefore, cell.getHeight(), 0.01,
                    "the drop-target highlight must not resize the cell");
            assertEquals(prefBefore, cell.prefHeight(-1), 0.01,
                    "the drop-target highlight must not change the preferred height");

            Background highlight = cell.getBackground();
            assertNotNull(highlight);
            assertNotSame(backgroundBefore, highlight);
            assertEquals(2, highlight.getFills().size(),
                    "the highlight should draw an accent bar plus a soft row fill");

            cell.pseudoClassStateChanged(DROP_TARGET, false);
            box.applyCss();
            box.layout();
            assertEquals(heightBefore, cell.getHeight(), 0.01,
                    "clearing the highlight must not resize the cell either");
        });
    }

    @Test
    void reorderAndMembershipIndicatorsAreDistinctAndDoNotResizeRowsInAnyTheme() {
        onFxThread(() -> {
            TreeView<String> tree = new TreeView<>();
            TreeItem<String> root = new TreeItem<>("root");
            root.getChildren().add(new TreeItem<>("server-1"));
            root.setExpanded(true);
            tree.setRoot(root);
            tree.setShowRoot(false);
            VBox box = new VBox(tree);
            stage.setScene(AppTheme.scene(box));
            stage.setWidth(360);
            stage.setHeight(260);
            stage.show();
            box.applyCss();
            box.layout();
            TreeCell<?> cell = (TreeCell<?>) tree.lookup(".tree-cell");
            assertNotNull(cell);
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                box.applyCss();
                box.layout();
                double height = cell.getHeight();
                double preferred = cell.prefHeight(-1);
                for (String state : new String[]{"drop-before", "drop-after", "drop-target", "drop-ungroup"}) {
                    PseudoClass pseudo = PseudoClass.getPseudoClass(state);
                    cell.pseudoClassStateChanged(pseudo, true);
                    box.applyCss();
                    box.layout();
                    assertEquals(height, cell.getHeight(), 0.01, "Drag feedback must not shift row positions");
                    assertEquals(preferred, cell.prefHeight(-1), 0.01);
                    var fills = cell.getBackground().getFills();
                    assertEquals(2, fills.size());
                    if (state.equals("drop-before")) assertTrue(fills.get(1).getInsets().getTop() > 0);
                    if (state.equals("drop-after")) assertTrue(fills.get(1).getInsets().getBottom() > 0);
                    Color line = (Color) fills.get(0).getFill();
                    Color surface = (Color) fills.get(1).getFill();
                    assertTrue(contrast(line, surface) >= 3, "Drop indicators must remain visible in " + theme);
                    cell.pseudoClassStateChanged(pseudo, false);
                }
            }
        });
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
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
