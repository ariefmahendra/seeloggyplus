package com.seeloggyplus.features.ssh.infrastructure;

import com.jcraft.jsch.ChannelExec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SSHServiceImplGuardPathsTest {

    @Test
    void stopTailingDisconnectsActiveTailChannelAndClearsState() throws Exception {
        SSHServiceImpl ssh = new SSHServiceImpl();
        AtomicBoolean isTailing = (AtomicBoolean) field(ssh, "isTailing");
        isTailing.set(true);
        setField(ssh, "activeTailChannel", new ChannelExec());

        assertDoesNotThrow(ssh::stopTailing);

        assertFalse(isTailing.get());
        assertNull(field(ssh, "activeTailChannel"));
    }

    @Test
    void cancelActiveCommandDisconnectsTrackedChannelWithoutThrowing() throws Exception {
        SSHServiceImpl ssh = new SSHServiceImpl();
        ChannelExec channel = new ChannelExec();
        setField(ssh, "activeCommandChannel", channel);

        assertDoesNotThrow(ssh::cancelActiveCommand);

        assertFalse(channel.isConnected());
    }

    @Test
    void interruptedDownloadReturnsFalseWithoutWaitingForASlot() {
        SSHServiceImpl ssh = new SSHServiceImpl();
        Thread.currentThread().interrupt();
        try {
            assertFalse(ssh.downloadFileConcurrent("/var/log/app.log", "app-download.log", 2, null));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void listFilesFallsBackToExecWhenSftpIsUnavailable() throws Exception {
        SSHServiceImpl ssh = new SSHServiceImpl();
        setField(ssh, "sftpAvailable", Boolean.FALSE);

        IOException error = assertThrows(IOException.class, () -> ssh.listFiles("/var/log"));

        assertNotNull(error.getMessage());
    }

    @Test
    void downloadFileConcurrentFallsBackToExecSizeWhenSftpIsUnavailable() throws Exception {
        SSHServiceImpl ssh = new SSHServiceImpl();
        setField(ssh, "sftpAvailable", Boolean.FALSE);

        assertFalse(ssh.downloadFileConcurrent("/var/log/app.log", "app-download.log", 2, null));
    }

    @Test
    void disconnectMarksDownloadsCancelledUntilNextConcurrentDownload(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        SSHServiceImpl ssh = new SSHServiceImpl();
        Path target = dir.resolve("target.log");
        ssh.disconnect();
        AtomicBoolean cancelled = (AtomicBoolean) field(ssh, "downloadCancelled");
        assertTrue(cancelled.get());

        assertFalse(ssh.downloadFileConcurrent(null, target.toString(), 2, null));

        assertFalse(cancelled.get());
        assertFalse(Files.exists(target));
    }

    private static Object field(SSHServiceImpl ssh, String name) throws Exception {
        Field field = SSHServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(ssh);
    }

    private static void setField(SSHServiceImpl ssh, String name, Object value) throws Exception {
        Field field = SSHServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(ssh, value);
    }
}
