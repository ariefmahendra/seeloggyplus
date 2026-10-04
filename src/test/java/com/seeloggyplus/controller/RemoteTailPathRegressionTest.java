package com.seeloggyplus.controller;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.LogSession;
import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.LogParser;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.util.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class RemoteTailPathRegressionTest {

    private static final String DOWNLOAD_LIKE_REMOTE_PATH =
            "/tmp/seeloggyplus-1790869020727-already-downloaded.log";
    private MainController controller;
    private TabPane tabs;
    private Map<Tab, LogSession> sessions;
    private SSHServerModel server;
    private final RecordingSsh ssh = new RecordingSsh();

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        tabs = (TabPane) field("logTabPane");
        sessions = (Map<Tab, LogSession>) field("sessionMap");
        server = new SSHServerModel("Path-test-" + UUID.randomUUID(), "127.0.0.1", 22, "user");
        server.setId(UUID.randomUUID().toString());
        server.setPassword("test-password");
        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    @AfterEach
    void cleanup(FxRobot robot) {
        robot.interact(() -> {
            dismissErrors();
            new ArrayList<>(sessions.values()).forEach(LogSession::close);
            sessions.clear();
            AppTheme.setTheme(AppTheme.Theme.GRAPHITE);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void remoteFileWithDownloadLikeNameCanBeTailed(FxRobot robot) {
        startTail(robot, DOWNLOAD_LIKE_REMOTE_PATH);

        assertEquals(DOWNLOAD_LIKE_REMOTE_PATH, ssh.tailedPath);
        assertEquals(1, ssh.tailCalls);
        assertEquals(1, tabs.getTabs().size());
        LogSession session = sessions.get(tabs.getSelectionModel().getSelectedItem());
        assertEquals(LogSession.SessionType.REMOTE, session.getSessionType());
        assertEquals(DOWNLOAD_LIKE_REMOTE_PATH, session.getRemotePath());
        assertTrue(session.isTailModeEnabled());
        assertNoError(robot);
    }

    @Test
    void appPrefixInServerDirectoryDoesNotMakeItALocalPath(FxRobot robot) {
        String path = "/var/log/seeloggyplus-production/application.log";
        startTail(robot, path);

        assertEquals(path, ssh.tailedPath);
        assertEquals(1, ssh.tailCalls);
        assertNoError(robot);
    }

    @Test
    void recentRemoteFileWithDownloadLikeNameReusesItsServerTab(FxRobot robot) {
        LogSession session = remoteSession(DOWNLOAD_LIKE_REMOTE_PATH);
        robot.interact(() -> {
            Tab tab = (Tab) invoke("createTabForSession", new Class<?>[]{LogSession.class}, session);
            sessions.put(tab, session);
            tabs.getTabs().add(tab);
            tabs.getSelectionModel().select(tab);
            invoke("handleRecentFileSelected", new Class<?>[]{RecentFilesDto.class},
                    new RecentFilesDto(session.getLogFileRecord(), null, server.getName(), RecentFile.MODE_TAIL));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertNoError(robot);
        assertEquals(1, tabs.getTabs().size());
        assertSame(session, sessions.get(tabs.getSelectionModel().getSelectedItem()));
        assertEquals(0, ssh.downloadCalls);
    }

    @Test
    void resumingRemoteSessionPreservesItsDownloadLikeServerPath(FxRobot robot) throws Exception {
        LogSession session = remoteSession(DOWNLOAD_LIKE_REMOTE_PATH);
        setField("currentSession", session);
        setField("currentLogFromDb", session.getLogFileRecord());
        robot.interact(() -> invoke("startRemoteTailInternal", new Class<?>[0]));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(DOWNLOAD_LIKE_REMOTE_PATH, ssh.tailedPath);
        assertEquals(1, ssh.tailCalls);
        assertNoError(robot);
    }

    @Test
    void resumingWithoutServerLocationStopsBeforeCallingSsh(FxRobot robot) throws Exception {
        LogSession session = remoteSession(null);
        setField("currentSession", session);
        setField("currentLogFromDb", session.getLogFileRecord());
        robot.interact(() -> invoke("startRemoteTailInternal", new Class<?>[0]));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0, ssh.tailCalls, "Missing metadata must not be passed to an SSH tailer");
        assertFalse(session.isTailModeEnabled());
        DialogPane pane = errorDialog(robot);
        assertEquals("Unable to monitor log from server", pane.getHeaderText());
        assertTrue(pane.getContentText().contains("location on the server is missing"));
        assertRecoveryInstructions(pane);
    }

    @Test
    void localWindowsLocationsShowRecoveryStepsAndSeparateTechnicalDetails(FxRobot robot) {
        for (String path : List.of("C:\\Users\\user\\Temp\\seeloggyplus-123-app.log",
                "C:/Users/user/Temp/app.log", "\\\\computer\\share\\app.log")) {
            startTail(robot, path);

            assertEquals(0, ssh.tailCalls);
            assertTrue(tabs.getTabs().isEmpty());
            DialogPane pane = errorDialog(robot);
            assertEquals("Unable to monitor log from server", pane.getHeaderText());
            assertTrue(pane.getContentText().contains("this computer"));
            assertFalse(pane.getContentText().contains("Windows"));
            assertFalse(pane.getContentText().contains(path), "Raw paths belong in Details, not the explanation");
            assertRecoveryInstructions(pane);
            assertInstanceOf(TextInputControl.class, pane.getExpandableContent());
            TextInputControl details = (TextInputControl) pane.getExpandableContent();
            assertTrue(details.getText().contains(path));
            assertFalse(details.isEditable());
            assertFalse(pane.isExpanded(), "Technical details should be collapsed initially");
            robot.interact(RemoteTailPathRegressionTest::dismissErrors);
            WaitForAsyncUtils.waitForFxEvents();
        }
    }

    @Test
    void emptyServerLocationsExplainWhatIsMissing(FxRobot robot) {
        for (String path : new String[]{null, "", "   "}) {
            startTail(robot, path);
            assertEquals(0, ssh.tailCalls);
            DialogPane pane = errorDialog(robot);
            assertEquals("Unable to monitor log from server", pane.getHeaderText());
            assertTrue(pane.getContentText().contains("location on the server is missing"));
            assertRecoveryInstructions(pane);
            robot.interact(RemoteTailPathRegressionTest::dismissErrors);
            WaitForAsyncUtils.waitForFxEvents();
        }
    }

    @Test
    void recoveryDialogStaysCompactAndDetailsRemainVisibleInEveryTheme(FxRobot robot) {
        for (AppTheme.Theme theme : AppTheme.Theme.values()) {
            AtomicReference<Alert> reference = new AtomicReference<>();
            robot.interact(() -> {
                AppTheme.setTheme(theme);
                Alert alert = MainController.createRemotePathError("C:\\Users\\user\\" + "long-folder\\".repeat(20) + "app.log");
                reference.set(alert);
                alert.show();
            });
            WaitForAsyncUtils.waitForFxEvents();
            robot.interact(() -> {
                DialogPane pane = reference.get().getDialogPane();
                assertTrue(pane.getWidth() <= 600, "A raw path must not stretch the error dialog");
                assertTrue(pane.getHeight() <= 420, "The explanation should fit a compact dialog");
                pane.setExpanded(true);
            });
            WaitForAsyncUtils.waitForFxEvents();
            robot.interact(() -> {
                DialogPane pane = reference.get().getDialogPane();
                assertTrue(pane.getWidth() <= 600);
                assertTrue(pane.getHeight() <= 600);
                assertTrue(pane.getExpandableContent().isVisible());
                var button = pane.lookupButton(ButtonType.OK);
                assertTrue(button.localToScene(button.getBoundsInLocal()).getMaxY() <= pane.getHeight() + 1,
                        "The close button must remain inside the dialog after expanding Details");
                reference.get().close();
            });
        }
    }

    @Test
    void staleRecentLocalCopyExplainsHowToSelectOriginalFile(FxRobot robot) {
        LogFile record = remoteSession("C:\\missing-" + UUID.randomUUID() + "\\app.log").getLogFileRecord();
        robot.interact(() -> invoke("handleRecentFileSelected", new Class<?>[]{RecentFilesDto.class},
                new RecentFilesDto(record, null, server.getName(), RecentFile.MODE_TAIL)));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0, ssh.tailCalls);
        DialogPane pane = errorDialog(robot);
        assertEquals("Unable to monitor log from server", pane.getHeaderText());
        assertRecoveryInstructions(pane);
    }

    @Test
    void recentEntryWithoutServerLocationShowsRecoveryInstructions(FxRobot robot) {
        LogFile record = remoteSession(null).getLogFileRecord();
        robot.interact(() -> invoke("handleRecentFileSelected", new Class<?>[]{RecentFilesDto.class},
                new RecentFilesDto(record, null, server.getName(), RecentFile.MODE_TAIL)));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0, ssh.tailCalls);
        DialogPane pane = errorDialog(robot);
        assertEquals("Unable to monitor log from server", pane.getHeaderText());
        assertTrue(pane.getContentText().contains("location on the server is missing"));
        assertRecoveryInstructions(pane);
    }

    @Test
    void downloadedCopyRetainsOriginalServerPathDespiteAppPrefix(FxRobot robot) throws Exception {
        robot.interact(() -> invoke("openRemoteLogFile",
                new Class<?>[]{String.class, String.class, SSHServiceImpl.class, SSHServerModel.class},
                DOWNLOAD_LIKE_REMOTE_PATH, "seeloggyplus-1790869020727-already-downloaded.log", ssh, server));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() ->
                sessions.values().stream().anyMatch(session -> session.getLocalFile() != null)).get());

        LogSession session = sessions.get(tabs.getSelectionModel().getSelectedItem());
        assertEquals(LogSession.SessionType.LOCAL, session.getSessionType());
        assertTrue(session.getLocalFile().isFile());
        assertEquals(DOWNLOAD_LIKE_REMOTE_PATH, session.getRemotePath());
        assertEquals(DOWNLOAD_LIKE_REMOTE_PATH, session.getLogFileRecord().getFilePath());
        assertTrue(session.getLogFileRecord().isRemote());
        assertEquals(server.getId(), session.getLogFileRecord().getSshServerID());
        assertNotEquals(session.getLocalFile().getAbsolutePath(), session.getRemotePath());
        assertNoError(robot);
    }

    private LogSession remoteSession(String path) {
        LogFile record = new LogFile();
        record.setId(UUID.randomUUID().toString());
        record.setName("app.log");
        record.setFilePath(path);
        record.setRemote(true);
        record.setSshServerID(server.getId());
        LogSession session = new LogSession("app.log", LogSession.SessionType.REMOTE);
        session.setRemotePath(path);
        session.setSshServer(server);
        session.setSshService(ssh);
        session.setLogFileRecord(record);
        return session;
    }

    private void startTail(FxRobot robot, String path) {
        robot.interact(() -> invoke("startRemoteTail",
                new Class<?>[]{String.class, SSHServiceImpl.class, SSHServerModel.class}, path, ssh, server));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void assertRecoveryInstructions(DialogPane pane) {
        assertTrue(pane.getContentText().contains("File Manager"));
        assertTrue(pane.getContentText().contains("original file"));
        assertTrue(pane.getContentText().contains("Tail"));
    }

    private static List<DialogPane> errorDialogs() {
        List<DialogPane> panes = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            if (window.getScene() != null && window.getScene().getRoot().lookup(".dialog-pane") instanceof DialogPane pane) {
                panes.add(pane);
            }
        }
        return panes;
    }

    private static void dismissErrors() {
        errorDialogs().forEach(pane -> pane.getScene().getWindow().hide());
    }

    private static DialogPane errorDialog(FxRobot robot) {
        AtomicReference<DialogPane> found = new AtomicReference<>();
        robot.interact(() -> {
            List<DialogPane> panes = errorDialogs();
            assertEquals(1, panes.size(), "An actionable error should be shown");
            found.set(panes.get(0));
        });
        return found.get();
    }

    private static void assertNoError(FxRobot robot) {
        robot.interact(() -> assertTrue(errorDialogs().isEmpty(), "A genuine server path must not show a local-copy error"));
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

    private Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = MainController.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(controller, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class RecordingSsh extends SSHServiceImpl {
        String tailedPath;
        int tailCalls;
        int downloadCalls;

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void tailFile(String path, int lines, Consumer<String> onLine, Consumer<String> onError) {
            tailedPath = path;
            tailCalls++;
        }

        @Override
        public boolean downloadFileConcurrent(String remotePath, String localPath, int threads,
                                              LogParser.ProgressCallback callback) {
            downloadCalls++;
            try {
                Files.writeString(Path.of(localPath), "downloaded line\n");
                return true;
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
