package com.seeloggyplus.service;

import com.seeloggyplus.model.Preference;

import java.util.List;
import java.util.Optional;

public interface PreferenceService {
    void savePreferences(Preference preferences);
    void updatePreferences(Preference preferences);
    Optional<String> getPreferencesByCode(String key);
    List<Preference> getListPreferences();
    void saveOrUpdatePreferences(Preference preferences);
}
