package com.seeloggyplus.shared.util;

import java.nio.file.Path;
import java.nio.file.Paths;

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
