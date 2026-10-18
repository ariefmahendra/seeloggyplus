package com.seeloggyplus.shared.ssh;

import com.seeloggyplus.shared.model.SSHServerModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SshAuthConfigTest {

    @Test
    @DisplayName("password servers map to password auth")
    void passwordServerMapsToPassword() {
        SSHServerModel server = new SSHServerModel();
        server.setAuthType(SSHServerModel.AUTH_PASSWORD);
        server.setKeyPath("ignored");

        SshAuthConfig config = SshAuthConfig.from(server);

        assertEquals(SshAuthConfig.AuthType.PASSWORD, config.type());
        assertNull(config.keyPath());
        assertNull(config.passphrase());
    }

    @Test
    @DisplayName("key servers map to key auth with path and passphrase")
    void keyServerMapsToKey() {
        SSHServerModel server = new SSHServerModel();
        server.setAuthType(SSHServerModel.AUTH_KEY);
        server.setKeyPath("C:\\keys\\id_ed25519");
        server.setKeyPassphrase("pw");

        SshAuthConfig config = SshAuthConfig.from(server);

        assertEquals(SshAuthConfig.AuthType.KEY, config.type());
        assertEquals("C:\\keys\\id_ed25519", config.keyPath());
        assertEquals("pw", config.passphrase());
    }

    @Test
    @DisplayName("null server falls back to password auth")
    void nullServerFallsBackToPassword() {
        assertEquals(SshAuthConfig.AuthType.PASSWORD, SshAuthConfig.from(null).type());
    }
}
