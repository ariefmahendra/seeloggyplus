package com.seeloggyplus.ui;

import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.ui.search.SearchResultPanel;
import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text selection highlight must be consistent across the canvas log, the search
 * result panel, the RichTextFX detail panel and JavaFX text controls, in both
 * light and dark mode.
 */
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
    void cssSelectionColorsMatchJavaConstants() throws Exception {
        String theme = normalize(readResource("/style/theme.css"));
        String themeDark = normalize(readResource("/style/theme-dark.css"));
        String richtext = normalize(readResource("/style/richtext.css"));

        String light = rgba(SelectionColors.LIGHT_BACKGROUND);
        String dark = rgba(SelectionColors.DARK_BACKGROUND);

        assertTrue(theme.contains(light), "theme.css must use the shared light selection colour: " + light);
        assertTrue(richtext.contains(light), "richtext.css must use the shared light selection colour: " + light);
        assertTrue(themeDark.contains(dark), "theme-dark.css must use the shared dark selection colour: " + dark);
    }

    @Test
    void detailPanelSelectionMatchesSharedColor() {
        onFxThread(() -> {
            StyleClassedTextArea area = themedArea();
            area.replaceText("select me please");
            area.selectRange(0, 6);
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

            AppTheme.setDark(false);
        });
    }

    private static Shape findSelectionPath(javafx.scene.Parent parent) {
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
            javafx.scene.Parent root = node.getScene().getRoot();
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

    private static String readResource(String path) throws Exception {
        try (InputStream in = SelectionColorConsistencyTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String css) {
        return css.replaceAll("\\s+", "").toLowerCase();
    }

    private static String rgba(Color color) {
        int r = (int) Math.round(color.getRed() * 255);
        int g = (int) Math.round(color.getGreen() * 255);
        int b = (int) Math.round(color.getBlue() * 255);
        return String.format("rgba(%d,%d,%d,%.2f)", r, g, b, color.getOpacity());
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
