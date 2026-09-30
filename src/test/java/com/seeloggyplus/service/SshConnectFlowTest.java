package com.seeloggyplus.service;

import com.seeloggyplus.dto.RemoteFileInfo;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.ssh.HostKeyVerificationException;
import com.seeloggyplus.ssh.SshAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Connect flow rules: the server's auth method is applied, an unknown host key
 * is only trusted after explicit confirmation (then retried), and a changed key
 * is always refused.
 */
class SshConnectFlowTest {

    private static final String FINGERPRINT = "SHA256:abc123";

    private SSHServerModel server(boolean keyAuth) {
        SSHServerModel server = new SSHServerModel();
        server.setHost("prod.example.test");
        server.setPort(2222);
        server.setUsername("deployer");
        server.setAuthType(keyAuth ? SSHServerModel.AUTH_KEY : SSHServerModel.AUTH_PASSWORD);
        server.setKeyPath("C:\\keys\\id_ed25519");
        server.setKeyPassphrase("key-pass");
        return server;
    }

    private HostKeyVerificationException unknownHostKey() {
        return new HostKeyVerificationException("prod.example.test", 2222, "ssh-ed25519",
                FINGERPRINT, HostKeyVerificationException.Reason.UNKNOWN);
    }

    private HostKeyVerificationException changedHostKey() {
        return new HostKeyVerificationException("prod.example.test", 2222, "ssh-ed25519",
                FINGERPRINT, HostKeyVerificationException.Reason.CHANGED);
    }

    @Test
    @DisplayName("unknown host key: user accepts, key is saved and the connect is retried")
    void unknownHostKeyAcceptedAndRetried() {
        FakeSshService service = new FakeSshService();
        service.toThrow = unknownHostKey();
        service.succeedAfterTrust = true;
        AtomicInteger confirmations = new AtomicInteger();
        AtomicInteger problems = new AtomicInteger();
        SshConnectFlow.HostKeyConfirmer confirmer = new SshConnectFlow.HostKeyConfirmer() {
            @Override public boolean confirmUnknownHostKey(HostKeyVerificationException problem) {
                assertEquals(FINGERPRINT, problem.getFingerprint());
                confirmations.incrementAndGet();
                return true;
            }
            @Override public void showHostKeyProblem(HostKeyVerificationException problem) {
                problems.incrementAndGet();
            }
        };

        boolean connected = SshConnectFlow.connect(service, server(true), "key-pass", confirmer);

        assertTrue(connected, "connect must succeed after the host key is trusted");
        assertEquals(2, service.connectAttempts, "the connect must be retried exactly once");
        assertTrue(service.trusted, "the accepted key must be persisted");
        assertEquals(1, confirmations.get());
        assertEquals(0, problems.get());
        assertEquals(SshAuthConfig.AuthType.KEY, service.lastAuth.type(),
                "key auth from the server model must be applied");
        assertEquals("C:\\keys\\id_ed25519", service.lastAuth.keyPath());
    }

    @Test
    @DisplayName("unknown host key: user cancels, nothing is trusted")
    void unknownHostKeyCancelled() {
        FakeSshService service = new FakeSshService();
        service.toThrow = unknownHostKey();
        service.succeedAfterTrust = true;
        AtomicInteger problems = new AtomicInteger();
        SshConnectFlow.HostKeyConfirmer confirmer = new SshConnectFlow.HostKeyConfirmer() {
            @Override public boolean confirmUnknownHostKey(HostKeyVerificationException problem) {
                return false;
            }
            @Override public void showHostKeyProblem(HostKeyVerificationException problem) {
                problems.incrementAndGet();
            }
        };

        assertFalse(SshConnectFlow.connect(service, server(false), "pw", confirmer));
        assertEquals(1, service.connectAttempts);
        assertFalse(service.trusted);
        assertEquals(1, problems.get(), "the user must be told the host key was refused");
    }

    @Test
    @DisplayName("changed host key is always refused, even if the user would accept")
    void changedHostKeyRefused() {
        FakeSshService service = new FakeSshService();
        service.toThrow = changedHostKey();
        AtomicBoolean confirmationAsked = new AtomicBoolean();
        AtomicInteger problems = new AtomicInteger();
        SshConnectFlow.HostKeyConfirmer confirmer = new SshConnectFlow.HostKeyConfirmer() {
            @Override public boolean confirmUnknownHostKey(HostKeyVerificationException problem) {
                confirmationAsked.set(true);
                return true;
            }
            @Override public void showHostKeyProblem(HostKeyVerificationException problem) {
                assertEquals(HostKeyVerificationException.Reason.CHANGED, problem.getReason());
                problems.incrementAndGet();
            }
        };

        assertFalse(SshConnectFlow.connect(service, server(false), "pw", confirmer));
        assertFalse(confirmationAsked.get(), "a changed key must never be silently re-trusted");
        assertFalse(service.trusted);
        assertEquals(1, problems.get());
    }

    @Test
    @DisplayName("password servers keep password auth and succeed without prompts")
    void passwordAuthPassesThrough() {
        FakeSshService service = new FakeSshService();
        AtomicBoolean asked = new AtomicBoolean();
        SshConnectFlow.HostKeyConfirmer confirmer = new SshConnectFlow.HostKeyConfirmer() {
            @Override public boolean confirmUnknownHostKey(HostKeyVerificationException problem) {
                asked.set(true);
                return true;
            }
            @Override public void showHostKeyProblem(HostKeyVerificationException problem) {
                asked.set(true);
            }
        };

        assertTrue(SshConnectFlow.connect(service, server(false), "pw", confirmer));
        assertEquals(1, service.connectAttempts);
        assertEquals(SshAuthConfig.AuthType.PASSWORD, service.lastAuth.type());
        assertFalse(asked.get());
    }

    /** Minimal fake that simulates host-key failures. */
    private static class FakeSshService implements SSHService {
        SshAuthConfig lastAuth;
        HostKeyVerificationException toThrow;
        boolean succeedAfterTrust;
        boolean trusted;
        int connectAttempts;

        @Override public void setAuthConfig(SshAuthConfig config) {
            this.lastAuth = config;
        }

        @Override public void trustPendingHostKey() {
            this.trusted = true;
        }

        @Override public boolean connect(String host, int port, String username, String password) {
            connectAttempts++;
            if (toThrow != null && !(trusted && succeedAfterTrust)) {
                HostKeyVerificationException problem = toThrow;
                toThrow = null;
                throw problem;
            }
            return true;
        }

        @Override public boolean connect(String host, int port, String username, String password, long ttlMillis) {
            return connect(host, port, username, password);
        }

        @Override public void disconnect() { }

        @Override public boolean isConnected() { return false; }

        @Override public void tailFile(String remotePath, int lines, Consumer<String> logConsumer,
                Consumer<String> errorConsumer) { }

        @Override public void stopTailing() { }

        @Override public String readFile(String remotePath) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override public List<RemoteFileInfo> listFiles(String remotePath) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override public String executeCommand(String command) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override public List<String> readFileLines(String remotePath) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override public List<String> readFileLines(String remotePath, int lineLimit) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override public boolean downloadFile(String remotePath, String localPath) {
            throw new UnsupportedOperationException();
        }

        @Override public boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount,
                LogParser.ProgressCallback progressCallback) {
            throw new UnsupportedOperationException();
        }
    }
}
