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
import java.util.Optional;

public class RecentConfigServiceImpl implements RecentFileService {
    private final RecentFileRepository recentFileRepository;

    public RecentConfigServiceImpl() {
        this.recentFileRepository = new RecentFileRepositoryImpl();
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

    @Override
    public RecentFilesDto findById(String id) {
        return recentFileRepository.findById(id);
    }

    @Override
    public Optional<RecentFile> findByFileId(String fileId) {
        return recentFileRepository.findByFileId(fileId);
    }

    @Override
    public void deleteById(String id) {
        recentFileRepository.deleteById(id);
    }

    @Override
    public void deleteByFileId(String fileId) {
        recentFileRepository.deleteByFileId(fileId);
    }
}
