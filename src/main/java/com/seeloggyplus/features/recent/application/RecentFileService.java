package com.seeloggyplus.features.recent.application;

import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.features.recent.domain.RecentFile;

import java.util.List;
import java.util.Optional;

public interface RecentFileService {

    List<RecentFilesDto> findAll();

    void save(LogFile logFile, RecentFile recentFile);

    void deleteAll();

    RecentFilesDto findById(String id);

    Optional<RecentFile> findByFileId(String fileId);

    void deleteById(String id);

    void deleteByFileId(String fileId);
}
