package com.seeloggyplus.repository;

import com.seeloggyplus.model.SSHServer;

import java.util.List;

public interface ServerManagement {
    void saveServer(SSHServer server);
    void deleteServer(String id);
    void updateServerLastUsed(String id);
    List<SSHServer> getAllServers();
}
