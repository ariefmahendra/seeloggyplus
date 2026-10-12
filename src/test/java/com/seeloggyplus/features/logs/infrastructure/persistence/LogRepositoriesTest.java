package com.seeloggyplus.features.logs.infrastructure.persistence;

import com.seeloggyplus.shared.database.FatalDatabaseException;
import com.seeloggyplus.shared.database.NotFoundException;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.model.ParsingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class LogRepositoriesTest {

    private Connection connection;

    @BeforeEach
    void openDatabase() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE log_files (id TEXT PRIMARY KEY, name TEXT, file_path TEXT, size TEXT, "
                    + "modified TEXT, is_remote BOOLEAN, ssh_server_id TEXT, parsing_configuration_id TEXT)");
            statement.execute("CREATE TABLE parsing_configs (id TEXT PRIMARY KEY, name TEXT UNIQUE, description TEXT, "
                    + "regex_pattern TEXT, timestamp_format TEXT)");
        }
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    @Test
    void logFileCrudUsesTheInjectedConnection() throws Exception {
        LogFileRepositoryImpl repository = new LogFileRepositoryImpl(connection);
        LogFile file = new LogFile("local", "app.log", "/logs", "10", "today", false, null, null);
        repository.insert(file);
        assertEquals("app.log", repository.findById("local").getName());
        file.setName("renamed.log");
        repository.update(file);
        assertEquals("local", repository.findByPathAndName("/logs", "renamed.log").getId());
        repository.updateParsingConfigId("config", "local");
        assertEquals("config", repository.findById("local").getParsingConfigurationID());
        repository.updateParsingConfigId(null, "local");
        assertNull(repository.findById("local").getParsingConfigurationID());
        repository.deleteById("local");
        assertThrows(NotFoundException.class, () -> repository.findById("local"));
        assertFalse(connection.isClosed(), "repositories borrow, not own, the injected connection");
    }

    @Test
    void localAndRemoteRecordsRemainScopedByServer() throws Exception {
        LogFileRepositoryImpl repository = new LogFileRepositoryImpl(connection);
        repository.insert(new LogFile("local", "app.log", "/logs", "10", "today", false, null, null));
        repository.insert(new LogFile("remote-a", "app.log", "/logs", "10", "today", true, "a", null));
        repository.insert(new LogFile("remote-b", "app.log", "/logs", "10", "today", true, "b", null));
        assertEquals("local", repository.findByPathNameAndServer("/logs", "app.log", null, false).getId());
        assertEquals("remote-a", repository.findByPathNameAndServer("/logs", "app.log", "a", true).getId());
        assertEquals("remote-b", repository.findByPathNameAndServer("/logs", "app.log", "b", true).getId());
        repository.deleteAll();
        assertThrows(NotFoundException.class, () -> repository.findById("remote-a"));
    }

    @Test
    void parsingConfigCrudPreservesFieldsAndRawFallback() throws Exception {
        ParsingConfigRepositoryImpl repository = new ParsingConfigRepositoryImpl(connection);
        ParsingConfig config = new ParsingConfig("Test", "(?<message>.*)");
        config.setId("config");
        config.setDescription("description");
        config.setTimestampFormat("yyyy-MM-dd");
        repository.save(config);
        ParsingConfig loaded = repository.findById("config").orElseThrow();
        assertEquals(config.getRegexPattern(), loaded.getRegexPattern());
        assertEquals("description", loaded.getDescription());
        assertEquals("yyyy-MM-dd", loaded.getTimestampFormat());
        config.setName("Updated");
        repository.update(config);
        assertEquals("Updated", repository.findAll().getFirst().getName());
        assertTrue(repository.findDefault().isEmpty());
        repository.delete(config);
        assertTrue(repository.findById("config").isEmpty());
        assertFalse(connection.isClosed());
    }

    @Test
    void parsingConfigMutationFailuresAreReported() throws Exception {
        ParsingConfigRepositoryImpl repository = new ParsingConfigRepositoryImpl(connection);
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE parsing_configs");
        }
        ParsingConfig config = new ParsingConfig("Test", "(?<message>.*)");
        config.setId("config");

        assertThrows(FatalDatabaseException.class, () -> repository.save(config));
        assertThrows(FatalDatabaseException.class, () -> repository.update(config));
        assertThrows(FatalDatabaseException.class, () -> repository.delete(config));
    }

    @Test
    void parsingConfigReadFailuresAreReported() throws Exception {
        ParsingConfigRepositoryImpl repository = new ParsingConfigRepositoryImpl(connection);
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE parsing_configs");
        }

        assertThrows(FatalDatabaseException.class, () -> repository.findById("config"));
        assertThrows(FatalDatabaseException.class, repository::findAll);
    }
}
