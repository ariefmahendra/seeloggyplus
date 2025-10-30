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
        logFile.setId(UUID.randomUUID().toString());
        try {
            logFileRepository.insert(logFile);
        } catch (FatalDatabaseException ex) {
            logger.error("error when insert log file: ", ex);
        }
    }

    @Override
    public LogFile getLogFileById(String id) {
        try {
            return logFileRepository.findById(id);
        } catch (NotFoundException ex){
            logger.warn("log file not found by id: {}", id);
            return null;
        } catch (FatalDatabaseException ex) {
            logger.error("error when get log file by id: ", ex);
            return null;
        }
    }

    @Override
    public void deleteLogFileById(String id) {
        try {
            logFileRepository.deleteById(id);
        } catch (NotFoundException ex){
            logger.warn("log file not found by id: {}", id);
        } catch (FatalDatabaseException ex) {
            logger.error("error when delete log file by id: ", ex);
        }
    }

    @Override
    public void updateLogFile(LogFile logFile) {
        try {
            logFileRepository.update(logFile);
        } catch (NotFoundException ex){
            logger.warn("log file not found by id: {}", logFile.getId());
        } catch (FatalDatabaseException ex) {
            logger.error("error when update log file: ", ex);
        }
    }

    @Override
    public LogFile getLogFileByPathAndName(String filePath, String name) {
        try {
            return logFileRepository.findByPathAndName(filePath, name);
        } catch (NotFoundException ex){
            logger.warn("log file not found by path: {} and name: {}", filePath, name);
            return null;
        } catch (FatalDatabaseException ex) {
            logger.error("error when get log file by path and name: ", ex);
            return null;
        }
    }
}
