package com.seeloggyplus.service;

import com.seeloggyplus.model.LogFile;

/**
 * Service interface for LogFile operations
 * Provides business logic and validation for log file management
 */
public interface LogFileService {
    /**
     * Insert a new log file record
     * @param logFile the log file to insert
     * @throws IllegalArgumentException if logFile is null or has invalid data
     */
    void insertLogFile(LogFile logFile);

    /**
     * Get log file by ID
     * @param id the log file ID
     * @return LogFile or null if not found
     * @throws IllegalArgumentException if id is null or empty
     */
    LogFile getLogFileById(String id);

    /**
     * Delete log file by ID
     * @param id the log file ID
     * @throws IllegalArgumentException if id is null or empty
     * @throws RuntimeException if log file not found or database error
     */
    void deleteLogFileById(String id);

    /**
     * Update an existing log file record
     * @param logFile the log file to update
     * @throws IllegalArgumentException if logFile is null or has invalid data
     * @throws RuntimeException if log file not found or database error
     */
    void updateLogFile(LogFile logFile);

    /**
     * Get log file by name and file path
     * @param name the log file name
     * @param filePath the log file path
     * @return LogFile or null if not found
     * @throws IllegalArgumentException if name or filePath is null or empty
     */
    LogFile getLogFileByPathAndName(String name, String filePath);
}
