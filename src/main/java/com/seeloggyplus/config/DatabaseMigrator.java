package com.seeloggyplus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Versioned, transactional database schema migrator.
 * <p>
 * Design rules (to avoid corrupting or losing user data):
 * <ul>
 *   <li><b>Additive only</b> — columns/tables are only ever added. Nothing is
 *       dropped, renamed or retyped, so downgrading to an older app version
 *       keeps working (old code reads columns by name and ignores extras).</li>
 *   <li><b>Idempotent</b> — every step is guarded by an existence check and a
 *       duplicate-column failure is tolerated, so concurrent instances or a
 *       repeated run cannot break.</li>
 *   <li><b>Atomic</b> — all changes run in a single transaction; on failure the
 *       transaction is rolled back and a {@link DatabaseMigrationException} is
 *       thrown.</li>
 *   <li><b>Backed up</b> — the database is backed up before migrating.</li>
 * </ul>
 */
public final class DatabaseMigrator {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrator.class);

    /** Current expected schema version. Increment when adding a migration. */
    public static final int SCHEMA_VERSION = 4;

    /** How many rolling backups to keep next to the database. */
    private static final int KEEP_BACKUPS = 3;

    private DatabaseMigrator() {
    }

    /**
     * Migrates {@code connection} to {@link #SCHEMA_VERSION}.
     *
     * @param connection    open connection to the SQLite database
     * @param dbFile        the database file (used for the pre-migration backup)
     * @param isNewDatabase {@code true} when the database was just created and
     *                      already has the latest schema
     * @throws DatabaseMigrationException when a migration fails and is rolled back
     * @throws SQLException                when the connection itself cannot be used
     */
    public static void migrate(Connection connection, Path dbFile, boolean isNewDatabase) throws SQLException {
        if (isNewDatabase) {
            setUserVersion(connection, SCHEMA_VERSION);
            logger.info("Fresh database created at schema version {}", SCHEMA_VERSION);
            return;
        }

        int from = getUserVersion(connection);
        if (from >= SCHEMA_VERSION) {
            logger.debug("Database already at schema version {} (no migration needed)", from);
            return;
        }

        logger.info("Migrating database schema from version {} to {}", from, SCHEMA_VERSION);
        Path backup = backupDatabase(connection, dbFile, from);

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            migrateTimestampFormat(connection);
            migrateRecentFileMode(connection);
            migrateServerOrganization(connection);
            migrateServerGroups(connection);
            migrateServerAuth(connection);
            setUserVersion(connection, SCHEMA_VERSION);
            connection.commit();
            logger.info("Database migration to version {} completed", SCHEMA_VERSION);
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Database migration failed and was rolled back. Backup: {}", backup, e);
            throw new DatabaseMigrationException(buildFailureMessage(backup), backup, e);
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static String buildFailureMessage(Path backup) {
        String base = "Database migration failed. No changes were applied (rolled back).";
        if (backup != null) {
            return base + " A backup was saved at: " + backup.toAbsolutePath();
        }
        return base + " No backup could be created.";
    }

    // ------------------------------------------------------------------
    // Migrations
    // ------------------------------------------------------------------

    /** v1: timestamp_format on parsing_configs. */
    private static void migrateTimestampFormat(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "parsing_configs", "timestamp_format", "TEXT");
    }

    /** v1: mode on recent_files. */
    private static void migrateRecentFileMode(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "recent_files", "mode", "TEXT");
    }

    /** v2: user organization of the server list (favorite / order / group). */
    private static void migrateServerOrganization(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "ssh_servers", "favorite", "BOOLEAN NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "ssh_servers", "sort_order", "INTEGER");
        addColumnIfMissing(connection, "ssh_servers", "group_name", "TEXT");
        backfillSortOrder(connection);
    }

    /** v4: per-server authentication method (password or private key). */
    private static void migrateServerAuth(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "ssh_servers", "auth_type", "TEXT");
        addColumnIfMissing(connection, "ssh_servers", "key_path", "TEXT");
        addColumnIfMissing(connection, "ssh_servers", "key_passphrase", "TEXT");
    }

    /** v3: first-class server groups, so an empty group survives a restart. */
    private static void migrateServerGroups(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS server_groups (name TEXT PRIMARY KEY, sort_order INTEGER)");
        }
        boolean alreadyPopulated;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM server_groups")) {
            alreadyPopulated = rs.next() && rs.getInt(1) > 0;
        }
        if (alreadyPopulated) {
            logger.debug("server_groups already populated; skipping backfill");
            return;
        }
        String backfill = "INSERT OR IGNORE INTO server_groups(name, sort_order) "
                + "SELECT group_name, ROW_NUMBER() OVER (ORDER BY MIN(created_at) ASC, group_name ASC) - 1 "
                + "FROM ssh_servers WHERE group_name IS NOT NULL AND TRIM(group_name) <> '' GROUP BY group_name";
        try (Statement stmt = connection.createStatement()) {
            int inserted = stmt.executeUpdate(backfill);
            logger.info("Migration: backfilled {} server group(s)", inserted);
        }
    }

    /**
     * Assigns a stable {@code sort_order} to every server that does not have one
     * yet, preserving the legacy {@code created_at DESC} order exactly once.
     */
    static void backfillSortOrder(Connection connection) throws SQLException {
        boolean needsBackfill;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ssh_servers WHERE sort_order IS NULL")) {
            needsBackfill = rs.next() && rs.getInt(1) > 0;
        }
        if (!needsBackfill) {
            return;
        }
        String sql = "UPDATE ssh_servers SET sort_order = ("
                + " SELECT COUNT(*) FROM ssh_servers s2"
                + " WHERE s2.created_at > ssh_servers.created_at"
                + "    OR (s2.created_at = ssh_servers.created_at AND s2.id < ssh_servers.id)"
                + ") WHERE sort_order IS NULL";
        try (Statement stmt = connection.createStatement()) {
            int updated = stmt.executeUpdate(sql);
            logger.info("Migration: backfilled sort_order for {} server(s)", updated);
        }
    }

    // ------------------------------------------------------------------
    // Schema helpers
    // ------------------------------------------------------------------

    static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition)
            throws SQLException {
        if (columnExists(connection, table, column)) {
            logger.debug("Column {}.{} already exists", table, column);
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            logger.info("Migration: added column {}.{}", table, column);
        } catch (SQLException e) {
            if (isDuplicateColumn(e)) {
                // Another instance migrated concurrently; this is fine.
                logger.debug("Column {}.{} was added concurrently", table, column);
                return;
            }
            throw e;
        }
    }

    private static boolean isDuplicateColumn(SQLException e) {
        return e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate column name");
    }

    // ------------------------------------------------------------------
    // Schema version
    // ------------------------------------------------------------------

    static int getUserVersion(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    static void setUserVersion(Connection connection, int version) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA user_version = " + version);
        }
    }

    // ------------------------------------------------------------------
    // Backup
    // ------------------------------------------------------------------

    /**
     * Creates a consistent, connection-aware backup using {@code VACUUM INTO}.
     * Failure aborts migration before any schema changes; raw copying a live
     * SQLite file could omit committed WAL data.
     */
    static Path backupDatabase(Connection connection, Path dbFile, int fromVersion) {
        Path backup = dbFile.resolveSibling(
                dbFile.getFileName() + ".bak-v" + fromVersion + "-" + System.currentTimeMillis());
        String escaped = backup.toAbsolutePath().toString().replace("'", "''");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("VACUUM INTO '" + escaped + "'");
            logger.info("Database backup created at {}", backup);
        } catch (SQLException e) {
            throw new DatabaseMigrationException("Backup failed; migration was not started.", backup, e);
        }
        pruneOldBackups(dbFile);
        return backup;
    }

    private static void pruneOldBackups(Path dbFile) {
        Path dir = dbFile.getParent();
        if (dir == null) {
            return;
        }
        String prefix = dbFile.getFileName() + ".bak-v";
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> backups = new ArrayList<>();
            stream.filter(p -> p.getFileName().toString().startsWith(prefix)).forEach(backups::add);
            if (backups.size() <= KEEP_BACKUPS) {
                return;
            }
            backups.sort(Comparator.comparingLong(DatabaseMigrator::lastModified));
            int toDelete = backups.size() - KEEP_BACKUPS;
            for (int i = 0; i < toDelete; i++) {
                try {
                    Files.deleteIfExists(backups.get(i));
                } catch (IOException e) {
                    logger.debug("Could not delete old backup {}", backups.get(i), e);
                }
            }
        } catch (IOException e) {
            logger.debug("Could not prune old backups", e);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }
}
