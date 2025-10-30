package com.seeloggyplus.service;

import com.seeloggyplus.model.LogFile;

public interface LogFileService {
    void insertLogFile(LogFile logFile);
    LogFile getLogFileById(String id);
    void deleteLogFileById(String id);
    void updateLogFile(LogFile logFile);
    LogFile getLogFileByPathAndName(String filePath, String name);
}
