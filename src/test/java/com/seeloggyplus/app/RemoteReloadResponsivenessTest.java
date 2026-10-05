package com.seeloggyplus.app;

import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.features.servers.application.ServerManagementService;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.features.servers.infrastructure.ServerManagementServiceImpl;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;

/**
 * Regression: reloading a remote tail must not block the JavaFX thread while the
 * SSH reconnect runs (this used to freeze the window with "Not Responding").
 */
@ExtendWith(ApplicationExtension.class)
class RemoteReloadResponsivenessTest {

    private MainController controller;
    private ServerManagementService serverService;
    private SSHServerModel server;

    /** Connect attempt that blocks until the test releases it (like a slow handshake). */
    static class SlowSshService extends SSHServiceImpl {
        final CountDownLatch connectStarted = new CountDownLatch(1);
        final CountDownLatch releaseConnect = new CountDownLatch(1);

        @Override
        public boolean connect(String host, int port, String username, String password) {
            connectStarted.countDown();
            try {
                releaseConnect.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return false;
        }

        @Override
        public String getLastConnectError() {
            return "simulated slow failure";
        }
    }

    @Start
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();

        serverService = new ServerManagementServiceImpl();
        server = new SSHServerModel("Reload-" + UUID.randomUUID().toString().substring(0, 8),
                "127.0.0.1", 22, "user");
        serverService.saveServer(server);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            try {
                serverService.deleteServer(server.getId());
            } catch (Exception ignored) {
            }
        }
    }

    private void setField(String name, Object value) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    @Test
    @DisplayName("reload remote tail keeps the UI responsive during a slow reconnect")
    void reloadDoesNotBlockTheFxThread() throws Exception {
        LogFile logFile = new LogFile();
        logFile.setId(UUID.randomUUID().toString());
        logFile.setSshServerID(server.getId());
        SlowSshService slow = new SlowSshService();
        setField("currentLogFromDb", logFile);
        setField("monitoringRemotePath", "/var/log/slow.log");
        setField("tailModeEnabled", true);
        setField("activeTailSshService", slow);

        Platform.runLater(() -> {
            try {
                Method reload =
                        MainController.class.getDeclaredMethod("reloadActiveRemoteTail");
                reload.setAccessible(true);
                reload.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(slow.connectStarted.await(3, TimeUnit.SECONDS),
                "the reconnect must actually start");

        try {
            // The connect is still blocked; the FX thread must stay free regardless
            // of how slow the CI machine is.
            long startedAt = System.nanoTime();
            CountDownLatch marker = new CountDownLatch(1);
            Platform.runLater(marker::countDown);
            assertTrue(marker.await(2, TimeUnit.SECONDS),
                    "FX thread must remain responsive while the reconnect runs");
            long blockedMs = (System.nanoTime() - startedAt) / 1_000_000;
            assertTrue(blockedMs < 500,
                    "reloading must run the SSH reconnect off the FX thread (blocked " + blockedMs + "ms)");
        } finally {
            slow.releaseConnect.countDown();
        }

        Thread.sleep(300);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
