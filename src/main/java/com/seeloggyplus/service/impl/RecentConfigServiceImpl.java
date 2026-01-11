package com.seeloggyplus.service.impl;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.repository.RecentFileRepository;
import com.seeloggyplus.repository.impl.RecentFileRepositoryImpl;
import com.seeloggyplus.service.RecentFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link RecentFileService}.
 * <p>
 * Manages the business logic for tracking recently opened files,
 * delegating persistence to {@link RecentFileRepository}.
 */
public class RecentConfigServiceImpl implements RecentFileService {

    private static final Logger logger = LoggerFactory.getLogger(RecentConfigServiceImpl.class);
    private final RecentFileRepository recentFileRepository;

    /**
     * Default constructor.
     * Initializes with the default repository implementation.
     */
    public RecentConfigServiceImpl() {
        this(new RecentFileRepositoryImpl());
    }

    /**
     * Constructor for dependency injection.
     *
     * @param recentFileRepository The repository instance to use.
     */
    public RecentConfigServiceImpl(RecentFileRepository recentFileRepository) {
        this.recentFileRepository = recentFileRepository;
    }

    @Override
    public List<RecentFilesDto> findAll() {
        logger.debug("Retrieving all recent files");
        return recentFileRepository.findAll();
    }

    @Override
    public void save(LogFile logFile, RecentFile recentFile) {
        logger.debug("Saving recent file entry: {}", recentFile.getId());
        recentFileRepository.save(recentFile);
        logger.info("Saved recent file: {}", recentFile.getId());
    }

    @Override
    public void deleteAll() {
        logger.debug("Delegating delete all recent files");
        recentFileRepository.deleteAll();
        logger.info("Deleted all recent files history");
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
        logger.info("Deleted recent file entry: {}", id);
    }

    @Override
    public void deleteByFileId(String fileId) {
        recentFileRepository.deleteByFileId(fileId);
        logger.info("Deleted recent file entry by file Id: {}", fileId);
    }
}
