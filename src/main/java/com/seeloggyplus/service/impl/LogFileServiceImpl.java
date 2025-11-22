package com.seeloggyplus.service.impl;

import com.seeloggyplus.exceptions.database.FatalDatabaseException;
import com.seeloggyplus.exceptions.database.NotFoundException;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.repository.LogFileRepository;
import com.seeloggyplus.repository.impl.LogFileRepositoryImpl;
import com.seeloggyplus.service.LogFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class LogFileServiceImpl implements LogFileService {
    private final LogFileRepository logFileRepository;
    private static final Logger logger = LoggerFactory.getLogger(LogFileServiceImpl.class);
    
    public LogFileServiceImpl() {
        this.logFileRepository = new LogFileRepositoryImpl();
    }

    @Override
    public void insertLogFile(LogFile logFile) {
        // Validation
        if (logFile == null) {
            logger.error("Cannot insert null log file");
            throw new IllegalArgumentException("LogFile cannot be null");
        }

        if (logFile.getName() == null || logFile.getName().trim().isEmpty()) {
            logger.error("Cannot insert log file with null or empty name");
            throw new IllegalArgumentException("LogFile name cannot be null or empty");
        }

        if (logFile.getFilePath() == null || logFile.getFilePath().trim().isEmpty()) {
            logger.error("Cannot insert log file with null or empty file path");
            throw new IllegalArgumentException("LogFile path cannot be null or empty");
        }

        // Set ID if not already set
        if (logFile.getId() == null || logFile.getId().trim().isEmpty()) {
            logFile.setId(UUID.randomUUID().toString());
        }

        try {
            logFileRepository.insert(logFile);
            logger.info("Successfully inserted log file: {} with id: {}", logFile.getName(), logFile.getId());
        } catch (FatalDatabaseException ex) {
            logger.error("Failed to insert log file: {}", logFile.getName(), ex);
            throw new RuntimeException("Failed to insert log file", ex);
        }
    }

    @Override
    public LogFile getLogFileById(String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.error("Cannot get log file with null or empty id");
            throw new IllegalArgumentException("LogFile id cannot be null or empty");
        }

        try {
            return logFileRepository.findById(id);
        } catch (NotFoundException ex){
            logger.warn("get log file by id not found : {}", id);
            return null;
        } catch (FatalDatabaseException ex) {
            logger.error("Database error when getting log file by id: {}", id, ex);
            throw new RuntimeException("Failed to get log file by id", ex);
        }
    }

    @Override
    public void deleteLogFileById(String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.error("Cannot delete log file with null or empty id");
            throw new IllegalArgumentException("LogFile id cannot be null or empty");
        }

        try {
            logFileRepository.deleteById(id);
            logger.info("Successfully deleted log file with id: {}", id);
        } catch (NotFoundException ex){
            logger.warn("get log file by id for delete log not found: {}", id);
            throw new RuntimeException("Log file not found", ex);
        } catch (FatalDatabaseException ex) {
            logger.error("Database error when deleting log file by id: {}", id, ex);
            throw new RuntimeException("Failed to delete log file", ex);
        }
    }

    @Override
    public void updateLogFile(LogFile logFile) {
        if (logFile == null) {
            logger.error("Cannot update null log file");
            throw new IllegalArgumentException("LogFile cannot be null");
        }

        if (logFile.getId() == null || logFile.getId().trim().isEmpty()) {
            logger.error("Cannot update log file with null or empty id");
            throw new IllegalArgumentException("LogFile id cannot be null or empty");
        }

        try {
            logFileRepository.update(logFile);
            logger.info("Successfully updated log file: {}", logFile.getName());
        } catch (NotFoundException ex){
            logger.warn("Log file not found by id: {}", logFile.getId());
            throw new RuntimeException("Log file not found", ex);
        } catch (FatalDatabaseException ex) {
            logger.error("Database error when updating log file: {}", logFile.getName(), ex);
            throw new RuntimeException("Failed to update log file", ex);
        }
    }

    @Override
    public LogFile getLogFileByPathAndName(String name, String filePath) {
        if (name == null || name.trim().isEmpty()) {
            logger.error("Cannot get log file with null or empty name");
            throw new IllegalArgumentException("LogFile name cannot be null or empty");
        }

        if (filePath == null || filePath.trim().isEmpty()) {
            logger.error("Cannot get log file with null or empty file path");
            throw new IllegalArgumentException("LogFile path cannot be null or empty");
        }

        try {
            return logFileRepository.findByPathAndName(filePath, name);
        } catch (NotFoundException ex){
            logger.warn("Log file not found by path: {} and name: {}", filePath, name);
            return null;
        } catch (FatalDatabaseException ex) {
            logger.error("Database error when getting log file by path and name: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to get log file by path and name", ex);
        }
    }
}
