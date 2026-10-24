package com.seeloggyplus.app;

import com.seeloggyplus.app.composition.LogServices;
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
import com.seeloggyplus.features.detail.presentation.LogDetailPaneController;
import com.seeloggyplus.features.search.presentation.SearchNavigator;
import com.seeloggyplus.shared.ui.AppTheme;
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
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
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
    public void storeBuildExplainsWhereUpdatesAreManaged() throws Exception {
        String previous = System.getProperty("seeloggyplus.distribution");
        try {
            System.setProperty("seeloggyplus.distribution", "store");
            WaitForAsyncUtils.asyncFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
                try {
                    loader.load();
                    MenuItem updates = (MenuItem) loader.getNamespace().get("storeMenuItem");
                    assertNotNull(updates);
                    assertEquals("Open Microsoft Store", updates.getText());
                    assertTrue(updates.isVisible());
                    assertFalse(updates.isDisable());
                    assertNotNull(updates.getOnAction());
                    assertNull(loader.getNamespace().get("autoCheckUpdatesMenuItem"));
                    assertNull(loader.getNamespace().get("autoDownloadUpdatesMenuItem"));
                    assertNull(loader.getNamespace().get("updateStatusLabel"));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).get();
        } finally {
            if (previous == null) System.clearProperty("seeloggyplus.distribution");
            else System.setProperty("seeloggyplus.distribution", previous);
        }
    }

    @Test
    public void portableHasNoUpdateControlsOrNotifications() throws Exception {
        String previous = System.getProperty("seeloggyplus.distribution");
        try {
            System.clearProperty("seeloggyplus.distribution");
            WaitForAsyncUtils.asyncFx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
                try {
                    loader.load();
                    MenuItem store = (MenuItem) loader.getNamespace().get("storeMenuItem");
                    assertNotNull(store);
                    assertFalse(store.isVisible());
                    assertNull(loader.getNamespace().get("checkForUpdatesMenuItem"));
                    assertNull(loader.getNamespace().get("autoCheckUpdatesMenuItem"));
                    assertNull(loader.getNamespace().get("autoDownloadUpdatesMenuItem"));
                    assertNull(loader.getNamespace().get("updateStatusLabel"));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).get();
        } finally {
            if (previous == null) System.clearProperty("seeloggyplus.distribution");
            else System.setProperty("seeloggyplus.distribution", previous);
        }
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
    public void testRecentPanelRootAndControllerAreBothInjected() throws Exception {
        Field panelField = MainController.class.getDeclaredField("recentPanel");
        panelField.setAccessible(true);
        Field controllerField = MainController.class.getDeclaredField("recentPanelController");
        controllerField.setAccessible(true);
        var recentController = (com.seeloggyplus.features.recent.presentation.RecentFilesPanelController) controllerField.get(controller);
        assertSame(recentController.getRoot(), panelField.get(controller));
    }

    @Test
    public void testFxmlDeclaresStylesheetsAndPreservesThemeSwitching() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            assertTrue(root.getStylesheets().stream().anyMatch(url -> url.endsWith("/style/theme.css")));
            assertTrue(root.getStylesheets().stream().anyMatch(url -> url.endsWith("/style/components.css")));
            Stage stage = new Stage();
            try {
                stage.setScene(AppTheme.scene(root)); stage.show();
                for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                    AppTheme.setTheme(theme); root.applyCss(); root.layout();
                    var label = (javafx.scene.control.Label) root.lookup("#statusLabel");
                    Color expected = theme == AppTheme.Theme.DARK ? Color.web("#d1d3d9") : Color.web("#192330");
                    assertEquals(expected, label.getTextFill(), theme.name());
                }
            } finally { stage.close(); AppTheme.setTheme(AppTheme.Theme.LIGHT); }
        }).get();
    }

    @Test
    public void testMainControllerKeepsInjectedLogServicesDuringFxmlInitialization() throws Exception {
        LogServices services = LogServices.createDefault();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        MainController injected = new MainController(services);
        loader.setControllerFactory(type -> {
            if (type == MainController.class)
                return injected;
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        assertNotNull(loader.load());
        assertSame(injected, loader.getController());
        for (var entry : java.util.Map.of("logFileService", services.files(),
                "parsingConfigService", services.parsingConfigs(), "logParserService", services.parser()).entrySet()) {
            Field field = MainController.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            assertSame(entry.getValue(), field.get(injected), "FXML initialization must retain injected ports");
        }
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
    public void testToolbarSearchNavigatorInjectedFromFxml() throws Exception {
        Field navigatorField = MainController.class.getDeclaredField("searchNavigator");
        navigatorField.setAccessible(true);
        assertInstanceOf(SearchNavigator.class, navigatorField.get(controller),
                "the toolbar SearchNavigator must be injected from MainView.fxml");
    }

    @Test
    public void testDetailPaneInjectedFromInclude() throws Exception {
        Field paneField = MainController.class.getDeclaredField("detailPane");
        paneField.setAccessible(true);
        Object pane = paneField.get(controller);
        assertNotNull(pane, "the fx:include fx:id=detailPane must inject the included root");

        Field detailControllerField = MainController.class.getDeclaredField("detailPaneController");
        detailControllerField.setAccessible(true);
        LogDetailPaneController detailPaneController = (LogDetailPaneController) detailControllerField
                .get(controller);
        assertNotNull(detailPaneController);
        assertSame(detailPaneController.getRoot(), pane);
    }

    @Test
    public void testViewRefreshMenuItemIsWiredToReload() throws Exception {
        Field refreshField = MainController.class.getDeclaredField("refreshMenuItem");
        refreshField.setAccessible(true);
        MenuItem refresh = (MenuItem) refreshField.get(controller);
        assertNotNull(refresh, "the View Refresh item must be injected from MainView.fxml");
        assertEquals("Refresh", refresh.getText());
        assertEquals(new KeyCodeCombination(KeyCode.F5), refresh.getAccelerator());
        assertNotNull(refresh.getOnAction(), "the View Refresh item must trigger a handler");
        assertDoesNotThrow(refresh::fire);
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

        KeyEvent downEvent = new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false);
        viewer.handleKeyNavigation(downEvent);

        assertEquals(0, viewer.getSelectedIndex(), "First DOWN arrow selects line 0");
        assertTrue(downEvent.isConsumed(), "Arrow key event must be consumed so it does not switch tabs");

        KeyEvent downEvent2 = new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false);
        viewer.handleKeyNavigation(downEvent2);

        assertEquals(1, viewer.getSelectedIndex(), "Second DOWN arrow selects line 1");
        assertTrue(downEvent2.isConsumed());

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

        KeyEvent downEvent = new KeyEvent(
                tabPane, tabPane, KeyEvent.KEY_PRESSED, "", "",
                KeyCode.DOWN, false, false, false, false);
        Event.fireEvent(tabPane, downEvent);

        assertTrue(downEvent.isConsumed(), "TabPane event filter must consume DOWN key event");
        assertEquals(tab1, tabPane.getSelectionModel().getSelectedItem(), "TabPane must NOT switch tabs on DOWN arrow key");

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

        Field cfgField = MainController.class.getDeclaredField("currentParsingConfig");
        cfgField.setAccessible(true);
        cfgField.set(controller, null);

        Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
        enableTailMethod.setAccessible(true);
        assertDoesNotThrow(() -> enableTailMethod.invoke(controller));

        assertNotNull(cfgField.get(controller), "enableTail should auto-fallback to RAW parsing config instead of throwing error");

        Method disableTailMethod = MainController.class.getDeclaredMethod("disableTail", boolean.class);
        disableTailMethod.setAccessible(true);
        disableTailMethod.invoke(controller, true);
    }

    @Test
    public void testToggleButtonIconFollowsTheThemeColours() throws Exception {
        Field tailButtonField = MainController.class.getDeclaredField("followTailButton");
        tailButtonField.setAccessible(true);
        ToggleButton tailButton = (ToggleButton) tailButtonField.get(controller);
        assertNotNull(tailButton);
        assertTrue(tailButton.getGraphic() instanceof FontAwesomeIconView);
        FontAwesomeIconView icon = (FontAwesomeIconView) tailButton.getGraphic();

        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setScene(AppTheme.scene(root));
            stage.show();
            tailButton.setDisable(false);
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
            root.applyCss();

            tailButton.setSelected(false);
            root.applyCss();
            assertEquals(Color.web("#192330"), icon.getFill(),
                    "A toolbar icon must follow the light chrome text token");

            tailButton.setSelected(true);
            root.applyCss();
            assertEquals(Color.web("#2f5eb9"), icon.getFill(),
                    "An enabled selected toolbar icon must use the light blue interaction token");

            AppTheme.setDark(true);
            root.applyCss();
            assertEquals(Color.web("#71a1fe"), icon.getFill(),
                    "Switching to dark must move an active icon to the brighter blue interaction token");

            AppTheme.setDark(false);
            stage.close();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
