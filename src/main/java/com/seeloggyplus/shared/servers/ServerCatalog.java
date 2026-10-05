package com.seeloggyplus.shared.servers;

import com.seeloggyplus.shared.model.SSHServerModel;

import java.util.List;

/**
 * Cross-feature port for the SSH server registry: reading the catalogue,
 * persisting servers and organising them into groups.
 */
public interface ServerCatalog {
    List<SSHServerModel> getAllServers();
    SSHServerModel getServerById(String id);
    void saveServer(SSHServerModel server);
    void reorderServers(List<String> orderedIds);
    List<String> getGroupNames();
    void createGroup(String name);
    void renameGroup(String oldName, String newName);
    void deleteGroup(String name);
    void updateServerLastUsed(String serverId);
}
