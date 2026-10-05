package com.seeloggyplus.shared.logs;

import com.seeloggyplus.shared.database.FatalDatabaseException;
import com.seeloggyplus.shared.database.NotFoundException;
import com.seeloggyplus.shared.model.LogFile;

public interface LogFileRepository {
    void insert(LogFile logFile) throws FatalDatabaseException;
    LogFile findById(String id) throws FatalDatabaseException, NotFoundException;
    void deleteById(String id) throws FatalDatabaseException, NotFoundException;
    void update(LogFile logFile) throws FatalDatabaseException, NotFoundException;
    LogFile findByPathAndName(String filePath, String name) throws FatalDatabaseException, NotFoundException;
    LogFile findByPathNameAndServer(String filePath, String name, String sshServerId, boolean isRemote) throws FatalDatabaseException, NotFoundException;
    void deleteAll() throws FatalDatabaseException;
    void updateParsingConfigId(String parsingConfigId, String logFileId) throws FatalDatabaseException, NotFoundException;
}
