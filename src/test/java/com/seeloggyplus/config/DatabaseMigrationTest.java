package com.seeloggyplus.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that schema migrations preserve user data and never leave the
 * database half-migrated.
 */
class DatabaseMigrationTest {

    @TempDir
    Path tempDir;

    private Connection connect(Path dbFile) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
    }

    /** Builds the legacy schema (before favorite/sort_order/group_name). */
    private void createLegacySchema(Connection c) throws SQLException {
        try (Statement stmt = c.createStatement()) {
            stmt.execute("CREATE TABLE parsing_configs (id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE,"
                    + " description TEXT, regex_pattern TEXT NOT NULL)");
            stmt.execute("CREATE TABLE recent_files (id TEXT PRIMARY KEY, file_id TEXT NOT NULL UNIQUE,"
                    + " last_opened TEXT NOT NULL)");
            stmt.execute("CREATE TABLE ssh_servers (id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE,"
                    + " host TEXT NOT NULL, port INTEGER NOT NULL, username TEXT NOT NULL, password TEXT,"
                    + " default_path TEXT, created_at TEXT NOT NULL, last_used TEXT,"
                    + " save_password BOOLEAN NOT NULL DEFAULT 0)");
        }
    }

    private void insertLegacyServer(Connection c, String id, String name, String createdAt) throws SQLException {
        try (Statement stmt = c.createStatement()) {
            stmt.execute("INSERT INTO ssh_servers(id, name, host, port, username, created_at)"
                    + " VALUES('" + id + "', '" + name + "', 'host', 22, 'user', '" + createdAt + "')");
        }
    }

    @Test
    void migratesLegacyDatabasePreservingData() throws Exception {
        Path dbFile = tempDir.resolve("seeloggyplus.db");
        try (Connection c = connect(dbFile)) {
            createLegacySchema(c);
            insertLegacyServer(c, "old-1", "Oldest", "2023-01-01T00:00:00");
            insertLegacyServer(c, "new-1", "Newest", "2024-01-01T00:00:00");

            DatabaseMigrator.migrate(c, dbFile, false);

            assertTrue(DatabaseMigrator.columnExists(c, "ssh_servers", "favorite"));
            assertTrue(DatabaseMigrator.columnExists(c, "ssh_servers", "sort_order"));
            assertTrue(DatabaseMigrator.columnExists(c, "ssh_servers", "group_name"));
            assertTrue(DatabaseMigrator.columnExists(c, "recent_files", "mode"));
            assertTrue(DatabaseMigrator.columnExists(c, "parsing_configs", "timestamp_format"));
            assertEquals(DatabaseMigrator.SCHEMA_VERSION, DatabaseMigrator.getUserVersion(c));

            // Existing rows must survive and get sensible defaults.
            assertEquals(2, countServers(c));
            assertEquals("Newest", serverName(c, "new-1"));
            assertEquals(0, serverFavorite(c, "new-1"));
            // Newest keeps the top position (sort_order 0), oldest becomes 1.
            assertEquals(0, serverSortOrder(c, "new-1"));
            assertEquals(1, serverSortOrder(c, "old-1"));

            // A backup must have been created before migrating.
            assertTrue(hasBackup(dbFile), "pre-migration backup should exist");
        }
    }

    @Test
    void migrationIsIdempotent() throws Exception {
        Path dbFile = tempDir.resolve("seeloggyplus.db");
        try (Connection c = connect(dbFile)) {
            createLegacySchema(c);
            insertLegacyServer(c, "s1", "Server One", "2024-01-01T00:00:00");

            DatabaseMigrator.migrate(c, dbFile, false);
            DatabaseMigrator.migrate(c, dbFile, false);

            assertEquals(DatabaseMigrator.SCHEMA_VERSION, DatabaseMigrator.getUserVersion(c));
            assertEquals(1, countServers(c));
            assertEquals("Server One", serverName(c, "s1"));
        }
    }

    @Test
    void freshDatabaseStampsVersionWithoutBackup() throws Exception {
        Path dbFile = tempDir.resolve("fresh.db");
        try (Connection c = connect(dbFile)) {
            DatabaseMigrator.migrate(c, dbFile, true);
            assertEquals(DatabaseMigrator.SCHEMA_VERSION, DatabaseMigrator.getUserVersion(c));
        }
        assertFalse(hasBackup(dbFile), "fresh database must not produce a backup");
    }

    @Test
    void failedMigrationRollsBackAndThrows() throws Exception {
        Path dbFile = tempDir.resolve("seeloggyplus.db");
        try (Connection c = connect(dbFile)) {
            createLegacySchema(c);
            insertLegacyServer(c, "s1", "Server One", "2024-01-01T00:00:00");

            // Force every write to fail.
            try (Statement stmt = c.createStatement()) {
                stmt.execute("PRAGMA query_only = 1");
            }

            assertThrows(DatabaseMigrationException.class,
                    () -> DatabaseMigrator.migrate(c, dbFile, false));

            // The schema must be untouched (rolled back).
            assertFalse(DatabaseMigrator.columnExists(c, "ssh_servers", "favorite"));
            assertEquals(0, DatabaseMigrator.getUserVersion(c));
            assertEquals(1, countServers(c));
        }
    }

    @Test
    void backupFailureStopsMigrationBeforeChangingSchema() throws Exception {
        Path db = tempDir.resolve("backup-failure.db");
        try (Connection c = connect(db)) {
            createLegacySchema(c);
            assertThrows(DatabaseMigrationException.class,
                    () -> DatabaseMigrator.migrate(c, tempDir.resolve("missing/sub/db"), false));
            assertFalse(DatabaseMigrator.columnExists(c, "ssh_servers", "favorite"));
            assertEquals(0, DatabaseMigrator.getUserVersion(c));
        }
    }

    @Test
    void migratesExistingGroupNamesIntoTheGroupsTable() throws Exception {
        Path dbFile = tempDir.resolve("groups.db");
        try (Connection c = connect(dbFile)) {
            createLegacySchema(c);
            insertLegacyServer(c, "s1", "One", "2024-01-01T00:00:00");
            insertLegacyServer(c, "s2", "Two", "2023-01-01T00:00:00");
            insertLegacyServer(c, "s3", "Three", "2022-01-01T00:00:00");
            DatabaseMigrator.migrate(c, dbFile, false);

            // Simulate a v2 database: groups live on ssh_servers only.
            try (Statement st = c.createStatement()) {
                st.execute("UPDATE ssh_servers SET group_name = 'Production' WHERE id = 's1'");
                st.execute("UPDATE ssh_servers SET group_name = 'Production' WHERE id = 's2'");
                st.execute("UPDATE ssh_servers SET group_name = 'Staging' WHERE id = 's3'");
                st.execute("DROP TABLE server_groups");
                st.execute("PRAGMA user_version = 2");
            }

            DatabaseMigrator.migrate(c, dbFile, false);

            assertTrue(DatabaseMigrator.tableExists(c, "server_groups"));
            assertEquals(2, groupCount(c));
            // Oldest server first: Staging (2022) then Production (2024).
            assertEquals("Staging", groupNameAt(c, 0));
            assertEquals("Production", groupNameAt(c, 1));
            assertEquals(3, countServers(c), "server rows must survive the group backfill");
            assertEquals("Production", serverGroupName(c, "s1"));
            assertTrue(hasBackup(dbFile));

            // Idempotent: a second run must not duplicate groups.
            DatabaseMigrator.migrate(c, dbFile, false);
            assertEquals(2, groupCount(c));
        }
    }

    @Test
    void failureAfterFirstAlterRollsBackAllEarlierChanges() throws Exception {
        Path db = tempDir.resolve("rollback.db");
        try (Connection c = connect(db); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE parsing_configs (id TEXT PRIMARY KEY)");
            // recent_files is deliberately absent: second ALTER fails.
            assertThrows(DatabaseMigrationException.class, () -> DatabaseMigrator.migrate(c, db, false));
            assertFalse(DatabaseMigrator.columnExists(c, "parsing_configs", "timestamp_format"));
            assertEquals(0, DatabaseMigrator.getUserVersion(c));
            assertTrue(hasBackup(db));
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private int countServers(Connection c) throws SQLException {
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ssh_servers")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private String serverName(Connection c, String id) throws SQLException {
        return queryString(c, "SELECT name FROM ssh_servers WHERE id = '" + id + "'");
    }

    private int serverFavorite(Connection c, String id) throws SQLException {
        return queryInt(c, "SELECT favorite FROM ssh_servers WHERE id = '" + id + "'");
    }

    private int serverSortOrder(Connection c, String id) throws SQLException {
        return queryInt(c, "SELECT sort_order FROM ssh_servers WHERE id = '" + id + "'");
    }

    private int groupCount(Connection c) throws SQLException {
        return queryInt(c, "SELECT COUNT(*) FROM server_groups");
    }

    private String groupNameAt(Connection c, int index) throws SQLException {
        return queryString(c, "SELECT name FROM server_groups ORDER BY sort_order ASC LIMIT 1 OFFSET " + index);
    }

    private String serverGroupName(Connection c, String id) throws SQLException {
        return queryString(c, "SELECT group_name FROM ssh_servers WHERE id = '" + id + "'");
    }

    private String queryString(Connection c, String sql) throws SQLException {
        try (Statement stmt = c.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private int queryInt(Connection c, String sql) throws SQLException {
        try (Statement stmt = c.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private boolean hasBackup(Path dbFile) throws Exception {
        Path dir = dbFile.getParent();
        if (dir == null || !Files.exists(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            List<Path> backups = new ArrayList<>();
            stream.filter(p -> p.getFileName().toString().startsWith(dbFile.getFileName() + ".bak-v"))
                    .forEach(backups::add);
            return !backups.isEmpty();
        }
    }
}
