package com.seeloggyplus.features.ssh.infrastructure;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.seeloggyplus.shared.ssh.HostKeyVerificationException;
import com.seeloggyplus.shared.ssh.SSHSessionManager;
import com.seeloggyplus.shared.ssh.SshAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SSHServiceImplSessionManagerTest {

    private final FakeSessionManager sessionManager = new FakeSessionManager();

    @Test
    @DisplayName("a failed connect keeps the reason from the session manager")
    void connectReportsFailureWithoutLosingTheReason() {
        sessionManager.connectFailure = new JSchException("Authentication failed");
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertFalse(ssh.connect("host", 22, "user", "secret"));
        assertEquals("Authentication failed", ssh.getLastConnectError());
    }

    @Test
    @DisplayName("the host-key warning travels up to the caller untouched")
    void connectPropagatesHostKeyVerificationFailures() {
        HostKeyVerificationException failure = new HostKeyVerificationException(
                "host", 22, "ssh-rsa", "AA:BB", HostKeyVerificationException.Reason.UNKNOWN);
        sessionManager.hostKeyFailure = failure;
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        HostKeyVerificationException thrown =
                assertThrows(HostKeyVerificationException.class, () -> ssh.connect("host", 22, "user", "secret"));

        assertEquals(failure.getFingerprint(), thrown.getFingerprint());
        assertEquals(failure.getMessage(), ssh.getLastConnectError());
    }

    @Test
    @DisplayName("connect asks the injected manager and reports the pooled session state")
    void connectUsesTheInjectedSessionManager() throws Exception {
        sessionManager.session = new JSch().getSession("user", "host", 22);
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertFalse(ssh.connect("host", 22, "user", "secret"),
                "an unconnected pooled session must be reported as not connected");
        assertEquals(1, sessionManager.getSessionCalls);
        assertNull(ssh.getLastConnectError());
    }

    @Test
    @DisplayName("trusting a host key is delegated to the injected manager")
    void trustPendingHostKeyDelegatesToTheInjectedManager() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        ssh.trustPendingHostKey();

        assertEquals(1, sessionManager.trustCalls);
    }

    @Test
    @DisplayName("recovering a stale session closes it through the injected manager")
    void recoverSessionClosesTheStaleSessionThroughTheManager() {
        sessionManager.connectFailure = new JSchException("session is down");
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        assertFalse(ssh.connect("host", 22, "user", "secret"));

        assertFalse(ssh.recoverSession(), "recovery without a working manager must report failure");
        assertEquals(List.of("user@host:22"), sessionManager.closed);
    }

    @Test
    @DisplayName("recovering without a stored password gives up after closing")
    void recoverSessionWithoutPasswordGivesUp() {
        sessionManager.connectFailure = new JSchException("session is down");
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        assertFalse(ssh.connect("host", 22, "user", null));

        assertFalse(ssh.recoverSession());
        assertEquals(List.of("user@host:22"), sessionManager.closed);
    }

    private static final class FakeSessionManager implements SSHSessionManager {
        private final List<String> closed = new ArrayList<>();
        private JSchException connectFailure;
        private HostKeyVerificationException hostKeyFailure;
        private Session session;
        private int getSessionCalls;
        private int trustCalls;

        @Override
        public Session getSession(String host, int port, String username, String password, long ttlMillis)
                throws JSchException {
            getSessionCalls++;
            if (connectFailure != null) {
                throw connectFailure;
            }
            if (hostKeyFailure != null) {
                throw hostKeyFailure;
            }
            return session;
        }

        @Override
        public Session getSession(String host, int port, String username, String secret, long ttlMillis,
                SshAuthConfig authConfig) throws JSchException {
            return getSession(host, port, username, secret, ttlMillis);
        }

        @Override
        public void trustPendingHostKey(String host, int port) {
            trustCalls++;
        }

        @Override
        public void closeSession(String host, int port, String username) {
            closed.add(username + "@" + host + ":" + port);
        }
    }
}
