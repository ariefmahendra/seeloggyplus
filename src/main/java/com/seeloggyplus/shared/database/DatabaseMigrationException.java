package com.seeloggyplus.shared.database;

import java.nio.file.Path;

public class DatabaseMigrationException extends RuntimeException {

    private final transient Path backupPath;

    public DatabaseMigrationException(String message, Path backupPath, Throwable cause) {
        super(message, cause);
        this.backupPath = backupPath;
    }

    public Path getBackupPath() {
        return backupPath;
    }
}
