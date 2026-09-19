package com.seeloggyplus.controller;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.LogSession;
import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.util.IntArrayList;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
public class CanvasSearchFilteringTest {

    private MainController controller;

    @Start
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root));
        stage.show();
    }

    @Test
    public void testCanvasLogViewerFilteringAndLineMapping() throws Exception {
        Platform.runLater(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            List<LogEntry> tailBuffer = new ArrayList<>();
            tailBuffer.add(new LogEntry(1L, "Line 1: Starting application"));
            tailBuffer.add(new LogEntry(2L, "Line 2: ERROR Connection failed"));
            tailBuffer.add(new LogEntry(3L, "Line 3: Reconnecting in 5s"));
            tailBuffer.add(new LogEntry(4L, "Line 4: ERROR Database unreachable"));
            tailBuffer.add(new LogEntry(5L, "Line 5: Shutdown complete"));

            viewer.setTailBuffer(tailBuffer);
            assertEquals(5, viewer.getTotalLines(), "Initial total lines should be 5");
            assertEquals(5, viewer.getItemCount(), "Initial item count should be 5");
            assertNull(viewer.getFilteredIndexes(), "Filtered indexes should initially be null");

            // Filter lines matching "ERROR" (indices 1 and 3)
            IntArrayList filtered = new IntArrayList();
            filtered.add(1);
            filtered.add(3);
            viewer.setFilteredIndexes(filtered);

            assertEquals(2, viewer.getItemCount(), "Filtered item count must be 2");
            assertEquals(5, viewer.getTotalLines(), "Underlying total lines must remain 5");
            assertNotNull(viewer.getFilteredIndexes(), "getFilteredIndexes must not be null when filtered");
            assertEquals(2, viewer.getFilteredIndexes().size());

            // Jump to line in filtered view
            viewer.jumpToLine(0);
            assertEquals(0, viewer.getSelectedIndex());

            viewer.jumpToLine(1);
            assertEquals(1, viewer.getSelectedIndex());

            // Append new match
            viewer.appendToFilter(4);
            assertEquals(3, viewer.getItemCount(), "Item count should increase to 3 after appendToFilter");

            // Clear filter
            viewer.clearFilter();
            assertNull(viewer.getFilteredIndexes(), "Filtered indexes must be null after clearFilter");
            assertEquals(5, viewer.getItemCount(), "Item count must restore to 5 after clearFilter");
            assertEquals(5, viewer.getTotalLines(), "Total lines must restore to 5 after clearFilter");
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testMainControllerTailSearchFiltersCanvas() throws Exception {
        LogSession session = new LogSession("tail-search-filter.log", LogSession.SessionType.LOCAL);
        session.setTailModeEnabled(true);
        session.getLiveTailList().add(new LogEntry(1L, "INFO application initialized"));
        session.getLiveTailList().add(new LogEntry(2L, "ERROR database timeout"));
        session.getLiveTailList().add(new LogEntry(3L, "DEBUG ping successful"));
        session.getLiveTailList().add(new LogEntry(4L, "ERROR redis connection lost"));

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                Tab tab = (Tab) createTabMethod.invoke(controller, session);
                assertNotNull(tab);

                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);
                onActiveSessionChangedMethod.invoke(controller, session);

                Field searchFieldRef = MainController.class.getDeclaredField("searchField");
                searchFieldRef.setAccessible(true);
                TextField searchField = (TextField) searchFieldRef.get(controller);
                assertNotNull(searchField);

                searchField.setText("ERROR");

                Method performSearchMethod = MainController.class.getDeclaredMethod("performSearch");
                performSearchMethod.setAccessible(true);
                performSearchMethod.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for search thread pool to complete
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                assertNotNull(viewer, "CanvasLogViewer should be present");
                assertEquals(4, viewer.getItemCount(), "Canvas should display all 4 lines");
                assertEquals(4, viewer.getTotalLines(), "Total lines in session should remain 4");
                assertTrue(viewer.hasSearchHighlight(), "Search highlight should be active");

                Field srpField = MainController.class.getDeclaredField("searchResultPanel");
                srpField.setAccessible(true);
                com.seeloggyplus.ui.search.SearchResultPanel panel = (com.seeloggyplus.ui.search.SearchResultPanel) srpField.get(controller);
                assertNotNull(panel);
                assertEquals(2, panel.getItemCount(), "Panel should contain 2 matches");

                // Clear search
                Method clearSearchMethod = MainController.class.getDeclaredMethod("clearSearch");
                clearSearchMethod.setAccessible(true);
                clearSearchMethod.invoke(controller);

                assertNull(viewer.getFilteredIndexes(), "CanvasLogViewer filtered indexes should be null after clearSearch");
                assertEquals(4, viewer.getItemCount(), "Canvas should show all 4 lines after clearSearch");
                assertEquals(4, viewer.getTotalLines(), "Total lines should remain 4");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testTabSwitchingRestoresCanvasSearchFilter() throws Exception {
        LogSession session1 = new LogSession("tab1.log", LogSession.SessionType.LOCAL);
        session1.setTailModeEnabled(true);
        session1.getLiveTailList().add(new LogEntry(1L, "INFO start 1"));
        session1.getLiveTailList().add(new LogEntry(2L, "WARN warning 1"));
        session1.getLiveTailList().add(new LogEntry(3L, "ERROR error 1"));

        LogSession session2 = new LogSession("tab2.log", LogSession.SessionType.LOCAL);
        session2.setTailModeEnabled(true);
        session2.getLiveTailList().add(new LogEntry(1L, "INFO start 2"));
        session2.getLiveTailList().add(new LogEntry(2L, "INFO progress 2"));

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                Tab tab1 = (Tab) createTabMethod.invoke(controller, session1);
                Tab tab2 = (Tab) createTabMethod.invoke(controller, session2);

                Field sessionMapField = MainController.class.getDeclaredField("sessionMap");
                sessionMapField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Tab, LogSession> sessionMap = (Map<Tab, LogSession>) sessionMapField.get(controller);
                sessionMap.put(tab1, session1);
                sessionMap.put(tab2, session2);

                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);

                // Activate session 1 and search "ERROR"
                onActiveSessionChangedMethod.invoke(controller, session1);
                Field searchFieldRef = MainController.class.getDeclaredField("searchField");
                searchFieldRef.setAccessible(true);
                TextField searchField = (TextField) searchFieldRef.get(controller);
                searchField.setText("ERROR");

                Method performSearchMethod = MainController.class.getDeclaredMethod("performSearch");
                performSearchMethod.setAccessible(true);
                performSearchMethod.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                // Session 1 should display all 3 lines in canvas and have highlight
                assertEquals(3, session1.getCanvasLogViewer().getItemCount());
                assertEquals(3, session1.getCanvasLogViewer().getTotalLines());
                assertTrue(session1.getCanvasLogViewer().hasSearchHighlight());

                // Switch to session 2
                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);
                onActiveSessionChangedMethod.invoke(controller, session2);

                // Session 2 should have all 2 lines and no highlight
                assertEquals(2, session2.getCanvasLogViewer().getItemCount());
                assertEquals(2, session2.getCanvasLogViewer().getTotalLines());
                assertFalse(session2.getCanvasLogViewer().hasSearchHighlight());

                // Switch back to session 1
                onActiveSessionChangedMethod.invoke(controller, session1);

                // Session 1 should preserve all 3 lines and restore highlight
                assertEquals(3, session1.getCanvasLogViewer().getItemCount());
                assertEquals(3, session1.getCanvasLogViewer().getTotalLines());
                assertTrue(session1.getCanvasLogViewer().hasSearchHighlight());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testLocalFileSearchFiltersCanvas() throws Exception {
        java.io.File tempLog = java.io.File.createTempFile("test-local-search", ".log");
        tempLog.deleteOnExit();
        java.nio.file.Files.writeString(tempLog.toPath(),
                "2026-09-01 [INFO] Started application\n" +
                "2026-09-01 [WARN] Slow database query\n" +
                "2026-09-01 [ERROR] Failed to connect to payment gateway\n" +
                "2026-09-01 [INFO] Ping OK\n" +
                "2026-09-01 [ERROR] Out of memory\n");

        LogSession session = new LogSession("local-file.log", LogSession.SessionType.LOCAL);
        session.setLocalFile(tempLog);

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                Tab tab = (Tab) createTabMethod.invoke(controller, session);
                assertNotNull(tab);

                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);
                onActiveSessionChangedMethod.invoke(controller, session);

                Method loadFileMethod = MainController.class.getDeclaredMethod(
                        "loadFileWithParallelParsing", LogSession.class, java.io.File.class, com.seeloggyplus.model.LogFile.class, boolean.class, boolean.class, boolean.class);
                loadFileMethod.setAccessible(true);
                loadFileMethod.invoke(controller, session, tempLog, null, false, false, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for file indexing
        WaitForAsyncUtils.sleep(1000, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                assertNotNull(viewer);
                assertEquals(5, viewer.getTotalLines(), "Indexed file should have 5 lines");
                assertEquals(5, viewer.getItemCount());

                // Set search field to "ERROR" and perform search
                Field searchFieldRef = MainController.class.getDeclaredField("searchField");
                searchFieldRef.setAccessible(true);
                TextField searchField = (TextField) searchFieldRef.get(controller);
                searchField.setText("ERROR");

                Method performSearchMethod = MainController.class.getDeclaredMethod("performSearch");
                performSearchMethod.setAccessible(true);
                performSearchMethod.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for parallel search task to complete
        WaitForAsyncUtils.sleep(1000, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                assertEquals(5, viewer.getItemCount(), "All 5 lines should remain displayed in canvas");
                assertEquals(5, viewer.getTotalLines(), "Underlying file lines remain 5");
                assertTrue(viewer.hasSearchHighlight());

                Field srpField = MainController.class.getDeclaredField("searchResultPanel");
                srpField.setAccessible(true);
                com.seeloggyplus.ui.search.SearchResultPanel panel = (com.seeloggyplus.ui.search.SearchResultPanel) srpField.get(controller);
                assertNotNull(panel);
                assertEquals(2, panel.getItemCount(), "Panel shows 2 matches");

                // Clear search
                Method clearSearchMethod = MainController.class.getDeclaredMethod("clearSearch");
                clearSearchMethod.setAccessible(true);
                clearSearchMethod.invoke(controller);

                assertNull(viewer.getFilteredIndexes(), "Filter indexes cleared");
                assertEquals(5, viewer.getItemCount(), "All 5 lines restored");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }
}
