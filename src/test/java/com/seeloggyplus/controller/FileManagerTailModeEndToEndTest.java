package com.seeloggyplus.controller;

import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.LogSession;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.LogParser;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End & Unit Tests:
 * Verifies opening files in TAIL mode from the Unified File Manager (both remote and local),
 * validating why previously tail contents did not appear on canvas (order of resetView vs setTailBuffer,
 * inactive session state, and local async race conditions), and assuring robust edge case handling.
 */
@ExtendWith(ApplicationExtension.class)
public class FileManagerTailModeEndToEndTest {

    private MainController controller;
    private TabPane logTabPane;
    private ListView<RecentFilesDto> recentFilesListView;
    private Label statusLabel;
    private ToggleButton tailButton;
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

        Field tailBtnField = MainController.class.getDeclaredField("tailButton");
        tailBtnField.setAccessible(true);
        tailButton = (ToggleButton) tailBtnField.get(controller);

        Field sessionMapField = MainController.class.getDeclaredField("sessionMap");
        sessionMapField.setAccessible(true);
        sessionMap = (Map<Tab, LogSession>) sessionMapField.get(controller);

        stage.setScene(new Scene(root));
        stage.show();
    }

    // --- MOCK SSH SERVICE ---
    private static class MockTailSSHService extends SSHServiceImpl {
        Consumer<String> lineConsumer;
        Consumer<String> errorConsumer;
        String tailedRemotePath;
        int downloadCount;
        volatile boolean blockDownload;
        final java.util.concurrent.CountDownLatch downloadStarted = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch releaseDownload = new java.util.concurrent.CountDownLatch(1);
        boolean connected = true;

        MockTailSSHService() {
            super();
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void tailFile(String remotePath, int numLines, Consumer<String> onLineReceived, Consumer<String> onError) {
            this.tailedRemotePath = remotePath;
            this.lineConsumer = onLineReceived;
            this.errorConsumer = onError;
        }

        @Override
        public boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount,
                                              LogParser.ProgressCallback progressCallback) {
            try {
                downloadCount++;
                downloadStarted.countDown();
                if (blockDownload) {
                    try {
                        releaseDownload.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                Files.writeString(new File(localPath).toPath(), "downloaded remote line\n");
                return true;
            } catch (java.io.IOException e) {
                return false;
            }
        }
    }

    private SSHServerModel createMockServer(String id, String name) {
        SSHServerModel s = new SSHServerModel(name, "192.168.1.100", 22, "admin");
        s.setId(id);
        s.setPassword("secret");
        return s;
    }

    private Tab addExistingSession(LogSession session) throws Exception {
        Method createTabMethod = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
        createTabMethod.setAccessible(true);
        Tab tab = (Tab) createTabMethod.invoke(controller, session);
        sessionMap.put(tab, session);
        logTabPane.getTabs().add(tab);
        logTabPane.getSelectionModel().select(tab);
        Method activeMethod = MainController.class.getDeclaredMethod("onActiveSessionChanged", LogSession.class);
        activeMethod.setAccessible(true);
        activeMethod.invoke(controller, session);
        return tab;
    }

    // =========================================================================
    // REGRESSION: CHANGING AN OPEN FILE TO TAIL REUSES ITS TAB
    // =========================================================================
    @Test
    @DisplayName("Regression Local Tail: opening an existing local file then enabling tail reuses the same tab and file")
    public void testLocalOpenFileThenTail_ReusesExistingTab() throws Exception {
        File file = File.createTempFile("seeloggyplus-open-local", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "existing local line\n");

        Platform.runLater(() -> invokePrivate("openLocalLogFile", new Class<?>[]{File.class, boolean.class}, file, false));
        awaitBackgroundWork();
        assertEquals(1, logTabPane.getTabs().size());
        Tab openedTab = logTabPane.getTabs().get(0);
        LogSession openedSession = sessionMap.get(openedTab);
        assertSame(file, openedSession.getLocalFile());

        Platform.runLater(() -> invokePrivate("enableTail", new Class<?>[0]));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, logTabPane.getTabs().size());
        assertSame(openedTab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(openedSession, sessionMap.get(openedTab));
        assertSame(file, openedSession.getLocalFile());
        assertTrue(openedSession.isTailModeEnabled());
    }

    @Test
    @DisplayName("Regression Remote Tail: opening a remote file then enabling tail reuses the same tab and SSH session")
    public void testRemoteOpenFileThenTail_ReusesExistingTab() throws Exception {
        SSHServerModel server = createMockServer("srv-open", "Open-Server");
        MockTailSSHService ssh = new MockTailSSHService();
        String remotePath = "/var/log/open-remote.log";

        Platform.runLater(() -> invokePrivate("openRemoteLogFile",
                new Class<?>[]{String.class, String.class, SSHServiceImpl.class, SSHServerModel.class},
                remotePath, "open-remote.log", ssh, server));
        awaitBackgroundWork();
        assertEquals(1, logTabPane.getTabs().size());
        Tab openedTab = logTabPane.getTabs().get(0);
        LogSession openedSession = sessionMap.get(openedTab);
        Platform.runLater(() -> logTabPane.getSelectionModel().select(openedTab));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(remotePath, openedSession.getRemotePath());
        assertSame(server, openedSession.getSshServer());
        assertNull(openedSession.getSshService(), "Open mode disconnects after download");

        File downloadedFile = openedSession.getLocalFile();
        assertNotNull(downloadedFile);
        Platform.runLater(() -> {
            invokePrivate("onActiveSessionChanged", new Class<?>[]{LogSession.class}, openedSession);
            invokePrivate("enableTail", new Class<?>[0]);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, logTabPane.getTabs().size());
        assertSame(openedTab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(openedSession, sessionMap.get(openedTab));
        assertEquals(remotePath, openedSession.getRemotePath());
        assertSame(downloadedFile, openedSession.getLocalFile());
        assertTrue(openedSession.isTailModeEnabled());
        assertNull(ssh.tailedRemotePath, "Open mode tails the downloaded local copy, not a new remote tab");
    }

    @Test
    @DisplayName("Regression Tail Isolation: enabling tail on the selected tab does not alter another open tab")
    public void testTailUsesSelectedTabOnly() throws Exception {
        File firstFile = File.createTempFile("seeloggyplus-tab-one", ".log");
        File secondFile = File.createTempFile("seeloggyplus-tab-two", ".log");
        firstFile.deleteOnExit();
        secondFile.deleteOnExit();
        Files.writeString(firstFile.toPath(), "one\n");
        Files.writeString(secondFile.toPath(), "two\n");

        Platform.runLater(() -> {
            invokePrivate("openLocalLogFile", new Class<?>[]{File.class, boolean.class}, firstFile, false);
            invokePrivate("openLocalLogFile", new Class<?>[]{File.class, boolean.class}, secondFile, false);
        });
        awaitBackgroundWork();
        assertEquals(2, logTabPane.getTabs().size());
        Tab secondTab = logTabPane.getTabs().get(1);
        LogSession firstSession = sessionMap.get(logTabPane.getTabs().get(0));
        LogSession secondSession = sessionMap.get(secondTab);

        Platform.runLater(() -> {
            logTabPane.getSelectionModel().select(secondTab);
            invokePrivate("enableTail", new Class<?>[0]);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(secondSession.isTailModeEnabled());
        assertFalse(firstSession.isTailModeEnabled());
        assertSame(secondTab, logTabPane.getSelectionModel().getSelectedItem());
    }

    @Test
    @DisplayName("E2E Local modes: OPEN twice then TAIL keeps one tab and one session")
    public void testLocalOpenAndTailModeTransitions_DoNotDuplicateTab() throws Exception {
        File file = File.createTempFile("seeloggyplus-mode-transition", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "line one\nline two\n");

        openLocal(file, false);
        awaitBackgroundWork();
        Tab originalTab = logTabPane.getTabs().get(0);
        LogSession originalSession = sessionMap.get(originalTab);

        openLocal(file, false);
        awaitBackgroundWork();
        assertEquals(1, logTabPane.getTabs().size());
        assertSame(originalSession, sessionMap.get(originalTab));
        assertSame(originalTab, logTabPane.getSelectionModel().getSelectedItem());

        Platform.runLater(() -> invokePrivate("enableTail", new Class<?>[0]));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, logTabPane.getTabs().size());
        assertSame(originalTab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(originalSession, sessionMap.get(originalTab));
        assertTrue(originalSession.isTailModeEnabled());
    }

    @Test
    @DisplayName("E2E Remote modes: OPEN then TAIL uses the downloaded open tab, not a second tab")
    public void testRemoteOpenAndTailModeTransitions_DoNotCreateOverlappingTab() throws Exception {
        SSHServerModel server = createMockServer("srv-mode", "Mode-Server");
        MockTailSSHService ssh = new MockTailSSHService();
        String remotePath = "/var/log/mode-transition.log";

        openRemote(remotePath, ssh, server);
        awaitBackgroundWork();
        Tab originalTab = logTabPane.getTabs().get(0);
        LogSession originalSession = sessionMap.get(originalTab);
        File downloadedFile = originalSession.getLocalFile();
        assertNotNull(downloadedFile);

        Platform.runLater(() -> {
            logTabPane.getSelectionModel().select(originalTab);
            invokePrivate("onActiveSessionChanged", new Class<?>[]{LogSession.class}, originalSession);
            invokePrivate("enableTail", new Class<?>[0]);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, logTabPane.getTabs().size());
        assertSame(originalTab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(originalSession, sessionMap.get(originalTab));
        assertSame(downloadedFile, originalSession.getLocalFile());
        assertEquals(remotePath, originalSession.getRemotePath());
        assertTrue(originalSession.isTailModeEnabled());
        assertNull(ssh.tailedRemotePath);
    }

    @Test
    @DisplayName("E2E Remote tail repeated: selecting TAIL twice does not create duplicate tabs")
    public void testRemoteTailRepeated_DoesNotDuplicateTab() throws Exception {
        SSHServerModel server = createMockServer("srv-repeat", "Repeat-Server");
        MockTailSSHService ssh = new MockTailSSHService();
        String remotePath = "/var/log/repeated-tail.log";

        invokeRemoteTail(remotePath, ssh, server);
        WaitForAsyncUtils.waitForFxEvents();
        Tab originalTab = logTabPane.getTabs().get(0);
        LogSession originalSession = sessionMap.get(originalTab);

        invokeRemoteTail(remotePath, ssh, server);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, logTabPane.getTabs().size());
        assertSame(originalTab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(originalSession, sessionMap.get(originalTab));
    }

    @Test
    @DisplayName("E2E Recent isolation: switching from tailed recent file to normal recent file does not leak tail mode")
    public void testRecentSelection_DoesNotLeakTailModeBetweenFiles() throws Exception {
        File tailedFile = File.createTempFile("seeloggyplus-recent-tailed", ".log");
        File normalFile = File.createTempFile("seeloggyplus-recent-normal", ".log");
        tailedFile.deleteOnExit();
        normalFile.deleteOnExit();
        Files.writeString(tailedFile.toPath(), "tail file\n");
        Files.writeString(normalFile.toPath(), "normal file\n");

        openLocal(tailedFile, true);
        awaitBackgroundWork();
        Tab tailedTab = logTabPane.getTabs().get(0);
        LogSession tailedSession = sessionMap.get(tailedTab);
        assertTrue(tailedSession.isTailModeEnabled());

        openLocal(normalFile, false);
        awaitBackgroundWork();
        Tab normalTab = logTabPane.getTabs().get(1);
        LogSession normalSession = sessionMap.get(normalTab);
        assertFalse(normalSession.isTailModeEnabled());

        LogFile normalLogFile = new LogFile();
        normalLogFile.setFilePath(normalFile.getAbsolutePath());
        normalLogFile.setName(normalFile.getName());
        normalLogFile.setRemote(false);
        RecentFilesDto normalRecent = new RecentFilesDto(normalLogFile, null, null);

        Platform.runLater(() -> invokePrivate("handleRecentFileSelected",
                new Class<?>[]{RecentFilesDto.class}, normalRecent));
        WaitForAsyncUtils.waitForFxEvents();
        assertSame(normalTab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(normalSession, sessionMap.get(normalTab));
        assertFalse(normalSession.isTailModeEnabled());
        assertFalse(tailButton.isSelected());

        Platform.runLater(() -> logTabPane.getSelectionModel().select(tailedTab));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(tailedSession.isTailModeEnabled());
        assertTrue(tailButton.isSelected());

        Platform.runLater(() -> logTabPane.getSelectionModel().select(normalTab));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(normalSession.isTailModeEnabled());
        assertFalse(tailButton.isSelected());
    }

    @Test
    @DisplayName("Regression exact reproduction: remote OPEN tab is reused after opening another remote TAIL tab")
    public void testRemoteOpenThenRemoteTailThenRecentOpen_ReusesDownloadedTab() throws Exception {
        SSHServerModel server = createMockServer("srv-repro", "Repro-Server");
        MockTailSSHService openSsh = new MockTailSSHService();
        MockTailSSHService tailSsh = new MockTailSSHService();
        String openPath = "/var/log/open-first.log";
        String tailPath = "/var/log/tail-second.log";

        invokePrivateOnFx("openRemoteLogFile",
                new Class<?>[]{String.class, String.class, SSHServiceImpl.class, SSHServerModel.class},
                openPath, "open-first.log", openSsh, server);
        awaitBackgroundWork();
        assertEquals(1, logTabPane.getTabs().size());
        Tab openTab = logTabPane.getTabs().get(0);
        LogSession openSession = sessionMap.get(openTab);
        assertEquals(openPath, openSession.getRemotePath());
        assertEquals(LogSession.SessionType.LOCAL, openSession.getSessionType());
        assertTrue(openSession.getLogFileRecord().isRemote());

        invokeRemoteTail(tailPath, tailSsh, server);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, logTabPane.getTabs().size());
        Tab tailTab = logTabPane.getTabs().get(1);
        LogSession tailSession = sessionMap.get(tailTab);
        assertTrue(tailSession.isTailModeEnabled());

        RecentFilesDto openRecent = new RecentFilesDto(openSession.getLogFileRecord(), null, server.getName());
        Platform.runLater(() -> invokePrivate("handleRecentFileSelected",
                new Class<?>[]{RecentFilesDto.class}, openRecent));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(2, logTabPane.getTabs().size(), "Clicking Recent OPEN must not create a third tab");
        assertSame(openTab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(openSession, sessionMap.get(openTab));
        assertFalse(openSession.isTailModeEnabled());
        assertTrue(tailSession.isTailModeEnabled());
        assertEquals(1, openSsh.downloadCount, "Initial remote OPEN must download exactly once");
    }

    @Test
    @DisplayName("E2E Cancel download: late progress and completion cannot reopen or glitch the loading overlay")
    public void testCancelRemoteDownload_IgnoresLateProgressAndCompletion() throws Exception {
        SSHServerModel server = createMockServer("srv-cancel", "Cancel-Server");
        MockTailSSHService ssh = new MockTailSSHService();
        ssh.blockDownload = true;

        invokePrivateOnFx("openRemoteLogFile",
                new Class<?>[]{String.class, String.class, SSHServiceImpl.class, SSHServerModel.class},
                "/var/log/cancelled.log", "cancelled.log", ssh, server);
        assertTrue(ssh.downloadStarted.await(2, java.util.concurrent.TimeUnit.SECONDS));
        Platform.runLater(controller::handleCancelProcessing);
        WaitForAsyncUtils.waitForFxEvents();
        ssh.releaseDownload.countDown();
        awaitBackgroundWork();

        assertEquals(0, logTabPane.getTabs().size(), "Cancelled download must not open a tab after it completes late");
        File cancelledFile = (File) getPrivateField("activeDownloadFile");
        assertNull(cancelledFile, "Cancelled download state must be cleared");
        Field overlayField = MainController.class.getDeclaredField("loadingOverlay");
        overlayField.setAccessible(true);
        javafx.scene.layout.Region overlay = (javafx.scene.layout.Region) overlayField.get(controller);
        assertFalse(overlay.isVisible(), "Late progress callback must not show the loading overlay again");
    }

    @Test
    @DisplayName("Regression Recent after close: cached remote OPEN file is reused without downloading again")
    public void testRecentClickAfterClosingRemoteTab_DoesNotDownloadAgain() throws Exception {
        SSHServerModel server = createMockServer("srv-cache", "Cache-Server");
        MockTailSSHService ssh = new MockTailSSHService();
        String remotePath = "/var/log/cached-open.log";

        invokePrivateOnFx("openRemoteLogFile",
                new Class<?>[]{String.class, String.class, SSHServiceImpl.class, SSHServerModel.class},
                remotePath, "cached-open.log", ssh, server);
        awaitBackgroundWork();
        assertEquals(1, ssh.downloadCount);
        LogSession openedSession = sessionMap.get(logTabPane.getTabs().get(0));
        RecentFilesDto recent = new RecentFilesDto(openedSession.getLogFileRecord(), null, server.getName());
        invokePrivateOnFx("closeSession", new Class<?>[]{LogSession.class}, openedSession);
        assertEquals(0, logTabPane.getTabs().size());

        Platform.runLater(() -> invokePrivate("handleRecentFileSelected",
                new Class<?>[]{RecentFilesDto.class}, recent));
        awaitBackgroundWork();

        assertEquals(1, logTabPane.getTabs().size());
        assertEquals(1, ssh.downloadCount, "Recent must reuse cached download after the original tab closes");
        LogSession reused = sessionMap.get(logTabPane.getTabs().get(0));
        assertSame(openedSession.getLocalFile(), reused.getLocalFile());
    }

    @Test
    @DisplayName("Regression Recent click: existing remote OPEN tab is selected without downloading again")
    public void testRecentClickOnOpenedRemoteFile_DoesNotDownloadAgain() throws Exception {
        SSHServerModel server = createMockServer("srv-no-redownload", "No-Redownload");
        MockTailSSHService ssh = new MockTailSSHService();
        String remotePath = "/var/log/already-downloaded.log";

        invokePrivateOnFx("openRemoteLogFile",
                new Class<?>[]{String.class, String.class, SSHServiceImpl.class, SSHServerModel.class},
                remotePath, "already-downloaded.log", ssh, server);
        awaitBackgroundWork();
        assertEquals(1, ssh.downloadCount);
        Tab openedTab = logTabPane.getTabs().get(0);
        LogSession openedSession = sessionMap.get(openedTab);

        RecentFilesDto recent = new RecentFilesDto(openedSession.getLogFileRecord(), null, server.getName());
        Platform.runLater(() -> invokePrivate("handleRecentFileSelected",
                new Class<?>[]{RecentFilesDto.class}, recent));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, logTabPane.getTabs().size());
        assertSame(openedTab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(openedSession, sessionMap.get(openedTab));
        assertEquals(1, ssh.downloadCount, "Clicking an already-open Recent file must not download again");
    }

    @Test
    @DisplayName("E2E Recent downloaded open file: selecting it reuses its existing tab beside a tail tab")
    public void testRecentDownloadedOpenFile_ReusesExistingTabWithoutDuplicate() throws Exception {
        File downloadedFile = File.createTempFile("seeloggyplus-recent-downloaded", ".log");
        downloadedFile.deleteOnExit();
        Files.writeString(downloadedFile.toPath(), "downloaded line\n");

        SSHServerModel server = createMockServer("srv-recent-open", "Recent-Server");
        MockTailSSHService ssh = new MockTailSSHService();
        String tailPath = "/var/log/active-tail.log";

        invokeRemoteTail(tailPath, ssh, server);
        WaitForAsyncUtils.waitForFxEvents();
        Tab tailTab = logTabPane.getTabs().get(0);
        LogSession tailSession = sessionMap.get(tailTab);
        assertTrue(tailSession.isTailModeEnabled());

        LogFile downloadedRecord = new LogFile();
        downloadedRecord.setFilePath(downloadedFile.getAbsolutePath());
        downloadedRecord.setName("downloaded-open.log");
        downloadedRecord.setRemote(true);
        downloadedRecord.setSshServerID(server.getId());
        LogSession openSession = new LogSession("downloaded-open.log", LogSession.SessionType.LOCAL);
        openSession.setLocalFile(downloadedFile);
        openSession.setRemotePath("/var/log/downloaded-open.log");
        openSession.setSshServer(server);
        openSession.setLogFileRecord(downloadedRecord);
        openSession.setTailModeEnabled(false);
        final Tab[] openTabRef = new Tab[1];
        Platform.runLater(() -> {
            try {
                openTabRef[0] = addExistingSession(openSession);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        Tab openTab = openTabRef[0];
        assertEquals(2, logTabPane.getTabs().size());

        RecentFilesDto recent = new RecentFilesDto(downloadedRecord, null, server.getName());
        Platform.runLater(() -> invokePrivate("handleRecentFileSelected",
                new Class<?>[]{RecentFilesDto.class}, recent));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(2, logTabPane.getTabs().size(), "Recent selection must not create a third tab");
        assertSame(openTab, logTabPane.getSelectionModel().getSelectedItem());
        assertSame(openSession, sessionMap.get(openTab));
        assertSame(downloadedFile, openSession.getLocalFile());
        assertFalse(openSession.isTailModeEnabled(), "Downloaded file opened normally must stay non-tail");
        assertTrue(tailSession.isTailModeEnabled(), "Existing tail tab must remain tail");
        assertFalse(tailTab == openTab);
    }

    @Test
    @DisplayName("E2E Recent normal local file: selecting a previously tailed file preserves its own session mode")
    public void testRecentSelection_UsesSessionTailStateAsSourceOfTruth() throws Exception {
        File first = File.createTempFile("seeloggyplus-recent-first", ".log");
        File second = File.createTempFile("seeloggyplus-recent-second", ".log");
        first.deleteOnExit();
        second.deleteOnExit();
        Files.writeString(first.toPath(), "first\n");
        Files.writeString(second.toPath(), "second\n");

        openLocal(first, true);
        awaitBackgroundWork();
        openLocal(second, false);
        awaitBackgroundWork();
        Tab firstTab = logTabPane.getTabs().get(0);
        Tab secondTab = logTabPane.getTabs().get(1);
        LogSession firstSession = sessionMap.get(firstTab);
        LogSession secondSession = sessionMap.get(secondTab);

        Platform.runLater(() -> logTabPane.getSelectionModel().select(firstTab));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(firstSession.isTailModeEnabled());

        Platform.runLater(() -> logTabPane.getSelectionModel().select(secondTab));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(secondSession.isTailModeEnabled());
        assertFalse(tailButton.isSelected());
        assertEquals(2, logTabPane.getTabs().size());
    }

    @Test
    @DisplayName("Temp cleanup deletes orphan downloads but preserves files used by open sessions")
    public void testTempCleanup_PreservesActiveDownloadAndDeletesOrphan() throws Exception {
        File activeDownload = new File(System.getProperty("java.io.tmpdir"), "seeloggyplus-active-" + UUID.randomUUID() + ".log");
        File orphanDownload = new File(System.getProperty("java.io.tmpdir"), "seeloggyplus-orphan-" + UUID.randomUUID() + ".log.partial");
        Files.writeString(activeDownload.toPath(), "active");
        Files.writeString(orphanDownload.toPath(), "orphan");
        activeDownload.deleteOnExit();
        orphanDownload.deleteOnExit();

        LogSession activeSession = new LogSession("active-download.log", LogSession.SessionType.LOCAL);
        activeSession.setLocalFile(activeDownload);
        Platform.runLater(() -> {
            try {
                addExistingSession(activeSession);
                invokePrivate("cleanupTempFiles", new Class<?>[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(activeDownload.exists(), "Cleanup must preserve a temp file used by an open session");
        assertFalse(orphanDownload.exists(), "Cleanup must delete orphan partial downloads");
    }

    private void invokePrivateOnFx(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Platform.runLater(() -> invokePrivate(name, parameterTypes, args));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void openLocal(File file, boolean tail) {
        Platform.runLater(() -> invokePrivate("openLocalLogFile",
                new Class<?>[]{File.class, boolean.class, boolean.class}, file, false, tail));
    }

    private void openRemote(String path, MockTailSSHService ssh, SSHServerModel server) {
        Platform.runLater(() -> invokePrivate("openRemoteLogFile",
                new Class<?>[]{String.class, String.class, SSHServiceImpl.class, SSHServerModel.class},
                path, new File(path).getName(), ssh, server));
    }

    private void invokeRemoteTail(String path, MockTailSSHService ssh, SSHServerModel server) {
        Platform.runLater(() -> invokePrivate("startRemoteTail",
                new Class<?>[]{String.class, SSHServiceImpl.class, SSHServerModel.class}, path, ssh, server));
    }

    private Object getPrivateField(String name) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private void invokePrivate(String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = MainController.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(controller, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void awaitBackgroundWork() throws Exception {
        Thread.sleep(700);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // =========================================================================
    // 1. E2E: REMOTE FILE OPENED IN TAIL MODE FROM FILE MANAGER
    // =========================================================================
    @Test
    @DisplayName("E2E Remote Tail: Opening remote file in TAIL mode binds buffer, activates session, and displays streamed lines")
    public void testRemoteTailModeFromFileManager_DisplaysLogsImmediatelyOnCanvas() throws Exception {
        String remotePath = "/var/log/application.log";
        SSHServerModel server = createMockServer("srv-1", "Prod-Server");
        MockTailSSHService mockSsh = new MockTailSSHService();

        Platform.runLater(() -> {
            try {
                // Call startRemoteTail as MainController does when FileManager returns OpenAction.TAIL
                Method startRemoteTailMethod = MainController.class.getDeclaredMethod(
                        "startRemoteTail", String.class, SSHServiceImpl.class, SSHServerModel.class);
                startRemoteTailMethod.setAccessible(true);
                startRemoteTailMethod.invoke(controller, remotePath, mockSsh, server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        // 1. Verify tab created and active
        assertEquals(1, logTabPane.getTabs().size(), "One tab should be created for the remote tail session");
        Tab activeTab = logTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(activeTab, "Active tab must be selected");
        LogSession session = sessionMap.get(activeTab);
        assertNotNull(session, "Session must exist for active tab");

        assertTrue(session.isActive(), "Session must be active so that scheduleTailFlush renders to canvas instead of queuing unread");
        assertTrue(session.isTailModeEnabled(), "Tail mode must be enabled on the session");
        assertTrue(tailButton.isSelected(), "Toolbar tail button must reflect active tail mode");

        CanvasLogViewer viewer = session.getCanvasLogViewer();
        assertNotNull(viewer, "CanvasLogViewer must be initialized");

        // 2. Stream lines from SSH tailer
        assertNotNull(mockSsh.lineConsumer, "SSH tailFile must have registered lineConsumer");
        Platform.runLater(() -> {
            mockSsh.lineConsumer.accept("2026-09-19 10:00:00 [INFO] Starting application backend...");
            mockSsh.lineConsumer.accept("2026-09-19 10:00:01 [INFO] Database pool initialized (10 connections)");
            mockSsh.lineConsumer.accept("2026-09-19 10:00:02 [WARN] High memory usage detected: 78%");
        });

        WaitForAsyncUtils.waitForFxEvents();

        // 3. Verify lines appear on canvas viewer (Crucial: Previously 0 due to resetView() wiping tailBuffer)
        assertEquals(3, session.getLiveTailList().size(), "Live tail list should contain 3 entries");
        assertEquals(3, viewer.getTotalLines(), "Canvas viewer totalLines MUST be 3 (not 0) when tailing remote file");
        assertEquals(3, viewer.getEffectiveLineCount(), "Canvas viewer effectiveLineCount must equal incoming tail lines");
        assertTrue(statusLabel.getText().contains("/ 3"), "Status label bottom bar should reflect actual line count (/ 3)");

        // 4. Stream additional line to verify ongoing real-time updates
        Platform.runLater(() -> {
            mockSsh.lineConsumer.accept("2026-09-19 10:00:03 [INFO] Incoming HTTP GET /api/v1/health 200 OK");
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(4, viewer.getTotalLines(), "Canvas viewer totalLines must advance to 4 on next streamed line");
        assertTrue(statusLabel.getText().contains("/ 4"), "Status label should update to show (/ 4)");
    }

    // =========================================================================
    // 2. E2E: LOCAL FILE OPENED IN TAIL MODE FROM FILE MANAGER
    // =========================================================================
    @Test
    @DisplayName("E2E Local Tail: Opening local file in TAIL mode indexes file then immediately enables tail without race conditions")
    public void testLocalTailModeFromFileManager_IndexesAndTailsCorrectly() throws Exception {
        File tempFile = File.createTempFile("seeloggyplus-test-local-mgr", ".log");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "Local Line 1\nLocal Line 2\nLocal Line 3\n");

        Platform.runLater(() -> {
            try {
                // Call openLocalLogFile with startTail = true (invoked when action == OpenAction.TAIL)
                Method openLocalMethod = MainController.class.getDeclaredMethod(
                        "openLocalLogFile", File.class, boolean.class, boolean.class);
                openLocalMethod.setAccessible(true);
                openLocalMethod.invoke(controller, tempFile, true, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for virtual thread indexing and platform runLater events to complete
        Thread.sleep(600);
        WaitForAsyncUtils.waitForFxEvents();

        Tab activeTab = logTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(activeTab);
        LogSession session = sessionMap.get(activeTab);
        assertNotNull(session);

        CanvasLogViewer viewer = session.getCanvasLogViewer();
        assertNotNull(viewer);

        // Initial 3 lines from file must be present
        assertTrue(viewer.getTotalLines() >= 3, "Viewer must display at least the initial 3 file lines");
        assertTrue(session.isTailModeEnabled(), "Tail mode must be engaged for the local file");

        // Simulate new lines arriving via tail service
        Platform.runLater(() -> {
            try {
                Method handleTailLine = MainController.class.getDeclaredMethod("handleTailLineBackground", LogSession.class, String.class);
                handleTailLine.setAccessible(true);
                handleTailLine.invoke(controller, session, "Local Line 4 (appended via tail)");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(4, viewer.getTotalLines(), "Canvas viewer must show 4 total lines (3 file lines + 1 tail line)");
    }

    // =========================================================================
    // 3. EDGE CASE: OPENING TAIL ON AN ALREADY OPEN FILE
    // =========================================================================
    @Test
    @DisplayName("Edge Case: Opening tail on an already opened tab selects tab and engages tail without duplicating")
    public void testOpenTailOnAlreadyOpenFile_FocusesExistingTabAndEnablesTail() throws Exception {
        String remotePath = "/var/log/already-open.log";
        SSHServerModel server = createMockServer("srv-1", "Prod-Server");
        MockTailSSHService mockSsh = new MockTailSSHService();

        // 1. Open remote file first
        Platform.runLater(() -> {
            try {
                Method startRemoteTailMethod = MainController.class.getDeclaredMethod(
                        "startRemoteTail", String.class, SSHServiceImpl.class, SSHServerModel.class);
                startRemoteTailMethod.setAccessible(true);
                startRemoteTailMethod.invoke(controller, remotePath, mockSsh, server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        int initialTabCount = logTabPane.getTabs().size();
        assertEquals(1, initialTabCount);

        // 2. Simulate user opening file manager again and selecting same remote file with TAIL
        Platform.runLater(() -> {
            try {
                Method startRemoteTailMethod = MainController.class.getDeclaredMethod(
                        "startRemoteTail", String.class, SSHServiceImpl.class, SSHServerModel.class);
                startRemoteTailMethod.setAccessible(true);
                startRemoteTailMethod.invoke(controller, remotePath, mockSsh, server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(initialTabCount, logTabPane.getTabs().size(), "Tab count should NOT increase when tailing an already opened tab");
        Tab selected = logTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(selected);
        LogSession session = sessionMap.get(selected);
        assertEquals(remotePath, session.getRemotePath());
    }

    // =========================================================================
    // 4. EDGE CASE: REMOTE TAIL SSH ERROR DISENGAGES TAIL GRACEFULLY
    // =========================================================================
    @Test
    @DisplayName("Edge Case: Remote tail SSH error disengages tail cleanly without throwing unhandled exceptions")
    public void testRemoteTailError_DisengagesTailCleanly() throws Exception {
        String remotePath = "/var/log/missing.log";
        SSHServerModel server = createMockServer("srv-err", "Err-Server");
        MockTailSSHService mockSsh = new MockTailSSHService();

        Platform.runLater(() -> {
            try {
                Method startRemoteTailMethod = MainController.class.getDeclaredMethod(
                        "startRemoteTail", String.class, SSHServiceImpl.class, SSHServerModel.class);
                startRemoteTailMethod.setAccessible(true);
                startRemoteTailMethod.invoke(controller, remotePath, mockSsh, server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        Tab activeTab = logTabPane.getSelectionModel().getSelectedItem();
        LogSession session = sessionMap.get(activeTab);
        assertTrue(session.isTailModeEnabled());

        // Trigger fatal SSH error (e.g. file not found on remote system)
        assertNotNull(mockSsh.errorConsumer);
        Platform.runLater(() -> {
            mockSsh.errorConsumer.accept("tail: cannot open '/var/log/missing.log' for reading: No such file or directory");
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Tail should be disabled safely
        assertFalse(session.isTailModeEnabled(), "Session tailModeEnabled should be false after error");
        assertFalse(tailButton.isSelected(), "Toolbar tail button should be unselected after error");
    }

    // =========================================================================
    // 5. EDGE CASE: INACTIVE DORMANT TAB ACCUMULATES UNREAD, THEN RENDERS ON FOCUS
    // =========================================================================
    @Test
    @DisplayName("Edge Case: Dormant tab accumulates unread lines, renders immediately when switched to active")
    public void testDormantTabTailBufferingAndActivationRender() throws Exception {
        // Tab 1: Remote tail
        String remotePath1 = "/var/log/app1.log";
        SSHServerModel server = createMockServer("srv-1", "Prod-Server");
        MockTailSSHService mockSsh1 = new MockTailSSHService();

        Platform.runLater(() -> {
            try {
                Method startRemoteTailMethod = MainController.class.getDeclaredMethod(
                        "startRemoteTail", String.class, SSHServiceImpl.class, SSHServerModel.class);
                startRemoteTailMethod.setAccessible(true);
                startRemoteTailMethod.invoke(controller, remotePath1, mockSsh1, server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        Tab tab1 = logTabPane.getTabs().get(0);
        LogSession session1 = sessionMap.get(tab1);

        // Tab 2: Remote tail (this becomes active)
        String remotePath2 = "/var/log/app2.log";
        MockTailSSHService mockSsh2 = new MockTailSSHService();
        Platform.runLater(() -> {
            try {
                Method startRemoteTailMethod = MainController.class.getDeclaredMethod(
                        "startRemoteTail", String.class, SSHServiceImpl.class, SSHServerModel.class);
                startRemoteTailMethod.setAccessible(true);
                startRemoteTailMethod.invoke(controller, remotePath2, mockSsh2, server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        Tab tab2 = logTabPane.getTabs().get(1);
        LogSession session2 = sessionMap.get(tab2);

        // Session 1 is now in background (inactive)
        assertFalse(session1.isActive(), "Tab 1 must be inactive while Tab 2 is selected");
        assertTrue(session2.isActive(), "Tab 2 must be active");

        // Send lines to Tab 1 (dormant)
        Platform.runLater(() -> {
            mockSsh1.lineConsumer.accept("Background Line 1");
            mockSsh1.lineConsumer.accept("Background Line 2");
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Background session buffered lines and incremented unread count
        assertEquals(2, session1.getLiveTailList().size(), "Live tail list should receive background lines");
        assertEquals(2, session1.getUnreadTailLines(), "Unread tail lines counter must increment");

        // Now switch back to Tab 1
        Platform.runLater(() -> {
            logTabPane.getSelectionModel().select(tab1);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(session1.isActive(), "Tab 1 is now active");
        assertEquals(0, session1.getUnreadTailLines(), "Unread lines reset upon switching to Tab 1");
        assertEquals(2, session1.getCanvasLogViewer().getTotalLines(), "Canvas viewer on Tab 1 must render the 2 lines");
    }

    // =========================================================================
    // 6. UNIT: UNIFIED FILE MANAGER DIALOG CONTROLLER SELECTION & TAIL ACTION
    // =========================================================================
    @Test
    @DisplayName("Unit: UnifiedFileManager enforces Tail for remote files and sets OpenAction.TAIL")
    public void testUnifiedFileManagerDialogSetsTailActionForRemote() throws Exception {
        UnifiedFileManagerDialogController dialogController = new UnifiedFileManagerDialogController();

        // Verify OpenAction enum contains OPEN, TAIL
        assertEquals(2, UnifiedFileManagerDialogController.OpenAction.values().length);
        assertEquals(UnifiedFileManagerDialogController.OpenAction.TAIL,
                UnifiedFileManagerDialogController.OpenAction.valueOf("TAIL"));

        // 1. Local file selection rejects TAIL and keeps OPEN
        FileInfo localFile = new FileInfo("test.log", "C:\\logs\\test.log", 1024L, false, System.currentTimeMillis(), FileInfo.SourceType.LOCAL);
        Field selectedFileField = UnifiedFileManagerDialogController.class.getDeclaredField("selectedFileResult");
        selectedFileField.setAccessible(true);
        selectedFileField.set(dialogController, localFile);

        Method handleTailMethod = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleTail");
        handleTailMethod.setAccessible(true);

        Platform.runLater(() -> {
            try {
                handleTailMethod.invoke(dialogController);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(UnifiedFileManagerDialogController.OpenAction.OPEN, dialogController.getOpenAction(),
                "Dialog OpenAction must remain OPEN when handleTail is called on a local file");

        // 2. Remote file selection with connected SSH sets TAIL
        FileInfo remoteFile = new FileInfo("remote.log", "/var/log/remote.log", 2048L, false, System.currentTimeMillis(), FileInfo.SourceType.REMOTE);
        selectedFileField.set(dialogController, remoteFile);
        Field sshServiceField = UnifiedFileManagerDialogController.class.getDeclaredField("activeSshService");
        sshServiceField.setAccessible(true);
        sshServiceField.set(dialogController, new MockTailSSHService());

        Platform.runLater(() -> {
            try {
                handleTailMethod.invoke(dialogController);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(UnifiedFileManagerDialogController.OpenAction.TAIL, dialogController.getOpenAction(),
                "Dialog OpenAction must be TAIL when handleTail is called on a remote file with active SSH");
    }
}
