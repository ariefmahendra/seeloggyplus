package com.seeloggyplus.repository;

import com.seeloggyplus.exceptions.database.FatalDatabaseException;
import com.seeloggyplus.exceptions.database.NotFoundException;
import com.seeloggyplus.model.LogFile;

public interface LogFileRepository {
    void insert(LogFile logFile) throws FatalDatabaseException;
    LogFile findById(String id) throws FatalDatabaseException, NotFoundException;
    void deleteById(String id) throws FatalDatabaseException, NotFoundException;
    void update(LogFile logFile) throws FatalDatabaseException, NotFoundException;
    LogFile findByPathAndName(String filePath, String name) throws FatalDatabaseException, NotFoundException;
    void deleteAll() throws FatalDatabaseException;
}
