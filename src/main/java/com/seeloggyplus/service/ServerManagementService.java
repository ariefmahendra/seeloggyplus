package com.seeloggyplus.service;

import com.seeloggyplus.model.SSHServer;

import java.util.List;

public interface ServerManagementService {
    void saveServer(SSHServer server);
    void deleteServer(String id);
    void updateServerLastUsed(String id);
    List<SSHServer> getAllServers();
}
