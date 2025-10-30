package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.Preference;
import com.seeloggyplus.repository.PreferenceRepository;
import com.seeloggyplus.repository.impl.PreferenceRepositoryImpl;
import com.seeloggyplus.service.PreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PreferenceServiceImpl implements PreferenceService {
    private static final Logger logger = LoggerFactory.getLogger(PreferenceServiceImpl.class);
    private final PreferenceRepository preferencesRepository;

    public PreferenceServiceImpl() {
        preferencesRepository = new PreferenceRepositoryImpl();
    }

    @Override
    public void savePreferences(Preference preferences) {
        logger.info("Starting Save Preferences");
        String id = UUID.randomUUID().toString();
        preferences.setId(id);
        preferencesRepository.savePreferences(preferences);
        logger.info("Finish save preferences");
    }

    @Override
    public void updatePreferences(Preference preferences) {
        logger.info("Starting update preferences");
        preferencesRepository.updatePreferences(preferences);
        logger.info("Finish update preferences");
    }

    @Override
    public Optional<String> getPreferencesByCode(String code) {
        logger.info("Starting get preferences");
        return preferencesRepository.getPreferencesByCode(code);
    }

    @Override
    public List<Preference> getListPreferences() {
        logger.info("Starting get list preferences");
        return preferencesRepository.getListPreferences();
    }

    @Override
    public void saveOrUpdatePreferences(Preference preferences) {
        preferencesRepository.saveOrUpdatePreferences(preferences);
    }
}
