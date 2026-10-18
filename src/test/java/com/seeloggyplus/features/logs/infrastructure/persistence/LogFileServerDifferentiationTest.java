package com.seeloggyplus.features.logs.infrastructure.persistence;

import com.seeloggyplus.features.logs.application.LogFileServiceImpl;
import com.seeloggyplus.features.logs.application.port.LogFileRepository;
import com.seeloggyplus.shared.database.DatabaseConfig;
import com.seeloggyplus.shared.logs.port.LogFileService;
import com.seeloggyplus.shared.model.LogFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LogFileServerDifferentiationTest {

    private LogFileService logFileService;
    private LogFileRepository logFileRepository;
    private String testPath;
    private String testName;
    private String serverId1;
    private String serverId2;
    private LogFile fileServer1;
    private LogFile fileServer2;
    private LogFile fileLocal;

    @BeforeEach
    void setUp() {
        logFileRepository = new LogFileRepositoryImpl(DatabaseConfig.getInstance().getConnection());
        logFileService = new LogFileServiceImpl(logFileRepository);
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        testPath = "/var/log/test-" + randomSuffix + "/application.log";
        testName = "application.log";
        serverId1 = "srv-" + UUID.randomUUID();
        serverId2 = "srv-" + UUID.randomUUID();
        fileServer1 = new LogFile();
        fileServer1.setName(testName);
        fileServer1.setFilePath(testPath);
        fileServer1.setRemote(true);
        fileServer1.setSshServerID(serverId1);
        fileServer1.setSize("10 KB");
        fileServer1.setModified(String.valueOf(System.currentTimeMillis()));
        logFileService.insertLogFile(fileServer1);
        fileServer2 = new LogFile();
        fileServer2.setName(testName);
        fileServer2.setFilePath(testPath);
        fileServer2.setRemote(true);
        fileServer2.setSshServerID(serverId2);
        fileServer2.setSize("20 KB");
        fileServer2.setModified(String.valueOf(System.currentTimeMillis()));
        logFileService.insertLogFile(fileServer2);
        fileLocal = new LogFile();
        fileLocal.setName(testName);
        fileLocal.setFilePath(testPath);
        fileLocal.setRemote(false);
        fileLocal.setSshServerID(null);
        fileLocal.setSize("5 KB");
        fileLocal.setModified(String.valueOf(System.currentTimeMillis()));
        logFileService.insertLogFile(fileLocal);
    }

    @AfterEach
    void tearDown() {
        for (LogFile file : new LogFile[] { fileServer1, fileServer2, fileLocal }) {
            if (file == null || file.getId() == null)
                continue;
            try {
                logFileService.deleteLogFileById(file.getId());
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Files with same name and path on different servers must be retrieved as distinct entities")
    void filesWithSamePathOnDifferentServersAreDistinct() {
        LogFile fetched1 = logFileService.getLogFileByPathNameAndServer(testName, testPath, serverId1, true);
        LogFile fetched2 = logFileService.getLogFileByPathNameAndServer(testName, testPath, serverId2, true);
        LogFile fetchedLocal = logFileService.getLogFileByPathNameAndServer(testName, testPath, null, false);
        assertNotNull(fetched1);
        assertNotNull(fetched2);
        assertNotNull(fetchedLocal);
        assertEquals(serverId1, fetched1.getSshServerID());
        assertEquals(serverId2, fetched2.getSshServerID());
        assertFalse(fetchedLocal.isRemote());
        assertNotEquals(fetched1.getId(), fetched2.getId());
        assertNotEquals(fetched1.getId(), fetchedLocal.getId());
        assertEquals("10 KB", fetched1.getSize());
        assertEquals("20 KB", fetched2.getSize());
        assertEquals("5 KB", fetchedLocal.getSize());
    }

    @Test
    @DisplayName("Searching for non-existent server ID with same path returns null")
    void unknownServerReturnsNull() {
        assertNull(logFileService.getLogFileByPathNameAndServer(testName, testPath,
                "srv-" + UUID.randomUUID(), true));
    }
}
