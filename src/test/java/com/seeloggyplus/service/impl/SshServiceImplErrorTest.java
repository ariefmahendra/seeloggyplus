package com.seeloggyplus.service.impl;

import com.seeloggyplus.ssh.SshAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The real SSH service must remember why a connect attempt failed so the UI can
 * show the actual reason (e.g. a missing/unsupported private key) instead of a
 * generic "check credentials" message.
 */
class SshServiceImplErrorTest {

    @Test
    @DisplayName("connection failure exposes the underlying reason")
    void connectionFailureExposesReason() {
        SSHServiceImpl ssh = new SSHServiceImpl();

        // Port 1 has no SSH server; the connect must fail fast and report why.
        assertFalse(ssh.connect("127.0.0.1", 1, "user", "pw"));
        assertNotNull(ssh.getLastConnectError(), "a failed connect must expose a reason");
        assertFalse(ssh.getLastConnectError().isBlank());
    }

    @Test
    @DisplayName("a missing private key file is reported clearly")
    void missingKeyFileIsReported() {
        SSHServiceImpl ssh = new SSHServiceImpl();
        ssh.setAuthConfig(SshAuthConfig.key("C:\\definitely\\missing\\id_ed25519", null));

        assertFalse(ssh.connect("127.0.0.1", 1, "user", null));
        assertNotNull(ssh.getLastConnectError());
        assertTrue(ssh.getLastConnectError().contains("Private key file not found"),
                "expected a key-file hint but was: " + ssh.getLastConnectError());
    }
}
