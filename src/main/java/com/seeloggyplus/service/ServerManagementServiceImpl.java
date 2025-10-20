package com.seeloggyplus.service;

import com.seeloggyplus.model.SSHServer;
import com.seeloggyplus.repository.ServerManagement;
import com.seeloggyplus.repository.ServerManagementImpl;

import java.util.List;

public class ServerManagementServiceImpl implements ServerManagementService {

    private final ServerManagement serverManagement;

    public ServerManagementServiceImpl() {
        this.serverManagement = new ServerManagementImpl();
    }

    @Override
    public void saveServer(SSHServer server) {
        serverManagement.saveServer(server);
    }

    @Override
    public void deleteServer(Long id) {
        serverManagement.deleteServer(id);
    }

    @Override
    public void updateServerLastUsed(Long id) {
        serverManagement.updateServerLastUsed(id);
    }

    @Override
    public List<SSHServer> getAllServers() {
        return serverManagement.getAllServers();
    }
}
