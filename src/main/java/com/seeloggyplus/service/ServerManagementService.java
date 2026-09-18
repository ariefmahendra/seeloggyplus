package com.seeloggyplus.service;

import com.seeloggyplus.model.SSHServerModel;

import java.util.List;

public interface ServerManagementService {
    void saveServer(SSHServerModel server);
    void deleteServer(String id);
    void updateServerLastUsed(String id);
    List<SSHServerModel> getAllServers();
    SSHServerModel getServerById(String id);
    default SSHServerModel cloneServer(String id) {
        return null;
    }
    default SSHServerModel createCloneModel(SSHServerModel source) {
        return null;
    }
}
