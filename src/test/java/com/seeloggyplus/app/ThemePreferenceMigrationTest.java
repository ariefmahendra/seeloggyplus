package com.seeloggyplus.app;

import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThemePreferenceMigrationTest {
    @Test void legacyThemeBecomesLightInMemoryAndStorageOnlyOnce() {
        for(String legacy:new String[]{"graphite","GRAPHITE"," Graphite "}) {
            var service=new MemoryPreferences(); service.values.put("app_theme",legacy); service.values.put("app_font_size","14");
            var startup=new HashMap<>(service.values);
            ThemePreferenceMigration.normalize(startup,service);
            assertEquals("light",startup.get("app_theme")); assertEquals("light",service.values.get("app_theme"));
            assertEquals("14",service.values.get("app_font_size"));
            ThemePreferenceMigration.normalize(startup,service); assertEquals(1,service.writes);
        }
    }
    @Test void supportedMissingAndUnknownPreferencesAreNotRewritten() {
        for(String value:new String[]{"light","dark","","unknown"}) {
            var service=new MemoryPreferences(); service.values.put("app_theme",value); var startup=new HashMap<>(service.values);
            ThemePreferenceMigration.normalize(startup,service);
            assertEquals(value,startup.get("app_theme")); assertEquals(0,service.writes);
        }
        var service=new MemoryPreferences(); var startup=new HashMap<String,String>(); ThemePreferenceMigration.normalize(startup,service);
        assertFalse(startup.containsKey("app_theme")); assertEquals(0,service.writes);
    }
    @Test void failedPersistenceStillUsesLightWithoutAbortingStartup() {
        var service=new MemoryPreferences(); service.fail=true; var startup=new HashMap<>(Map.of("app_theme","graphite"));
        assertDoesNotThrow(() -> ThemePreferenceMigration.normalize(startup,service)); assertEquals("light",startup.get("app_theme"));
    }
    private static class MemoryPreferences implements PreferenceService {
        final Map<String,String> values=new HashMap<>(); int writes; boolean fail;
        public void savePreferences(Preference preference) { saveOrUpdatePreferences(preference); }
        public void updatePreferences(Preference preference) { saveOrUpdatePreferences(preference); }
        public Optional<String> getPreferencesByCode(String code) { return Optional.ofNullable(values.get(code)); }
        public List<Preference> getListPreferences() { return values.entrySet().stream().map(entry -> new Preference(entry.getKey(),entry.getValue())).toList(); }
        public void saveOrUpdatePreferences(Preference preference) { if(fail) throw new IllegalStateException("Read-only storage"); values.put(preference.getCode(),preference.getValue()); writes++; }
    }
}
