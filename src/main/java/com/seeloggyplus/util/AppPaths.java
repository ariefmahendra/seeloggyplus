package com.seeloggyplus.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Central locations for application data.
 * <p>
 * The data directory defaults to {@code .data} but can be overridden with the
 * {@code seeloggyplus.dataDir} system property. Tests use this to isolate their
 * data and keep the developer's real recent-files/preferences database clean.
 */
public final class AppPaths {

    public static final String DATA_DIR_PROPERTY = "seeloggyplus.dataDir";

    private AppPaths() {
    }

    public static Path dataDir() {
        return Paths.get(System.getProperty(DATA_DIR_PROPERTY, ".data"));
    }

    public static Path dataFile(String name) {
        return dataDir().resolve(name);
    }
}
