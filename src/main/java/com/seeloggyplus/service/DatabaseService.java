package com.seeloggyplus.service;

import com.seeloggyplus.model.SSHServer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private static final String DATABASE_URL = "jdbc:sqlite:seeloggyplus.db";
    private static DatabaseService instance;
    private Connection connection;

    private DatabaseService() {
        try {
            connection = DriverManager.getConnection(DATABASE_URL);
            logger.info("Database connection established.");
            createTables();
        } catch (SQLException e) {
            logger.error("Failed to connect to database.", e);
        }
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    private void createTables() {
        String createParsingConfigTable = "CREATE TABLE IF NOT EXISTS parsing_configs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL UNIQUE,"
                + "description TEXT,"
                + "regex_pattern TEXT NOT NULL,"
                + "is_default BOOLEAN NOT NULL DEFAULT 0"
                + ");";

        String createSshServerTable = "CREATE TABLE IF NOT EXISTS ssh_servers ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL UNIQUE,"
                + "host TEXT NOT NULL,"
                + "port INTEGER NOT NULL,"
                + "username TEXT NOT NULL,"
                + "password TEXT,"
                + "use_private_key BOOLEAN NOT NULL DEFAULT 0,"
                + "private_key_path TEXT,"
                + "passphrase TEXT,"
                + "default_path TEXT,"
                + "created_at TEXT NOT NULL,"
                + "last_used TEXT,"
                + "save_password BOOLEAN NOT NULL DEFAULT 0"
                + ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createParsingConfigTable);
            stmt.execute(createSshServerTable);
            logger.info("Tables created or already exist.");
        } catch (SQLException e) {
            logger.error("Failed to create tables.", e);
        }
    }    public List<SSHServer> getAllSSHServers() {
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

    public void saveSSHServer(SSHServer server) {
        String sql = "INSERT INTO ssh_servers(name, host, port, username, password, use_private_key, private_key_path, passphrase, default_path, created_at, last_used, save_password) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, server.getName());
            pstmt.setString(2, server.getHost());
            pstmt.setInt(3, server.getPort());
            pstmt.setString(4, server.getUsername());
            pstmt.setString(5, server.getPassword());
            pstmt.setBoolean(6, server.isUsePrivateKey());
            pstmt.setString(7, server.getPrivateKeyPath());
            pstmt.setString(8, server.getPassphrase());
            pstmt.setString(9, server.getDefaultPath());
            pstmt.setString(10, server.getCreatedAt().toString());
            pstmt.setString(11, server.getLastUsed() != null ? server.getLastUsed().toString() : null);
            pstmt.setBoolean(12, server.isSavePassword());
            pstmt.executeUpdate();

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()");
                if (rs.next()) {
                    server.setId(rs.getLong(1));
                }
            }

        } catch (SQLException e) {
            logger.error("Error saving SSH server: " + server.getName(), e);
        }
    }

    public void deleteSSHServer(Long id) {
        String sql = "DELETE FROM ssh_servers WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting SSH server: " + id, e);
        }
    }

    public void updateSSHServerLastUsed(Long id) {
        String sql = "UPDATE ssh_servers SET last_used = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, LocalDateTime.now().toString());
            pstmt.setLong(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating SSH server last used timestamp: " + id, e);
        }
    }

    private SSHServer mapRowToSSHServer(ResultSet rs) throws SQLException {
        SSHServer server = new SSHServer();
        server.setId(rs.getLong("id"));
        server.setName(rs.getString("name"));
        server.setHost(rs.getString("host"));
        server.setPort(rs.getInt("port"));
        server.setUsername(rs.getString("username"));
        server.setPassword(rs.getString("password"));
        server.setUsePrivateKey(rs.getBoolean("use_private_key"));
        server.setPrivateKeyPath(rs.getString("private_key_path"));
        server.setPassphrase(rs.getString("passphrase"));
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