package com.seeloggyplus.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static DatabaseConfig instance;
    private Connection connection;

    private DatabaseConfig() {
        try {
            // Path dbPath = Path.of(System.getProperty("user.home"), "/.seeloggyplus", "/data", "seeloggyplus.db");
            Path dbPath = Paths.get( ".","/.data", "seeloggyplus.db");

            System.out.println(dbPath.toAbsolutePath());

            if (!Files.exists(dbPath)){
                File dbFile = new File(dbPath.toUri());
                File parentDir = dbFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    boolean resultMkdir = parentDir.mkdirs();
                    if (!resultMkdir){
                        logger.info("Failed create directory and file db path");
                    }
                    logger.info("Success create directory and file db path");
                }
            }

            String resultPath = String.format("jdbc:sqlite:%s", dbPath.toAbsolutePath());
            connection = DriverManager.getConnection(resultPath);
            logger.info("Database connection established.");
            createTables();
        } catch (SQLException e) {
            logger.error("Failed to connect to database.", e);
        }
    }

    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    private void createTables() {
        String createParsingConfigTable = "CREATE TABLE IF NOT EXISTS parsing_configs ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL UNIQUE,"
                + "description TEXT,"
                + "regex_pattern TEXT NOT NULL,"
                + "timestamp_format TEXT"
                + ");";

        String createSshServerTable = "CREATE TABLE IF NOT EXISTS ssh_servers ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL UNIQUE,"
                + "host TEXT NOT NULL,"
                + "port INTEGER NOT NULL,"
                + "username TEXT NOT NULL,"
                + "password TEXT,"
                + "default_path TEXT,"
                + "created_at TEXT NOT NULL,"
                + "last_used TEXT,"
                + "save_password BOOLEAN NOT NULL DEFAULT 0"
                + ");";

        String createPreferencesTable = "CREATE TABLE IF NOT EXISTS preferences ("
                + "code TEXT PRIMARY KEY,"
                + "value TEXT"
                + ");";

        String createLogFileTable = "CREATE TABLE IF NOT EXISTS log_files (" +
                "id TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "file_path TEXT NOT NULL," +
                "size TEXT," +
                "modified TEXT," +
                "is_remote BOOLEAN DEFAULT 0," +
                "ssh_server_id TEXT," +
                "parsing_configuration_id TEXT," +
                "FOREIGN KEY (ssh_server_id) REFERENCES ssh_servers(id) ON DELETE SET NULL," +
                "FOREIGN KEY (parsing_configuration_id) REFERENCES parsing_configs(id) ON DELETE SET NULL" +
                ");";

        String createRecentFiles = "CREATE TABLE IF NOT EXISTS recent_files (" +
                "id TEXT PRIMARY KEY," +
                "file_id TEXT NOT NULL UNIQUE," +
                "last_opened TEXT NOT NULL," +
                "FOREIGN KEY (file_id) REFERENCES log_files(id) ON DELETE CASCADE" +
                ");";

        String createFavoriteFoldersTable = "CREATE TABLE IF NOT EXISTS favorite_folders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "path TEXT NOT NULL," +
                "locationId TEXT NOT NULL," +
                "UNIQUE(path, locationId)" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createParsingConfigTable);
            stmt.execute(createSshServerTable);
            stmt.execute(createPreferencesTable);
            stmt.execute(createLogFileTable);
            stmt.execute(createRecentFiles);
            stmt.execute(createFavoriteFoldersTable);
            logger.info("Tables created or already exist.");
            
            // Migration: Add timestamp_format column if not exists
            migrateTimestampFormat();
        } catch (SQLException e) {
            logger.error("Failed to create tables.", e);
        }
    }
    
    /**
     * Migration: Add timestamp_format column to existing parsing_configs table
     */
    private void migrateTimestampFormat() {
        String checkColumn = "PRAGMA table_info(parsing_configs)";
        boolean columnExists = false;
        
        try (Statement stmt = connection.createStatement();
             var rs = stmt.executeQuery(checkColumn)) {
            while (rs.next()) {
                String columnName = rs.getString("name");
                if ("timestamp_format".equals(columnName)) {
                    columnExists = true;
                    break;
                }
            }
            
            if (!columnExists) {
                String addColumn = "ALTER TABLE parsing_configs ADD COLUMN timestamp_format TEXT";
                stmt.execute(addColumn);
                logger.info("Migration: Added timestamp_format column to parsing_configs table");
            } else {
                logger.debug("timestamp_format column already exists in parsing_configs table");
            }
        } catch (SQLException e) {
            logger.error("Failed to migrate timestamp_format column", e);
        }
    }
}