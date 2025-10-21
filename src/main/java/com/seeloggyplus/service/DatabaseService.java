package com.seeloggyplus.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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

        String createPreferencesTable = "CREATE TABLE IF NOT EXISTS preferences ("
                + "code TEXT PRIMARY KEY,"
                + "value TEXT"
                + ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createParsingConfigTable);
            stmt.execute(createSshServerTable);
            stmt.execute(createPreferencesTable);
            logger.info("Tables created or already exist.");
        } catch (SQLException e) {
            logger.error("Failed to create tables.", e);
        }
    }
}