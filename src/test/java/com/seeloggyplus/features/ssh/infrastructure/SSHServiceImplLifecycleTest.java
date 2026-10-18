package com.seeloggyplus.features.ssh.infrastructure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SSHServiceImplLifecycleTest {

    @Test
    void freshServiceReportsDisconnectedWithoutErrors() {
        SSHServiceImpl ssh = new SSHServiceImpl();

        assertFalse(ssh.isConnected());
        assertNull(ssh.getLastConnectError());
    }

    @Test
    void lifecycleCallsWithoutConnectionAreSafe() {
        SSHServiceImpl ssh = new SSHServiceImpl();

        assertDoesNotThrow(ssh::cancelActiveCommand);
        assertDoesNotThrow(ssh::stopTailing);
        assertDoesNotThrow(ssh::disconnect);
        assertFalse(ssh.isConnected());
    }

    @Test
    void listingFilesWithoutASessionFailsWithAClearReason() {
        SSHServiceImpl ssh = new SSHServiceImpl();

        IOException error = assertThrows(IOException.class, () -> ssh.listFiles("/var/log"));

        assertTrue(error.getMessage() != null && !error.getMessage().isBlank());
    }

    @Test
    void remoteOperationsWithoutASessionFailCleanly() throws Exception {
        SSHServiceImpl ssh = new SSHServiceImpl();
        Path temp = Files.createTempFile("ssh-offline", ".log");
        try {
            assertThrows(IOException.class, () -> ssh.executeCommand("ls"));
            assertThrows(IOException.class, () -> ssh.readFile("/var/log/app.log"));
            assertThrows(IOException.class, () -> ssh.readFileLines("/var/log/app.log"));
            assertThrows(IOException.class, () -> ssh.readFileLines("/var/log/app.log", 10));
            assertFalse(ssh.downloadFile("/var/log/app.log", temp.toString()));
            assertFalse(ssh.downloadFileConcurrent("/var/log/app.log", temp.toString(), 2, null));
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
