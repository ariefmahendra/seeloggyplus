package com.seeloggyplus.service;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.RecentFile;

import java.util.List;

public interface RecentFileService {
    List<RecentFilesDto> findAll();
    void save(LogFile logFile, RecentFile recentFile);
    void deleteAll();
}
