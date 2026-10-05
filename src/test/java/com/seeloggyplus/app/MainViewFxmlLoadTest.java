package com.seeloggyplus.app;

import com.seeloggyplus.shared.session.LogSession;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.ui.canvas.CanvasLogViewer;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import org.testfx.util.WaitForAsyncUtils;

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

        Field tabPaneField = MainController.class.getDeclaredField("logTabPane");
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
        Field panelField = MainController.class.getDeclaredField("recentPanelController");
        panelField.setAccessible(true);
        Object panel = panelField.get(controller);
        Field filterField = panel.getClass().getDeclaredField("recentFilesFilterField");
        filterField.setAccessible(true);
        assertNotNull(filterField.get(panel), "recentFilesFilterField must be injected from FXML");
    }

    @Test
    public void testCanvasCopySelectedLines() {
        CanvasLogViewer viewer = new CanvasLogViewer();
        assertFalse(viewer.hasSelection());
        List<LogEntry> buffer = new ArrayList<>();
        buffer.add(new LogEntry(1, "2026-09-06 [INFO] Hello World"));
        buffer.add(new LogEntry(2, "2026-09-06 [ERROR] Something failed"));
        viewer.setTailBuffer(buffer);

        viewer.selectLine(0);
        assertTrue(viewer.hasSelection());

        AtomicReference<String> clipboardContent = new AtomicReference<>();
        Platform.runLater(() -> {
            viewer.copySelectedLines();
            Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasString()) {
                clipboardContent.set(clipboard.getString());
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
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
        CanvasLogViewer viewer = new CanvasLogViewer();
        List<LogEntry> buffer = new ArrayList<>();
        buffer.add(new LogEntry(1, "Line 1: Hello World"));
        buffer.add(new LogEntry(2, "Line 2: Processing event"));
        buffer.add(new LogEntry(3, "Line 3: Operation done"));
        viewer.setTailBuffer(buffer);

        assertEquals(-1, viewer.getSelectedIndex());

        // Press DOWN arrow
        KeyEvent downEvent = new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false);
        viewer.handleKeyNavigation(downEvent);

        assertEquals(0, viewer.getSelectedIndex(), "First DOWN arrow selects line 0");
        assertTrue(downEvent.isConsumed(), "Arrow key event must be consumed so it does not switch tabs");

        // Press DOWN arrow again
        KeyEvent downEvent2 = new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false);
        viewer.handleKeyNavigation(downEvent2);

        assertEquals(1, viewer.getSelectedIndex(), "Second DOWN arrow selects line 1");
        assertTrue(downEvent2.isConsumed());

        // Press UP arrow
        KeyEvent upEvent = new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.UP, false, false, false, false);
        viewer.handleKeyNavigation(upEvent);

        assertEquals(0, viewer.getSelectedIndex(), "UP arrow moves selection back up to line 0");
        assertTrue(upEvent.isConsumed());
    }

    @Test
    public void testLogTabPaneEventFilterConsumesArrowKeys() throws Exception {
        Field tabPaneField = MainController.class.getDeclaredField("logTabPane");
        tabPaneField.setAccessible(true);
        TabPane tabPane = (TabPane) tabPaneField.get(controller);

        Tab tab1 = new Tab("Tab 1");
        Tab tab2 = new Tab("Tab 2");
        tabPane.getTabs().addAll(tab1, tab2);
        tabPane.getSelectionModel().select(tab1);

        assertEquals(tab1, tabPane.getSelectionModel().getSelectedItem());

        // Fire DOWN key event on TabPane
        KeyEvent downEvent = new KeyEvent(
                tabPane, tabPane, KeyEvent.KEY_PRESSED, "", "",
                KeyCode.DOWN, false, false, false, false);
        Event.fireEvent(tabPane, downEvent);

        assertTrue(downEvent.isConsumed(), "TabPane event filter must consume DOWN key event");
        assertEquals(tab1, tabPane.getSelectionModel().getSelectedItem(), "TabPane must NOT switch tabs on DOWN arrow key");

        // Fire UP key event on TabPane
        KeyEvent upEvent = new KeyEvent(
                tabPane, tabPane, KeyEvent.KEY_PRESSED, "", "",
                KeyCode.UP, false, false, false, false);
        Event.fireEvent(tabPane, upEvent);

        assertTrue(upEvent.isConsumed(), "TabPane event filter must consume UP key event");
        assertEquals(tab1, tabPane.getSelectionModel().getSelectedItem(), "TabPane must NOT switch tabs on UP arrow key");
    }

    @Test
    public void testEnableTailFallbacksToRawConfigWhenParsingConfigIsNull() throws Exception {
        File tempLog = File.createTempFile("test-tail", ".log");
        tempLog.deleteOnExit();
        Files.writeString(tempLog.toPath(), "2026-09-06 [INFO] Started\n");

        LogSession session = new LogSession("test-tail.log", LogSession.SessionType.LOCAL);
        session.setLocalFile(tempLog);

        Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
        onActiveSessionChangedMethod.setAccessible(true);
        onActiveSessionChangedMethod.invoke(controller, session);

        // Ensure currentParsingConfig can be null
        Field cfgField = MainController.class.getDeclaredField("currentParsingConfig");
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
        Field tailButtonField = MainController.class.getDeclaredField("tailButton");
        tailButtonField.setAccessible(true);
        ToggleButton tailButton = (ToggleButton) tailButtonField.get(controller);
        assertNotNull(tailButton);
        assertTrue(tailButton.getGraphic() instanceof FontAwesomeIconView);
        FontAwesomeIconView icon = (FontAwesomeIconView) tailButton.getGraphic();

        Field fileField = MainController.class.getDeclaredField("currentFile");
        fileField.setAccessible(true);
        File tempFile = File.createTempFile("test-tail", ".log");
        tempFile.deleteOnExit();
        fileField.set(controller, tempFile);

        Platform.runLater(() -> {
            tailButton.setSelected(true);
            assertEquals(Color.WHITE, icon.getFill(), "Icon fill must be WHITE when toggle button is active");

            tailButton.setSelected(false);
            assertNotEquals(Color.WHITE, icon.getFill(), "Icon fill must not be WHITE when toggle button is inactive");
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
