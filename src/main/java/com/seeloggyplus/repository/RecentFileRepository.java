package com.seeloggyplus.repository;

import com.seeloggyplus.model.RecentFile;
import java.util.List;

public interface RecentFileRepository {

    List<RecentFile> findAll();

    void save(RecentFile recentFile);

    void deleteAll();
}
