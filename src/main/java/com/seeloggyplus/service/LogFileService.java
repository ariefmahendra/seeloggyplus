package com.seeloggyplus.service;

import com.seeloggyplus.model.LogFile;

/**
 * Service interface for managing Log Files.
 * <p>
 * Provides CRUD operations for persisting and retrieving metadata about log
 * files
 * tracked by the application (Local, SSH, etc.).
 */
public interface LogFileService {

    /**
     * Inserts a new log file record.
     *
     * @param logFile The log file object to persist.
     * @throws IllegalArgumentException if logFile is null or invalid.
     * @throws RuntimeException         if database operation fails.
     */
    void insertLogFile(LogFile logFile);

    /**
     * Retrieves a log file by its unique ID.
     *
     * @param id The UUID of the log file.
     * @return The LogFile object, or null if not found.
     * @throws IllegalArgumentException if ID is null or empty.
     * @throws RuntimeException         if database operation fails.
     */
    LogFile getLogFileById(String id);

    /**
     * Deletes a log file by its ID.
     *
     * @param id The UUID of the log file to delete.
     * @throws IllegalArgumentException if ID is null or empty.
     * @throws RuntimeException         if database operation fails or file not
     *                                  found.
     */
    void deleteLogFileById(String id);

    /**
     * Updates an existing log file record.
     *
     * @param logFile The log file object with updated values.
     * @throws IllegalArgumentException if logFile is null or has invalid ID.
     * @throws RuntimeException         if database operation fails or file not
     *                                  found.
     */
    void updateLogFile(LogFile logFile);

    /**
     * Finds a log file by its name and file path combination.
     *
     * @param name     The name of the log file.
     * @param filePath The absolute path of the file.
     * @return The LogFile object, or null if not found.
     * @throws IllegalArgumentException if inputs are invalid.
     * @throws RuntimeException         if database operation fails.
     */
    LogFile getLogFileByPathAndName(String name, String filePath);

    /**
     * Finds a log file by its name, file path, SSH server ID, and remote flag.
     *
     * @param name        The name of the log file.
     * @param filePath    The path of the file.
     * @param sshServerId The SSH server ID (required if remote, null if local).
     * @param isRemote    True if the file is on a remote server.
     * @return The LogFile object, or null if not found.
     */
    LogFile getLogFileByPathNameAndServer(String name, String filePath, String sshServerId, boolean isRemote);

    /**
     * Deletes all log files from the database.
     * Use with caution.
     *
     * @throws RuntimeException if database operation fails.
     */
    void deleteAllLogFiles();

    /**
     * Updates the associated Parsing Configuration ID for a specific log file.
     *
     * @param parsingConfigId The new parsing configuration ID (can be null).
     * @param logFileId       The ID of the log file to update.
     * @throws IllegalArgumentException if logFileId is null or empty.
     * @throws RuntimeException         if database operation fails or file not
     *                                  found.
     */
    void updateParsingConfigIdForLogFiles(String parsingConfigId, String logFileId);
}
