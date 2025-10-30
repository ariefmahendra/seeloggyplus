package com.seeloggyplus.repository.impl;

import com.seeloggyplus.model.SSHServer;
import com.seeloggyplus.repository.ServerManagement;
import com.seeloggyplus.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ServerManagementImpl implements ServerManagement {

    private static final Logger logger = LoggerFactory.getLogger(ServerManagementImpl.class);
    private final Connection connection = DatabaseConfig.getInstance().getConnection();

    @Override
    public void saveServer(SSHServer server) {
        String sql = "INSERT INTO ssh_servers(name, host, port, username, password, default_path, created_at, last_used, save_password) VALUES(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, server.getName());
            preparedStatement.setString(2, server.getHost());
            preparedStatement.setInt(3, server.getPort());
            preparedStatement.setString(4, server.getUsername());
            preparedStatement.setString(5, server.getPassword());
            preparedStatement.setString(6, server.getDefaultPath());
            preparedStatement.setString(7, server.getCreatedAt().toString());
            preparedStatement.setString(8, server.getLastUsed() != null ? server.getLastUsed().toString() : null);
            preparedStatement.setBoolean(9, server.isSavePassword());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving SSH server: {}", server.getName(), e);
        }
    }

    @Override
    public void deleteServer(String id) {
        String sql = "DELETE FROM ssh_servers WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, id);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting SSH server: {}", id, e);
        }
    }

    @Override
    public void updateServerLastUsed(String id) {
        String sql = "UPDATE ssh_servers SET last_used = ? WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, LocalDateTime.now().toString());
            preparedStatement.setString(2, id);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating SSH server last used timestamp: {}", id, e);
        }
    }

    @Override
    public List<SSHServer> getAllServers() {
        List<SSHServer> servers = new ArrayList<>();
        String sql = "SELECT * FROM ssh_servers";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                servers.add(mapRowToSSHServer(rs));
            }
        } catch (SQLException e) {
            logger.error("Error getting all SSH servers", e);
        }
        return servers;
    }

    private SSHServer mapRowToSSHServer(ResultSet rs) throws SQLException {
        SSHServer server = new SSHServer();
        server.setId(rs.getString("id"));
        server.setName(rs.getString("name"));
        server.setHost(rs.getString("host"));
        server.setPort(rs.getInt("port"));
        server.setUsername(rs.getString("username"));
        server.setPassword(rs.getString("password"));
        server.setDefaultPath(rs.getString("default_path"));
        server.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        String lastUsed = rs.getString("last_used");
        if (lastUsed != null) {
            server.setLastUsed(LocalDateTime.parse(lastUsed));
        }
        server.setSavePassword(rs.getBoolean("save_password"));
        return server;
    }
}
