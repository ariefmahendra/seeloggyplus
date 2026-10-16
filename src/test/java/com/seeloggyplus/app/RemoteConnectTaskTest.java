package com.seeloggyplus.app;

import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.servers.ServerCatalog;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteConnectTaskTest {

    private final FakeCatalog catalog = new FakeCatalog();
    private final FakeSshService ssh = new FakeSshService();
    private final SSHServiceFactory factory = () -> ssh;

    @Test
    @DisplayName("a blank server id fails with a clear reason")
    void blankServerIdFails() {
        RemoteConnectTask task = new RemoteConnectTask("  ", catalog, factory, null);

        IOException error = assertThrows(IOException.class, task::resolveServer);
        assertTrue(error.getMessage().contains("does not have SSH server information"));
    }

    @Test
    @DisplayName("an unknown server id fails with the id in the reason")
    void unknownServerIdFails() {
        RemoteConnectTask task = new RemoteConnectTask("srv-missing", catalog, factory, null);

        IOException error = assertThrows(IOException.class, task::resolveServer);
        assertTrue(error.getMessage().contains("srv-missing"));
    }

    @Test
    @DisplayName("the configured server is resolved through the catalog")
    void resolvesTheConfiguredServer() throws Exception {
        SSHServerModel server = catalog.addServer("srv-1", "host");
        RemoteConnectTask task = new RemoteConnectTask("srv-1", catalog, factory, null);

        assertEquals(server, task.resolveServer());
    }

    @Test
    @DisplayName("connect goes through the shared flow and records the last use")
    void connectRecordsLastUsed() throws Exception {
        SSHServerModel server = catalog.addServer("srv-1", "host");
        RemoteConnectTask task = new RemoteConnectTask("srv-1", catalog, factory, null);

        SSHService connected = task.connect(server, "secret");

        assertEquals(ssh, connected);
        assertEquals(1, ssh.connectCalls);
        assertEquals(List.of("srv-1"), catalog.lastUsed);
    }

    @Test
    @DisplayName("a refused connection throws without recording the last use")
    void refusedConnectionThrows() {
        SSHServerModel server = catalog.addServer("srv-1", "host");
        ssh.connectResult = false;
        RemoteConnectTask task = new RemoteConnectTask("srv-1", catalog, factory, null);

        IOException error = assertThrows(IOException.class, () -> task.connect(server, "secret"));
        assertTrue(error.getMessage().contains("Could not connect to host"));
        assertTrue(catalog.lastUsed.isEmpty());
    }

    private static final class FakeCatalog implements ServerCatalog {
        private final List<SSHServerModel> servers = new ArrayList<>();
        private final List<String> lastUsed = new ArrayList<>();

        SSHServerModel addServer(String id, String host) {
            SSHServerModel server = new SSHServerModel("Prod", host, 22, "user");
            server.setId(id);
            servers.add(server);
            return server;
        }

        @Override
        public List<SSHServerModel> getAllServers() {
            return List.copyOf(servers);
        }

        @Override
        public SSHServerModel getServerById(String id) {
            return servers.stream().filter(server -> server.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public void saveServer(SSHServerModel server) {
        }

        @Override
        public void reorderServers(List<String> orderedIds) {
        }

        @Override
        public List<String> getGroupNames() {
            return List.of();
        }

        @Override
        public void createGroup(String name) {
        }

        @Override
        public void renameGroup(String oldName, String newName) {
        }

        @Override
        public void deleteGroup(String name) {
        }

        @Override
        public void updateServerLastUsed(String serverId) {
            lastUsed.add(serverId);
        }
    }

    private static final class FakeSshService extends SSHServiceImpl {
        int connectCalls;
        boolean connectResult = true;
        boolean connected;

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean connect(String host, int port, String username, String password) {
            connectCalls++;
            if (connectResult) {
                connected = true;
            }
            return connectResult;
        }
    }
}
