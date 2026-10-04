package com.seeloggyplus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SshConnectionFeedbackTest {

    private static final String AUTH_FAILURE = "Auth fail for methods 'publickey,gssapi-keyex,gssapi-with-mic,password'";

    @Test
    void passwordRejectionHasActionableAdviceInsteadOfProtocolNames() {
        var feedback = SshConnectionFeedback.describe(AUTH_FAILURE, false);
        assertEquals("Unable to sign in to server", feedback.title());
        assertTrue(feedback.message().contains("server rejected the login"));
        assertTrue(feedback.message().contains("username and password"));
        assertFalse(feedback.message().contains("gssapi"));
        assertFalse(feedback.message().contains("Auth fail"));
        assertEquals(AUTH_FAILURE, feedback.detail(), "Diagnostic details must retain the original error");
    }

    @Test
    void keyRejectionSuggestsKeyAndPassphraseSettings() {
        var feedback = SshConnectionFeedback.describe(AUTH_FAILURE, true);
        assertTrue(feedback.message().contains("private key"));
        assertTrue(feedback.message().contains("passphrase"));
        assertFalse(feedback.message().contains("username and password"));
        assertEquals(AUTH_FAILURE, feedback.detail());
    }

    @Test
    void timeoutAndRefusedConnectionAreNotMisreportedAsBadCredentials() {
        var timeout = SshConnectionFeedback.describe("java.net.SocketTimeoutException: connect timed out", false);
        assertTrue(timeout.message().contains("address and port"));
        assertTrue(timeout.message().contains("network"));
        assertFalse(timeout.message().contains("rejected the login"));
        var refused = SshConnectionFeedback.describe("java.net.ConnectException: Connection refused", false);
        assertTrue(refused.message().contains("SSH service"));
        assertFalse(refused.message().contains("rejected the login"));
    }

    @Test
    void unreadableKeyAndChangedIdentityKeepTheirSpecificRecoveryAdvice() {
        var key = SshConnectionFeedback.describe("invalid privatekey: C:\\keys\\id_ed25519", true);
        assertTrue(key.message().contains("private key file"));
        assertFalse(key.message().contains("C:\\keys"));
        var identity = SshConnectionFeedback.describe("Host key has changed", false);
        assertTrue(identity.message().contains("identity"));
        assertFalse(identity.message().contains("username and password"));
    }

    @Test
    void missingDetailsGiveNeutralGuidanceWithoutInventingAFailureReason() {
        for (String detail : new String[]{null, "", "   "}) {
            var feedback = SshConnectionFeedback.describe(detail, false);
            assertEquals("Unable to connect to server", feedback.title());
            assertTrue(feedback.message().contains("server settings"));
            assertFalse(feedback.message().contains("rejected the login"));
            assertFalse(feedback.detail().isBlank());
        }
    }
}
