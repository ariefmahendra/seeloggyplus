package com.seeloggyplus.features.update.domain;

import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {

    private static final String VERSION = resolve();

    private AppVersion() {
    }

    public static String current() {
        return VERSION;
    }

    private static String resolve() {
        String version = "DEV";
        try (InputStream input = AppVersion.class.getResourceAsStream("/version.properties")) {
            if (input != null) {
                Properties properties = new Properties();
                properties.load(input);
                version = properties.getProperty("version", version);
            }
        } catch (Exception ignored) {
        }
        return version;
    }
}
