package com.seeloggyplus.repository;

import com.seeloggyplus.model.SSHServerModel;

import java.util.List;

public interface ServerManagementRepository {
    void reorderServers(java.util.List<String> ids);

    java.util.List<String> getGroupNames();
    void createGroup(String name);
    void renameGroup(String oldName, String newName);
    void deleteGroup(String name);

    void saveServer(SSHServerModel server);
    void deleteServer(String id);
    void updateServerLastUsed(String id);
    List<SSHServerModel> getAllServers();
    SSHServerModel getServerById(String id);
}
