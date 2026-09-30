package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.repository.ServerManagementRepository;
import com.seeloggyplus.repository.impl.ServerManagementRepositoryImpl;
import com.seeloggyplus.service.ServerManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * High-quality implementation of ServerManagementService
 * Handles business logic for SSH server CRUD operations
 */
    public class ServerManagementServiceImpl implements ServerManagementService {

    private static final Logger logger = LoggerFactory.getLogger(ServerManagementServiceImpl.class);
    private final ServerManagementRepository serverManagementRepository;

    public ServerManagementServiceImpl() {
        this.serverManagementRepository = new ServerManagementRepositoryImpl();
    }

    /**
     * Save or update SSH server configuration
     * - If server has no ID (new): generate UUID and set creation timestamp
     * - If server has ID (existing): update existing record
     * 
     * @param server SSHServer to save/update
     * @throws IllegalArgumentException if server is null or invalid
     */
    @Override
    public void reorderServers(List<String> ids) {
        serverManagementRepository.reorderServers(ids);
    }

    @Override
    public List<String> getGroupNames() {
        return serverManagementRepository.getGroupNames();
    }

    @Override
    public void createGroup(String name) {
        String clean = normalizeGroupPath(name);
        List<String> existing = serverManagementRepository.getGroupNames();
        // Create any missing ancestor so nested paths ("Parent/Child") always render.
        StringBuilder path = new StringBuilder();
        for (String segment : clean.split("/")) {
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(segment);
            String current = path.toString();
            if (!existing.contains(current)) {
                serverManagementRepository.createGroup(current);
                existing.add(current);
                logger.info("Created server group: {}", current);
            }
        }
    }

    @Override
    public void renameGroup(String oldName, String newName) {
        String from = normalizeGroupPath(oldName);
        String to = normalizeGroupPath(newName);
        if (from.equals(to)) {
            return;
        }
        if (to.equals(from) || to.startsWith(from + "/")) {
            throw new IllegalArgumentException("A group cannot be moved inside itself");
        }
        if (serverManagementRepository.getGroupNames().contains(to)) {
            throw new IllegalArgumentException("A group named '" + to + "' already exists");
        }
        // Make sure the target parent exists before renaming the subtree.
        int lastSlash = to.lastIndexOf('/');
        if (lastSlash > 0) {
            createGroup(to.substring(0, lastSlash));
        }
        serverManagementRepository.renameGroup(from, to);
        logger.info("Renamed server group '{}' to '{}'", from, to);
    }

    @Override
    public void deleteGroup(String name) {
        String clean = normalizeGroupPath(name);
        serverManagementRepository.deleteGroup(clean);
        logger.info("Deleted server group subtree: {}", clean);
    }

    /** Normalizes a group path ("Parent/Child"), rejecting blank segments. */
    private static String normalizeGroupPath(String name) {
        String raw = name == null ? "" : name.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Group name cannot be empty");
        }
        StringBuilder normalized = new StringBuilder();
        for (String segment : raw.split("/", -1)) {
            String clean = segment.trim();
            if (clean.isEmpty()) {
                throw new IllegalArgumentException("Group name cannot contain an empty name");
            }
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(clean);
        }
        return normalized.toString();
    }

    @Override
    public void saveServer(SSHServerModel server) {
        if (server == null) {
            logger.error("Attempted to save null server");
            throw new IllegalArgumentException("Server cannot be null");
        }

        if (!server.isValid()) {
            String error = server.getValidationError();
            logger.error("Attempted to save invalid server: {}", error);
            throw new IllegalArgumentException("Invalid server configuration: " + error);
        }

        // New server - generate ID and set creation time
        if (server.getId() == null || server.getId().trim().isEmpty()) {
            server.setId(UUID.randomUUID().toString());
            server.setCreatedAt(LocalDateTime.now());
            logger.info("Creating new server: {} ({}@{}:{})", server.getName(), server.getUsername(), server.getHost(), server.getPort());
        } else {
            logger.info("Updating existing server: {} (ID: {})", server.getName(), server.getId());
        }

        try {
            serverManagementRepository.saveServer(server);
            logger.debug("Server saved successfully: {}", server.getId());
        } catch (Exception e) {
            logger.error("Failed to save server: {}", server.getId(), e);
            throw new RuntimeException("Failed to save server: " + e.getMessage(), e);
        }
    }

    /**
     * Delete SSH server by ID
     * 
     * @param id Server ID to delete
     * @throws IllegalArgumentException if id is null or empty
     */
    @Override
    public void deleteServer(String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.error("Attempted to delete server with null/empty ID");
            throw new IllegalArgumentException("Server ID cannot be null or empty");
        }

        logger.info("Deleting server: {}", id);
        
        try {
            serverManagementRepository.deleteServer(id);
            logger.debug("Server deleted successfully: {}", id);
        } catch (Exception e) {
            logger.error("Failed to delete server: {}", id, e);
            throw new RuntimeException("Failed to delete server: " + e.getMessage(), e);
        }
    }

    /**
     * Update last used timestamp for server
     * Called when user successfully connects to server
     * 
     * @param id Server ID
     */
    @Override
    public void updateServerLastUsed(String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.warn("Attempted to update last used with null/empty ID");
            return;
        }

        logger.debug("Updating last used timestamp for server: {}", id);
        
        try {
            serverManagementRepository.updateServerLastUsed(id);
        } catch (Exception e) {
            logger.error("Failed to update last used for server: {}", id, e);
            // Don't throw - this is not critical
        }
    }

    /**
     * Get all configured SSH servers
     * 
     * @return List of all servers (never null, may be empty)
     */
    @Override
    public List<SSHServerModel> getAllServers() {
        logger.debug("Fetching all servers");
        
        try {
            List<SSHServerModel> servers = serverManagementRepository.getAllServers();
            logger.info("Retrieved {} servers", servers.size());
            return servers;
        } catch (Exception e) {
            logger.error("Failed to retrieve servers", e);
            throw new RuntimeException("Failed to retrieve servers: " + e.getMessage(), e);
        }
    }

    @Override
    public SSHServerModel getServerById(String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.error("Attempted to get server with null/empty ID");
            throw new IllegalArgumentException("Server ID cannot be null or empty");
        }

        logger.debug("Fetching server by ID: {}", id);

        try {
            SSHServerModel server = serverManagementRepository.getServerById(id);
            if (server != null) {
                logger.info("Server found: {} (ID: {})", server.getName(), id);
            } else {
                logger.warn("Server not found with ID: {}", id);
            }
            return server;
        } catch (Exception e) {
            logger.error("Failed to retrieve server: {}", id, e);
            throw new RuntimeException("Failed to retrieve server: " + e.getMessage(), e);
        }
    }

    @Override
    public SSHServerModel createCloneModel(SSHServerModel source) {
        if (source == null) {
            logger.error("Attempted to clone null server");
            throw new IllegalArgumentException("Source server cannot be null");
        }

        SSHServerModel clone = new SSHServerModel();
        clone.setId(UUID.randomUUID().toString());
        String baseName = (source.getName() != null && !source.getName().isBlank())
                ? source.getName()
                : (source.getHost() != null && !source.getHost().isBlank() ? source.getHost() : "Server");
        clone.setName(baseName + " (Copy)");
        clone.setHost(source.getHost());
        clone.setPort(source.getPort() > 0 ? source.getPort() : 22);
        clone.setUsername(source.getUsername());
        clone.setPassword(source.getPassword());
        clone.setDefaultPath(source.getDefaultPath() != null ? source.getDefaultPath() : "/");
        clone.setSavePassword(source.isSavePassword());
        clone.setGroupName(source.getGroupName());
        clone.setAuthType(source.getAuthType());
        clone.setKeyPath(source.getKeyPath());
        clone.setKeyPassphrase(source.getKeyPassphrase());
        clone.setCreatedAt(LocalDateTime.now());
        clone.setLastUsed(null);
        clone.setConnectionStatus(SSHServerModel.ConnectionStatus.UNKNOWN);
        return clone;
    }

    @Override
    public SSHServerModel cloneServer(String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.error("Attempted to clone server with null/empty ID");
            throw new IllegalArgumentException("Server ID cannot be null or empty");
        }

        SSHServerModel source = getServerById(id);
        if (source == null) {
            logger.error("Cannot clone non-existent server: {}", id);
            throw new IllegalArgumentException("Server with ID " + id + " does not exist");
        }

        SSHServerModel clone = createCloneModel(source);
        saveServer(clone);
        logger.info("Cloned server '{}' ({}) to new server '{}' ({})",
                source.getName(), source.getId(), clone.getName(), clone.getId());
        return clone;
    }
}
