package com.seeloggyplus.features.servers.infrastructure;

import com.seeloggyplus.features.servers.application.ServerManagementService;

import com.seeloggyplus.shared.database.DatabaseConfig;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.util.CredentialEncryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Authentication settings (password vs private key) must round-trip through the
 * database, with the key passphrase stored encrypted like the password.
 */
class ServerAuthPersistenceTest {

    private ServerManagementService service;
    private final List<SSHServerModel> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ServerManagementServiceImpl();
    }

    @AfterEach
    void tearDown() {
        for (SSHServerModel server : created) {
            try {
                service.deleteServer(server.getId());
            } catch (Exception ignored) {
            }
        }
    }

    private SSHServerModel create(String label) {
        SSHServerModel server = new SSHServerModel();
        server.setName("Auth " + label + " " + UUID.randomUUID().toString().substring(0, 8));
        server.setHost("10.9.9.9");
        server.setPort(22);
        server.setUsername("user");
        service.saveServer(server);
        created.add(server);
        return server;
    }

    @Test
    @DisplayName("key auth settings round-trip and the passphrase is stored encrypted")
    void keyAuthRoundTripsAndEncryptsPassphrase() throws Exception {
        SSHServerModel server = create("Key");
        server.setAuthType(SSHServerModel.AUTH_KEY);
        server.setKeyPath("C:\\keys\\id_ed25519");
        server.setKeyPassphrase("top-secret-passphrase");
        service.saveServer(server);

        SSHServerModel loaded = service.getServerById(server.getId());
        assertEquals(SSHServerModel.AUTH_KEY, loaded.getAuthType());
        assertEquals("C:\\keys\\id_ed25519", loaded.getKeyPath());
        assertEquals("top-secret-passphrase", loaded.getKeyPassphrase());
        assertTrue(loaded.usesKeyAuth());

        String raw = rawColumn(server.getId(), "key_passphrase");
        assertNotNull(raw);
        assertNotEquals("top-secret-passphrase", raw, "passphrase must never be stored in plaintext");
        assertTrue(CredentialEncryptor.getInstance().isEncrypted(raw));
    }

    @Test
    @DisplayName("servers without auth settings default to password auth")
    void defaultAuthIsPassword() {
        SSHServerModel loaded = service.getServerById(create("Default").getId());

        assertEquals(SSHServerModel.AUTH_PASSWORD, loaded.getAuthType());
        assertNull(loaded.getKeyPath());
        assertNull(loaded.getKeyPassphrase());
        assertFalse(loaded.usesKeyAuth());
    }

    @Test
    @DisplayName("switching back to password clears the key settings")
    void switchingBackToPasswordClearsKeySettings() {
        SSHServerModel server = create("Switch");
        server.setAuthType(SSHServerModel.AUTH_KEY);
        server.setKeyPath("C:\\keys\\id_rsa");
        server.setKeyPassphrase("pw");
        service.saveServer(server);

        server.setAuthType(SSHServerModel.AUTH_PASSWORD);
        server.setKeyPath(null);
        server.setKeyPassphrase(null);
        service.saveServer(server);

        SSHServerModel loaded = service.getServerById(server.getId());
        assertEquals(SSHServerModel.AUTH_PASSWORD, loaded.getAuthType());
        assertNull(loaded.getKeyPath());
        assertNull(loaded.getKeyPassphrase());
    }

    private String rawColumn(String id, String column) throws Exception {
        Connection connection = DatabaseConfig.getInstance().getConnection();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + column + " FROM ssh_servers WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
