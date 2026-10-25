package com.seeloggyplus.features.ssh.infrastructure;

import com.seeloggyplus.shared.ssh.SshAuthConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SshServiceImplErrorTest {

    private SSHSessionManagerImpl sessionManager;

    @BeforeEach
    void createSessionManager() {
        sessionManager = new SSHSessionManagerImpl(Executors.newSingleThreadScheduledExecutor());
    }

    @AfterEach
    void closeSessionManager() {
        sessionManager.close();
    }

    @Test
    @DisplayName("connection failure exposes the underlying reason")
    void connectionFailureExposesReason() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertFalse(ssh.connect("127.0.0.1", 1, "user", "pw"));
        assertNotNull(ssh.getLastConnectError(), "a failed connect must expose a reason");
        assertFalse(ssh.getLastConnectError().isBlank());
    }

    @Test
    @DisplayName("a missing private key file is reported clearly")
    void missingKeyFileIsReported() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        ssh.setAuthConfig(SshAuthConfig.key("C:\\definitely\\missing\\id_ed25519", null));

        assertFalse(ssh.connect("127.0.0.1", 1, "user", null));
        assertNotNull(ssh.getLastConnectError());
        assertTrue(ssh.getLastConnectError().contains("Private key file not found"),
                "expected a key-file hint but was: " + ssh.getLastConnectError());
    }
}
