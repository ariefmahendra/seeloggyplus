package com.seeloggyplus.app;

import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.model.Preference;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ThemePreferenceMigration {
    private static final Logger logger = LoggerFactory.getLogger(ThemePreferenceMigration.class);

    private ThemePreferenceMigration() { }

    static void normalize(Map<String, String> startup, PreferenceService preferences) {
        String value = startup.get("app_theme");
        if (value == null || !"graphite".equalsIgnoreCase(value.trim())) return;
        startup.put("app_theme", "light");
        try {
            preferences.saveOrUpdatePreferences(new Preference("app_theme", "light"));
        } catch (RuntimeException failure) {
            logger.warn("Could not persist the Light theme migration; continuing with Light", failure);
        }
    }
}
