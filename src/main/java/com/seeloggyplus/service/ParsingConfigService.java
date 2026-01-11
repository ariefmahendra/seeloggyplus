package com.seeloggyplus.service;

import com.seeloggyplus.model.ParsingConfig;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing parsing configurations.
 * <p>
 * Provides operations to CRUD {@link ParsingConfig} entities and intelligent
 * autodetection of log formats from sample lines.
 */
public interface ParsingConfigService {

    /**
     * Finds a parsing configuration by its ID.
     *
     * @param id The unique identifier.
     * @return An Optional containing the config if found.
     */
    Optional<ParsingConfig> findById(String id);

    /**
     * Retrieves all available parsing configurations.
     *
     * @return List of all parsing configs.
     */
    List<ParsingConfig> findAll();

    /**
     * Saves a new parsing configuration.
     *
     * @param config The configuration to save.
     */
    void save(ParsingConfig config);

    /**
     * Updates an existing parsing configuration.
     *
     * @param config The configuration with updated values.
     */
    void update(ParsingConfig config);

    /**
     * Deletes a parsing configuration.
     *
     * @param config The configuration to delete.
     */
    void delete(ParsingConfig config);

    /**
     * Finds the default parsing configuration.
     *
     * @return An Optional containing the default config if set.
     */
    Optional<ParsingConfig> findDefault();

    /**
     * Attempts to automatically detect the log format from sample lines.
     * <p>
     * Uses Heuristics and Grok patterns to identify common log formats
     * (Logback, Apache, Syslog, etc.) and generates a corresponding
     * {@link ParsingConfig}.
     *
     * @param sampleLines A list of sample log lines to analyze.
     * @return A generated ParsingConfig if detection is successful, or a generic
     *         fallback.
     */
    ParsingConfig detectLogFormat(List<String> sampleLines);
}
