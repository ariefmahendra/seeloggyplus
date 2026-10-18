package com.seeloggyplus.features.ssh.infrastructure;

import com.jcraft.jsch.JSchException;
import com.seeloggyplus.shared.dto.RemoteFileInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SSHServiceSessionRecoveryTest {

    static class FlakySshService extends SSHServiceImpl {
        int attempts;
        boolean recoveryCalled;
        boolean recoveryPossible = true;
        private final IOException firstFailure;

        FlakySshService(IOException firstFailure) {
            this.firstFailure = firstFailure;
        }

        @Override
        List<RemoteFileInfo> listFilesOnce(String remotePath) throws IOException {
            attempts++;
            if (attempts == 1) {
                throw firstFailure;
            }
            return List.of();
        }

        @Override
        boolean recoverSession() {
            recoveryCalled = true;
            return recoveryPossible;
        }
    }

    @Test
    void retriesOnceAfterStaleSessionError() throws Exception {
        FlakySshService ssh = new FlakySshService(new IOException(
                "exec listFiles failed: channel is not opened.", new JSchException("channel is not opened.")));

        assertEquals(List.of(), ssh.listFiles("/var/log"));

        assertEquals(2, ssh.attempts, "a stale session error must be retried exactly once");
        assertTrue(ssh.recoveryCalled, "the stale session must be recovered before the retry");
    }

    @Test
    void propagatesUnrelatedErrorsWithoutReconnect() {
        FlakySshService ssh = new FlakySshService(new IOException("SFTP ls failed for /var/log: No such file"));

        IOException error = assertThrows(IOException.class, () -> ssh.listFiles("/var/log"));

        assertTrue(error.getMessage().contains("No such file"));
        assertEquals(1, ssh.attempts);
        assertFalse(ssh.recoveryCalled, "an unrelated error must not trigger a reconnect");
    }

    @Test
    void doesNotRetryWhenRecoveryFails() {
        FlakySshService ssh = new FlakySshService(new IOException(
                "exec listFiles failed: channel is not opened.", new JSchException("channel is not opened.")));
        ssh.recoveryPossible = false;

        assertThrows(IOException.class, () -> ssh.listFiles("/var/log"));

        assertEquals(1, ssh.attempts);
        assertTrue(ssh.recoveryCalled);
    }

    @Test
    void detectsSessionDownMessagesInNestedCauses() {
        assertTrue(SSHServiceImpl.isSessionDownError(new JSchException("channel is not opened.")));
        assertTrue(SSHServiceImpl.isSessionDownError(new IOException("outer", new JSchException("session is down"))));
        assertTrue(SSHServiceImpl.isSessionDownError(new IOException("java.net.SocketException: Broken pipe")));
        assertFalse(SSHServiceImpl.isSessionDownError(new IOException("SFTP ls failed for /var/log: No such file")));
        assertFalse(SSHServiceImpl.isSessionDownError(new IOException("Authentication failed")));
    }
}
