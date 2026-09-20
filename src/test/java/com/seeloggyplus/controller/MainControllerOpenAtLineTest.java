package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;

import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.LogSession;
import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that opening a file with a target line jumps the canvas viewer to that
 * specific line, clamps out-of-range lines, and keeps the default behaviour when
 * no target line is requested.
 */
@ExtendWith(ApplicationExtension.class)
public class MainControllerOpenAtLineTest {

    private MainController controller;
    private TabPane logTabPane;
    private Map<Tab, LogSession> sessionMap;

    @Start
    @SuppressWarnings("unchecked")
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        Field tabPaneField = MainController.class.getDeclaredField("logTabPane");
        tabPaneField.setAccessible(true);
        logTabPane = (TabPane) tabPaneField.get(controller);

        Field sessionMapField = MainController.class.getDeclaredField("sessionMap");
        sessionMapField.setAccessible(true);
        sessionMap = (Map<Tab, LogSession>) sessionMapField.get(controller);

        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    private File createLogFile(int lineCount) throws Exception {
        File file = File.createTempFile("seeloggyplus-jump", ".log");
        file.deleteOnExit();
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= lineCount; i++) {
            content.append("line ").append(i).append('\n');
        }
        Files.writeString(file.toPath(), content.toString());
        return file;
    }

    private LogSession prepareSession(File file) throws Exception {
        LogSession session = new LogSession(file.getName(), LogSession.SessionType.LOCAL);
        session.setLocalFile(file);

        WaitForAsyncUtils.asyncFx(() -> {
            try {
                Method createTab = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTab.setAccessible(true);
                Tab tab = (Tab) createTab.invoke(controller, session);
                sessionMap.put(tab, session);
                logTabPane.getTabs().add(tab);
                logTabPane.getSelectionModel().select(tab);

                Method onActive = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActive.setAccessible(true);
                onActive.invoke(controller, session);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).get();
        return session;
    }

    private void loadWithTargetLine(LogSession session, File file, int targetLine) {
        Platform.runLater(() -> {
            try {
                Method load = MainController.class.getDeclaredMethod("loadFileWithParallelParsing",
                        LogSession.class, File.class, LogFile.class,
                        boolean.class, boolean.class, boolean.class, boolean.class, int.class);
                load.setAccessible(true);
                load.invoke(controller, session, file, null, false, false, false, false, targetLine);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void waitForIndex(LogSession session) throws Exception {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            WaitForAsyncUtils.waitForFxEvents();
            CanvasLogViewer viewer = session.getCanvasLogViewer();
            if (session.getIndex() != null && viewer != null && viewer.getTotalLines() > 0) {
                return;
            }
            Thread.sleep(40);
        }
        fail("File indexing did not complete in time");
    }

    @Test
    public void openJumpsToRequestedLine() throws Exception {
        File file = createLogFile(50);
        LogSession session = prepareSession(file);

        loadWithTargetLine(session, file, 30);
        waitForIndex(session);

        assertEquals(29, session.getCanvasLogViewer().getSelectedIndex(),
                "Viewer must select the requested 1-based line (30 -> index 29)");
    }

    @Test
    public void openWithoutTargetLineKeepsViewerAtTop() throws Exception {
        File file = createLogFile(50);
        LogSession session = prepareSession(file);

        loadWithTargetLine(session, file, 0);
        waitForIndex(session);

        assertEquals(0, session.getCanvasLogViewer().getSelectedIndex(),
                "Without a target line the viewer must stay at the top");
    }

    @Test
    public void targetLineBeyondEndIsClampedToLastLine() throws Exception {
        File file = createLogFile(20);
        LogSession session = prepareSession(file);

        loadWithTargetLine(session, file, 9999);
        waitForIndex(session);

        assertEquals(19, session.getCanvasLogViewer().getSelectedIndex(),
                "Out-of-range target line must clamp to the last indexed line");
    }
}
