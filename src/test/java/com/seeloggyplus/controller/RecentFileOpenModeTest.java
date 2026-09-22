package com.seeloggyplus.controller;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.LogSession;
import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.LogParser;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the Recent-list open-mode defect:
 *
 * <ul>
 *   <li>A Recent entry that was last opened in TAIL mode must stream again, not
 *       silently switch to download/OPEN mode.</li>
 *   <li>Opening a remote file that is already streaming must reuse the existing
 *       tab instead of starting a conflicting download (which surfaced as an error).</li>
 * </ul>
 */
@ExtendWith(ApplicationExtension.class)
class RecentFileOpenModeTest {

    private MainController controller;
    private TabPane logTabPane;
    private Map<Tab, LogSession> sessionMap;

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        Field tabPaneField = MainController.class.getDeclaredField("logTabPane");
        tabPaneField.setAccessible(true);
        logTabPane = (TabPane) tabPaneField.get(controller);

        Field sessionMapField = MainController.class.getDeclaredField("sessionMap");
        sessionMapField.setAccessible(true);
        sessionMap = (Map<Tab, LogSession>) sessionMapField.get(controller);

        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    // -------------------------------------------------------------------------
    // Mode detection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isTailModeRecent() is true only for entries persisted with TAIL mode")
    void tailModeDetection() {
        LogFile logFile = new LogFile();
        assertTrue(MainController.isTailModeRecent(
                new RecentFilesDto(logFile, null, "s", RecentFile.MODE_TAIL)));
        assertFalse(MainController.isTailModeRecent(
                new RecentFilesDto(logFile, null, "s", RecentFile.MODE_OPEN)));
        assertFalse(MainController.isTailModeRecent(
                new RecentFilesDto(logFile, null, "s", null)));
        assertFalse(MainController.isTailModeRecent(
                new RecentFilesDto(logFile, null, "s")), "3-arg (legacy) must default to OPEN");
        assertFalse(MainController.isTailModeRecent(null));
    }

    // -------------------------------------------------------------------------
    // Local file: Recent mode decides whether tail is engaged
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Recent local entry with mode TAIL reopens in tail mode")
    void localRecentInTailModeReopensTail() throws Exception {
        File file = File.createTempFile("seeloggyplus-recent-tail-mode", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "line one\nline two\n");

        LogFile logFile = new LogFile();
        logFile.setName(file.getName());
        logFile.setFilePath(file.getAbsolutePath());
        logFile.setRemote(false);

        RecentFilesDto dto = new RecentFilesDto(logFile, null, null, RecentFile.MODE_TAIL);
        onFx(() -> invoke("handleRecentFileSelected", new Class<?>[]{RecentFilesDto.class}, dto));
        Thread.sleep(700);
        WaitForAsyncUtils.waitForFxEvents();

        Tab tab = logTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(tab, "a tab must be opened");
        LogSession session = sessionMap.get(tab);
        assertNotNull(session);
        assertTrue(session.isTailModeEnabled(), "TAIL recent entry must reopen in tail mode");
    }

    @Test
    @DisplayName("Recent local entry without TAIL mode opens normally")
    void localRecentWithoutModeOpensNormally() throws Exception {
        File file = File.createTempFile("seeloggyplus-recent-open-mode", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "line one\nline two\n");

        LogFile logFile = new LogFile();
        logFile.setName(file.getName());
        logFile.setFilePath(file.getAbsolutePath());
        logFile.setRemote(false);

        RecentFilesDto dto = new RecentFilesDto(logFile, null, null); // defaults to OPEN
        onFx(() -> invoke("handleRecentFileSelected", new Class<?>[]{RecentFilesDto.class}, dto));
        Thread.sleep(700);
        WaitForAsyncUtils.waitForFxEvents();

        Tab tab = logTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(tab, "a tab must be opened");
        LogSession session = sessionMap.get(tab);
        assertNotNull(session);
        assertFalse(session.isTailModeEnabled(), "OPEN recent entry must not enable tail");
    }

    // -------------------------------------------------------------------------
    // Remote file: opening an already-streaming file must reuse the tab
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Opening a remote file already in tail mode reuses the tab and does not download")
    void remoteOpenReusesActiveTailTab() throws Exception {
        SSHServerModel server = new SSHServerModel("Prod", "10.0.0.9", 22, "root");
        server.setId("srv-reuse");
        server.setPassword("secret");

        MockSSH tailSsh = new MockSSH();
        MockSSH openSsh = new MockSSH();
        String remotePath = "/var/log/reuse.log";

        onFx(() -> invoke("startRemoteTail",
                new Class<?>[]{String.class, SSHServiceImpl.class, SSHServerModel.class},
                remotePath, tailSsh, server));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, logTabPane.getTabs().size());
        Tab tailTab = logTabPane.getTabs().get(0);
        LogSession tailSession = sessionMap.get(tailTab);
        assertTrue(tailSession.isTailModeEnabled());

        // User now tries to OPEN the same remote file (e.g. from the file manager).
        assertDoesNotThrow(() -> onFx(() -> invoke("openRemoteLogFile",
                new Class<?>[]{String.class, String.class, SSHServiceImpl.class, SSHServerModel.class, int.class},
                remotePath, "reuse.log", openSsh, server, 0)));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, logTabPane.getTabs().size(), "existing remote tail tab must be reused");
        assertSame(tailTab, logTabPane.getSelectionModel().getSelectedItem());
        assertEquals(0, openSsh.downloadCount, "opening an already-streaming file must not download");
    }

    // -------------------------------------------------------------------------

    private void onFx(Runnable action) {
        Platform.runLater(action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void invoke(String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = MainController.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(controller, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Minimal SSH double: never touches the network. */
    private static class MockSSH extends SSHServiceImpl {
        volatile int downloadCount;
        Consumer<String> lineConsumer;
        boolean connected = true;

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void tailFile(String remotePath, int lines, Consumer<String> onLine, Consumer<String> onError) {
            this.lineConsumer = onLine;
        }

        @Override
        public void stopTailing() {
            this.lineConsumer = null;
        }

        @Override
        public boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount,
                                              LogParser.ProgressCallback progressCallback) {
            downloadCount++;
            try {
                Files.writeString(new File(localPath).toPath(), "downloaded\n");
            } catch (Exception e) {
                return false;
            }
            return true;
        }
    }
}
