package com.seeloggyplus.features.ssh.infrastructure;

import com.seeloggyplus.shared.dto.RemoteFileInfo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SSHServiceImplParseLsLineTest {

    private final SSHServiceImpl ssh = new SSHServiceImpl();

    @Test
    void fullTimeLineWithSingleWordNameKeepsTheModifiedTime() {
        String line = "-rw-r--r-- 1 deploy deploy 1234 2026-10-13 09:30:00.123456789 +0700 app.log";

        RemoteFileInfo info = ssh.parseLsLine(line, "/var/log");

        assertNotNull(info);
        assertEquals("app.log", info.getName());
        long expected = LocalDateTime.parse("2026-10-13 09:30:00",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        assertEquals(expected, info.getModifiedTime());
    }

    @Test
    void fullTimeLineWithMultiWordNameStillWorks() {
        String line = "-rw-r--r-- 1 deploy deploy 1234 2026-10-13 09:30:00.000000000 +0700 app server.log";

        RemoteFileInfo info = ssh.parseLsLine(line, "/var/log");

        assertNotNull(info);
        assertEquals("app server.log", info.getName());
        assertTrue(info.getModifiedTime() > 0);
    }

    @Test
    void legacyLineWithSingleWordNameStillParses() {
        String line = "-rw-r--r-- 1 deploy deploy 1234 Oct 13 09:30 app.log";

        RemoteFileInfo info = ssh.parseLsLine(line, "/var/log");

        assertNotNull(info);
        assertEquals("app.log", info.getName());
        assertTrue(info.getModifiedTime() > 0);
    }
}
