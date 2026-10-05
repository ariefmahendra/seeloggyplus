package com.seeloggyplus.features.servers.application;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.servers.ServerCatalog;

public interface ServerManagementService extends ServerCatalog {
    void deleteServer(String id);
    default SSHServerModel cloneServer(String id) {
        return null;
    }
    default SSHServerModel createCloneModel(SSHServerModel source) {
        return null;
    }
}
