package com.seeloggyplus.service;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.impl.ServerManagementServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ServerManagementServiceCloneTest {

    private ServerManagementService serverService;
    private SSHServerModel originalServer;

    @BeforeEach
    void setUp() {
        serverService = new ServerManagementServiceImpl();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        originalServer = new SSHServerModel();
        originalServer.setName("Prod Web " + suffix);
        originalServer.setHost("192.168.1.150");
        originalServer.setPort(2222);
        originalServer.setUsername("deployer");
        originalServer.setPassword("SecretP@ss123");
        originalServer.setDefaultPath("/var/log/nginx");
        originalServer.setSavePassword(true);

        serverService.saveServer(originalServer);
    }

    @AfterEach
    void tearDown() {
        if (originalServer != null && originalServer.getId() != null) {
            try {
                serverService.deleteServer(originalServer.getId());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("createCloneModel should duplicate all server properties and append (Copy)")
    void testCreateCloneModel() {
        SSHServerModel cloneModel = serverService.createCloneModel(originalServer);

        assertNotNull(cloneModel);
        assertNotNull(cloneModel.getId());
        assertNotEquals(originalServer.getId(), cloneModel.getId(), "Clone must have a distinct ID");
        assertEquals(originalServer.getName() + " (Copy)", cloneModel.getName());
        assertEquals(originalServer.getHost(), cloneModel.getHost());
        assertEquals(originalServer.getPort(), cloneModel.getPort());
        assertEquals(originalServer.getUsername(), cloneModel.getUsername());
        assertEquals(originalServer.getPassword(), cloneModel.getPassword());
        assertEquals(originalServer.getDefaultPath(), cloneModel.getDefaultPath());
        assertEquals(originalServer.isSavePassword(), cloneModel.isSavePassword());
        assertNull(cloneModel.getLastUsed(), "Cloned server lastUsed should be reset to null");
        assertEquals(SSHServerModel.ConnectionStatus.UNKNOWN, cloneModel.getConnectionStatus());
    }

    @Test
    @DisplayName("createCloneModel with empty name should fallback to host (Copy)")
    void testCreateCloneModelWithoutName() {
        SSHServerModel noName = new SSHServerModel();
        noName.setHost("bastion.example.com");
        noName.setPort(22);
        noName.setUsername("admin");

        SSHServerModel cloneModel = serverService.createCloneModel(noName);

        assertEquals("bastion.example.com (Copy)", cloneModel.getName());
        assertEquals("bastion.example.com", cloneModel.getHost());
    }

    @Test
    @DisplayName("createCloneModel should reject null source")
    void testCreateCloneModelNull() {
        assertThrows(IllegalArgumentException.class, () -> serverService.createCloneModel(null));
    }

    @Test
    @DisplayName("cloneServer should persist the clone in the database with distinct ID")
    void testCloneServerPersistsToDatabase() {
        SSHServerModel persistedClone = serverService.cloneServer(originalServer.getId());

        try {
            assertNotNull(persistedClone);
            assertNotNull(persistedClone.getId());
            assertNotEquals(originalServer.getId(), persistedClone.getId());
            assertEquals(originalServer.getName() + " (Copy)", persistedClone.getName());

            // Retrieve from DB to verify persistence
            SSHServerModel retrieved = serverService.getServerById(persistedClone.getId());
            assertNotNull(retrieved, "Cloned server must be stored in database");
            assertEquals(persistedClone.getName(), retrieved.getName());
            assertEquals(originalServer.getHost(), retrieved.getHost());
            assertEquals(originalServer.getPort(), retrieved.getPort());
            assertEquals(originalServer.getUsername(), retrieved.getUsername());
            assertEquals(originalServer.getDefaultPath(), retrieved.getDefaultPath());
            assertEquals(originalServer.isSavePassword(), retrieved.isSavePassword());
        } finally {
            // Clean up cloned server
            if (persistedClone != null && persistedClone.getId() != null) {
                try {
                    serverService.deleteServer(persistedClone.getId());
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Test
    @DisplayName("cloneServer should fail for non-existent server ID")
    void testCloneServerNonExistent() {
        assertThrows(IllegalArgumentException.class, () -> serverService.cloneServer("non-existent-id-9999"));
    }

    @Test
    @DisplayName("cloneServer should fail for null or empty ID")
    void testCloneServerInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> serverService.cloneServer(null));
        assertThrows(IllegalArgumentException.class, () -> serverService.cloneServer("   "));
    }
}
