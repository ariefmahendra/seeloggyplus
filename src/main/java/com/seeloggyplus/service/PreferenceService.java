package com.seeloggyplus.service;

import com.seeloggyplus.model.Preference;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing application preferences.
 * <p>
 * Provides operations to create, read, update, and persist user preferences.
 */
public interface PreferenceService {

    /**
     * Saves a new preference setting.
     *
     * @param preferences The preference entity to save.
     */
    void savePreferences(Preference preferences);

    /**
     * Updates an existing preference setting.
     *
     * @param preferences The preference entity with updated values.
     */
    void updatePreferences(Preference preferences);

    /**
     * Retrieves a preference value by its unique code (key).
     *
     * @param code The unique code/key of the preference.
     * @return An Optional containing the value if found.
     */
    Optional<String> getPreferencesByCode(String code);

    /**
     * Retrieves all stored preferences.
     *
     * @return A list of all preferences.
     */
    List<Preference> getListPreferences();

    /**
     * Saves a preference if it doesn't exist, or updates it if it does.
     * <p>
     * This is an idempotent operation acting as an "upsert".
     *
     * @param preferences The preference entity to save or update.
     */
    void saveOrUpdatePreferences(Preference preferences);
}
