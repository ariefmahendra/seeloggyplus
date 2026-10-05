package com.seeloggyplus.app;

import com.seeloggyplus.features.detail.presentation.LogDetailPaneController;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: the toolbar Clear button must empty the log view of the ACTIVE
 * tab. It used to clean only controller-level fields, leaving the session's
 * canvas (reader/index/tail buffer) untouched, so the lines stayed visible.
 */
@ExtendWith(ApplicationExtension.class)
class ClearActiveLogViewTest {

    private MainController controller;
    private TabPane logTabPane;
    private Label statusLabel;
    private Map<Tab, LogSession> sessionMap;

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        logTabPane = (TabPane) field("logTabPane");
        statusLabel = (Label) field("statusLabel");
        sessionMap = ((TabSessionManager) field("tabSessionManager")).sessions();
        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    @Test
    @DisplayName("Clear empties the active local tab without closing it")
    void clearEmptiesActiveLocalTab() throws Exception {
        File file = File.createTempFile("seeloggyplus-clear-local", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "line one\nline two\nline three\n");

        Platform.runLater(() -> invokePrivate("openLocalLogFile",
                new Class<?>[]{File.class, boolean.class}, file, false));
        awaitViewerLines(3);

        Tab activeTab = logTabPane.getSelectionModel().getSelectedItem();
        LogSession session = sessionMap.get(activeTab);
        assertNotNull(session);
        assertTrue(session.getCanvasLogViewer().getTotalLines() > 0, "the file must be visible before clearing");

        Platform.runLater(() -> {
            session.getCanvasLogViewer().selectLine(0);
            invokePrivate("displayLogDetailFromCanvas", new Class<?>[]{long.class, String.class}, 0L, "line one");
            controller.handleClearLog();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0, session.getCanvasLogViewer().getTotalLines(), "the canvas must be empty after Clear");
        assertEquals(0, session.getCanvasLogViewer().getEffectiveLineCount());
        assertEquals("Line: 0 / 0", statusLabel.getText(), "the status bar must reflect the empty view");
        assertEquals(1, logTabPane.getTabs().size(), "Clear must keep the tab open");
        assertSame(activeTab, logTabPane.getSelectionModel().getSelectedItem());
        assertNull(session.getReader(), "the cleared session must release its file reader");
        assertTrue(detailArea().getText().isEmpty(), "the Detail panel must be cleared as well");
    }

    @Test
    @DisplayName("Clear stops a remote tail and empties its streamed lines")
    void clearStopsRemoteTailAndEmptiesLines() {
        SSHServerModel server = new SSHServerModel("Clear-Server", "127.0.0.1", 22, "user");
        server.setId("clear-server");
        server.setPassword("secret");
        MockTailSSHService ssh = new MockTailSSHService();

        Platform.runLater(() -> invokePrivate("startRemoteTail",
                new Class<?>[]{String.class, SSHService.class, SSHServerModel.class},
                "/var/log/clear.log", ssh, server));
        WaitForAsyncUtils.waitForFxEvents();

        Tab tab = logTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(tab);
        LogSession session = sessionMap.get(tab);
        assertTrue(session.isTailModeEnabled());

        Platform.runLater(() -> {
            ssh.lineConsumer.accept("first streamed line");
            ssh.lineConsumer.accept("second streamed line");
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, session.getCanvasLogViewer().getTotalLines());

        Platform.runLater(controller::handleClearLog);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0, session.getCanvasLogViewer().getTotalLines(), "streamed lines must disappear");
        assertFalse(session.isTailModeEnabled(), "Clear must stop tail mode");
        assertTrue(ssh.stopCalls > 0, "the SSH tailer must be stopped");
        assertEquals(1, logTabPane.getTabs().size(), "the tab must stay open");
        assertEquals("Line: 0 / 0", statusLabel.getText());
    }

    @Test
    @DisplayName("Clear with no open tabs does nothing and never throws")
    void clearWithoutTabsIsSafe() {
        Platform.runLater(controller::handleClearLog);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(0, logTabPane.getTabs().size());
    }

    @Test
    @DisplayName("Reload after Clear restores the same file in the same tab")
    void reloadAfterClearRestoresTheFile() throws Exception {
        File file = File.createTempFile("seeloggyplus-clear-reload", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "alpha\nbeta\ngamma\n");

        Platform.runLater(() -> invokePrivate("openLocalLogFile",
                new Class<?>[]{File.class, boolean.class}, file, false));
        awaitViewerLines(3);
        Tab tab = logTabPane.getSelectionModel().getSelectedItem();
        LogSession session = sessionMap.get(tab);

        Platform.runLater(controller::handleClearLog);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(0, session.getCanvasLogViewer().getTotalLines());

        Platform.runLater(() -> invokePrivate("handleReload", new Class<?>[0]));
        awaitViewerLines(3);
        assertEquals(1, logTabPane.getTabs().size(), "reload must reuse the cleared tab");
        assertSame(tab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(session, sessionMap.get(tab));
        assertNotNull(session.getReader(), "reload must rebuild the file reader");
    }

    private StyleClassedTextArea detailArea() {
        try {
            Field field = MainController.class.getDeclaredField("detailPaneController");
            field.setAccessible(true);
            LogDetailPaneController detailPane = (LogDetailPaneController) field.get(controller);
            return detailPane.getCodeArea();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void awaitViewerLines(int minimum) throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() -> {
            Tab tab = logTabPane.getSelectionModel().getSelectedItem();
            LogSession session = tab == null ? null : sessionMap.get(tab);
            return session != null && session.getCanvasLogViewer() != null
                    && session.getCanvasLogViewer().getTotalLines() >= minimum;
        }).get());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Object field(String name) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private Object invokePrivate(String name, Class<?>[] types, Object... args) {
        try {
            Method method = MainController.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(controller, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Minimal SSH double: captures the stream consumer and counts stop requests. */
    private static class MockTailSSHService extends SSHServiceImpl {
        Consumer<String> lineConsumer;
        int stopCalls;

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void tailFile(String remotePath, int numLines, Consumer<String> onLineReceived, Consumer<String> onError) {
            this.lineConsumer = onLineReceived;
        }

        @Override
        public void stopTailing() {
            stopCalls++;
        }
    }
}
