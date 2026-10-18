package com.seeloggyplus.shared.settings;

import com.seeloggyplus.shared.model.Preference;

import java.util.List;
import java.util.Optional;

public interface PreferenceService {

    void savePreferences(Preference preferences);

    void updatePreferences(Preference preferences);

    Optional<String> getPreferencesByCode(String code);

    List<Preference> getListPreferences();

    void saveOrUpdatePreferences(Preference preferences);
}
