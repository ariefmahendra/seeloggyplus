package com.seeloggyplus.app;

import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ssh.SshAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConnectTaskTest {

    private final FakeSshService ssh = new FakeSshService();

    @Test
    @DisplayName("an already connected service is reused without a new connect")
    void reusesAnAlreadyConnectedService() {
        ssh.connected = true;
        ServerConnectTask task = new ServerConnectTask(ssh, passwordServer("secret"), "secret");

        assertTrue(task.connect());
        assertEquals(0, ssh.connectCalls, "an already connected service must not connect again");
    }

    @Test
    @DisplayName("a missing saved password fails fast without touching SSH")
    void missingPasswordFailsFast() {
        ServerConnectTask task = new ServerConnectTask(ssh, passwordServer(null), null);

        assertFalse(task.connect());
        assertEquals(0, ssh.connectCalls);
    }

    @Test
    @DisplayName("a blank saved password fails fast as well")
    void blankPasswordFailsFast() {
        ServerConnectTask task = new ServerConnectTask(ssh, passwordServer("   "), "   ");

        assertFalse(task.connect());
        assertEquals(0, ssh.connectCalls);
    }

    @Test
    @DisplayName("key auth without a passphrase still attempts the connection")
    void keyAuthConnectsWithoutPassphrase() {
        SSHServerModel server = passwordServer(null);
        server.setAuthType(SSHServerModel.AUTH_KEY);
        ServerConnectTask task = new ServerConnectTask(ssh, server, null);

        assertTrue(task.connect());
        assertEquals(1, ssh.connectCalls);
        assertEquals(SshAuthConfig.AuthType.KEY, ssh.lastAuthConfig.type());
    }

    @Test
    @DisplayName("a successful connect goes through the shared flow with the auth config")
    void connectsThroughTheSharedFlow() {
        SSHServerModel server = passwordServer("secret");
        ServerConnectTask task = new ServerConnectTask(ssh, server, "secret");

        assertTrue(task.connect());
        assertEquals(1, ssh.connectCalls);
        assertNotNull(ssh.lastAuthConfig);
        assertEquals(SshAuthConfig.AuthType.PASSWORD, ssh.lastAuthConfig.type());
        assertEquals("user@host:22:secret", ssh.lastConnectCall);
    }

    @Test
    @DisplayName("a refused connection reports failure instead of an exception")
    void refusedConnectionReportsFailure() {
        ssh.connectResult = false;
        ServerConnectTask task = new ServerConnectTask(ssh, passwordServer("secret"), "secret");

        assertFalse(task.connect());
        assertNull(task.getException());
    }

    @Test
    @DisplayName("an unexpected connect error surfaces to the worker")
    void unexpectedErrorSurfaces() {
        ssh.connectError = new IllegalStateException("boom");
        ServerConnectTask task = new ServerConnectTask(ssh, passwordServer("secret"), "secret");

        IllegalStateException error =
                org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, task::connect);
        assertEquals("boom", error.getMessage());
    }

    private static SSHServerModel passwordServer(String password) {
        SSHServerModel server = new SSHServerModel("Prod", "host", 22, "user");
        server.setPassword(password);
        return server;
    }

    private static final class FakeSshService extends SSHServiceImpl {
        int connectCalls;
        boolean connected;
        boolean connectResult = true;
        RuntimeException connectError;
        SshAuthConfig lastAuthConfig;
        String lastConnectCall;

        @Override
        public void setAuthConfig(SshAuthConfig config) {
            this.lastAuthConfig = config;
            super.setAuthConfig(config);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean connect(String host, int port, String username, String password) {
            connectCalls++;
            lastConnectCall = username + "@" + host + ":" + port + ":" + password;
            if (connectError != null) {
                throw connectError;
            }
            if (connectResult) {
                connected = true;
            }
            return connectResult;
        }
    }
}
