package com.seeloggyplus.features.servers.infrastructure;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.database.DatabaseConfig;
import com.seeloggyplus.shared.util.CredentialEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;

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
public class ServerManagementRepositoryImpl implements ServerManagementRepository {

    private static final Logger logger = LoggerFactory.getLogger(ServerManagementRepositoryImpl.class);
    
    // SQL Queries
    private static final String SQL_CHECK_EXISTS = "SELECT COUNT(*) FROM ssh_servers WHERE id = ?";
    private static final String SQL_INSERT = 
        "INSERT INTO ssh_servers(id, name, host, port, username, password, default_path, created_at, last_used, save_password, favorite, sort_order, group_name, auth_type, key_path, key_passphrase) " +
        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT COALESCE(MIN(sort_order), 0) - 1 FROM ssh_servers), ?, ?, ?, ?)";
    private static final String SQL_UPDATE = 
        "UPDATE ssh_servers SET name = ?, host = ?, port = ?, username = ?, password = ?, " +
        "default_path = ?, save_password = ?, favorite = ?, group_name = ?, " +
        "auth_type = ?, key_path = ?, key_passphrase = ? WHERE id = ?";
    private static final String SQL_DELETE = "DELETE FROM ssh_servers WHERE id = ?";
    private static final String SQL_UPDATE_LAST_USED = "UPDATE ssh_servers SET last_used = ? WHERE id = ?";
    private static final String SQL_GET_ALL = "SELECT * FROM ssh_servers ORDER BY sort_order ASC, created_at DESC, id ASC";
    private static final String SQL_GET_BY_ID = "SELECT * FROM ssh_servers WHERE id = ?";
    private static final String SQL_GET_GROUPS = "SELECT name FROM server_groups ORDER BY sort_order ASC, name ASC";
    private static final String SQL_INSERT_GROUP = "INSERT OR IGNORE INTO server_groups(name, sort_order) "
            + "VALUES(?, (SELECT COALESCE(MAX(sort_order), -1) + 1 FROM server_groups))";
    private static final String SQL_RENAME_GROUP_SUBTREE =
            "UPDATE server_groups SET name = ? || SUBSTR(name, ?) WHERE name = ? OR name LIKE ? ESCAPE '\\'";
    private static final String SQL_RENAME_MEMBERS_SUBTREE =
            "UPDATE ssh_servers SET group_name = ? || SUBSTR(group_name, ?) WHERE group_name = ? OR group_name LIKE ? ESCAPE '\\'";
    private static final String SQL_DELETE_GROUP_SUBTREE =
            "DELETE FROM server_groups WHERE name = ? OR name LIKE ? ESCAPE '\\'";
    private static final String SQL_CLEAR_MEMBERS_SUBTREE =
            "UPDATE ssh_servers SET group_name = NULL WHERE group_name = ? OR group_name LIKE ? ESCAPE '\\'";

    /** Escapes LIKE wildcards so group names containing % or _ behave literally. */
    private static String likePrefix(String path) {
        return path.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%";
    }

    /** Dedicated connection so a multi-statement group change commits or rolls back as a unit. */
    private Connection openDedicatedConnection() throws SQLException {
        return DriverManager.getConnection(DatabaseConfig.getInstance().getConnection().getMetaData().getURL());
    }

    @Override
    public List<String> getGroupNames() {
        List<String> names = new ArrayList<>();
        try {
            Connection connection = DatabaseConfig.getInstance().getConnection();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(SQL_GET_GROUPS)) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
            return names;
        } catch (SQLException e) {
            logger.error("Failed to retrieve server groups", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void createGroup(String name) {
        try (Connection c = openDedicatedConnection();
             PreparedStatement ps = c.prepareStatement(SQL_INSERT_GROUP)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to create server group: {}", name, e);
            throw new RuntimeException("Database error while creating server group", e);
        }
    }

    @Override
    public void renameGroup(String oldName, String newName) {
        try (Connection c = openDedicatedConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement renameGroups = c.prepareStatement(SQL_RENAME_GROUP_SUBTREE);
                 PreparedStatement ensure = c.prepareStatement(SQL_INSERT_GROUP);
                 PreparedStatement renameMembers = c.prepareStatement(SQL_RENAME_MEMBERS_SUBTREE)) {
                // Rename the whole subtree: "A" and "A/Child" both move to the new path.
                String oldPrefix = likePrefix(oldName);
                int suffixStart = oldName.length() + 1;
                renameGroups.setString(1, newName);
                renameGroups.setInt(2, suffixStart);
                renameGroups.setString(3, oldName);
                renameGroups.setString(4, oldPrefix);
                renameGroups.executeUpdate();

                ensure.setString(1, newName);
                ensure.executeUpdate();

                renameMembers.setString(1, newName);
                renameMembers.setInt(2, suffixStart);
                renameMembers.setString(3, oldName);
                renameMembers.setString(4, oldPrefix);
                renameMembers.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Failed to rename server group {} to {}", oldName, newName, e);
            throw new RuntimeException("Database error while renaming server group", e);
        }
    }

    @Override
    public void deleteGroup(String name) {
        try (Connection c = openDedicatedConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement delete = c.prepareStatement(SQL_DELETE_GROUP_SUBTREE);
                 PreparedStatement clearMembers = c.prepareStatement(SQL_CLEAR_MEMBERS_SUBTREE)) {
                // Deleting a group removes its descendants; member servers survive ungrouped.
                String prefix = likePrefix(name);
                delete.setString(1, name);
                delete.setString(2, prefix);
                delete.executeUpdate();
                clearMembers.setString(1, name);
                clearMembers.setString(2, prefix);
                clearMembers.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Failed to delete server group: {}", name, e);
            throw new RuntimeException("Database error while deleting server group", e);
        }
    }

    @Override
    public void reorderServers(List<String> ids) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size())
            throw new IllegalArgumentException("Server IDs must be unique and non-null");
        // Use a dedicated connection so rollback cannot include another UI operation.
        try (Connection c = DriverManager.getConnection(DatabaseConfig.getInstance().getConnection().getMetaData().getURL())) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("UPDATE ssh_servers SET sort_order = ? WHERE id = ?")) {
                List<String> order = new ArrayList<>(ids);
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(SQL_GET_ALL)) {
                    while (rs.next()) if (!order.contains(rs.getString("id"))) order.add(rs.getString("id"));
                }
                for (int i = 0; i < order.size(); i++) {
                    ps.setInt(1, i); ps.setString(2, order.get(i));
                    if (ps.executeUpdate() != 1) throw new SQLException("Server no longer exists: " + order.get(i));
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Could not save server order", e); }
    }

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
            ps.setString(6, CredentialEncryptor.getInstance().encrypt(server.getPassword()));
            ps.setString(7, server.getDefaultPath());
            ps.setString(8, server.getCreatedAt() != null ? server.getCreatedAt().toString() : LocalDateTime.now().toString());
            ps.setString(9, server.getLastUsed() != null ? server.getLastUsed().toString() : null);
            ps.setBoolean(10, server.isSavePassword());
            ps.setBoolean(11, server.isFavorite());
            ps.setString(12, server.getGroupName());
            ps.setString(13, server.getAuthType());
            ps.setString(14, server.getKeyPath());
            ps.setString(15, encryptSecret(server.getKeyPassphrase()));
            
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
            ps.setString(5, CredentialEncryptor.getInstance().encrypt(server.getPassword()));
            ps.setString(6, server.getDefaultPath());
            ps.setBoolean(7, server.isSavePassword());
            ps.setBoolean(8, server.isFavorite());
            ps.setString(9, server.getGroupName());
            ps.setString(10, server.getAuthType());
            ps.setString(11, server.getKeyPath());
            ps.setString(12, encryptSecret(server.getKeyPassphrase()));
            ps.setString(13, server.getId());
            
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

    @Override
    public SSHServerModel getServerById(String id) {
        Connection connection = null;

        try {
            connection = DatabaseConfig.getInstance().getConnection();

            try (PreparedStatement ps = connection.prepareStatement(SQL_GET_BY_ID)) {
                ps.setString(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRowToSSHServer(rs);
                    } else {
                        logger.warn("No server found with ID: {}", id);
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve server by ID: {}", id, e);
            throw new RuntimeException("Database error while retrieving server", e);
        }
    }

    /**
     * Map ResultSet row to SSHServer object
     * Handles null values safely.
     * Decrypts password and auto-migrates legacy plaintext passwords.
     * 
     * @param rs ResultSet positioned at current row
     * @return SSHServer object
     * @throws SQLException if column access fails
     */
    private SSHServerModel mapRowToSSHServer(ResultSet rs) throws SQLException {
        SSHServerModel server = new SSHServerModel();
        
        server.setId(rs.getString("id"));
        server.setFavorite(rs.getBoolean("favorite"));
        server.setSortOrder(rs.getInt("sort_order"));
        server.setGroupName(rs.getString("group_name"));
        String authType = rs.getString("auth_type");
        server.setAuthType(authType == null || authType.isBlank()
                ? SSHServerModel.AUTH_PASSWORD
                : authType);
        server.setKeyPath(rs.getString("key_path"));
        server.setKeyPassphrase(decryptSecret(rs.getString("key_passphrase")));
        server.setName(rs.getString("name"));
        server.setHost(rs.getString("host"));
        server.setPort(rs.getInt("port"));
        server.setUsername(rs.getString("username"));
        server.setDefaultPath(rs.getString("default_path"));
        server.setSavePassword(rs.getBoolean("save_password"));

        // Decrypt password; auto-migrate legacy plaintext on next save
        String storedPassword = rs.getString("password");
        CredentialEncryptor encryptor = CredentialEncryptor.getInstance();
        String decrypted = encryptor.decrypt(storedPassword);
        server.setPassword(decrypted);

        // Auto-migrate: if stored value was plaintext, re-encrypt it now
        if (storedPassword != null && !storedPassword.isBlank() && !encryptor.isEncrypted(storedPassword)) {
            migratePasswordInBackground(server.getId(), storedPassword);
        }
        
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

    /** Encrypts an optional secret (null/blank stays null). */
    private static String encryptSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return CredentialEncryptor.getInstance().encrypt(secret);
    }

    /** Decrypts an optional secret (legacy plaintext passes through). */
    private static String decryptSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return CredentialEncryptor.getInstance().decrypt(secret);
    }

    /**
     * Migrate a plaintext password to encrypted form in the database.
     */
    private void migratePasswordInBackground(String serverId, String plaintext) {
        try {
            Connection connection = DatabaseConfig.getInstance().getConnection();
            String encrypted = CredentialEncryptor.getInstance().encrypt(plaintext);
            try (PreparedStatement ps = connection.prepareStatement("UPDATE ssh_servers SET password = ? WHERE id = ?")) {
                ps.setString(1, encrypted);
                ps.setString(2, serverId);
                ps.executeUpdate();
                logger.info("Migrated plaintext password to encrypted for server: {}", serverId);
            }
        } catch (SQLException e) {
            logger.warn("Failed to migrate password for server: {}", serverId, e);
        }
    }
}
