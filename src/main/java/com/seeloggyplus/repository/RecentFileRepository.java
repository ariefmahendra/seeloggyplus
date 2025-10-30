package com.seeloggyplus.repository;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.RecentFile;
import java.util.List;

public interface RecentFileRepository {
    List<RecentFilesDto> findAll();
    void save(RecentFile recentFile);
    void deleteAll();
}
