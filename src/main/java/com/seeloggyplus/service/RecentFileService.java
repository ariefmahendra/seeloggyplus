package com.seeloggyplus.service;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.RecentFile;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing recently accessed files.
 * <p>
 * Provides operations to track, retrieve, and manage the history of opened
 * files.
 */
public interface RecentFileService {

    /**
     * Retrieves all recent files.
     *
     * @return List of {@link RecentFilesDto} representing recent files history.
     */
    List<RecentFilesDto> findAll();

    /**
     * Saves a file to the recent files history.
     *
     * @param logFile    The log file metadata.
     * @param recentFile The recent file entry to save.
     */
    void save(LogFile logFile, RecentFile recentFile);

    /**
     * Clears the entire recent files history.
     */
    void deleteAll();

    /**
     * Finds a recent file entry by its ID.
     *
     * @param id The unique identifier.
     * @return The DTO if found, or null.
     */
    RecentFilesDto findById(String id);

    /**
     * Finds a recent file entry by the associated file ID.
     *
     * @param fileId The ID of the file (LogFile ID).
     * @return An Optional containing the recent file record if found.
     */
    Optional<RecentFile> findByFileId(String fileId);

    /**
     * Deletes a recent file entry by its ID.
     *
     * @param id The unique identifier.
     */
    void deleteById(String id);

    /**
     * Deletes a recent file entry associated with a specific file ID.
     *
     * @param fileId The ID of the file.
     */
    void deleteByFileId(String fileId);
}
