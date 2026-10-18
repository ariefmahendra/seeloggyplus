package com.seeloggyplus.shared.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPathsTest {

    private String originalDataDir;

    @BeforeEach
    void captureDataDirProperty() {
        originalDataDir = System.getProperty(AppPaths.DATA_DIR_PROPERTY);
    }

    @AfterEach
    void restoreDataDirProperty() {
        if (originalDataDir == null) {
            System.clearProperty(AppPaths.DATA_DIR_PROPERTY);
        } else {
            System.setProperty(AppPaths.DATA_DIR_PROPERTY, originalDataDir);
        }
    }

    @Test
    void dataDirPropertyNameIsStable() {
        assertEquals("seeloggyplus.dataDir", AppPaths.DATA_DIR_PROPERTY);
    }

    @Test
    void dataDirFollowsTheSystemPropertyWhenSet() {
        System.setProperty(AppPaths.DATA_DIR_PROPERTY, "build/isolated-data");

        assertEquals(Path.of("build/isolated-data"), AppPaths.dataDir());
        assertTrue(AppPaths.dataFile("app.db").endsWith("app.db"));
    }

    @Test
    void dataDirFallsBackToTheLocalDotDataDirectory() {
        System.clearProperty(AppPaths.DATA_DIR_PROPERTY);

        assertEquals(Path.of(".data"), AppPaths.dataDir());
    }
}
