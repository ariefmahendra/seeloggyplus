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
            logger.info("Creating new server: {} ({}@{}:{})", 
                server.getName(), server.getUsername(), server.getHost(), server.getPort());
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
}
