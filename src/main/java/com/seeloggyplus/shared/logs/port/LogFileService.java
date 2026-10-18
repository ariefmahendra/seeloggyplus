package com.seeloggyplus.shared.logs.port;

import com.seeloggyplus.shared.model.LogFile;

public interface LogFileService {

    void insertLogFile(LogFile logFile);

    LogFile getLogFileById(String id);

    void deleteLogFileById(String id);

    void updateLogFile(LogFile logFile);

    LogFile getLogFileByPathAndName(String name, String filePath);

    LogFile getLogFileByPathNameAndServer(String name, String filePath, String sshServerId, boolean isRemote);

    void deleteAllLogFiles();

    void updateParsingConfigIdForLogFiles(String parsingConfigId, String logFileId);
}
