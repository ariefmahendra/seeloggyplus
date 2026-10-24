package com.seeloggyplus.features.update.presentation;

import com.seeloggyplus.features.update.domain.AppVersion;
import com.seeloggyplus.features.update.domain.UpdatePreferences;
import com.seeloggyplus.features.update.infrastructure.UpdateLayout;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class UpdateFeatureBehaviorTest {

    @TempDir
    Path root;
    private final Map<String, String> originals = new HashMap<>();
    private final MemoryPreferences preferences = new MemoryPreferences();
    private final List<String> shown = new ArrayList<>();

    @BeforeEach
    void configure() {
        for (String key : List.of("seeloggyplus.distribution", "seeloggyplus.disableUpdateCheck",
                "seeloggyplus.dev", "seeloggyplus.installRoot")) originals.put(key, System.getProperty(key));
        System.setProperty("seeloggyplus.distribution", "store");
        System.clearProperty("seeloggyplus.disableUpdateCheck");
        System.clearProperty("seeloggyplus.dev");
        System.setProperty("seeloggyplus.installRoot", root.toString());
    }

    @AfterEach
    void restore() {
        originals.forEach((key, value) -> {
            if (value == null) System.clearProperty(key);
            else System.setProperty(key, value);
        });
    }

    private void showNotes() {
        var feature = new UpdateFeature(preferences, (version, notes) -> shown.add(version + ":" + notes));
        feature.showWhatsNewIfNeeded();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void firstStoreLaunchRecordsVersionWithoutAnUpdatePopup() {
        showNotes();
        assertTrue(shown.isEmpty());
        assertEquals(Optional.of(AppVersion.current()), preferences.getPreferencesByCode(UpdatePreferences.SEEN_VERSION));
    }

    @Test
    void storeShowsBundledNotesOnceAfterTheVersionChanges() {
        preferences.values.put(UpdatePreferences.SEEN_VERSION, "0.0.1");
        showNotes();
        showNotes();
        assertEquals(1, shown.size());
        assertTrue(shown.getFirst().startsWith(AppVersion.current() + ":"));
        assertTrue(shown.getFirst().length() > AppVersion.current().length() + 1);
    }

    @Test
    void portableNeverShowsOrChecksForUpdatesEvenWithOldUpdatePreferences() {
        System.clearProperty("seeloggyplus.distribution");
        preferences.values.put(UpdatePreferences.SEEN_VERSION, "0.0.1");
        preferences.values.put("update_auto_check", "true");
        preferences.values.put("update_auto_download", "true");
        showNotes();
        assertTrue(shown.isEmpty());
        assertEquals(Optional.of("0.0.1"), preferences.getPreferencesByCode(UpdatePreferences.SEEN_VERSION));
    }

    @Test
    void startupGuardsPreventReleaseNotesInDevelopmentAndHeadlessTests() {
        preferences.values.put(UpdatePreferences.SEEN_VERSION, "0.0.1");
        System.setProperty("seeloggyplus.disableUpdateCheck", "true");
        showNotes();
        System.clearProperty("seeloggyplus.disableUpdateCheck");
        System.setProperty("seeloggyplus.dev", "true");
        showNotes();
        assertTrue(shown.isEmpty());
        assertEquals(Optional.of("0.0.1"), preferences.getPreferencesByCode(UpdatePreferences.SEEN_VERSION));
    }

    @Test
    void portableConfirmsUpgradesDeliveredByOlderInstallers() throws Exception {
        System.clearProperty("seeloggyplus.distribution");
        var layout = new UpdateLayout(root);
        Path jar = layout.jarFor(AppVersion.current());
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "new application");
        layout.writeCurrent(AppVersion.current());
        new UpdateFeature(preferences).reconcileUpdateStartup();
        assertTrue(layout.isHealthy(AppVersion.current()));
    }

    @Test
    void storeDoesNotModifyLegacyInstallationState() throws Exception {
        var layout = new UpdateLayout(root);
        layout.writeCurrent("99.0.0");
        layout.writePrevious("0.0.1");
        Path jar = layout.jarFor("0.0.1");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "previous");
        new UpdateFeature(preferences).reconcileUpdateStartup();
        assertEquals(Optional.of("99.0.0"), layout.currentVersion());
        assertFalse(layout.isHealthy(AppVersion.current()));
    }

    @Test
    void legacyFailedActivationStillRollsBack() throws Exception {
        System.clearProperty("seeloggyplus.distribution");
        var layout = new UpdateLayout(root);
        layout.writeCurrent("99.0.0");
        layout.writePrevious("0.0.1");
        Path jar = layout.jarFor("0.0.1");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "previous");
        new UpdateFeature(preferences).reconcileUpdateStartup();
        assertEquals(Optional.of("0.0.1"), layout.currentVersion());
    }

    private static final class MemoryPreferences implements PreferenceService {
        private final Map<String, String> values = new HashMap<>();
        public void savePreferences(Preference preference) { saveOrUpdatePreferences(preference); }
        public void updatePreferences(Preference preference) { saveOrUpdatePreferences(preference); }
        public Optional<String> getPreferencesByCode(String code) { return Optional.ofNullable(values.get(code)); }
        public List<Preference> getListPreferences() { return List.of(); }
        public void saveOrUpdatePreferences(Preference preference) { values.put(preference.getCode(), preference.getValue()); }
    }
}
