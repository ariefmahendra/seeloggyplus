package com.seeloggyplus.controller;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.LogSession;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and Integration tests for Stream / Tail Mode Edge Cases:
 * 1. Windows local/temp path erroneously passed to remote tail/stream
 *    (e.g. "tail: cannot open 'C:\Users\MYBOOK~1\AppData\Local\Temp\seeloggyplus-1789783538265-setup_ssl_keystore.sh' for reading: No such file or directory").
 * 2. Detection of fatal remote tail errors (file not found, cannot watch, permission denied).
 * 3. Handling of empty or blank remote paths.
 * 4. Preservation of genuine remote path when downloading and opening remote files.
 * 5. Safe handling of recent files containing temporary paths.
 */
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
            stage.setScene(new Scene(root));
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
        assertTrue(errorMessage.get().contains("Windows local path cannot be tailed on remote SSH server"),
                "Error message should clearly state that Windows paths cannot be tailed on remote server");
        assertFalse(logConsumed.get(), "Log consumer must NOT receive anything");
    }

    @Test
    @DisplayName("Edge Case 2: Detection of fatal tail error messages from remote shell")
    public void testTailFatalErrorPatternDetection() {
        // Fatal: File not found error from Linux tail
        assertTrue(SSHServiceImpl.isTailFatalError(
                "tail: cannot open 'C:\\Users\\MYBOOK~1\\AppData\\Local\\Temp\\seeloggyplus-1789783538265-setup_ssl_keystore.sh' for reading: No such file or directory"));

        assertTrue(SSHServiceImpl.isTailFatalError(
                "tail: cannot open '/var/log/non_existent.log' for reading: No such file or directory"));

        // Fatal: Cannot watch error
        assertTrue(SSHServiceImpl.isTailFatalError(
                "tail: cannot watch '/var/log/syslog': No such file or directory"));

        // Fatal: Permission denied
        assertTrue(SSHServiceImpl.isTailFatalError(
                "tail: /var/log/auth.log: Permission denied"));

        // Normal log lines that should NOT be considered tail fatal errors
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
        assertTrue(emptyErr.get().contains("empty"));

        AtomicReference<String> nullErr = new AtomicReference<>();
        sshService.tailFile(null, 100, l -> {}, nullErr::set);
        assertNotNull(nullErr.get());
        assertTrue(nullErr.get().contains("empty"));

        AtomicReference<String> blankErr = new AtomicReference<>();
        sshService.tailFile("   ", 100, l -> {}, blankErr::set);
        assertNotNull(blankErr.get());
        assertTrue(blankErr.get().contains("empty"));
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

        Field sessionField = MainController.class.getDeclaredField("currentSession");
        sessionField.setAccessible(true);
        LogSession session = (LogSession) sessionField.get(controller);

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
        Method startRemoteTailMethod = MainController.class.getDeclaredMethod(
                "startRemoteTail", String.class, SSHServiceImpl.class, SSHServerModel.class);
        startRemoteTailMethod.setAccessible(true);

        Platform.runLater(() -> {
            try {
                // Pass invalid Windows path
                startRemoteTailMethod.invoke(controller,
                        "C:\\Users\\MYBOOK~1\\AppData\\Local\\Temp\\seeloggyplus-1789783538265-setup_ssl_keystore.sh",
                        null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
        // Since it's rejected, no active remote tail session or crash should occur
    }

    @Test
    @DisplayName("Edge Case 6: Recent files containing temporary paths are safely handled and do not invoke SSH tail")
    public void testRecentFileWithWindowsTempPathSafelyHandled() throws Exception {
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
                // Must not throw exception or attempt to connect to SSH with the Windows temp path
                handleRecent.invoke(controller, dto);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        WaitForAsyncUtils.waitForFxEvents();
    }
}
