package com.seeloggyplus.service;

import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.repository.LogFileRepository;
import com.seeloggyplus.repository.impl.LogFileRepositoryImpl;
import com.seeloggyplus.service.impl.LogFileServiceImpl;
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
        logFileRepository = new LogFileRepositoryImpl();
        logFileService = new LogFileServiceImpl(logFileRepository);

        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        testPath = "/var/log/test-" + randomSuffix + "/application.log";
        testName = "application.log";
        serverId1 = "srv-" + UUID.randomUUID();
        serverId2 = "srv-" + UUID.randomUUID();

        // 1. Remote file on Server 1
        fileServer1 = new LogFile();
        fileServer1.setName(testName);
        fileServer1.setFilePath(testPath);
        fileServer1.setRemote(true);
        fileServer1.setSshServerID(serverId1);
        fileServer1.setSize("10 KB");
        fileServer1.setModified(String.valueOf(System.currentTimeMillis()));
        logFileService.insertLogFile(fileServer1);

        // 2. Remote file on Server 2 (same path, same name, different server)
        fileServer2 = new LogFile();
        fileServer2.setName(testName);
        fileServer2.setFilePath(testPath);
        fileServer2.setRemote(true);
        fileServer2.setSshServerID(serverId2);
        fileServer2.setSize("20 KB");
        fileServer2.setModified(String.valueOf(System.currentTimeMillis()));
        logFileService.insertLogFile(fileServer2);

        // 3. Local file with same path and name
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
        if (fileServer1 != null && fileServer1.getId() != null) {
            try { logFileService.deleteLogFileById(fileServer1.getId()); } catch (Exception ignored) {}
        }
        if (fileServer2 != null && fileServer2.getId() != null) {
            try { logFileService.deleteLogFileById(fileServer2.getId()); } catch (Exception ignored) {}
        }
        if (fileLocal != null && fileLocal.getId() != null) {
            try { logFileService.deleteLogFileById(fileLocal.getId()); } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("Files with same name and path on different servers must be retrieved as distinct entities")
    void testDifferentServersWithSamePathProduceDistinctLogFiles() {
        // Query Server 1
        LogFile retrieved1 = logFileService.getLogFileByPathNameAndServer(testName, testPath, serverId1, true);
        assertNotNull(retrieved1, "Server 1 log file must be found");
        assertEquals(fileServer1.getId(), retrieved1.getId());
        assertEquals(serverId1, retrieved1.getSshServerID());
        assertTrue(retrieved1.isRemote());

        // Query Server 2
        LogFile retrieved2 = logFileService.getLogFileByPathNameAndServer(testName, testPath, serverId2, true);
        assertNotNull(retrieved2, "Server 2 log file must be found");
        assertEquals(fileServer2.getId(), retrieved2.getId());
        assertEquals(serverId2, retrieved2.getSshServerID());
        assertTrue(retrieved2.isRemote());

        // Query Local
        LogFile retrievedLocal = logFileService.getLogFileByPathNameAndServer(testName, testPath, null, false);
        assertNotNull(retrievedLocal, "Local log file must be found");
        assertEquals(fileLocal.getId(), retrievedLocal.getId());
        assertNull(retrievedLocal.getSshServerID());
        assertFalse(retrievedLocal.isRemote());

        // Crucial: All three IDs must be distinct from each other
        assertNotEquals(retrieved1.getId(), retrieved2.getId(), "Server 1 and Server 2 must not share the same entity");
        assertNotEquals(retrieved1.getId(), retrievedLocal.getId(), "Remote Server 1 and Local file must not share the same entity");
        assertNotEquals(retrieved2.getId(), retrievedLocal.getId(), "Remote Server 2 and Local file must not share the same entity");
    }

    @Test
    @DisplayName("Searching for non-existent server ID with same path returns null")
    void testNonExistentServerReturnsNull() {
        LogFile notFound = logFileService.getLogFileByPathNameAndServer(testName, testPath, "non-existent-server", true);
        assertNull(notFound, "Must return null when server ID does not match");
    }
}
