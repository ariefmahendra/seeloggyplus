package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;

import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.ssh.SSHService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.control.Dialog;
import com.seeloggyplus.shared.ui.DialogPresenter;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Stream / Tail Mode Edge Case Tests")
public class StreamModeEdgeCasesTest {

    private static MainController controller;

    @Start
    public void start(Stage stage) throws Exception {
        if (controller == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            stage.setScene(AppTheme.scene(root));
            stage.show();
        }
    }

    @Test
    @DisplayName("Edge Case 1: Windows local/temp path passed to SSHService.tailFile must be rejected immediately")
    public void testWindowsLocalPathRejectedBySSHService() {
        SSHServiceImpl sshService = new SSHServiceImpl();
        AtomicReference<String> errorMessage = new AtomicReference<>();
        AtomicBoolean logConsumed = new AtomicBoolean(false);

        String windowsTempPath = "C:\\Users\\MYBOOK~1\\AppData\\Local\\Temp\\seeloggyplus-1789783538265-setup_ssl_keystore.sh";

        sshService.tailFile(windowsTempPath, 1000,
                line -> logConsumed.set(true),
                errorMessage::set);

        assertNotNull(errorMessage.get(), "Error consumer must receive an error for Windows path");
        assertTrue(errorMessage.get().contains("this computer"));
        assertTrue(errorMessage.get().contains("File Manager"));
        assertTrue(errorMessage.get().contains("original file"));
        assertTrue(errorMessage.get().contains("Tail"));
        assertFalse(errorMessage.get().contains(windowsTempPath), "Raw locations belong in diagnostic details or logs");
        assertFalse(logConsumed.get(), "Log consumer must NOT receive anything");
    }

    @Test
    @DisplayName("Edge Case 2: Detection of fatal tail error messages from remote shell")
    public void testTailFatalErrorPatternDetection() {
        assertTrue(SSHServiceImpl.isTailFatalError(
                "tail: cannot open 'C:\\Users\\MYBOOK~1\\AppData\\Local\\Temp\\seeloggyplus-1789783538265-setup_ssl_keystore.sh' for reading: No such file or directory"));

        assertTrue(SSHServiceImpl.isTailFatalError(
                "tail: cannot open '/var/log/non_existent.log' for reading: No such file or directory"));

        assertTrue(SSHServiceImpl.isTailFatalError(
                "tail: cannot watch '/var/log/syslog': No such file or directory"));

        assertTrue(SSHServiceImpl.isTailFatalError(
                "tail: /var/log/auth.log: Permission denied"));

        assertFalse(SSHServiceImpl.isTailFatalError(
                "2026-09-19 10:00:00 [INFO] Starting service setup_ssl_keystore.sh"));

        assertFalse(SSHServiceImpl.isTailFatalError(
                "2026-09-19 10:00:01 [WARN] Client cannot open connection to backend"));

        assertFalse(SSHServiceImpl.isTailFatalError(
                "INFO: tail latency is below 10ms"));

        assertFalse(SSHServiceImpl.isTailFatalError(null));
        assertFalse(SSHServiceImpl.isTailFatalError(""));
    }

    @Test
    @DisplayName("Edge Case 3: Empty or null remote path must trigger error consumer")
    public void testEmptyOrNullRemotePathHandling() {
        SSHServiceImpl sshService = new SSHServiceImpl();

        AtomicReference<String> emptyErr = new AtomicReference<>();
        sshService.tailFile("", 100, l -> {}, emptyErr::set);
        assertNotNull(emptyErr.get());
        assertTrue(emptyErr.get().contains("location on the server is missing"));

        AtomicReference<String> nullErr = new AtomicReference<>();
        sshService.tailFile(null, 100, l -> {}, nullErr::set);
        assertNotNull(nullErr.get());
        assertTrue(nullErr.get().contains("location on the server is missing"));

        AtomicReference<String> blankErr = new AtomicReference<>();
        sshService.tailFile("   ", 100, l -> {}, blankErr::set);
        assertNotNull(blankErr.get());
        assertTrue(blankErr.get().contains("location on the server is missing"));
    }

    @Test
    @DisplayName("Edge Case 4: Downloaded remote file preserves real remote path in session and database")
    public void testOpenDownloadedRemoteFilePreservesTrueRemotePath() throws Exception {
        File tempLocalFile = File.createTempFile("seeloggyplus-1789783538265-setup_ssl_keystore", ".sh");
        tempLocalFile.deleteOnExit();
        Files.writeString(tempLocalFile.toPath(), "#!/bin/bash\necho 'Setting up keystore'\n");

        SSHServerModel server = new SSHServerModel();
        server.setId("test-server-123");
        server.setName("Production-Bastion");
        server.setHost("192.168.1.100");
        server.setPort(22);
        server.setUsername("root");

        String genuineRemotePath = "/etc/ssl/scripts/setup_ssl_keystore.sh";

        Platform.runLater(() -> {
            try {
                Method openMethod = MainController.class.getDeclaredMethod(
                        "openLocalLogFile", File.class, String.class, SSHServerModel.class, String.class, boolean.class);
                openMethod.setAccessible(true);
                openMethod.invoke(controller, tempLocalFile, "setup_ssl_keystore.sh", server, genuineRemotePath, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        Field managerField = MainController.class.getDeclaredField("tabSessionManager");
        managerField.setAccessible(true);
        LogSession session = ((TabSessionManager) managerField.get(controller)).getActive();

        assertNotNull(session, "Session must be created");
        assertEquals(genuineRemotePath, session.getRemotePath(),
                "Session must preserve the true remote Unix path, NOT the local temp file path");

        assertNotNull(session.getLogFileRecord(), "LogFile record must be created");
        assertEquals(genuineRemotePath, session.getLogFileRecord().getFilePath(),
                "LogFile stored path must be the genuine remote Unix path");
        assertTrue(session.getLogFileRecord().isRemote(), "LogFile must be marked as remote");
    }

    @Test
    @DisplayName("Edge Case 5: startRemoteTail must reject Windows local paths")
    public void testStartRemoteTailRejectsWindowsPath() throws Exception {
        List<Dialog<?>> errors = new CopyOnWriteArrayList<>();
        controller.setDialogPresenter(recordingPresenter(errors));
        Method startRemoteTailMethod = MainController.class.getDeclaredMethod(
                "startRemoteTail", String.class, SSHService.class, SSHServerModel.class);
        startRemoteTailMethod.setAccessible(true);

        Platform.runLater(() -> {
            try {
                startRemoteTailMethod.invoke(controller,
                        "C:\\Users\\MYBOOK~1\\AppData\\Local\\Temp\\seeloggyplus-1789783538265-setup_ssl_keystore.sh",
                        null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !errors.isEmpty());
        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("Edge Case 6: Recent files containing temporary paths are safely handled and do not invoke SSH tail")
    public void testRecentFileWithWindowsTempPathSafelyHandled() throws Exception {
        List<Dialog<?>> errors = new CopyOnWriteArrayList<>();
        controller.setDialogPresenter(recordingPresenter(errors));
        LogFile staleLogFile = new LogFile();
        staleLogFile.setName("setup_ssl_keystore.sh");
        staleLogFile.setFilePath("C:\\Users\\MYBOOK~1\\AppData\\Local\\Temp\\seeloggyplus-non-existent.sh");
        staleLogFile.setRemote(true);
        staleLogFile.setSshServerID("server-nonexistent");

        RecentFilesDto dto = new RecentFilesDto(staleLogFile, null, "TestServer");

        Platform.runLater(() -> {
            try {
                Method handleRecent = MainController.class.getDeclaredMethod("handleRecentFileSelected", RecentFilesDto.class);
                handleRecent.setAccessible(true);

                handleRecent.invoke(controller, dto);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !errors.isEmpty());
        assertFalse(errors.isEmpty());
    }

    private static DialogPresenter recordingPresenter(List<Dialog<?>> target) {
        return new DialogPresenter() {
            @Override
            public void present(Stage dialog) {
            }

            @Override
            public <T> Optional<T> present(Dialog<T> dialog) {
                target.add(dialog);
                return Optional.empty();
            }
        };
    }
}
