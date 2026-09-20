package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import com.seeloggyplus.util.SyntaxHighlighter;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JSON/XML prettify syntax highlighting in the detail panel must use the
 * theme colours in BOTH light and dark mode (regression: the default text rule
 * previously overrode the syntax classes).
 */
@ExtendWith(ApplicationExtension.class)
class HighlightThemeTest {

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
    void jsonHighlightingFollowsTheme() {
        onFxThread(() -> {
            StyleClassedTextArea area = themedArea();
            String json = "{\"name\": \"value\", \"count\": 42, \"ok\": true}";
            area.replaceText(json);
            area.setStyleSpans(0, SyntaxHighlighter.computeJsonHighlighting(json));
            layout(area);

            assertEquals(Color.web("#0b5563"), fillOf(area, "json-key"), "light json-key colour");
            assertEquals(Color.web("#2f855a"), fillOf(area, "json-string"), "light json-string colour");
            assertEquals(Color.web("#b45309"), fillOf(area, "json-number"), "light json-number colour");

            AppTheme.setDark(true);
            area.applyCss();
            assertEquals(Color.web("#7dd3fc"), fillOf(area, "json-key"), "dark json-key colour");
            assertEquals(Color.web("#86efac"), fillOf(area, "json-string"), "dark json-string colour");
            assertEquals(Color.web("#fdba74"), fillOf(area, "json-number"), "dark json-number colour");

            AppTheme.setDark(false);
        });
    }

    @Test
    void xmlHighlightingFollowsTheme() {
        onFxThread(() -> {
            StyleClassedTextArea area = themedArea();
            String xml = "<root id=\"1\"><name>value</name></root>";
            area.replaceText(xml);
            area.setStyleSpans(0, SyntaxHighlighter.computeXmlHighlighting(xml));
            layout(area);

            assertEquals(Color.web("#1e3a8a"), fillOf(area, "xml-tag"), "light xml-tag colour");
            assertEquals(Color.web("#2f855a"), fillOf(area, "xml-value"), "light xml-value colour");

            AppTheme.setDark(true);
            area.applyCss();
            assertEquals(Color.web("#93c5fd"), fillOf(area, "xml-tag"), "dark xml-tag colour");
            assertEquals(Color.web("#86efac"), fillOf(area, "xml-value"), "dark xml-value colour");

            AppTheme.setDark(false);
        });
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

    private void layout(Node node) {
        if (node.getScene() != null) {
            Parent root = node.getScene().getRoot();
            root.applyCss();
            root.layout();
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static javafx.scene.paint.Paint fillOf(Parent parent, String styleClass) {
        for (Node node : parent.lookupAll("." + styleClass)) {
            if (node instanceof Text text && text.getText() != null && !text.getText().isEmpty()) {
                return text.getFill();
            }
        }
        return fail("No text node with style class: " + styleClass);
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
