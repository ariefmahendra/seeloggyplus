package com.seeloggyplus.service;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.RecentFile;

import java.util.List;
import java.util.Optional;

public interface RecentFileService {
    List<RecentFilesDto> findAll();
    void save(LogFile logFile, RecentFile recentFile);
    void deleteAll();
    RecentFilesDto findById(String id);
    Optional<RecentFile> findByFileId(String fileId);
}
