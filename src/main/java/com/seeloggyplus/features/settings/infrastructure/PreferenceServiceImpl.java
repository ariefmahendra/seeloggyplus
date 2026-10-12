package com.seeloggyplus.features.settings.infrastructure;

import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PreferenceServiceImpl implements PreferenceService {

    private static final Logger logger = LoggerFactory.getLogger(PreferenceServiceImpl.class);
    private final PreferenceRepository preferencesRepository;

    public PreferenceServiceImpl() {
        this(new PreferenceRepositoryImpl());
    }

    public PreferenceServiceImpl(PreferenceRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    @Override
    public void savePreferences(Preference preferences) {
        logger.debug("Saving new preference: {}", preferences.getCode());
        if (preferences.getId() == null || preferences.getId().isEmpty()) {
            preferences.setId(UUID.randomUUID().toString());
        }
        preferencesRepository.savePreferences(preferences);
        logger.info("Saved preference: {}", preferences.getCode());
    }

    @Override
    public void updatePreferences(Preference preferences) {
        logger.debug("Updating preference: {}", preferences.getCode());
        preferencesRepository.updatePreferences(preferences);
        logger.info("Updated preference: {}", preferences.getCode());
    }

    @Override
    public Optional<String> getPreferencesByCode(String code) {
        logger.debug("Retrieving preference for code: {}", code);
        return preferencesRepository.getPreferencesByCode(code);
    }

    @Override
    public List<Preference> getListPreferences() {
        logger.debug("Retrieving all preferences");
        return preferencesRepository.getListPreferences();
    }

    @Override
    public void saveOrUpdatePreferences(Preference preferences) {
        logger.debug("Save or Update preference: {}", preferences.getCode());
        preferencesRepository.saveOrUpdatePreferences(preferences);
    }
}
