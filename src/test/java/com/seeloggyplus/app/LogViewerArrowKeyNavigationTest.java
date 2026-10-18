package com.seeloggyplus.app;

import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class LogViewerArrowKeyNavigationTest {

    private Parent root;
    private MainController controller;
    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        controller = loader.getController();
        Scene scene = AppTheme.scene(root);
        stage.setScene(scene);
        stage.setWidth(1000);
        stage.setHeight(640);
        stage.show();
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    @Test
    void downAndUpKeysMoveSelectedRow() {
        onFxThread(() -> {
            try {
                TabPane pane = getField("logTabPane");
                LogSession session = new LogSession("test.log", LogSession.SessionType.LOCAL);
                for (int i = 0; i < 50; i++) {
                    session.getLiveTailList().add(new LogEntry(i + 1, "line " + (i + 1)));
                }
                Method createTab = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTab.setAccessible(true);
                Tab tab = (Tab) createTab.invoke(controller, session);
                pane.getTabs().add(tab);
                pane.getSelectionModel().select(tab);

                CanvasLogViewer viewer = session.getCanvasLogViewer();
                assertNotNull(viewer, "session must have a canvas viewer");
                setField("canvasLogViewer", viewer);

                root.applyCss();
                root.layout();
                viewer.applyCss();
                viewer.layout();
                WaitForAsyncUtils.waitForFxEvents();
                viewer.setFollowTail(false);

                viewer.getVScrollBar().setValue(0);
                viewer.applyCss();
                viewer.layout();
                WaitForAsyncUtils.waitForFxEvents();

                fireKey(root, KeyCode.DOWN);
                assertEquals(0, viewer.getSelectedIndex(), "first DOWN must select the first row");
                fireKey(root, KeyCode.DOWN);
                assertEquals(1, viewer.getSelectedIndex(), "second DOWN must move to the next row");
                fireKey(root, KeyCode.UP);
                assertEquals(0, viewer.getSelectedIndex(), "UP must move back one row");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void selectionStatusLabelFollowsTheCanvasSelection() {
        onFxThread(() -> {
            try {
                TabPane pane = getField("logTabPane");
                LogSession session = new LogSession("selection.log", LogSession.SessionType.LOCAL);
                for (int i = 0; i < 20; i++) {
                    session.getLiveTailList().add(new LogEntry(i + 1, "line " + (i + 1)));
                }
                Method createTab = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTab.setAccessible(true);
                Tab tab = (Tab) createTab.invoke(controller, session);
                pane.getTabs().add(tab);
                pane.getSelectionModel().select(tab);

                CanvasLogViewer viewer = session.getCanvasLogViewer();
                assertNotNull(viewer, "session must have a canvas viewer");
                setField("canvasLogViewer", viewer);
                viewer.setFollowTail(false);
                viewer.getVScrollBar().setValue(0);

                Method activate = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                activate.setAccessible(true);
                activate.invoke(controller, session);
                root.applyCss();
                root.layout();
                WaitForAsyncUtils.waitForFxEvents();

                javafx.scene.control.Label selection = getField("selectionStatusLabel");
                assertNotNull(selection, "the status bar must expose a selection label");
                assertFalse(selection.isVisible(), "no selection must keep the label hidden");

                viewer.selectLine(2);
                assertEquals("1 line, 6 chars", viewer.getSelectionSummary(),
                        "selectLine must select the whole line");
                assertTrue(selection.isVisible(), "a selection must reveal the label");
                assertEquals("Selected: 1 line, 6 chars", selection.getText());

                viewer.clearFilter();
                assertFalse(selection.isVisible(), "clearing the selection must hide the label again");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void fireKey(Node target, KeyCode code) {
        KeyEvent event = new KeyEvent(target, target, KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
        Event.fireEvent(target, event);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String name) throws ReflectiveOperationException {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(controller);
    }

    private void setField(String name, Object value) throws ReflectiveOperationException {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
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
