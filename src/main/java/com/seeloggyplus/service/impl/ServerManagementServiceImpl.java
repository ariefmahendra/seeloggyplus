package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.SSHServer;
import com.seeloggyplus.repository.ServerManagement;
import com.seeloggyplus.repository.impl.ServerManagementImpl;
import com.seeloggyplus.service.ServerManagementService;

import java.util.List;
import java.util.UUID;

public class ServerManagementServiceImpl implements ServerManagementService {

    private final ServerManagement serverManagement;

    public ServerManagementServiceImpl() {
        this.serverManagement = new ServerManagementImpl();
    }

    @Override
    public void saveServer(SSHServer server) {
        server.setId(UUID.randomUUID().toString());
        serverManagement.saveServer(server);
    }

    @Override
    public void deleteServer(String id) {
        serverManagement.deleteServer(id);
    }

    @Override
    public void updateServerLastUsed(String id) {
        serverManagement.updateServerLastUsed(id);
    }

    @Override
    public List<SSHServer> getAllServers() {
        return serverManagement.getAllServers();
    }
}
