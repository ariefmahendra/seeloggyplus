package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
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

/**
 * Verifies the log detail panel (RichTextFX) is wired to the theme stylesheet
 * and that its background/text follow the light/dark tokens.
 */
@ExtendWith(ApplicationExtension.class)
class DetailPanelThemeTest {

    private Parent mainRoot;
    private MainController controller;
    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        mainRoot = loader.load();
        controller = loader.getController();
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    /** MainController must create the detail area with the richtext stylesheet. */
    @Test
    void detailAreaHasRichtextStylesheet() {
        onFxThread(() -> {
            StyleClassedTextArea area = mainDetailArea();
            assertNotNull(area, "detailCodeArea must be created during initialization");
            assertTrue(area.getStyleClass().contains("detail-area"),
                    "detail area must carry the detail-area style class");
            assertTrue(area.getStylesheets().stream().anyMatch(url -> url.contains("richtext.css")),
                    "detail area must load richtext.css");
        });
    }

    @Test
    void detailAreaBackgroundFollowsTheme() {
        onFxThread(() -> {
            StyleClassedTextArea area = themedArea();
            rootLayout(area);

            assertEquals(Color.web("#ffffff"), backgroundFill(area),
                    "light theme detail background must be the light surface");

            AppTheme.setDark(true);
            area.applyCss();
            assertEquals(Color.web("#1e2226"), backgroundFill(area),
                    "dark theme detail background must be the dark surface");

            AppTheme.setDark(false);
        });
    }

    @Test
    void defaultDetailTextUsesThemeFill() {
        onFxThread(() -> {
            StyleClassedTextArea area = themedArea();
            String line = "plain unstyled log line";

            // The application always applies highlighting spans; unmatched parts
            // carry the "default" class so they follow the theme.
            area.replaceText(line);
            area.setStyleSpans(0, com.seeloggyplus.util.SyntaxHighlighter.computeLogHighlighting(line));
            rootLayout(area);

            Text lightText = firstTextNode(area);
            assertNotNull(lightText, "detail area must render text nodes");
            assertEquals(Color.web("#1f2329"), lightText.getFill(),
                    "light theme default detail text must be dark");

            AppTheme.setDark(true);
            area.applyCss();
            Text darkText = firstTextNode(area);
            assertNotNull(darkText);
            assertEquals(Color.web("#d7dbe0"), darkText.getFill(),
                    "dark theme default detail text must be light (not black)");

            AppTheme.setDark(false);
        });
    }

    /** Builds a standalone detail area whose scene is themed via AppTheme. */
    private StyleClassedTextArea themedArea() {
        StyleClassedTextArea area = new StyleClassedTextArea();
        area.setEditable(false);
        area.getStyleClass().add("detail-area");
        area.getStylesheets().add(requireRichtextCss());
        StackPane root = new StackPane(area);
        root.setId("detail-theme-root");
        Scene scene = AppTheme.scene(root);
        stage.setScene(scene);
        stage.show();
        root.applyCss();
        root.layout();
        return area;
    }

    private String requireRichtextCss() {
        java.net.URL url = getClass().getResource("/style/richtext.css");
        assertNotNull(url, "richtext.css must be on the classpath");
        return url.toExternalForm();
    }

    private void rootLayout(Node node) {
        if (node.getScene() != null) {
            Parent root = node.getScene().getRoot();
            root.applyCss();
            root.layout();
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Text firstTextNode(Parent parent) {
        for (Node node : parent.lookupAll(".text")) {
            if (node instanceof Text text && text.getText() != null && !text.getText().isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private StyleClassedTextArea mainDetailArea() {
        try {
            Field f = MainController.class.getDeclaredField("detailCodeArea");
            f.setAccessible(true);
            return (StyleClassedTextArea) f.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Color backgroundFill(Region region) {
        Background bg = region.getBackground();
        if (bg == null || bg.getFills().isEmpty()) {
            return null;
        }
        Paint paint = bg.getFills().get(0).getFill();
        return paint instanceof Color color ? color : null;
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
