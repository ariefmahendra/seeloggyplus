package com.seeloggyplus.controller;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.LogSession;
import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.ui.search.SearchResultPanel;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End and Integration tests for the Search feature:
 * 1. Boolean conditions (AND, OR, NOT).
 * 2. SearchResultPanel appears on the right displaying matched rows.
 * 3. Clicking a matched row jumps CanvasLogViewer to the target line.
 * 4. CanvasLogViewer continues to display the full log (never hidden or filtered out).
 * 5. Works across local file, remote file, tail, and stream sessions.
 */
@ExtendWith(ApplicationExtension.class)
@DisplayName("Search Feature E2E and Unit Tests")
public class SearchFeatureE2ETest {

    private static MainController controller;

    @Start
    public void start(Stage stage) throws Exception {
        if (controller == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            stage.setScene(new Scene(root));
            stage.show();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String fieldName) throws Exception {
        Field field = MainController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(controller);
    }

    private void performSearch(String query) throws Exception {
        TextField searchField = getField("searchField");
        assertNotNull(searchField);
        searchField.setText(query);

        Method performSearchMethod = MainController.class.getDeclaredMethod("performSearch");
        performSearchMethod.setAccessible(true);
        performSearchMethod.invoke(controller);
    }

    // =========================================================================
    // 1. Boolean Search Query Logic (AND, OR, NOT)
    // =========================================================================
    @Test
    @DisplayName("Requirement 1: createBooleanSearchPredicate should accurately evaluate AND, OR, and NOT")
    public void testBooleanPredicateEvaluation() throws Exception {
        assertNotNull(controller, "Controller must be initialized");

        Method method = MainController.class.getDeclaredMethod("createBooleanSearchPredicate", String.class, boolean.class);
        method.setAccessible(true);

        // Test AND
        @SuppressWarnings("unchecked")
        Predicate<String> andPred = (Predicate<String>) method.invoke(controller, "ERROR AND database", false);
        assertTrue(andPred.test("2026-09-19 [ERROR] database unreachable"));
        assertFalse(andPred.test("2026-09-19 [ERROR] network timeout"));
        assertFalse(andPred.test("2026-09-19 [INFO] database connected"));

        // Test OR
        @SuppressWarnings("unchecked")
        Predicate<String> orPred = (Predicate<String>) method.invoke(controller, "database OR redis", false);
        assertTrue(orPred.test("2026-09-19 [INFO] connecting to database"));
        assertTrue(orPred.test("2026-09-19 [INFO] connecting to redis"));
        assertFalse(orPred.test("2026-09-19 [INFO] connecting to kafka"));

        // Test NOT
        @SuppressWarnings("unchecked")
        Predicate<String> notPred = (Predicate<String>) method.invoke(controller, "ERROR AND NOT timeout", false);
        assertTrue(notPred.test("2026-09-19 [ERROR] database crash"));
        assertFalse(notPred.test("2026-09-19 [ERROR] connection timeout"));
        assertFalse(notPred.test("2026-09-19 [INFO] all clear"));

        // Test Case Sensitivity
        @SuppressWarnings("unchecked")
        Predicate<String> casePred = (Predicate<String>) method.invoke(controller, "Error", true);
        assertTrue(casePred.test("Critical Error occurred"));
        assertFalse(casePred.test("critical error occurred"));
    }

    // =========================================================================
    // 2, 3, 4, 5. Local File Regular Open Search
    // =========================================================================
    @Test
    @DisplayName("Requirements 2, 3, 4, 5: Local File Open Search with AND/NOT")
    public void testLocalFileOpenSearchFlow() throws Exception {
        assertNotNull(controller, "Controller must be initialized");

        File tempLog = File.createTempFile("search-local-e2e", ".log");
        tempLog.deleteOnExit();
        Files.writeString(tempLog.toPath(),
                "Line 1: [INFO] System started\n" +
                "Line 2: [ERROR] Database connection lost\n" +
                "Line 3: [WARN] Retrying database connection\n" +
                "Line 4: [ERROR] Connection timeout to database\n" +
                "Line 5: [INFO] Heartbeat OK\n" +
                "Line 6: [ERROR] Database deadlock occurred\n");

        LogSession session = new LogSession("search-local-e2e.log", LogSession.SessionType.LOCAL);
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
                        "loadFileWithParallelParsing", LogSession.class, File.class, com.seeloggyplus.model.LogFile.class, boolean.class, boolean.class, boolean.class);
                loadFileMethod.setAccessible(true);
                loadFileMethod.invoke(controller, session, tempLog, null, false, false, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.sleep(1200, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        // Perform search with AND and NOT: "ERROR AND NOT timeout"
        // Matches: Line 2 (index 1) and Line 6 (index 5)
        Platform.runLater(() -> {
            try {
                performSearch("ERROR AND NOT timeout");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.sleep(1200, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                SearchResultPanel panel = getField("searchResultPanel");
                SplitPane splitPane = getField("searchSplitPane");

                // 4. In canvas log, ALL 6 lines must remain displayed (full log displayed)
                assertEquals(6, viewer.getTotalLines(), "Canvas log viewer total lines must remain 6");
                assertEquals(6, viewer.getItemCount(), "Canvas log viewer item count must remain 6 (full log displayed)");
                assertTrue(viewer.hasSearchHighlight(), "Search highlights should be active on canvas");

                // 2. Right panel appears displaying matched lines
                assertTrue(splitPane.getItems().contains(panel), "SearchResultPanel must be attached to SplitPane on the right");
                assertTrue(panel.isVisible(), "SearchResultPanel must be visible");
                assertEquals(2, panel.getItemCount(), "SearchResultPanel must list exactly 2 matching lines");

                // 3. Clicking a row in SearchResultPanel jumps to that line in canvas log
                // Click index 1 (corresponds to Line 6, global line index 5)
                panel.selectAndTriggerLine(1);
                assertEquals(5, viewer.getSelectedIndex(), "Canvas log must directly select line index 5 (Line 6)");

                // Click index 0 (corresponds to Line 2, global line index 1)
                panel.selectAndTriggerLine(0);
                assertEquals(1, viewer.getSelectedIndex(), "Canvas log must directly select line index 1 (Line 2)");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // 2, 3, 4, 5. Local File Tail Search
    // =========================================================================
    @Test
    @DisplayName("Requirements 2, 3, 4, 5: Local Tail Mode Search with OR")
    public void testLocalTailSearchFlow() throws Exception {
        assertNotNull(controller, "Controller must be initialized");

        LogSession session = new LogSession("tail-local-e2e.log", LogSession.SessionType.LOCAL);
        session.setTailModeEnabled(true);
        session.getLiveTailList().add(new LogEntry(1L, "Line 1: Server listening on port 8080"));
        session.getLiveTailList().add(new LogEntry(2L, "Line 2: WARN High memory usage"));
        session.getLiveTailList().add(new LogEntry(3L, "Line 3: ERROR Disk space critical"));
        session.getLiveTailList().add(new LogEntry(4L, "Line 4: INFO Healthcheck passed"));
        session.getLiveTailList().add(new LogEntry(5L, "Line 5: ERROR Out of memory"));

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                createTabMethod.invoke(controller, session);

                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);
                onActiveSessionChangedMethod.invoke(controller, session);

                // Search: "WARN OR Disk" -> matches index 1 (Line 2) and index 2 (Line 3)
                performSearch("WARN OR Disk");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.sleep(800, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                SearchResultPanel panel = getField("searchResultPanel");
                SplitPane splitPane = getField("searchSplitPane");

                // 4. Canvas log still displays all 5 tail lines
                assertEquals(5, viewer.getTotalLines(), "Canvas total lines must remain 5");
                assertEquals(5, viewer.getItemCount(), "Canvas item count must remain 5");
                assertTrue(viewer.hasSearchHighlight());

                // 2. Right panel appears with 2 matches
                assertTrue(splitPane.getItems().contains(panel), "Right panel must be shown in split pane");
                assertEquals(2, panel.getItemCount(), "Panel must show 2 matches");

                // 3. Clicking row 0 in panel jumps to line index 1 (Line 2)
                panel.selectAndTriggerLine(0);
                assertEquals(1, viewer.getSelectedIndex(), "Canvas must jump to Line 2 (index 1)");

                // Clicking row 1 in panel jumps to line index 2 (Line 3)
                panel.selectAndTriggerLine(1);
                assertEquals(2, viewer.getSelectedIndex(), "Canvas must jump to Line 3 (index 2)");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // 2, 3, 4, 5. Remote File Tail Stream Search
    // =========================================================================
    @Test
    @DisplayName("Requirements 2, 3, 4, 5: Remote Tail Stream Search with AND")
    public void testRemoteTailSearchFlow() throws Exception {
        assertNotNull(controller, "Controller must be initialized");

        LogSession session = new LogSession("remote-tail.log", LogSession.SessionType.REMOTE);
        session.setTailModeEnabled(true);
        session.getLiveTailList().add(new LogEntry(1L, "2026-09-19 [INFO] remote daemon started"));
        session.getLiveTailList().add(new LogEntry(2L, "2026-09-19 [ERROR] auth failed for user admin"));
        session.getLiveTailList().add(new LogEntry(3L, "2026-09-19 [INFO] accepted publickey for deploy"));
        session.getLiveTailList().add(new LogEntry(4L, "2026-09-19 [ERROR] auth failed for user test"));

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                createTabMethod.invoke(controller, session);

                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);
                onActiveSessionChangedMethod.invoke(controller, session);

                // Search: "ERROR AND admin" -> matches line index 1
                performSearch("ERROR AND admin");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.sleep(800, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                SearchResultPanel panel = getField("searchResultPanel");
                SplitPane splitPane = getField("searchSplitPane");

                // 4. Canvas shows full 4 lines
                assertEquals(4, viewer.getTotalLines());
                assertEquals(4, viewer.getItemCount());

                // 2. Right panel displays 1 match
                assertTrue(splitPane.getItems().contains(panel));
                assertEquals(1, panel.getItemCount());

                // 3. Click match
                panel.selectAndTriggerLine(0);
                assertEquals(1, viewer.getSelectedIndex(), "Canvas must jump to Line index 1");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // 2, 3, 4, 5. Remote File Open Search
    // =========================================================================
    @Test
    @DisplayName("Requirements 2, 3, 4, 5: Remote File Regular Open Search with OR")
    public void testRemoteFileOpenSearchFlow() throws Exception {
        assertNotNull(controller, "Controller must be initialized");

        File tempRemoteLog = File.createTempFile("seeloggyplus-remote-mock", ".log");
        tempRemoteLog.deleteOnExit();
        Files.writeString(tempRemoteLog.toPath(),
                "Remote Entry 1: [INFO] SSH Session Opened\n" +
                "Remote Entry 2: [WARN] TLS handshake slow\n" +
                "Remote Entry 3: [ERROR] Connection terminated unexpectedly\n");

        LogSession session = new LogSession("remote-app.log", LogSession.SessionType.REMOTE);
        session.setLocalFile(tempRemoteLog);

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                createTabMethod.invoke(controller, session);

                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);
                onActiveSessionChangedMethod.invoke(controller, session);

                Method loadFileMethod = MainController.class.getDeclaredMethod(
                        "loadFileWithParallelParsing", LogSession.class, File.class, com.seeloggyplus.model.LogFile.class, boolean.class, boolean.class, boolean.class);
                loadFileMethod.setAccessible(true);
                loadFileMethod.invoke(controller, session, tempRemoteLog, null, false, false, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.sleep(1200, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                // Search: "ERROR OR WARN" -> matches index 1 and 2
                performSearch("ERROR OR WARN");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.sleep(1200, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                SearchResultPanel panel = getField("searchResultPanel");
                SplitPane splitPane = getField("searchSplitPane");

                // 4. Canvas shows all 3 lines
                assertEquals(3, viewer.getTotalLines());
                assertEquals(3, viewer.getItemCount());

                // 2. Right panel displays 2 matches
                assertTrue(splitPane.getItems().contains(panel));
                assertEquals(2, panel.getItemCount());

                // 3. Jump on select
                panel.selectAndTriggerLine(1);
                assertEquals(2, viewer.getSelectedIndex());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }
}
