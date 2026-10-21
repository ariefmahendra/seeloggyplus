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
            controller.setDefaultFormatted(false);
            controller.clear();
        });
    }

    @Test void queuedFormattingCannotRestoreAnOlderLineOrClearedContent() throws Exception {
        java.util.List<Runnable> jobs = new java.util.ArrayList<>();
        var field = LogDetailPaneController.class.getDeclaredField("formatExecutor");
        field.setAccessible(true);
        field.set(controller, (java.util.concurrent.Executor) jobs::add);
        String raw = "{\"key\":\"" + "x".repeat(5000) + "\"}";
        onFxThread(() -> {controller.setDefaultFormatted(true); controller.show(0, raw);});
        org.junit.jupiter.api.Assertions.assertEquals(1, jobs.size());
        WaitForAsyncUtils.asyncFx(() -> {
            jobs.removeFirst().run();
            controller.show(1, "latest line");
        }).get();
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("latest line", controller.getCodeArea().getText());
        onFxThread(() -> {controller.show(2, raw); controller.clear();});
        jobs.forEach(Runnable::run);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("", controller.getCodeArea().getText());
    }

    @Test
    @DisplayName("Large JSON payloads are prettified asynchronously")
    void largeJsonIsPrettifiedAsynchronously() throws Exception {
        String raw = "{\"key\":\"" + "x".repeat(5000) + "\"}";
        onFxThread(() -> {
            controller.setDefaultFormatted(true);
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
            controller.setDefaultFormatted(true);
            controller.show(0, raw);
        });

        String text = awaitTextContaining("\n");
        assertTrue(text.contains("<root>"), "the prettified XML must replace the raw line");
    }

    @Test
    @DisplayName("Toggling prettify on formats the line that is already shown")
    void togglingOnFormatsVisibleContent() {
        onFxThread(() -> {
            controller.setDefaultFormatted(false);
            controller.show(0, "{\"a\":1}");
        });

        onFxThread(() -> toggle("formattedModeButton").fire());

        assertTrue(controller.getCodeArea().getText().contains("\n"));
        assertTrue(controller.getCodeArea().getText().contains("\"a\""));
    }

    @Test
    @DisplayName("Formatted mode survives an unsupported row and detects the next XML payload")
    void formattedModeSurvivesUnsupportedRow() {
        onFxThread(() -> {
            controller.setDefaultFormatted(true);
            controller.show(0,"{\"a\":1}");
            assertTrue(controller.getCodeArea().getText().contains("\n"));
            controller.show(1,"plain text");
            assertEquals("plain text",controller.getCodeArea().getText());
            controller.show(2,"<r><a>1</a></r>");
            assertTrue(controller.getCodeArea().getText().contains("\n"));
        });
    }

    @Test
    @DisplayName("Raw mode remains selected when the next supported payload is shown")
    void rawModeSurvivesNextPayload() {
        onFxThread(() -> {
            controller.setDefaultFormatted(true); controller.show(0,"<r><a>1</a></r>");
            toggle("rawModeButton").fire();
            controller.show(1,"{\"a\":1}");
            assertEquals("{\"a\":1}",controller.getCodeArea().getText());
        });
    }

    @Test
    @DisplayName("Raw restores the exact original text after formatting")
    void rawRestoresOriginal() {
        String raw="prefix {\"a\":1} suffix";
        onFxThread(() -> {
            controller.setDefaultFormatted(true);controller.show(0,raw);
            toggle("rawModeButton").fire();
            assertEquals(raw,controller.getCodeArea().getText());
        });
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
