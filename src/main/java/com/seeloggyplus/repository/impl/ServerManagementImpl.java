package com.seeloggyplus.repository.impl;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.repository.ServerManagement;
import com.seeloggyplus.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * High-quality implementation of ServerManagement repository
 * Handles database operations for SSH server configurations
 * 
 * Features:
 * - UPSERT logic (INSERT or UPDATE based on existence)
 * - Proper error handling with exceptions
 * - Resource management with try-with-resources
 * - SQL injection prevention with PreparedStatement
 * - Null safety checks
 */
public class ServerManagementImpl implements ServerManagement {

    private static final Logger logger = LoggerFactory.getLogger(ServerManagementImpl.class);
    
    // SQL Queries
    private static final String SQL_CHECK_EXISTS = "SELECT COUNT(*) FROM ssh_servers WHERE id = ?";
    private static final String SQL_INSERT = 
        "INSERT INTO ssh_servers(id, name, host, port, username, password, default_path, created_at, last_used, save_password) " +
        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_UPDATE = 
        "UPDATE ssh_servers SET name = ?, host = ?, port = ?, username = ?, password = ?, " +
        "default_path = ?, save_password = ? WHERE id = ?";
    private static final String SQL_DELETE = "DELETE FROM ssh_servers WHERE id = ?";
    private static final String SQL_UPDATE_LAST_USED = "UPDATE ssh_servers SET last_used = ? WHERE id = ?";
    private static final String SQL_GET_ALL = "SELECT * FROM ssh_servers ORDER BY created_at DESC";
    private static final String SQL_GET_BY_ID = "SELECT * FROM ssh_servers WHERE id = ?";

    /**
     * Save or update SSH server configuration
     * Uses UPSERT pattern: INSERT if new, UPDATE if exists
     * 
     * @param server SSHServer to save
     * @throws SQLException if database operation fails
     */
    @Override
    public void saveServer(SSHServerModel server) {
        Connection connection = null;
        try {
            connection = DatabaseConfig.getInstance().getConnection();
            
            if (serverExists(connection, server.getId())) {
                updateServer(connection, server);
                logger.debug("Updated existing server: {}", server.getId());
            } else {
                insertServer(connection, server);
                logger.debug("Inserted new server: {}", server.getId());
            }
        } catch (SQLException e) {
            logger.error("Failed to save server: {} ({}@{})", 
                server.getName(), server.getUsername(), server.getHost(), e);
            throw new RuntimeException("Database error while saving server", e);
        }
    }

    /**
     * Check if server exists in database
     */
    private boolean serverExists(Connection connection, String id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SQL_CHECK_EXISTS)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Insert new server into database
     */
    private void insertServer(Connection connection, SSHServerModel server) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SQL_INSERT)) {
            ps.setString(1, server.getId());
            ps.setString(2, server.getName());
            ps.setString(3, server.getHost());
            ps.setInt(4, server.getPort());
            ps.setString(5, server.getUsername());
            ps.setString(6, server.getPassword());
            ps.setString(7, server.getDefaultPath());
            ps.setString(8, server.getCreatedAt() != null ? server.getCreatedAt().toString() : LocalDateTime.now().toString());
            ps.setString(9, server.getLastUsed() != null ? server.getLastUsed().toString() : null);
            ps.setBoolean(10, server.isSavePassword());
            
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Insert failed, no rows affected");
            }
        }
    }

    /**
     * Update existing server in database
     */
    private void updateServer(Connection connection, SSHServerModel server) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, server.getName());
            ps.setString(2, server.getHost());
            ps.setInt(3, server.getPort());
            ps.setString(4, server.getUsername());
            ps.setString(5, server.getPassword());
            ps.setString(6, server.getDefaultPath());
            ps.setBoolean(7, server.isSavePassword());
            ps.setString(8, server.getId());
            
            int affected = ps.executeUpdate();
            if (affected == 0) {
                logger.warn("Update affected 0 rows for server: {}", server.getId());
            }
        }
    }

    /**
     * Delete SSH server by ID
     * 
     * @param id Server ID to delete
     * @throws SQLException if database operation fails
     */
    @Override
    public void deleteServer(String id) {
        Connection connection = null;
        try {
            connection = DatabaseConfig.getInstance().getConnection();
            
            try (PreparedStatement ps = connection.prepareStatement(SQL_DELETE)) {
                ps.setString(1, id);
                int affected = ps.executeUpdate();
                
                if (affected == 0) {
                    logger.warn("Delete affected 0 rows for server: {}", id);
                } else {
                    logger.debug("Deleted server: {}", id);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to delete server: {}", id, e);
            throw new RuntimeException("Database error while deleting server", e);
        }
    }

    /**
     * Update last used timestamp for server
     * 
     * @param id Server ID
     */
    @Override
    public void updateServerLastUsed(String id) {
        Connection connection = null;
        try {
            connection = DatabaseConfig.getInstance().getConnection();
            
            try (PreparedStatement ps = connection.prepareStatement(SQL_UPDATE_LAST_USED)) {
                ps.setString(1, LocalDateTime.now().toString());
                ps.setString(2, id);
                ps.executeUpdate();
                logger.debug("Updated last_used for server: {}", id);
            }
        } catch (SQLException e) {
            logger.error("Failed to update last_used for server: {}", id, e);
            // Don't throw - this is not critical
        }
    }

    /**
     * Get all SSH servers from database
     * 
     * @return List of all servers (never null, may be empty)
     */
    @Override
    public List<SSHServerModel> getAllServers() {
        Connection connection = null;
        List<SSHServerModel> servers = new ArrayList<>();
        
        try {
            connection = DatabaseConfig.getInstance().getConnection();
            
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(SQL_GET_ALL)) {
                
                while (rs.next()) {
                    try {
                        servers.add(mapRowToSSHServer(rs));
                    } catch (Exception e) {
                        logger.error("Failed to map server row, skipping", e);
                    }
                }
            }
            
            logger.debug("Retrieved {} servers from database", servers.size());
            return servers;
            
        } catch (SQLException e) {
            logger.error("Failed to retrieve servers from database", e);
            return Collections.emptyList();
        }
    }

    /**
     * Map ResultSet row to SSHServer object
     * Handles null values safely
     * 
     * @param rs ResultSet positioned at current row
     * @return SSHServer object
     * @throws SQLException if column access fails
     */
    private SSHServerModel mapRowToSSHServer(ResultSet rs) throws SQLException {
        SSHServerModel server = new SSHServerModel();
        
        server.setId(rs.getString("id"));
        server.setName(rs.getString("name"));
        server.setHost(rs.getString("host"));
        server.setPort(rs.getInt("port"));
        server.setUsername(rs.getString("username"));
        server.setPassword(rs.getString("password"));
        server.setDefaultPath(rs.getString("default_path"));
        server.setSavePassword(rs.getBoolean("save_password"));
        
        // Handle nullable timestamps
        String createdAt = rs.getString("created_at");
        if (createdAt != null && !createdAt.trim().isEmpty()) {
            try {
                server.setCreatedAt(LocalDateTime.parse(createdAt));
            } catch (Exception e) {
                logger.warn("Failed to parse created_at for server {}: {}", server.getId(), createdAt);
            }
        }
        
        String lastUsed = rs.getString("last_used");
        if (lastUsed != null && !lastUsed.trim().isEmpty()) {
            try {
                server.setLastUsed(LocalDateTime.parse(lastUsed));
            } catch (Exception e) {
                logger.warn("Failed to parse last_used for server {}: {}", server.getId(), lastUsed);
            }
        }
        
        return server;
    }
}
