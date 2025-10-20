package com.seeloggyplus.repository;

import com.seeloggyplus.model.SSHServer;

import java.util.List;

public interface ServerManagement {
    void saveServer(SSHServer server);
    void deleteServer(Long id);
    void updateServerLastUsed(Long id);
    List<SSHServer> getAllServers();
}
