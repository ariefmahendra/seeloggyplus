package com.seeloggyplus.features.settings.infrastructure;

import com.seeloggyplus.shared.database.FatalDatabaseException;
import com.seeloggyplus.shared.model.Preference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferenceRepositoryFailureTest {

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
        PreferenceRepositoryImpl repository = new PreferenceRepositoryImpl(connection);
        Preference preference = new Preference("font", "14");

        assertThrows(FatalDatabaseException.class, () -> repository.savePreferences(preference));
        assertThrows(FatalDatabaseException.class, () -> repository.saveOrUpdatePreferences(preference));
        assertThrows(FatalDatabaseException.class, () -> repository.updatePreferences(preference));
        assertThrows(FatalDatabaseException.class, () -> repository.getPreferencesByCode("font"));
        assertThrows(FatalDatabaseException.class, repository::getListPreferences);
    }

    @Test
    void missingCodeReturnsAnEmptyOptional() throws Exception {
        createTable();
        PreferenceRepositoryImpl repository = new PreferenceRepositoryImpl(connection);

        assertTrue(repository.getPreferencesByCode("absent").isEmpty());
    }

    @Test
    void updateWritesTheValueForTheGivenCode() throws Exception {
        createTable();
        PreferenceRepositoryImpl repository = new PreferenceRepositoryImpl(connection);
        repository.saveOrUpdatePreferences(new Preference("font", "12"));

        repository.updatePreferences(new Preference("font", "14"));

        assertEquals("14", repository.getPreferencesByCode("font").orElseThrow());
    }

    @Test
    void crudRoundTripsThroughTheInjectedConnection() throws Exception {
        createTable();
        PreferenceRepositoryImpl repository = new PreferenceRepositoryImpl(connection);

        repository.savePreferences(new Preference("theme", "graphite"));
        assertEquals("graphite", repository.getPreferencesByCode("theme").orElseThrow());

        repository.saveOrUpdatePreferences(new Preference("theme", "dark"));
        assertEquals("dark", repository.getPreferencesByCode("theme").orElseThrow());

        List<Preference> stored = repository.getListPreferences();
        assertEquals(1, stored.size());
        assertEquals("theme", stored.get(0).getCode());
        assertEquals("dark", stored.get(0).getValue());
        assertFalse(connection.isClosed(), "repositories borrow, not own, the injected connection");
    }

    private void createTable() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE preferences (code TEXT PRIMARY KEY, value TEXT)");
        }
    }
}
