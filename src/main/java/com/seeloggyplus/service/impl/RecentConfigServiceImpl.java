package com.seeloggyplus.service.impl;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.repository.LogFileRepository;
import com.seeloggyplus.repository.RecentFileRepository;
import com.seeloggyplus.repository.impl.LogFileRepositoryImpl;
import com.seeloggyplus.repository.impl.RecentFileRepositoryImpl;
import com.seeloggyplus.service.RecentFileService;

import java.util.List;

public class RecentConfigServiceImpl implements RecentFileService {
    private final RecentFileRepository recentFileRepository;
    private final LogFileRepository logFileRepository;

    public RecentConfigServiceImpl() {
        this.recentFileRepository = new RecentFileRepositoryImpl();
        this.logFileRepository = new LogFileRepositoryImpl();
    }

    @Override
    public List<RecentFilesDto> findAll() {
        return recentFileRepository.findAll();
    }

    @Override
    public void save(LogFile logFile, RecentFile recentFile) {
        recentFileRepository.save(recentFile);
    }

    @Override
    public void deleteAll() {
        recentFileRepository.deleteAll();
    }
}
