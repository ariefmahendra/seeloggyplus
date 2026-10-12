package com.seeloggyplus.shared.database;

import com.seeloggyplus.shared.util.AppPaths;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {

    @Test
    void databaseIsIsolatedFromRealDataDir() throws Exception {
        String dataDir = System.getProperty(AppPaths.DATA_DIR_PROPERTY);
        assertNotNull(dataDir, "seeloggyplus.dataDir must be set for tests");
        assertTrue(dataDir.contains("test-data"),
                "tests must use an isolated data dir, was: " + dataDir);

        Connection connection = DatabaseConfig.getInstance().getConnection();
        assertNotNull(connection, "database connection must be available");

        String url = connection.getMetaData().getURL();
        assertTrue(url.replace('\\', '/').contains("test-data"),
                "database URL must point inside the isolated test dir, was: " + url);
        assertTrue(AppPaths.dataDir().toString().replace('\\', '/').contains("test-data"),
                "AppPaths must resolve to the isolated test dir");
    }
}
