package com.seeloggyplus.shared.database;

import java.nio.file.Path;

/**
 * Thrown when the database schema could not be migrated.
 * <p>
 * The migration is transactional: when this exception is thrown the schema has
 * been rolled back to its previous state, so the data is consistent. A backup
 * of the database (taken before the migration) may be available via
 * {@link #getBackupPath()} so the user can recover manually if needed.
 */
public class DatabaseMigrationException extends RuntimeException {

    private final transient Path backupPath;

    public DatabaseMigrationException(String message, Path backupPath, Throwable cause) {
        super(message, cause);
        this.backupPath = backupPath;
    }

    /**
     * @return path to the pre-migration backup, or {@code null} if no backup was created
     */
    public Path getBackupPath() {
        return backupPath;
    }
}
