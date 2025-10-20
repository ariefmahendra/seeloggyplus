package com.seeloggyplus.service;

import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.repository.RecentFileRepository;
import com.seeloggyplus.repository.RecentFileRepositoryImpl;

import java.util.List;

public class RecentConfigServiceImpl implements RecentFileService{
    private final RecentFileRepository recentFileRepository;

    public RecentConfigServiceImpl() {
        this.recentFileRepository = new RecentFileRepositoryImpl();
    }

    @Override
    public List<RecentFile> findAll() {
        return recentFileRepository.findAll();
    }

    @Override
    public void save(RecentFile recentFile) {
        recentFileRepository.save(recentFile);
    }

    @Override
    public void deleteAll() {
        recentFileRepository.deleteAll();
    }
}
