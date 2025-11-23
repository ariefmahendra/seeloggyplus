package com.seeloggyplus.repository;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.RecentFile;
import java.util.List;
import java.util.Optional;

public interface RecentFileRepository {
    List<RecentFilesDto> findAll();
    void save(RecentFile recentFile);
    void deleteAll();
    RecentFilesDto findById(String id);
    Optional<RecentFile> findByFileId(String fileId);
    void deleteById(String id);
    void deleteByFileId(String fileId);
}
