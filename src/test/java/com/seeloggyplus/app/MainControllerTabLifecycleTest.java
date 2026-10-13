package com.seeloggyplus.app;

import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class MainControllerTabLifecycleTest {

    private MainController controller;
    private TabPane logTabPane;
    private TabSessionManager sessionManager;
    private Map<Tab, LogSession> sessionMap;

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        logTabPane = (TabPane) field("logTabPane");
        sessionManager = (TabSessionManager) field("tabSessionManager");
        sessionMap = sessionManager.sessions();
        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    @AfterEach
    void cleanup() {
        Platform.runLater(() -> {
            new ArrayList<>(sessionMap.values()).forEach(LogSession::close);
            sessionMap.clear();
            logTabPane.getTabs().clear();
            AppTheme.setDark(false);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void closeOtherTabsKeepsOnlyGivenSession() {
        Platform.runLater(() -> {
            try {
                LogSession first = openSession("first.log", LogSession.SessionType.LOCAL);
                LogSession keep = openSession("keep.log", LogSession.SessionType.LOCAL);
                LogSession last = openSession("last.log", LogSession.SessionType.LOCAL);

                invoke("handleCloseOtherTabs", new Class<?>[]{LogSession.class}, keep);

                assertEquals(1, logTabPane.getTabs().size());
                assertSame(keep.getTab(), logTabPane.getTabs().get(0));
                assertEquals(1, sessionMap.size());
                assertTrue(first.isClosed());
                assertTrue(last.isClosed());
                assertFalse(keep.isClosed());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void closeTabsToTheRightClosesOnlyLaterTabs() {
        Platform.runLater(() -> {
            try {
                LogSession first = openSession("first.log", LogSession.SessionType.LOCAL);
                LogSession middle = openSession("middle.log", LogSession.SessionType.LOCAL);
                LogSession last = openSession("last.log", LogSession.SessionType.LOCAL);

                invoke("handleCloseTabsToTheRight", new Class<?>[]{LogSession.class}, middle);

                assertEquals(2, logTabPane.getTabs().size());
                assertSame(first.getTab(), logTabPane.getTabs().get(0));
                assertSame(middle.getTab(), logTabPane.getTabs().get(1));
                assertFalse(first.isClosed());
                assertFalse(middle.isClosed());
                assertTrue(last.isClosed());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void closeCurrentTabClosesActiveSession() {
        Platform.runLater(() -> {
            try {
                openSession("first.log", LogSession.SessionType.LOCAL);
                LogSession active = openSession("active.log", LogSession.SessionType.LOCAL);
                openSession("last.log", LogSession.SessionType.LOCAL);
                logTabPane.getSelectionModel().select(active.getTab());

                assertSame(active.getTab(), logTabPane.getSelectionModel().getSelectedItem());
                invoke("handleCloseCurrentTab", new Class<?>[0]);

                assertEquals(2, logTabPane.getTabs().size());
                assertFalse(logTabPane.getTabs().contains(active.getTab()));
                assertTrue(active.isClosed());
                Tab selected = logTabPane.getSelectionModel().getSelectedItem();
                assertNotNull(selected);
                assertSame(sessionMap.get(selected), sessionManager.getActive());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void openSameLocalFileTwiceReusesExistingTab() throws Exception {
        File file = File.createTempFile("seeloggyplus-reuse-open", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "line one\nline two\nline three\n");

        Platform.runLater(() -> {
            try {
                invoke("openLocalLogFile", new Class<?>[]{File.class, boolean.class}, file, false);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        awaitViewerLines(3);

        Tab original = logTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(original);
        LogSession session = sessionMap.get(original);
        assertNotNull(session);

        Platform.runLater(() -> {
            try {
                invoke("openLocalLogFile", new Class<?>[]{File.class, boolean.class}, file, false);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, logTabPane.getTabs().size(), "Opening the same file must reuse the existing tab");
        assertSame(original, logTabPane.getSelectionModel().getSelectedItem());
        assertEquals(3, session.getCanvasLogViewer().getTotalLines());
    }

    @Test
    void updateTabBadgeShowsUnreadCountForInactiveSession() {
        AtomicReference<LogSession> reference = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                LogSession session = openSession("app.log", LogSession.SessionType.LOCAL);
                session.setActive(false);
                session.addUnreadTailLines(5);
                invoke("updateTabBadge", new Class<?>[]{LogSession.class}, session);
                reference.set(session);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("app.log (+5)", reference.get().getTab().getText());
    }

    @Test
    void updateTabBadgeCapsUnreadCountAt999() {
        AtomicReference<LogSession> reference = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                LogSession session = openSession("busy.log", LogSession.SessionType.LOCAL);
                session.setActive(false);
                session.addUnreadTailLines(1500);
                invoke("updateTabBadge", new Class<?>[]{LogSession.class}, session);
                reference.set(session);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("busy.log (+999+)", reference.get().getTab().getText());
    }

    @Test
    void liveIndicatorFollowsTheThemeLiveToken() {
        AtomicReference<LogSession> reference = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                LogSession session = openSession("live.log", LogSession.SessionType.REMOTE);
                session.setRemotePath("/var/log/live.log");
                session.setTailModeEnabled(true);
                invoke("updateTabBadge", new Class<?>[]{LogSession.class}, session);
                reference.set(session);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        Circle dot = reference.get().getLiveIndicatorDot();
        Scene scene = dot.getScene();
        assertNotNull(scene, "the live indicator must be attached to the tab header");
        Parent root = scene.getRoot();

        Platform.runLater(root::applyCss);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(Color.web("#2f855a"), dot.getFill(),
                "graphite live indicator must use the live token");

        Platform.runLater(() -> AppTheme.setDark(true));
        WaitForAsyncUtils.waitForFxEvents();
        Platform.runLater(root::applyCss);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(Color.web("#4ade80"), dot.getFill(),
                "dark live indicator must use the dark live token");

        Platform.runLater(() -> AppTheme.setDark(false));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void updateTabBadgeTogglesLiveDotWithTailMode() {
        AtomicReference<LogSession> reference = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                LogSession session = openSession("stream.log", LogSession.SessionType.REMOTE);
                session.setRemotePath("/var/log/stream.log");
                reference.set(session);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        LogSession session = reference.get();
        assertFalse(session.getLiveIndicatorDot().isVisible());

        Platform.runLater(() -> {
            try {
                session.setTailModeEnabled(true);
                invoke("updateTabBadge", new Class<?>[]{LogSession.class}, session);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(session.getLiveIndicatorDot().isVisible());
        assertTrue(session.getLiveIndicatorDot().isManaged());

        Platform.runLater(() -> {
            try {
                session.setTailModeEnabled(false);
                invoke("updateTabBadge", new Class<?>[]{LogSession.class}, session);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(session.getLiveIndicatorDot().isVisible());
        assertFalse(session.getLiveIndicatorDot().isManaged());
    }

    @Test
    void createTabForSessionUsesLocalAbsolutePathAsTooltip() throws Exception {
        File file = File.createTempFile("seeloggyplus-tooltip", ".log");
        file.deleteOnExit();
        LogSession session = new LogSession(file.getName(), LogSession.SessionType.LOCAL);
        session.setLocalFile(file);

        Platform.runLater(() -> {
            try {
                Tab tab = (Tab) invoke("createTabForSession", new Class<?>[]{LogSession.class}, session);
                assertEquals(file.getName(), tab.getText());
                assertNotNull(tab.getTooltip());
                assertEquals(file.getAbsolutePath(), tab.getTooltip().getText());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void closeRequestOnTabClosesSession() {
        AtomicReference<LogSession> reference = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                reference.set(openSession("closable.log", LogSession.SessionType.LOCAL));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        LogSession session = reference.get();
        Tab tab = session.getTab();
        Platform.runLater(() -> Event.fireEvent(tab, new Event(Tab.TAB_CLOSE_REQUEST_EVENT)));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(session.isClosed());
        assertFalse(logTabPane.getTabs().contains(tab));
        assertTrue(sessionMap.isEmpty());
    }

    @Test
    void updateLeftPanelDisplayHidesPanelWhenUnpinned() {
        Platform.runLater(() -> {
            try {
                SplitPane split = (SplitPane) field("horizontalSplitPane");
                VBox leftPanel = (VBox) field("leftPanel");
                ToggleButton toggle = (ToggleButton) field("toggleLeftPanelButton");
                CheckMenuItem menuItem = (CheckMenuItem) field("showLeftPanelMenuItem");

                setField("isLeftPanelPinned", false);
                invoke("updateLeftPanelDisplay", new Class<?>[0]);

                assertFalse(split.getItems().contains(leftPanel));
                assertFalse(leftPanel.isVisible());
                assertFalse(leftPanel.isManaged());
                assertFalse(toggle.isSelected());
                assertFalse(menuItem.isSelected());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void updateLeftPanelDisplayRestoresPanelWhenPinned() {
        Platform.runLater(() -> {
            try {
                SplitPane split = (SplitPane) field("horizontalSplitPane");
                VBox leftPanel = (VBox) field("leftPanel");
                ToggleButton toggle = (ToggleButton) field("toggleLeftPanelButton");
                CheckMenuItem menuItem = (CheckMenuItem) field("showLeftPanelMenuItem");

                setField("isLeftPanelPinned", false);
                invoke("updateLeftPanelDisplay", new Class<?>[0]);
                setField("isLeftPanelPinned", true);
                invoke("updateLeftPanelDisplay", new Class<?>[0]);

                assertSame(leftPanel, split.getItems().get(0));
                assertTrue(leftPanel.isVisible());
                assertTrue(leftPanel.isManaged());
                assertTrue(toggle.isSelected());
                assertTrue(menuItem.isSelected());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void updateBottomPanelDisplayHidesDetailPaneWhenUnpinned() {
        Platform.runLater(() -> {
            try {
                SplitPane split = (SplitPane) field("verticalSplitPane");
                VBox detailPane = (VBox) field("detailPane");
                ToggleButton toggle = (ToggleButton) field("toggleBottomPanelButton");
                CheckMenuItem menuItem = (CheckMenuItem) field("showBottomPanelMenuItem");

                setField("isBottomPanelPinned", false);
                invoke("updateBottomPanelDisplay", new Class<?>[0]);

                assertFalse(split.getItems().contains(detailPane));
                assertFalse(detailPane.isVisible());
                assertFalse(detailPane.isManaged());
                assertFalse(toggle.isSelected());
                assertFalse(menuItem.isSelected());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void updateBottomPanelDisplayRestoresDetailPaneWhenPinned() {
        Platform.runLater(() -> {
            try {
                SplitPane split = (SplitPane) field("verticalSplitPane");
                VBox detailPane = (VBox) field("detailPane");
                ToggleButton toggle = (ToggleButton) field("toggleBottomPanelButton");
                CheckMenuItem menuItem = (CheckMenuItem) field("showBottomPanelMenuItem");

                setField("isBottomPanelPinned", false);
                invoke("updateBottomPanelDisplay", new Class<?>[0]);
                setField("isBottomPanelPinned", true);
                invoke("updateBottomPanelDisplay", new Class<?>[0]);

                assertTrue(split.getItems().contains(detailPane));
                assertTrue(detailPane.isVisible());
                assertTrue(detailPane.isManaged());
                assertTrue(toggle.isSelected());
                assertTrue(menuItem.isSelected());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private LogSession openSession(String title, LogSession.SessionType type) throws Exception {
        LogSession session = new LogSession(title, type);
        Tab tab = (Tab) invoke("createTabForSession", new Class<?>[]{LogSession.class}, session);
        sessionMap.put(tab, session);
        logTabPane.getTabs().add(tab);
        logTabPane.getSelectionModel().select(tab);
        invoke("onActiveSessionChanged", new Class<?>[]{LogSession.class}, session);
        return session;
    }

    private void awaitViewerLines(int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            WaitForAsyncUtils.waitForFxEvents();
            Tab selected = logTabPane.getSelectionModel().getSelectedItem();
            if (selected != null) {
                LogSession session = sessionMap.get(selected);
                if (session != null && session.getCanvasLogViewer() != null
                        && session.getCanvasLogViewer().getTotalLines() >= expected) {
                    return;
                }
            }
            Thread.sleep(40);
        }
        fail("Viewer did not reach " + expected + " lines in time");
    }

    private Object field(String name) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MainController.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(controller, args);
    }
}
