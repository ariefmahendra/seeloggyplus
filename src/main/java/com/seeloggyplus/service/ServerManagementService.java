package com.seeloggyplus.service;

import com.seeloggyplus.model.SSHServer;

import java.util.List;

public interface ServerManagementService {
    void saveServer(SSHServer server);
    void deleteServer(Long id);
    void updateServerLastUsed(Long id);
    List<SSHServer> getAllServers();
}
