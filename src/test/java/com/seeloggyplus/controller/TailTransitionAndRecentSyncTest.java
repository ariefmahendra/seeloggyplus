package com.seeloggyplus.controller;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.LogSession;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.ui.cell.RecentFileListCell;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.util.LineOffsetIndex;
import com.seeloggyplus.util.MappedFileReader;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering:
 * 1. Stream mode tail transition (clears previous logs, does not append, then runs tail).
 * 2. Clear recent log file closes corresponding tab pane(s).
 * 3. Recent log files list automatically activates/selects the row matching the open tab pane.
 * 4. Bottom bar line count is always accurate and updated with actual data.
 * 5. High performance & low RAM usage (clears buffers on tail reset, prevents leak).
 * 6. Edge cases (rapid toggles, clearing unopened recent file, downloaded remote file matching, empty file zero division).
 */
@ExtendWith(ApplicationExtension.class)
public class TailTransitionAndRecentSyncTest {

    private MainController controller;
    private TabPane logTabPane;
    private ListView<RecentFilesDto> recentFilesListView;
    private Label statusLabel;
    private Map<Tab, LogSession> sessionMap;

    @Start
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        Field tabPaneField = MainController.class.getDeclaredField("logTabPane");
        tabPaneField.setAccessible(true);
        logTabPane = (TabPane) tabPaneField.get(controller);

        Field recentListField = MainController.class.getDeclaredField("recentFilesListView");
        recentListField.setAccessible(true);
        recentFilesListView = (ListView<RecentFilesDto>) recentListField.get(controller);

        Field statusLabelField = MainController.class.getDeclaredField("statusLabel");
        statusLabelField.setAccessible(true);
        statusLabel = (Label) statusLabelField.get(controller);

        Field sessionMapField = MainController.class.getDeclaredField("sessionMap");
        sessionMapField.setAccessible(true);
        sessionMap = (Map<Tab, LogSession>) sessionMapField.get(controller);

        stage.setScene(new Scene(root));
        stage.show();
    }

    private Tab openSessionInController(LogSession session) throws Exception {
        Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
        createTabMethod.setAccessible(true);
        Tab tab = (Tab) createTabMethod.invoke(controller, session);
        sessionMap.put(tab, session);
        logTabPane.getTabs().add(tab);
        logTabPane.getSelectionModel().select(tab);

        Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
        onActiveSessionChangedMethod.setAccessible(true);
        onActiveSessionChangedMethod.invoke(controller, session);
        return tab;
    }

    @Test
    @DisplayName("Recent log file has no monitoring flag while remote tail is active")
    public void testRecentCell_DoesNotDisplayMonitoringFlag() {
        LogFile remoteFile = new LogFile();
        remoteFile.setName("application.log");
        remoteFile.setFilePath("/var/log/application.log");
        remoteFile.setRemote(true);

        class TestCell extends RecentFileListCell {
            TestCell() { super(null, () -> "/var/log/application.log"); }
            void render(RecentFilesDto item) { updateItem(item, false); }
        }
        TestCell cell = new TestCell();
        cell.render(new RecentFilesDto(remoteFile, null, "server"));

        assertNotNull(cell.getGraphic());
        javafx.scene.layout.VBox content = (javafx.scene.layout.VBox) cell.getGraphic();
        String renderedText = content.getChildren().stream()
                .filter(node -> node instanceof Label)
                .map(node -> ((Label) node).getText())
                .reduce("", (all, text) -> all + " " + text);
        assertFalse(renderedText.contains("Monitoring"));
    }

    // =========================================================================
    // 1. STREAM MODE TAIL TRANSITION CLEARS RATHER THAN APPENDS
    // =========================================================================
    @Test
    @DisplayName("Mode Stream: Clicking tail mode clears previous logs and runs tail afresh without appending")
    public void testStreamModeTailTransition_ClearsPreviousLogsAndDoesNotAppend() throws Exception {
        LogSession session = new LogSession("stream-service.log", LogSession.SessionType.REMOTE);
        session.setRemotePath("/var/log/stream-service.log");
        MockSSHService mockSsh = new MockSSHService();
        session.setSshService(mockSsh);

        Platform.runLater(() -> {
            try {
                openSessionInController(session);

                // Simulate stream receiving 5 initial lines before tail mode is engaged
                for (int i = 1; i <= 5; i++) {
                    session.getLiveTailList().add(new LogEntry((long) i, "Old Stream Line " + i));
                }
                session.getCanvasLogViewer().setTailBuffer(session.getLiveTailList());
                session.getCanvasLogViewer().refreshTail();

                assertEquals(5, session.getCanvasLogViewer().getTotalLines(), "Viewer should initially display 5 old stream lines");

                // Disable tail initially to simulate non-tail stream viewing
                Method disableTailMethod = MainController.class.getDeclaredMethod("disableTail", LogSession.class, boolean.class);
                disableTailMethod.setAccessible(true);
                disableTailMethod.invoke(controller, session, true);

                // Old stream lines must still be visible while tail is disabled
                assertEquals(5, session.getCanvasLogViewer().getTotalLines());

                // User now clicks Tail button (enableTail)
                Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
                enableTailMethod.setAccessible(true);
                enableTailMethod.invoke(controller);

                // Verify previous stream logs were CLEARED immediately
                assertEquals(0, session.getLiveTailList().size(), "Live tail list must be cleared on transition to tail");
                assertEquals(0, session.getCanvasLogViewer().getTotalLines(), "Canvas viewer must be cleared and reset to 0 lines");
                assertEquals("Line: 0 / 0", statusLabel.getText(), "Status label should reset to 0 / 0");

                // Now tailer starts streaming fresh lines
                assertNotNull(mockSsh.lineConsumer, "Tailer lineConsumer must be registered");
                mockSsh.lineConsumer.accept("Fresh Tail Line 1");
                mockSsh.lineConsumer.accept("Fresh Tail Line 2");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        // Give background queue a moment to flush to UI thread
        Thread.sleep(150);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            // Must contain ONLY 2 lines, NOT 5 + 2 = 7 lines!
            assertEquals(2, session.getLiveTailList().size(), "Must contain only newly received tail lines");
            assertEquals(2, session.getCanvasLogViewer().getTotalLines(), "Viewer total lines must be 2, without appending to old logs");
            assertTrue(statusLabel.getText().contains("2"), "Bottom bar line count must reflect 2 lines");
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // 2. CLEAR RECENT LOG FILE CLOSES CORRESPONDING TAB PANE
    // =========================================================================
    @Test
    @DisplayName("Clear Recent: Deleting selected file from recent files closes its open tab pane")
    public void testClearSelectedRecentFile_ClosesCorrespondingTabPane() throws Exception {
        String idA = UUID.randomUUID().toString();
        String idB = UUID.randomUUID().toString();

        LogFile logFileA = new LogFile();
        logFileA.setId(idA);
        logFileA.setName("service-a.log");
        logFileA.setFilePath("/var/log/service-a.log");
        logFileA.setRemote(true);

        LogFile logFileB = new LogFile();
        logFileB.setId(idB);
        logFileB.setName("service-b.log");
        logFileB.setFilePath("/var/log/service-b.log");
        logFileB.setRemote(true);

        LogSession sessionA = new LogSession("service-a.log", LogSession.SessionType.REMOTE);
        sessionA.setRemotePath(logFileA.getFilePath());
        sessionA.setLogFileRecord(logFileA);

        LogSession sessionB = new LogSession("service-b.log", LogSession.SessionType.REMOTE);
        sessionB.setRemotePath(logFileB.getFilePath());
        sessionB.setLogFileRecord(logFileB);

        Platform.runLater(() -> {
            try {
                Tab tabA = openSessionInController(sessionA);
                Tab tabB = openSessionInController(sessionB);

                assertEquals(2, logTabPane.getTabs().size(), "Both tabs should be open");

                // Close tabs for logFileA
                controller.closeTabsForLogFile(logFileA);

                // Tab A should be closed, Tab B should remain open
                assertEquals(1, logTabPane.getTabs().size(), "Only one tab should remain after closing file A");
                assertFalse(logTabPane.getTabs().contains(tabA), "Tab A must be closed");
                assertTrue(logTabPane.getTabs().contains(tabB), "Tab B must remain open");
                assertFalse(sessionMap.containsKey(tabA), "Session map must not retain Tab A");
                assertTrue(sessionMap.containsKey(tabB), "Session map must retain Tab B");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // 2b. CLEAR ALL RECENT FILES CLOSES ALL TAB PANES
    // =========================================================================
    @Test
    @DisplayName("Clear Recent: Clearing all recent files closes all open tabs and resets UI")
    public void testClearAllRecentFiles_ClosesAllTabPanesAndResetsUI() throws Exception {
        LogSession session1 = new LogSession("tab1.log", LogSession.SessionType.LOCAL);
        LogSession session2 = new LogSession("tab2.log", LogSession.SessionType.LOCAL);

        Platform.runLater(() -> {
            try {
                openSessionInController(session1);
                openSessionInController(session2);

                assertEquals(2, logTabPane.getTabs().size());

                // Execute close all tabs as done in handleClearRecentFiles
                controller.handleCloseAllTabs();

                assertEquals(0, logTabPane.getTabs().size(), "All tabs should be closed");
                assertTrue(sessionMap.isEmpty(), "Session map must be empty");
                assertEquals("Line: 0 / 0", statusLabel.getText(), "Status label must reset to Line: 0 / 0");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // 3. RECENT SIDEBAR AUTOMATICALLY HIGHLIGHTS CURRENT TAB
    // =========================================================================
    @Test
    @DisplayName("Recent Sidebar: Active tab selection synchronizes and selects the matching row in recent files")
    public void testActiveTabSelection_SynchronizesRecentSidebarRow() throws Exception {
        LogFile logFile1 = new LogFile();
        logFile1.setId("id-file-1");
        logFile1.setName("app1.log");
        logFile1.setFilePath("C:\\logs\\app1.log");
        logFile1.setRemote(false);

        LogFile logFile2 = new LogFile();
        logFile2.setId("id-file-2");
        logFile2.setName("app2.log");
        logFile2.setFilePath("C:\\logs\\app2.log");
        logFile2.setRemote(false);

        RecentFilesDto dto1 = new RecentFilesDto(logFile1, null, null);
        RecentFilesDto dto2 = new RecentFilesDto(logFile2, null, null);

        LogSession session1 = new LogSession("app1.log", LogSession.SessionType.LOCAL);
        session1.setLocalFile(new File("C:\\logs\\app1.log"));
        session1.setLogFileRecord(logFile1);

        LogSession session2 = new LogSession("app2.log", LogSession.SessionType.LOCAL);
        session2.setLocalFile(new File("C:\\logs\\app2.log"));
        session2.setLogFileRecord(logFile2);

        Platform.runLater(() -> {
            try {
                // Populate recent files list view
                recentFilesListView.setItems(FXCollections.observableArrayList(dto1, dto2));

                Tab tab1 = openSessionInController(session1);
                Tab tab2 = openSessionInController(session2);

                // Tab 2 is currently active
                controller.selectRecentFileForSession(session2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            RecentFilesDto selected = recentFilesListView.getSelectionModel().getSelectedItem();
            assertNotNull(selected, "A recent file row must be selected");
            assertEquals("app2.log", selected.logFile().getName(), "Active tab 2 must be selected in recent list");

            // Switch to tab 1
            controller.selectRecentFileForSession(session1);
        });

        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            RecentFilesDto selected = recentFilesListView.getSelectionModel().getSelectedItem();
            assertNotNull(selected, "A recent file row must be selected");
            assertEquals("app1.log", selected.logFile().getName(), "Switching to tab 1 must select app1.log in recent list");
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // 4. BOTTOM BAR LINE COUNT ACCURACY (OPEN FILE AND TAIL MODE)
    // =========================================================================
    @Test
    @DisplayName("Bottom Bar: Line count is always accurate on file load, scroll/jump, and live tail")
    public void testBottomBarLineCountAccuracy_FileLoadAndTailMode() throws Exception {
        // Create temp test log file with 25 lines
        File tempFile = File.createTempFile("status_bar_test", ".log");
        tempFile.deleteOnExit();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            sb.append("2026-09-19 10:00:00 Line content ").append(i).append("\n");
        }
        Files.writeString(tempFile.toPath(), sb.toString());

        MappedFileReader reader = new MappedFileReader(tempFile);
        LineOffsetIndex index = new LineOffsetIndex();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(tempFile, "r")) {
            index.buildIndex(raf, p -> {});
        }

        LogSession session = new LogSession("status-test.log", LogSession.SessionType.LOCAL);
        session.setLocalFile(tempFile);
        session.setReader(reader);
        session.setIndex(index);
        session.setTotalEntries(index.getLineCount());

        Platform.runLater(() -> {
            try {
                openSessionInController(session);
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                viewer.loadFile(reader, index);
                controller.updateBottomBarLineCount(session);

                // 1. Initial load line count verification
                assertEquals(25, viewer.getTotalLines());
                assertTrue(statusLabel.getText().contains("25"),
                        "Status label must reflect 25 lines on initial load. Actual: " + statusLabel.getText());
                assertTrue(statusLabel.getText().contains("Line: 1 / 25"),
                        "Status label should start at Line 1. Actual: " + statusLabel.getText());

                // 2. Jump to line 15
                viewer.jumpToLine(14); // 0-indexed 14 -> Line 15
                controller.updateBottomBarLineCount(session);
                assertTrue(statusLabel.getText().contains("Line: 15 / 25"),
                        "Status label must update to Line 15. Actual: " + statusLabel.getText());

                // 3. Add tail entries dynamically
                session.getLiveTailList().add(new LogEntry(26L, "Appended tail line 26"));
                session.getLiveTailList().add(new LogEntry(27L, "Appended tail line 27"));
                viewer.refreshTail();
                controller.updateBottomBarLineCount(session);

                assertEquals(27, viewer.getTotalLines());
                assertTrue(statusLabel.getText().contains("27"),
                        "Status label must reflect incremented line count (27). Actual: " + statusLabel.getText());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
        reader.close();
    }

    // =========================================================================
    // 5. EDGE CASES
    // =========================================================================
    @Test
    @DisplayName("Edge Case: Rapid toggling tail mode does not duplicate buffers or leak memory")
    public void testEdgeCase_RapidTailToggle_NoBufferDuplicationOrMemoryLeak() throws Exception {
        LogSession session = new LogSession("toggle-edge.log", LogSession.SessionType.REMOTE);
        session.setRemotePath("/var/log/toggle.log");
        MockSSHService mockSsh = new MockSSHService();
        session.setSshService(mockSsh);

        Platform.runLater(() -> {
            try {
                openSessionInController(session);
                Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
                enableTailMethod.setAccessible(true);
                Method disableTailMethod = MainController.class.getDeclaredMethod("disableTail", LogSession.class, boolean.class);
                disableTailMethod.setAccessible(true);

                // Rapid toggle cycle: Enable -> add lines -> Disable -> Enable -> add lines
                enableTailMethod.invoke(controller);
                mockSsh.lineConsumer.accept("Cycle 1 Line A");
                mockSsh.lineConsumer.accept("Cycle 1 Line B");

                disableTailMethod.invoke(controller, session, true);

                // Second enable: MUST clear old 2 lines from cycle 1
                enableTailMethod.invoke(controller);
                assertEquals(0, session.getLiveTailList().size(), "Tail buffer must be cleanly cleared on re-enable");
                assertEquals(0, session.getCanvasLogViewer().getTotalLines(), "Viewer must show 0 lines immediately after re-enable");

                // Third cycle
                mockSsh.lineConsumer.accept("Cycle 2 Line C");
                assertEquals(0, session.getTailSearchScannedUpTo(), "Search scan cursor must be reset to 0 to save RAM");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("Edge Case: Clearing a recent file that is not open in tabs executes safely without error")
    public void testEdgeCase_ClearRecentFileWithoutOpenTab_DoesNotCrash() throws Exception {
        LogFile unOpenFile = new LogFile();
        unOpenFile.setId("unopened-id");
        unOpenFile.setName("unopened.log");
        unOpenFile.setFilePath("C:\\logs\\unopened.log");
        unOpenFile.setRemote(false);

        LogSession openSession = new LogSession("active.log", LogSession.SessionType.LOCAL);
        openSession.setLocalFile(new File("C:\\logs\\active.log"));

        Platform.runLater(() -> {
            try {
                Tab tab = openSessionInController(openSession);
                assertEquals(1, logTabPane.getTabs().size());

                // Attempt to close tabs for a file that isn't open
                assertDoesNotThrow(() -> controller.closeTabsForLogFile(unOpenFile));

                // Active tab remains completely intact
                assertEquals(1, logTabPane.getTabs().size());
                assertTrue(logTabPane.getTabs().contains(tab));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("Edge Case: Downloaded remote file (local temp backing) correctly activates remote row in recent list")
    public void testEdgeCase_DownloadedRemoteFile_SyncsRemotePathInRecentList() throws Exception {
        SSHServerModel sshServer = new SSHServerModel();
        sshServer.setId("srv-99");
        sshServer.setName("ProdServer");

        LogFile remoteLogFile = new LogFile();
        remoteLogFile.setId("remote-db-id");
        remoteLogFile.setName("catalina.out");
        remoteLogFile.setFilePath("/var/log/tomcat/catalina.out");
        remoteLogFile.setRemote(true);
        remoteLogFile.setSshServerID("srv-99");

        RecentFilesDto remoteDto = new RecentFilesDto(remoteLogFile, null, "ProdServer");

        // Downloaded file has local temp file, but logFileRecord is the remote database record
        File tempFile = new File(System.getProperty("java.io.tmpdir"), "seeloggyplus-12345-catalina.out");
        LogSession downloadedSession = new LogSession("catalina.out (Remote)", LogSession.SessionType.LOCAL);
        downloadedSession.setLocalFile(tempFile);
        downloadedSession.setRemotePath("/var/log/tomcat/catalina.out");
        downloadedSession.setSshServer(sshServer);
        downloadedSession.setLogFileRecord(remoteLogFile);

        Platform.runLater(() -> {
            try {
                recentFilesListView.setItems(FXCollections.observableArrayList(remoteDto));
                openSessionInController(downloadedSession);

                controller.selectRecentFileForSession(downloadedSession);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            RecentFilesDto selected = recentFilesListView.getSelectionModel().getSelectedItem();
            assertNotNull(selected, "Downloaded remote file must find and highlight its remote recent entry");
            assertEquals("/var/log/tomcat/catalina.out", selected.logFile().getFilePath());
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("Edge Case: Empty session (0 lines) status update displays Line: 0 / 0 without NaN or Exception")
    public void testEdgeCase_EmptySessionStatusUpdate_DisplaysZeroWithoutException() throws Exception {
        LogSession emptySession = new LogSession("empty.log", LogSession.SessionType.LOCAL);

        Platform.runLater(() -> {
            try {
                openSessionInController(emptySession);
                controller.updateBottomBarLineCount(emptySession);

                assertEquals("Line: 0 / 0", statusLabel.getText(),
                        "Empty session must cleanly display 'Line: 0 / 0' without divide by zero or NaN");

                // Null session (all tabs closed)
                controller.updateBottomBarLineCount(null);
                assertEquals("Line: 0 / 0", statusLabel.getText(),
                        "Null session must cleanly display 'Line: 0 / 0'");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // 6. SCROLLER POSITION ACCURACY (OPEN FILE VS STREAM/TAIL MODE)
    // =========================================================================
    @Test
    @DisplayName("Scroller Position: Regular file open accurately positions scroller at top (0.0) and tracks jump positions")
    public void testScrollerPosition_RegularOpenFile_StartsAtTopAndTracksPosition() throws Exception {
        File tempFile = File.createTempFile("scroller_test", ".log");
        tempFile.deleteOnExit();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            sb.append("2026-09-19 10:00:00 Log line ").append(i).append("\n");
        }
        Files.writeString(tempFile.toPath(), sb.toString());

        MappedFileReader reader = new MappedFileReader(tempFile);
        LineOffsetIndex index = new LineOffsetIndex();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(tempFile, "r")) {
            index.buildIndex(raf, p -> {});
        }

        LogSession session = new LogSession("scroller-open.log", LogSession.SessionType.LOCAL);
        session.setLocalFile(tempFile);
        session.setReader(reader);
        session.setIndex(index);
        session.setTotalEntries(index.getLineCount());

        Platform.runLater(() -> {
            try {
                openSessionInController(session);
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                viewer.loadFile(reader, index);

                // Regular open must start at top: currentTopLine = 0 and vScrollBar value = 0.0
                assertEquals(0, viewer.getCurrentTopLine(), "Top line must be 0 for regular open");
                assertEquals(0.0, viewer.getVScrollBar().getValue(), 0.001, "Vertical scrollbar value must be 0.0 at top");
                assertEquals(0.0, viewer.getHScrollBar().getValue(), 0.001, "Horizontal scrollbar value must be 0.0 at left");
                assertFalse(viewer.isFollowTail(), "followTail must be false for normal file viewing");

                // Jump to line 40 (0-indexed 39)
                viewer.jumpToLine(39);
                assertEquals(39, viewer.getCurrentTopLine(), "Top line must jump to 39");
                assertEquals(39.0, viewer.getVScrollBar().getValue(), 0.001, "Scrollbar value must match top line 39.0");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
        reader.close();
    }

    @Test
    @DisplayName("Scroller Position: Stream/tail mode keeps scroller thumb pegged accurately to bottom")
    public void testScrollerPosition_StreamTailMode_FollowTailPegsToBottom() throws Exception {
        LogSession session = new LogSession("stream-scroller.log", LogSession.SessionType.REMOTE);
        session.setRemotePath("/var/log/stream-scroller.log");
        MockSSHService mockSsh = new MockSSHService();
        session.setSshService(mockSsh);

        Platform.runLater(() -> {
            try {
                openSessionInController(session);
                CanvasLogViewer viewer = session.getCanvasLogViewer();

                // Enable tail
                Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
                enableTailMethod.setAccessible(true);
                enableTailMethod.invoke(controller);

                assertTrue(viewer.isFollowTail(), "followTail should be true in tail mode");

                // Add 80 lines
                for (int i = 1; i <= 80; i++) {
                    mockSsh.lineConsumer.accept("Stream line " + i);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(150);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            CanvasLogViewer viewer = session.getCanvasLogViewer();
            double max = viewer.getVScrollBar().getMax();
            assertTrue(max > 0, "Max scroll should be positive with 80 lines");
            assertEquals(max, viewer.getVScrollBar().getValue(), 0.001,
                    "Vertical scrollbar must be pegged precisely to max value in follow-tail mode");
            assertEquals((long) max, viewer.getCurrentTopLine(),
                    "currentTopLine must match max scroll position");
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("Edge Case: User dragging scrollbar up pauses followTail without snapping back on new stream lines")
    public void testEdgeCase_Scroller_UserDragsScrollbarUp_PausesFollowTailAndKeepsPosition() throws Exception {
        LogSession session = new LogSession("drag-scroller.log", LogSession.SessionType.REMOTE);
        session.setRemotePath("/var/log/drag.log");
        MockSSHService mockSsh = new MockSSHService();
        session.setSshService(mockSsh);

        Platform.runLater(() -> {
            try {
                openSessionInController(session);
                CanvasLogViewer viewer = session.getCanvasLogViewer();

                Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
                enableTailMethod.setAccessible(true);
                enableTailMethod.invoke(controller);

                // Stream 60 lines initially
                for (int i = 1; i <= 60; i++) {
                    mockSsh.lineConsumer.accept("Line " + i);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(150);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            CanvasLogViewer viewer = session.getCanvasLogViewer();
            assertTrue(viewer.isFollowTail());

            // Simulate user dragging vertical scrollbar up to position 10.0
            viewer.getVScrollBar().setValue(10.0);

            // followTail should automatically become false
            assertFalse(viewer.isFollowTail(), "Dragging scrollbar up must auto-pause followTail");
            assertEquals(10, viewer.getCurrentTopLine(), "View should stay at dragged position 10");

            // More tail lines arrive from remote stream
            mockSsh.lineConsumer.accept("Additional line 61");
            mockSsh.lineConsumer.accept("Additional line 62");
        });

        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(150);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            CanvasLogViewer viewer = session.getCanvasLogViewer();
            // Crucial: viewer must NOT snap back to bottom because followTail is paused!
            assertEquals(10, viewer.getCurrentTopLine(),
                    "Viewer must remain at position 10 and not snap back to bottom while followTail is paused");
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("Edge Case: Clamping scroller position when buffer shrinks or resets prevents out-of-bounds scroll")
    public void testEdgeCase_Scroller_ClampingOnResetOrShrink() throws Exception {
        LogSession session = new LogSession("clamp.log", LogSession.SessionType.LOCAL);

        Platform.runLater(() -> {
            try {
                openSessionInController(session);
                CanvasLogViewer viewer = session.getCanvasLogViewer();

                for (int i = 1; i <= 50; i++) {
                    session.getLiveTailList().add(new LogEntry((long) i, "Line " + i));
                }
                viewer.setTailBuffer(session.getLiveTailList());
                viewer.refreshTail();

                // Scroll down
                viewer.getVScrollBar().setValue(25);
                assertEquals(25, viewer.getCurrentTopLine());

                // Reset view (0 lines)
                viewer.resetView();

                // Scroller must clamp cleanly to 0.0 with max = 0.0
                assertEquals(0.0, viewer.getVScrollBar().getMax(), 0.001);
                assertEquals(0.0, viewer.getVScrollBar().getValue(), 0.001);
                assertEquals(0, viewer.getCurrentTopLine());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // MOCK HELPER
    // =========================================================================
    private static class MockSSHService extends com.seeloggyplus.service.impl.SSHServiceImpl {
        java.util.function.Consumer<String> lineConsumer;

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void tailFile(String remoteFilePath, int initialLines,
                             java.util.function.Consumer<String> lineConsumer,
                             java.util.function.Consumer<String> errorConsumer) {
            this.lineConsumer = lineConsumer;
        }

        @Override
        public void stopTailing() {
            this.lineConsumer = null;
        }
    }
}
