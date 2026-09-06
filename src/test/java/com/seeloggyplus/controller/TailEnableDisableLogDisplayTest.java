package com.seeloggyplus.controller;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.LogSession;
import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
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

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
public class TailEnableDisableLogDisplayTest {

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
    public void testCreateTabForSessionBindsViewerTailBuffer() throws Exception {
        LogSession session = new LogSession("test-tail-bind.log", LogSession.SessionType.LOCAL);

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                Tab tab = (Tab) createTabMethod.invoke(controller, session);

                assertNotNull(tab);
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                assertNotNull(viewer, "CanvasLogViewer must be created for session");

                // Before adding tail lines
                assertEquals(0, viewer.getTotalLines());

                // Add line to session live tail list
                LogEntry entry = new LogEntry(1L, "2026-09-06 [INFO] Service started");
                session.getLiveTailList().add(entry);
                viewer.refreshTail();

                // CanvasLogViewer must immediately reflect the tail lines because tailBuffer is bound
                assertEquals(1, viewer.getTotalLines(), "Viewer totalLines must reflect session live tail list");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testRemoteTailDisablePreservesLogsAndDoesNotWipeScreen() throws Exception {
        LogSession session = new LogSession("remote-prod.log", LogSession.SessionType.REMOTE);
        session.setRemotePath("/var/log/remote-prod.log");
        TestSSHService mockSsh = new TestSSHService();
        session.setSshService(mockSsh);

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                Tab tab = (Tab) createTabMethod.invoke(controller, session);

                Field sessionMapField = MainController.class.getDeclaredField("sessionMap");
                sessionMapField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Tab, LogSession> sessionMap = (Map<Tab, LogSession>) sessionMapField.get(controller);
                sessionMap.put(tab, session);

                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);
                onActiveSessionChangedMethod.invoke(controller, session);

                // Simulate remote lines received
                session.getLiveTailList().add(new LogEntry(1L, "Remote Line 1"));
                session.getLiveTailList().add(new LogEntry(2L, "Remote Line 2"));
                session.getLiveTailList().add(new LogEntry(3L, "Remote Line 3"));

                CanvasLogViewer viewer = session.getCanvasLogViewer();
                viewer.refreshTail();
                assertEquals(3, viewer.getTotalLines());

                // Disable tail
                Method disableTailMethod = MainController.class.getDeclaredMethod("disableTail", LogSession.class, boolean.class);
                disableTailMethod.setAccessible(true);
                disableTailMethod.invoke(controller, session, false);

                // Crucial check: remote lines must NOT be cleared from liveTailList or viewer
                assertEquals(3, session.getLiveTailList().size(), "Remote logs must NOT be wiped when disabling tail");
                assertEquals(3, viewer.getTotalLines(), "Canvas viewer total lines must remain intact after disabling tail");

                // Re-enable tail: should not wipe liveTailList
                Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
                enableTailMethod.setAccessible(true);
                assertDoesNotThrow(() -> enableTailMethod.invoke(controller));

                assertEquals(3, session.getLiveTailList().size(), "Remote logs must still be preserved after re-enabling tail");
                assertEquals(3, viewer.getTotalLines());

                // Cleanup
                disableTailMethod.invoke(controller, session, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testLocalTailEnableAndDisablePreservesFileLines() throws Exception {
        File tempLog = File.createTempFile("test-local-tail", ".log");
        tempLog.deleteOnExit();
        Files.writeString(tempLog.toPath(), "Line 1\nLine 2\nLine 3\n");

        LogSession session = new LogSession(tempLog.getName(), LogSession.SessionType.LOCAL);
        session.setLocalFile(tempLog);

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                Tab tab = (Tab) createTabMethod.invoke(controller, session);

                Field sessionMapField = MainController.class.getDeclaredField("sessionMap");
                sessionMapField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Tab, LogSession> sessionMap = (Map<Tab, LogSession>) sessionMapField.get(controller);
                sessionMap.put(tab, session);

                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);
                onActiveSessionChangedMethod.invoke(controller, session);

                // Load file into session via MappedFileReader and LineOffsetIndex
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(tempLog, "r")) {
                    com.seeloggyplus.util.LineOffsetIndex index = new com.seeloggyplus.util.LineOffsetIndex();
                    index.buildIndex(raf, p -> {});
                    session.setIndex(index);
                    session.setReader(new com.seeloggyplus.util.MappedFileReader(tempLog));
                    session.setTotalEntries(index.getLineCount());
                }

                CanvasLogViewer viewer = session.getCanvasLogViewer();
                viewer.loadFile(session.getReader(), session.getIndex());
                assertEquals(3, viewer.getTotalLines(), "Initial file has 3 lines");

                // Enable tail
                Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
                enableTailMethod.setAccessible(true);
                enableTailMethod.invoke(controller);

                // File lines must remain displayed (not erased)
                assertEquals(3, viewer.getTotalLines(), "File lines must remain displayed when enabling tail");

                // Simulate incoming tail line
                Method handleTailLineMethod = MainController.class.getDeclaredMethod("handleTailLineBackground", LogSession.class, String.class);
                handleTailLineMethod.setAccessible(true);
                handleTailLineMethod.invoke(controller, session, "Line 4 (tail)");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                CanvasLogViewer viewer = session.getCanvasLogViewer();
                assertEquals(4, viewer.getTotalLines(), "Incoming tail line must be visible in viewer");

                // Disable tail
                Method disableTailMethod = MainController.class.getDeclaredMethod("disableTail", LogSession.class, boolean.class);
                disableTailMethod.setAccessible(true);
                disableTailMethod.invoke(controller, session, true);

                // All 4 lines must remain visible in viewer
                assertEquals(4, viewer.getTotalLines(), "All lines (file + tail) must remain visible when tail is disabled");

                if (session.getReader() != null) {
                    session.getReader().close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testRemoteTailResumedSessionBindsToTargetSessionNotCurrentSession() throws Exception {
        LogSession sessionA = new LogSession("remote-A.log", LogSession.SessionType.REMOTE);
        sessionA.setRemotePath("/var/log/A.log");
        TestSSHService mockSshA = new TestSSHService();
        sessionA.setSshService(mockSshA);

        LogSession sessionB = new LogSession("remote-B.log", LogSession.SessionType.REMOTE);
        sessionB.setRemotePath("/var/log/B.log");
        TestSSHService mockSshB = new TestSSHService();
        sessionB.setSshService(mockSshB);

        Platform.runLater(() -> {
            try {
                Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
                createTabMethod.setAccessible(true);
                Tab tabA = (Tab) createTabMethod.invoke(controller, sessionA);
                Tab tabB = (Tab) createTabMethod.invoke(controller, sessionB);

                Field sessionMapField = MainController.class.getDeclaredField("sessionMap");
                sessionMapField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Tab, LogSession> sessionMap = (Map<Tab, LogSession>) sessionMapField.get(controller);
                sessionMap.put(tabA, sessionA);
                sessionMap.put(tabB, sessionB);

                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);

                // Activate Session A
                onActiveSessionChangedMethod.invoke(controller, sessionA);

                // Enable tail on Session A (invokes startRemoteTailInternal)
                Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
                enableTailMethod.setAccessible(true);
                enableTailMethod.invoke(controller);

                assertNotNull(mockSshA.lineConsumer, "Session A must register a lineConsumer when tail is enabled");

                // Now switch active session to Session B
                onActiveSessionChangedMethod.invoke(controller, sessionB);

                // Verify currentSession is Session B
                Field currentSessionField = MainController.class.getDeclaredField("currentSession");
                currentSessionField.setAccessible(true);
                assertEquals(sessionB, currentSessionField.get(controller));

                // Session A receives a log line while Session B is active
                mockSshA.lineConsumer.accept("2026-09-06 [INFO] Event from Session A");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                // Assert Session A got the line
                assertEquals(1, sessionA.getLiveTailList().size(), "Session A must receive its own tail line");
                assertEquals("2026-09-06 [INFO] Event from Session A", sessionA.getLiveTailList().get(0).getRawLog());

                // Crucial assertion: Session B must NOT receive Session A's tail line!
                assertEquals(0, sessionB.getLiveTailList().size(), "Session B live list must NOT receive lines from Session A");
                assertEquals(0, sessionB.getCanvasLogViewer().getTotalLines(), "Session B viewer must remain empty");

                // Session A was in background, so its unread tail count must be 1
                assertEquals(1, sessionA.getUnreadTailLines(), "Session A unread tail lines must be incremented for dormant tab");

                // Cleanup
                Method disableTailMethod = MainController.class.getDeclaredMethod("disableTail", LogSession.class, boolean.class);
                disableTailMethod.setAccessible(true);
                disableTailMethod.invoke(controller, sessionA, true);
                disableTailMethod.invoke(controller, sessionB, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testTailSessionIsolationWhenSwitchingFilesAndTailingBoth() throws Exception {
        LogSession session1 = new LogSession("server1.log", LogSession.SessionType.REMOTE);
        session1.setRemotePath("/var/log/server1.log");
        TestSSHService mockSsh1 = new TestSSHService();
        session1.setSshService(mockSsh1);

        LogSession session2 = new LogSession("server2.log", LogSession.SessionType.REMOTE);
        session2.setRemotePath("/var/log/server2.log");
        TestSSHService mockSsh2 = new TestSSHService();
        session2.setSshService(mockSsh2);

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

                Method enableTailMethod = MainController.class.getDeclaredMethod("enableTail");
                enableTailMethod.setAccessible(true);

                // 1. Activate session 1 and enable tail
                onActiveSessionChangedMethod.invoke(controller, session1);
                enableTailMethod.invoke(controller);
                assertNotNull(mockSsh1.lineConsumer);

                // 2. Switch to session 2 and enable tail
                onActiveSessionChangedMethod.invoke(controller, session2);
                enableTailMethod.invoke(controller);
                assertNotNull(mockSsh2.lineConsumer);

                // 3. Emit lines to both services
                mockSsh1.lineConsumer.accept("Log line from server 1");
                mockSsh2.lineConsumer.accept("Log line from server 2");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                // Check session 2 (currently active)
                assertEquals(1, session2.getLiveTailList().size());
                assertEquals("Log line from server 2", session2.getLiveTailList().get(0).getRawLog());
                assertEquals(1, session2.getCanvasLogViewer().getTotalLines());

                // Check session 1 (in background)
                assertEquals(1, session1.getLiveTailList().size());
                assertEquals("Log line from server 1", session1.getLiveTailList().get(0).getRawLog());
                assertEquals(1, session1.getUnreadTailLines());

                // Switch back to session 1
                Method onActiveSessionChangedMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
                onActiveSessionChangedMethod.setAccessible(true);
                onActiveSessionChangedMethod.invoke(controller, session1);

                // Session 1 is now active, unread count reset, viewer reflects lines
                assertEquals(0, session1.getUnreadTailLines());
                assertEquals(1, session1.getCanvasLogViewer().getTotalLines());

                // Cleanup
                Method disableTailMethod = MainController.class.getDeclaredMethod("disableTail", LogSession.class, boolean.class);
                disableTailMethod.setAccessible(true);
                disableTailMethod.invoke(controller, session1, true);
                disableTailMethod.invoke(controller, session2, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    private static class TestSSHService extends com.seeloggyplus.service.impl.SSHServiceImpl {
        java.util.function.Consumer<String> lineConsumer;

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void tailFile(String remoteFilePath, int initialLines, java.util.function.Consumer<String> lineConsumer, java.util.function.Consumer<String> errorConsumer) {
            this.lineConsumer = lineConsumer;
        }

        @Override
        public void stopTailing() {
            this.lineConsumer = null;
        }
    }
}
