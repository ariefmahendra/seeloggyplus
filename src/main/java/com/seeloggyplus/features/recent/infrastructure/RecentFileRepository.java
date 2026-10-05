package com.seeloggyplus.features.recent.infrastructure;

import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.features.recent.domain.RecentFile;
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
