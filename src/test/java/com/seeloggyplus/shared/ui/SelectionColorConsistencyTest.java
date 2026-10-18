package com.seeloggyplus.shared.ui;

import com.seeloggyplus.shared.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.features.search.presentation.SearchResultPanel;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.Parent;

@ExtendWith(ApplicationExtension.class)
class SelectionColorConsistencyTest {

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
    void canvasAndSearchPanelUseSharedSelectionColors() throws Exception {
        CanvasLogViewer canvas = new CanvasLogViewer();
        SearchResultPanel panel = new SearchResultPanel();

        canvas.setDarkMode(false);
        panel.setDarkMode(false);
        assertEquals(SelectionColors.LIGHT_BACKGROUND, privateSelection(canvas));
        assertEquals(SelectionColors.LIGHT_BACKGROUND, privateSelection(panel));

        canvas.setDarkMode(true);
        panel.setDarkMode(true);
        assertEquals(SelectionColors.DARK_BACKGROUND, privateSelection(canvas));
        assertEquals(SelectionColors.DARK_BACKGROUND, privateSelection(panel));

        assertNotEquals(SelectionColors.LIGHT_BACKGROUND, SelectionColors.DARK_BACKGROUND);
    }

    @Test
    void nullAndBooleanOverloadsResolveSupportedThemePalettes() {
        assertEquals(SelectionColors.LIGHT_BACKGROUND, SelectionColors.background((AppTheme.Theme)null));
        assertEquals(SelectionColors.background(AppTheme.Theme.LIGHT), SelectionColors.background(false));
        assertEquals(SelectionColors.background(AppTheme.Theme.DARK), SelectionColors.background(true));
    }

    @Test
    void detailPanelSelectionMatchesSharedColor() {
        AtomicReference<StyleClassedTextArea> reference=new AtomicReference<>();
        onFxThread(() -> {
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
            StyleClassedTextArea area = themedArea();
            reference.set(area);
            area.replaceText("select me please");
            area.selectRange(0, 6);
            area.requestFocus();
        });
        onFxThread(() -> {
            StyleClassedTextArea area=reference.get();
            assertTrue(area.isFocused(),"active selection requires keyboard focus");
            rootLayout(area);

            Shape selection = findSelectionPath(area);
            assertNotNull(selection, "detail panel must render a .selection path");
            assertEquals(SelectionColors.LIGHT_BACKGROUND, selection.getFill(),
                    "light detail selection background must match the shared colour");

            AppTheme.setDark(true);
            area.applyCss();
            Shape darkSelection = findSelectionPath(area);
            assertNotNull(darkSelection);
            assertEquals(SelectionColors.DARK_BACKGROUND, darkSelection.getFill(),
                    "dark detail selection background must match the shared colour");

            Parent root=area.getScene().getRoot();
            root.setFocusTraversable(true);
            root.requestFocus();
            root.applyCss();
            assertFalse(area.isFocused());
            assertEquals(Color.web("#33353b"),findSelectionPath(area).getFill(),
                    "inactive dark detail selection must use the inactive Islands token");

            AppTheme.setDark(false);
        });
    }

    private static Shape findSelectionPath(Parent parent) {
        for (Node node : parent.lookupAll(".selection")) {
            if (node instanceof Shape shape) {
                return shape;
            }
        }
        return null;
    }

    private StyleClassedTextArea themedArea() {
        StyleClassedTextArea area = new StyleClassedTextArea();
        area.setEditable(false);
        area.getStyleClass().add("detail-area");
        area.getStylesheets().add(getClass().getResource("/style/richtext.css").toExternalForm());
        StackPane root = new StackPane(area);
        Scene scene = AppTheme.scene(root);
        stage.setScene(scene);
        stage.show();
        root.applyCss();
        root.layout();
        return area;
    }

    private void rootLayout(Node node) {
        if (node.getScene() != null) {
            Parent root = node.getScene().getRoot();
            root.applyCss();
            root.layout();
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Color privateSelection(Object viewer) throws Exception {
        Field f = viewer.getClass().getDeclaredField("selectionColor");
        f.setAccessible(true);
        return (Color) f.get(viewer);
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
