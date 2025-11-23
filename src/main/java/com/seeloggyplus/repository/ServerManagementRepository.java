package com.seeloggyplus.repository;

import com.seeloggyplus.model.SSHServerModel;

import java.util.List;

public interface ServerManagementRepository {
    void saveServer(SSHServerModel server);
    void deleteServer(String id);
    void updateServerLastUsed(String id);
    List<SSHServerModel> getAllServers();
    SSHServerModel getServerById(String id);
}
