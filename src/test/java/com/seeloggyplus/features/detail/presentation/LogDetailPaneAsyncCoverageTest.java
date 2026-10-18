package com.seeloggyplus.features.detail.presentation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogDetailPaneAsyncCoverageTest extends ApplicationTest {

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
    @DisplayName("Large JSON payloads are prettified asynchronously")
    void largeJsonIsPrettifiedAsynchronously() throws Exception {
        String raw = "{\"key\":\"" + "x".repeat(5000) + "\"}";
        onFxThread(() -> {
            controller.setAutoPrettify(true, false);
            controller.show(0, raw);
        });

        String text = awaitTextContaining("\n");
        assertTrue(text.contains("\"key\""), "the prettified payload must replace the raw line");
        assertTrue(text.length() > raw.length() / 2);
    }

    @Test
    @DisplayName("Large XML payloads are prettified asynchronously")
    void largeXmlIsPrettifiedAsynchronously() throws Exception {
        String raw = "<root><data>" + "y".repeat(5000) + "</data></root>";
        onFxThread(() -> {
            controller.setAutoPrettify(false, true);
            controller.show(0, raw);
        });

        String text = awaitTextContaining("\n");
        assertTrue(text.contains("<root>"), "the prettified XML must replace the raw line");
    }

    @Test
    @DisplayName("Toggling prettify on formats the line that is already shown")
    void togglingOnFormatsVisibleContent() {
        onFxThread(() -> {
            controller.setAutoPrettify(false, false);
            controller.show(0, "{\"a\":1}");
        });

        onFxThread(() -> toggle("prettifyJsonButton").setSelected(true));

        assertTrue(controller.getCodeArea().getText().contains("\n"));
        assertTrue(controller.getCodeArea().getText().contains("\"a\""));
    }

    @Test
    @DisplayName("Turning XML prettify off keeps the JSON formatting that still applies")
    void restoreKeepsJsonPrettify() {
        onFxThread(() -> {
            controller.setAutoPrettify(true, true);
            controller.show(0, "{\"a\":1}");
        });
        assertTrue(controller.getCodeArea().getText().contains("\n"));

        onFxThread(() -> toggle("prettifyXmlButton").setSelected(false));

        assertTrue(controller.getCodeArea().getText().contains("\n"),
                "JSON prettify must stay active while XML is off");
    }

    @Test
    @DisplayName("Turning JSON prettify off keeps the XML formatting that still applies")
    void restoreKeepsXmlPrettify() {
        onFxThread(() -> {
            controller.setAutoPrettify(true, true);
            controller.show(0, "<r><x>1</x></r>");
        });
        assertTrue(controller.getCodeArea().getText().contains("\n"));

        onFxThread(() -> toggle("prettifyJsonButton").setSelected(false));

        assertTrue(controller.getCodeArea().getText().contains("<r>"),
                "XML prettify must stay active while JSON is off");
    }

    @Test
    @DisplayName("Toggling both off restores the raw line exactly once")
    void togglingBothOffRestoresRaw() {
        String raw = "{\"a\":1}";
        onFxThread(() -> {
            controller.setAutoPrettify(true, true);
            controller.show(0, raw);
        });

        onFxThread(() -> toggle("prettifyJsonButton").setSelected(false));
        onFxThread(() -> toggle("prettifyXmlButton").setSelected(false));

        assertEquals(raw, controller.getCodeArea().getText());
    }

    private String awaitTextContaining(String needle) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        String text = "";
        while (System.currentTimeMillis() < deadline) {
            text = WaitForAsyncUtils.asyncFx(() -> controller.getCodeArea().getText()).get();
            if (text.contains(needle)) {
                return text;
            }
            Thread.sleep(40);
        }
        return text;
    }

    private void onFxThread(Runnable action) {
        WaitForAsyncUtils.asyncFx(action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private ToggleButton toggle(String id) {
        return (ToggleButton) root.lookup("#" + id);
    }
}
