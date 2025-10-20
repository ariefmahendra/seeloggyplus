package com.seeloggyplus.service;

import com.seeloggyplus.model.RecentFile;

import java.util.List;

public interface RecentFileService {
    List<RecentFile> findAll();
    void save(RecentFile recentFile);
    void deleteAll();
}
