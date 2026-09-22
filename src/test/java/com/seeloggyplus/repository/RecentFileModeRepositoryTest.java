package com.seeloggyplus.repository;

import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.repository.impl.LogFileRepositoryImpl;
import com.seeloggyplus.repository.impl.RecentFileRepositoryImpl;
import com.seeloggyplus.service.LogFileService;
import com.seeloggyplus.service.impl.LogFileServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@code recent_files.mode} persistence added so the Recent list can
 * reopen a file in the same mode (OPEN or TAIL) it was last used.
 */
class RecentFileModeRepositoryTest {

    private RecentFileRepository recentFileRepository;
    private LogFileService logFileService;
    private LogFile logFile;

    @BeforeEach
    void setUp() throws Exception {
        recentFileRepository = new RecentFileRepositoryImpl();
        logFileService = new LogFileServiceImpl(new LogFileRepositoryImpl());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        logFile = new LogFile();
        logFile.setName("mode-" + suffix + ".log");
        logFile.setFilePath("/var/log/mode-" + suffix + ".log");
        logFile.setRemote(true);
        logFile.setSshServerID("srv-" + suffix);
        logFile.setSize("1 KB");
        logFile.setModified(String.valueOf(System.currentTimeMillis()));
        logFileService.insertLogFile(logFile);
    }

    @AfterEach
    void tearDown() {
        try {
            recentFileRepository.deleteByFileId(logFile.getId());
        } catch (Exception ignored) {
        }
        try {
            logFileService.deleteLogFileById(logFile.getId());
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("save() persists the open mode and findByFileId() reads it back")
    void modeRoundTrips() {
        RecentFile recent = new RecentFile();
        recent.setFileId(logFile.getId());
        recent.setLastOpened(LocalDateTime.now());
        recent.setMode(RecentFile.MODE_TAIL);
        recentFileRepository.save(recent);

        Optional<RecentFile> loaded = recentFileRepository.findByFileId(logFile.getId());
        assertTrue(loaded.isPresent(), "saved recent file must be found");
        assertEquals(RecentFile.MODE_TAIL, loaded.get().getMode(), "TAIL mode must round-trip");
    }

    @Test
    @DisplayName("Re-saving the same file updates the mode without creating duplicates")
    void savingAgainUpdatesModeInsteadOfDuplicating() {
        RecentFile first = new RecentFile();
        first.setFileId(logFile.getId());
        first.setLastOpened(LocalDateTime.now().minusMinutes(5));
        first.setMode(RecentFile.MODE_TAIL);
        recentFileRepository.save(first);

        RecentFile second = new RecentFile();
        second.setFileId(logFile.getId());
        second.setLastOpened(LocalDateTime.now());
        second.setMode(RecentFile.MODE_OPEN);
        recentFileRepository.save(second);

        long matchingRows = recentFileRepository.findAll().stream()
                .filter(dto -> dto.logFile() != null && logFile.getId().equals(dto.logFile().getId()))
                .count();
        assertEquals(1, matchingRows, "re-saving must not duplicate the recent row");

        assertEquals(RecentFile.MODE_OPEN,
                recentFileRepository.findByFileId(logFile.getId()).orElseThrow().getMode(),
                "the latest mode must win");
    }

    @Test
    @DisplayName("findAll() exposes the persisted mode through the DTO")
    void findAllExposesMode() {
        RecentFile recent = new RecentFile();
        recent.setFileId(logFile.getId());
        recent.setLastOpened(LocalDateTime.now());
        recent.setMode(RecentFile.MODE_TAIL);
        recentFileRepository.save(recent);

        boolean found = recentFileRepository.findAll().stream()
                .anyMatch(dto -> logFile.getId().equals(dto.logFile().getId())
                        && RecentFile.MODE_TAIL.equals(dto.openMode()));
        assertTrue(found, "findAll() must surface openMode=TAIL for this file");
    }
}
