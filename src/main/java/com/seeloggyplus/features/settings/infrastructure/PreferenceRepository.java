package com.seeloggyplus.features.settings.infrastructure;

import com.seeloggyplus.shared.model.Preference;

import java.util.List;
import java.util.Optional;

public interface PreferenceRepository {
    void savePreferences(Preference preferences);
    void saveOrUpdatePreferences(Preference preferences);
    void updatePreferences(Preference preferences);
    Optional<String> getPreferencesByCode(String key);
    List<Preference> getListPreferences();
}
