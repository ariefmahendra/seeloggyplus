package com.seeloggyplus.controller;

import com.seeloggyplus.model.LogSession;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
public class MainViewFxmlLoadTest {

    private Parent root;
    private MainController controller;

    @Start
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        controller = loader.getController();
    }

    @Test
    public void testMainViewFxmlLoadsSuccessfully() throws Exception {
        assertNotNull(root);
        assertNotNull(controller);

        java.lang.reflect.Field tabPaneField = MainController.class.getDeclaredField("logTabPane");
        tabPaneField.setAccessible(true);
        TabPane tabPane = (TabPane) tabPaneField.get(controller);
        assertNotNull(tabPane);
        assertEquals(TabPane.TabClosingPolicy.ALL_TABS, tabPane.getTabClosingPolicy());
        assertEquals(TabPane.TabDragPolicy.REORDER, tabPane.getTabDragPolicy());
    }

    @Test
    public void testTabHasTextForMenuNavigation() throws Exception {
        LogSession session = new LogSession("app-production.log", LogSession.SessionType.LOCAL);

        Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
        createTabMethod.setAccessible(true);
        Tab tab = (Tab) createTabMethod.invoke(controller, session);

        assertNotNull(tab);
        assertEquals("app-production.log", tab.getText(), "Tab text must be set for TabPane overflow menu button to display the file name");
        assertNotNull(tab.getGraphic(), "Tab graphic must be set for styled tab header");
    }

    @Test
    public void testCleanDownloadedTabName() throws Exception {
        LogSession session = new LogSession("seeloggyplus-1788673390465-application.log", LogSession.SessionType.LOCAL);

        Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
        createTabMethod.setAccessible(true);
        Tab tab = (Tab) createTabMethod.invoke(controller, session);

        assertNotNull(tab);
        assertEquals("application.log", tab.getText(), "Temporary prefix must be removed from tab text");
        assertEquals("application.log", session.getTitle());
    }

    @Test
    public void testTabContextMenuContainsExtendedActions() throws Exception {
        LogSession session = new LogSession("server.log", LogSession.SessionType.LOCAL);

        Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
        createTabMethod.setAccessible(true);
        Tab tab = (Tab) createTabMethod.invoke(controller, session);

        assertNotNull(tab.getContextMenu());
        boolean hasCloseRight = tab.getContextMenu().getItems().stream()
                .anyMatch(item -> "Close Tabs to the Right".equals(item.getText()));
        boolean hasCopyPath = tab.getContextMenu().getItems().stream()
                .anyMatch(item -> "Copy File Path".equals(item.getText()));
        boolean hasShowInExplorer = tab.getContextMenu().getItems().stream()
                .anyMatch(item -> "Show in File Explorer".equals(item.getText()));

        assertTrue(hasCloseRight, "Context menu should have Close Tabs to the Right");
        assertTrue(hasCopyPath, "Context menu should have Copy File Path");
        assertTrue(hasShowInExplorer, "Context menu should have Show in File Explorer");
    }

    @Test
    public void testRecentFilesFilterFieldInjected() throws Exception {
        java.lang.reflect.Field filterField = MainController.class.getDeclaredField("recentFilesFilterField");
        filterField.setAccessible(true);
        assertNotNull(filterField.get(controller), "recentFilesFilterField must be injected from FXML");
    }

    @Test
    public void testCanvasCopySelectedLines() {
        com.seeloggyplus.ui.canvas.CanvasLogViewer viewer = new com.seeloggyplus.ui.canvas.CanvasLogViewer();
        assertFalse(viewer.hasSelection());
        java.util.List<com.seeloggyplus.model.LogEntry> buffer = new java.util.ArrayList<>();
        buffer.add(new com.seeloggyplus.model.LogEntry(1, "2026-09-06 [INFO] Hello World"));
        buffer.add(new com.seeloggyplus.model.LogEntry(2, "2026-09-06 [ERROR] Something failed"));
        viewer.setTailBuffer(buffer);

        viewer.selectLine(0);
        assertTrue(viewer.hasSelection());

        java.util.concurrent.atomic.AtomicReference<String> clipboardContent = new java.util.concurrent.atomic.AtomicReference<>();
        javafx.application.Platform.runLater(() -> {
            viewer.copySelectedLines();
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            if (clipboard.hasString()) {
                clipboardContent.set(clipboard.getString());
            }
        });
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
        assertEquals("2026-09-06 [INFO] Hello World", clipboardContent.get());
    }

    @Test
    public void testCleanupFileResourcesSafeWhenCanvasIsNull() throws Exception {
        Method cleanupMethod = MainController.class.getDeclaredMethod("cleanupFileResources");
        cleanupMethod.setAccessible(true);
        assertDoesNotThrow(() -> cleanupMethod.invoke(controller));
    }

    @Test
    public void testClearSearchSafeWhenCanvasIsNull() throws Exception {
        Method clearSearchMethod = MainController.class.getDeclaredMethod("clearSearch");
        clearSearchMethod.setAccessible(true);
        assertDoesNotThrow(() -> clearSearchMethod.invoke(controller));
    }

    @Test
    public void testCanvasArrowKeysMoveSelectionNotTab() {
        com.seeloggyplus.ui.canvas.CanvasLogViewer viewer = new com.seeloggyplus.ui.canvas.CanvasLogViewer();
        java.util.List<com.seeloggyplus.model.LogEntry> buffer = new java.util.ArrayList<>();
        buffer.add(new com.seeloggyplus.model.LogEntry(1, "Line 1: Hello World"));
        buffer.add(new com.seeloggyplus.model.LogEntry(2, "Line 2: Processing event"));
        buffer.add(new com.seeloggyplus.model.LogEntry(3, "Line 3: Operation done"));
        viewer.setTailBuffer(buffer);

        assertEquals(-1, viewer.getSelectedIndex());

        // Press DOWN arrow
        javafx.scene.input.KeyEvent downEvent = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.DOWN, false, false, false, false);
        viewer.handleKeyNavigation(downEvent);

        assertEquals(0, viewer.getSelectedIndex(), "First DOWN arrow selects line 0");
        assertTrue(downEvent.isConsumed(), "Arrow key event must be consumed so it does not switch tabs");

        // Press DOWN arrow again
        javafx.scene.input.KeyEvent downEvent2 = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.DOWN, false, false, false, false);
        viewer.handleKeyNavigation(downEvent2);

        assertEquals(1, viewer.getSelectedIndex(), "Second DOWN arrow selects line 1");
        assertTrue(downEvent2.isConsumed());

        // Press UP arrow
        javafx.scene.input.KeyEvent upEvent = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.UP, false, false, false, false);
        viewer.handleKeyNavigation(upEvent);

        assertEquals(0, viewer.getSelectedIndex(), "UP arrow moves selection back up to line 0");
        assertTrue(upEvent.isConsumed());
    }

    @Test
    public void testLogTabPaneEventFilterConsumesArrowKeys() throws Exception {
        java.lang.reflect.Field tabPaneField = MainController.class.getDeclaredField("logTabPane");
        tabPaneField.setAccessible(true);
        TabPane tabPane = (TabPane) tabPaneField.get(controller);

        Tab tab1 = new Tab("Tab 1");
        Tab tab2 = new Tab("Tab 2");
        tabPane.getTabs().addAll(tab1, tab2);
        tabPane.getSelectionModel().select(tab1);

        assertEquals(tab1, tabPane.getSelectionModel().getSelectedItem());

        // Fire DOWN key event on TabPane
        javafx.scene.input.KeyEvent downEvent = new javafx.scene.input.KeyEvent(
                tabPane, tabPane, javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                javafx.scene.input.KeyCode.DOWN, false, false, false, false);
        javafx.event.Event.fireEvent(tabPane, downEvent);

        assertTrue(downEvent.isConsumed(), "TabPane event filter must consume DOWN key event");
        assertEquals(tab1, tabPane.getSelectionModel().getSelectedItem(), "TabPane must NOT switch tabs on DOWN arrow key");

        // Fire UP key event on TabPane
        javafx.scene.input.KeyEvent upEvent = new javafx.scene.input.KeyEvent(
                tabPane, tabPane, javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                javafx.scene.input.KeyCode.UP, false, false, false, false);
        javafx.event.Event.fireEvent(tabPane, upEvent);

        assertTrue(upEvent.isConsumed(), "TabPane event filter must consume UP key event");
        assertEquals(tab1, tabPane.getSelectionModel().getSelectedItem(), "TabPane must NOT switch tabs on UP arrow key");
    }

    @Test
    public void testEnableTailFallbacksToRawConfigWhenParsingConfigIsNull() throws Exception {
        java.io.File tempLog = java.io.File.createTempFile("test-tail", ".log");
        tempLog.deleteOnExit();
        java.nio.file.Files.writeString(tempLog.toPath(), "2026-09-06 [INFO] Started\n");

        LogSession session = new LogSession("test-tail.log", LogSession.SessionType.LOCAL);
        session.setLocalFile(tempLog);

        Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
        onActiveSessionChangedMethod.setAccessible(true);
        onActiveSessionChangedMethod.invoke(controller, session);

        // Ensure currentParsingConfig can be null
        java.lang.reflect.Field cfgField = MainController.class.getDeclaredField("currentParsingConfig");
        cfgField.setAccessible(true);
        cfgField.set(controller, null);

        Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
        enableTailMethod.setAccessible(true);
        assertDoesNotThrow(() -> enableTailMethod.invoke(controller));

        assertNotNull(cfgField.get(controller), "enableTail should auto-fallback to RAW parsing config instead of throwing error");

        // Cleanup tail service
        Method disableTailMethod = MainController.class.getDeclaredMethod("disableTail", boolean.class);
        disableTailMethod.setAccessible(true);
        disableTailMethod.invoke(controller, true);
    }

    @Test
    public void testToggleButtonIconChangesColorWhenActive() throws Exception {
        java.lang.reflect.Field tailButtonField = MainController.class.getDeclaredField("tailButton");
        tailButtonField.setAccessible(true);
        ToggleButton tailButton = (ToggleButton) tailButtonField.get(controller);
        assertNotNull(tailButton);
        assertTrue(tailButton.getGraphic() instanceof de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView);
        de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView icon = (de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView) tailButton.getGraphic();

        javafx.application.Platform.runLater(() -> {
            tailButton.setSelected(true);
            assertEquals(javafx.scene.paint.Color.WHITE, icon.getFill(), "Icon fill must be WHITE when toggle button is active");

            tailButton.setSelected(false);
            assertNotEquals(javafx.scene.paint.Color.WHITE, icon.getFill(), "Icon fill must not be WHITE when toggle button is inactive");
        });
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
    }
}
