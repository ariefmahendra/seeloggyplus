package com.seeloggyplus.features.detail.presentation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import javafx.scene.input.Clipboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogDetailPaneControllerTest extends ApplicationTest {

    private LogDetailPaneController controller;
    private Parent root;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LogDetailPane.fxml"));
        root = loader.load();
        controller = loader.getController();
    }

    @AfterEach
    void resetPanel() {
        onFxThread(() -> {
            controller.setAutoPrettify(false, false);
            controller.clear();
        });
    }

    @Test
    @DisplayName("show() renders the selected line and updates the title")
    void showRendersLineAndHidesPlaceholder() {
        onFxThread(() -> controller.show(41, "2026-01-01 12:00:00 [INFO] ready"));

        assertEquals("Line 42", label("detailLabel").getText());
        assertEquals("2026-01-01 12:00:00 [INFO] ready", controller.getCodeArea().getText());
    }

    @Test
    @DisplayName("clear() restores the default title and empties the content")
    void clearRestoresInitialState() {
        onFxThread(() -> controller.show(0, "payload"));

        onFxThread(() -> controller.clear());

        assertEquals("Log Detail", label("detailLabel").getText());
        assertTrue(controller.getCodeArea().getText().isEmpty());
    }

    @Test
    @DisplayName("The pin button triggers the shell close request")
    void pinButtonTriggersCloseRequest() {
        List<String> calls = new ArrayList<>();

        onFxThread(() -> {
            controller.setOnCloseRequest(() -> calls.add("closed"));
            button("pinBottomPanelButton").fire();
        });

        assertEquals(List.of("closed"), calls);
    }

    @Test
    @DisplayName("Toggling auto-prettify reports preference changes")
    void autoPrettifyTogglesReportPreferences() {
        List<String> changes = new ArrayList<>();

        onFxThread(() -> {
            controller.setOnPreferenceChanged((code, value) -> changes.add(code + "=" + value));
            toggle("prettifyJsonButton").setSelected(true);
            toggle("prettifyXmlButton").setSelected(true);
        });

        assertEquals(List.of("main_auto_prettify_json=true", "main_auto_prettify_xml=true"), changes);
    }

    @Test
    @DisplayName("Auto-prettify JSON formats inline JSON on show")
    void autoPrettifyJsonFormatsInlineJson() {
        onFxThread(() -> {
            controller.setAutoPrettify(true, false);
            controller.show(0, "{\"level\":\"INFO\",\"message\":\"ready\"}");
        });

        String text = controller.getCodeArea().getText();
        assertTrue(text.contains("\n"), "inline JSON must be prettified into multiple lines");
        assertTrue(text.contains("\"level\""));
    }

    @Test
    @DisplayName("Auto-prettify XML formats inline XML on show")
    void autoPrettifyXmlFormatsInlineXml() {
        onFxThread(() -> {
            controller.setAutoPrettify(false, true);
            controller.show(0, "<root><entry>1</entry></root>");
        });

        String text = controller.getCodeArea().getText();
        assertTrue(text.contains("\n"), "inline XML must be prettified into multiple lines");
        assertTrue(text.contains("<root>"));
    }

    @Test
    @DisplayName("Turning auto-prettify off restores the raw line")
    void turningPrettifyOffRestoresRawText() {
        String raw = "{\"a\":1}";
        onFxThread(() -> {
            controller.setAutoPrettify(true, false);
            controller.show(0, raw);
        });
        assertTrue(controller.getCodeArea().getText().contains("\n"));

        onFxThread(() -> toggle("prettifyJsonButton").setSelected(false));

        assertEquals(raw, controller.getCodeArea().getText());
    }

    @Test
    @DisplayName("show() ignores null content and keeps the previous line")
    void showIgnoresNullContent() {
        onFxThread(() -> controller.show(4, "kept"));
        onFxThread(() -> controller.show(9, null));

        assertEquals("Line 5", label("detailLabel").getText());
        assertEquals("kept", controller.getCodeArea().getText());
    }

    @Test
    @DisplayName("The clear button empties the pane without a shell callback")
    void clearButtonEmptiesThePane() {
        onFxThread(() -> {
            controller.show(0, "payload");
            button("clearDetailButton").fire();
        });

        assertEquals("Log Detail", label("detailLabel").getText());
        assertTrue(controller.getCodeArea().getText().isEmpty());
    }

    @Test
    @DisplayName("The copy button copies the rendered content")
    void copyButtonCopiesRenderedContent() {
        onFxThread(() -> {
            controller.show(0, "copy-me");
            button("copyButton").fire();
        });

        assertEquals("copy-me", clipboardText());
    }

    @Test
    @DisplayName("Font style is applied to the rendered code area")
    void fontStyleIsApplied() {
        onFxThread(() -> controller.setFontStyle("-fx-font-size: 17px;"));

        assertTrue(controller.getCodeArea().getStyle().contains("17px"));
    }

    private void onFxThread(Runnable action) {
        WaitForAsyncUtils.asyncFx(action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String clipboardText() {
        try {
            return WaitForAsyncUtils.asyncFx(() -> Clipboard.getSystemClipboard().getString()).get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Label label(String id) {
        return (Label) root.lookup("#" + id);
    }

    private ToggleButton toggle(String id) {
        return (ToggleButton) root.lookup("#" + id);
    }

    private Button button(String id) {
        return (Button) root.lookup("#" + id);
    }
}
