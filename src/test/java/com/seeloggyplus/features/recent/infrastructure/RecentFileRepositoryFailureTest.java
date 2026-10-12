package com.seeloggyplus.features.recent.infrastructure;

import com.seeloggyplus.features.recent.domain.RecentFile;
import com.seeloggyplus.shared.database.FatalDatabaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentFileRepositoryFailureTest {

    private Connection connection;

    @BeforeEach
    void openDatabase() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    @Test
    void failuresAreReportedWhenTheSchemaIsMissing() {
        RecentFileRepositoryImpl repository = new RecentFileRepositoryImpl(connection);
        RecentFile recent = recentFile("recent-1", "file-1");

        assertThrows(FatalDatabaseException.class, () -> repository.save(recent));
        assertThrows(FatalDatabaseException.class, repository::findAll);
        assertThrows(FatalDatabaseException.class, () -> repository.findById("recent-1"));
        assertThrows(FatalDatabaseException.class, () -> repository.findByFileId("file-1"));
        assertThrows(FatalDatabaseException.class, () -> repository.deleteById("recent-1"));
        assertThrows(FatalDatabaseException.class, () -> repository.deleteByFileId("file-1"));
        assertThrows(FatalDatabaseException.class, repository::deleteAll);
    }

    @Test
    void crudUsesTheInjectedConnection() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE recent_files (id TEXT PRIMARY KEY, file_id TEXT NOT NULL, "
                    + "last_opened TEXT NOT NULL, mode TEXT)");
        }
        RecentFileRepositoryImpl repository = new RecentFileRepositoryImpl(connection);
        RecentFile recent = recentFile("recent-1", "file-1");

        repository.save(recent);

        RecentFile loaded = repository.findByFileId("file-1").orElseThrow();
        assertEquals("recent-1", loaded.getId());
        assertEquals("TAIL", loaded.getMode());
        assertEquals(recent.getLastOpened().toString(), loaded.getLastOpened().toString());

        repository.deleteByFileId("file-1");
        assertTrue(repository.findByFileId("file-1").isEmpty());
        repository.save(recent);
        repository.deleteAll();
        assertTrue(repository.findByFileId("file-1").isEmpty());
        assertFalse(connection.isClosed(), "repositories borrow, not own, the injected connection");
    }

    private static RecentFile recentFile(String id, String fileId) {
        RecentFile recent = new RecentFile();
        recent.setId(id);
        recent.setFileId(fileId);
        recent.setLastOpened(java.time.LocalDateTime.now());
        recent.setMode("TAIL");
        return recent;
    }
}
