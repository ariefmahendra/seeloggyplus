package com.seeloggyplus.features.ssh.infrastructure;

import com.jcraft.jsch.JSchException;
import com.seeloggyplus.shared.dto.RemoteFileInfo;
import com.seeloggyplus.shared.ssh.SshAuthConfig;
import com.seeloggyplus.shared.util.RemoteLogPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SSHServiceImplPureLogicTest {

    private SSHSessionManagerImpl sessionManager;

    @BeforeEach
    void createSessionManager() {
        sessionManager = new SSHSessionManagerImpl(Executors.newSingleThreadScheduledExecutor());
    }

    @AfterEach
    void closeSessionManager() {
        sessionManager.close();
    }

    private static final String MISSING_KEY_PATH = "C:\\definitely\\missing\\id_ed25519";

    private static SshAuthConfig authConfigOf(SSHServiceImpl ssh) throws Exception {
        Field field = SSHServiceImpl.class.getDeclaredField("authConfig");
        field.setAccessible(true);
        return (SshAuthConfig) field.get(ssh);
    }

    @Test
    @DisplayName("plain path is wrapped in single quotes")
    void escapesPlainPath() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertEquals("'/var/log/app.log'", ssh.escapeShellArgument("/var/log/app.log"));
    }

    @Test
    @DisplayName("embedded single quote is escaped for POSIX shells")
    void escapesEmbeddedSingleQuote() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertEquals("'it'\\''s.log'", ssh.escapeShellArgument("it's.log"));
    }

    @Test
    @DisplayName("every embedded single quote is escaped")
    void escapesMultipleSingleQuotes() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertEquals("'a'\\''b'\\''c'", ssh.escapeShellArgument("a'b'c"));
    }

    @Test
    @DisplayName("spaces, dollar signs and backslashes stay literal inside quotes")
    void escapesSpacesDollarAndBackslashLiterally() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertEquals("'/var/log/my $file\\x.log'", ssh.escapeShellArgument("/var/log/my $file\\x.log"));
    }

    @Test
    @DisplayName("a leading dash cannot become a command option")
    void escapesLeadingDash() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertEquals("'-rf /'", ssh.escapeShellArgument("-rf /"));
    }

    @Test
    @DisplayName("full-time entry with a multi-word filename keeps the whole name")
    void parsesFullTimeEntryWithMultiWordFilename() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        String line = "drwxr-xr-x 2 root root 4096 2026-03-24 18:54:31.000000000 +0700 my logs";

        RemoteFileInfo info = ssh.parseLsLine(line, "/var");

        assertNotNull(info);
        assertEquals("my logs", info.getName());
        assertEquals("/var/my logs", info.getPath());
        assertEquals("root", info.getOwner());
        assertEquals(4096L, info.getSize());
        assertEquals("drwxr-xr-x", info.getPermissions());
        assertTrue(info.isDirectory());
        long expected = LocalDateTime.of(2026, 3, 24, 18, 54, 31)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(expected, info.getModifiedTime());
    }

    @Test
    @DisplayName("full-time symlink strips the arrow and its target")
    void parsesFullTimeSymlinkAndStripsArrowTarget() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        String line = "lrwxrwxrwx 1 root root 7 2026-03-24 18:54:31.000000000 +0700 latest.log -> app.log";

        RemoteFileInfo info = ssh.parseLsLine(line, "/var/log");

        assertNotNull(info);
        assertEquals("latest.log", info.getName());
        assertEquals("/var/log/latest.log", info.getPath());
        assertEquals("lrwxrwxrwx", info.getPermissions());
        assertFalse(info.isDirectory());
        assertTrue(info.getModifiedTime() > 0);
    }

    @Test
    @DisplayName("old style listing with a year is parsed as a date")
    void parsesOldStyleTimestampWithYear() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        String line = "-rw-r--r-- 1 deploy deploy 128 Jan 15 2024 old.log";

        RemoteFileInfo info = ssh.parseLsLine(line, "/srv");

        assertNotNull(info);
        assertEquals("old.log", info.getName());
        assertEquals("/srv/old.log", info.getPath());
        assertEquals("deploy", info.getOwner());
        assertEquals(128L, info.getSize());
        long expected = LocalDate.of(2024, 1, 15).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        assertEquals(expected, info.getModifiedTime());
    }

    @Test
    @DisplayName("non numeric size falls back to zero")
    void nonNumericSizeFallsBackToZero() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        String line = "-rw-r--r-- 1 u g abc Jan 15 10:30 weird.log";

        RemoteFileInfo info = ssh.parseLsLine(line, "/var/log");

        assertNotNull(info);
        assertEquals("weird.log", info.getName());
        assertEquals(0L, info.getSize());
        assertTrue(info.getModifiedTime() > 0);
    }

    @Test
    @DisplayName("an impossible month and day leaves the modified time at zero")
    void unparsableMonthDayLeavesModifiedTimeZero() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        String line = "-rw-r--r-- 1 u g 10 Feb 30 10:30 bad-date.log";

        RemoteFileInfo info = ssh.parseLsLine(line, "/var/log");

        assertNotNull(info);
        assertEquals("bad-date.log", info.getName());
        assertEquals(0L, info.getModifiedTime());
    }

    @Test
    @DisplayName("an impossible full-time stamp leaves the modified time at zero")
    void unparsableFullTimeStampLeavesModifiedTimeZero() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        String line = "drwxr-xr-x 2 root root 4096 2026-13-45 99:99:99.000000000 +0700 bad stamp";

        RemoteFileInfo info = ssh.parseLsLine(line, "/var");

        assertNotNull(info);
        assertEquals("bad stamp", info.getName());
        assertEquals("/var/bad stamp", info.getPath());
        assertEquals(0L, info.getModifiedTime());
    }

    @Test
    @DisplayName("a parent path with a trailing slash is not doubled")
    void keepsParentPathWithTrailingSlash() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        String line = "-rw-r--r-- 1 u g 10 Jan 15 10:30 a.log";

        RemoteFileInfo info = ssh.parseLsLine(line, "/var/log/");

        assertNotNull(info);
        assertEquals("/var/log/a.log", info.getPath());
    }

    @Test
    @DisplayName("socket closed and connection reset messages are session down errors")
    void detectsSocketClosedAndConnectionResetMessages() {
        assertAll(
                () -> assertTrue(SSHServiceImpl.isSessionDownError(
                        new IOException("java.net.SocketException: Socket is closed"))),
                () -> assertTrue(SSHServiceImpl.isSessionDownError(
                        new IOException("Connection reset by peer"))),
                () -> assertTrue(SSHServiceImpl.isSessionDownError(
                        new IOException("SESSION IS DOWN"))));
    }

    @Test
    @DisplayName("unrelated messages and missing messages are not session down errors")
    void ignoresUnrelatedOrMissingMessages() {
        assertAll(
                () -> assertFalse(SSHServiceImpl.isSessionDownError(new IOException((String) null))),
                () -> assertFalse(SSHServiceImpl.isSessionDownError(new IOException("Connection timed out"))),
                () -> assertFalse(SSHServiceImpl.isSessionDownError(
                        new IOException("wrapper", new JSchException("auth fail",
                                new IOException("permission denied"))))));
    }

    @Test
    @DisplayName("a session down message is found deep in the cause chain")
    void findsSessionDownMessageDeepInCauseChain() {
        IOException error = new IOException("outer",
                new RuntimeException("middle", new JSchException("socket is closed")));

        assertTrue(SSHServiceImpl.isSessionDownError(error));
    }

    @Test
    @DisplayName("a missing remote path reports the missing location message")
    void tailFileWithMissingPathReportsMissingLocation() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        List<String> logs = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ssh.tailFile(null, 10, logs::add, errors::add);
        ssh.tailFile("   ", 10, logs::add, errors::add);

        assertEquals(List.of(RemoteLogPaths.MISSING_LOCATION_MESSAGE, RemoteLogPaths.MISSING_LOCATION_MESSAGE),
                errors);
        assertTrue(logs.isEmpty());
        assertDoesNotThrow(() -> ssh.tailFile(null, 10, null, null));
    }

    @Test
    @DisplayName("a Windows local path reports the local location message")
    void tailFileWithWindowsLocalPathReportsLocalLocation() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        List<String> logs = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ssh.tailFile("C:\\logs\\app.log", 10, logs::add, errors::add);

        assertEquals(List.of(RemoteLogPaths.LOCAL_LOCATION_MESSAGE), errors);
        assertTrue(logs.isEmpty());
    }

    @Test
    @DisplayName("tailing without a session reports an init error")
    void tailFileWithoutSessionReportsInitError() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        List<String> logs = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ssh.tailFile("/var/log/app.log", 10, logs::add, errors::add);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("Init Error:"), errors.get(0));
        assertTrue(errors.get(0).contains("Not connected"), errors.get(0));
        assertTrue(logs.isEmpty());
    }

    @Test
    @DisplayName("a non positive line limit falls back to a full read and fails without a session")
    void nonPositiveLineLimitFailsWithoutSession() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertThrows(IOException.class, () -> ssh.readFileLines("/var/log/app.log", 0));
        assertThrows(IOException.class, () -> ssh.readFileLines("/var/log/app.log", -5));
    }

    @Test
    @DisplayName("download guards reject null and blank paths without a session")
    void downloadGuardsRejectNullAndBlankPathsWithoutSession(@TempDir Path tempDir) {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        Path target = tempDir.resolve("out.log");

        assertFalse(ssh.downloadFile(null, target.toString()));
        assertFalse(ssh.downloadFile("   ", target.toString()));
        assertFalse(ssh.downloadFileConcurrent(null, target.toString(), 2, null));
        assertFalse(Files.exists(target));
    }

    @Test
    @DisplayName("recovering without credentials reports failure")
    void recoverSessionWithoutCredentialsReturnsFalse() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertFalse(ssh.recoverSession());
        assertFalse(ssh.isConnected());
    }

    @Test
    @DisplayName("lifecycle calls are idempotent without a connection")
    void lifecycleCallsAreIdempotentWithoutConnection() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertDoesNotThrow(() -> {
            ssh.disconnect();
            ssh.disconnect();
            ssh.stopTailing();
            ssh.stopTailing();
            ssh.cancelActiveCommand();
            ssh.cancelActiveCommand();
        });

        assertFalse(ssh.isConnected());
        assertNull(ssh.getLastConnectError());
    }

    @Test
    @DisplayName("auth config defaults to password and a null config resets it")
    void authConfigDefaultsToPasswordAndNullResetsIt() throws Exception {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        assertEquals(SshAuthConfig.password(), authConfigOf(ssh));

        SshAuthConfig keyConfig = SshAuthConfig.key("C:\\keys\\id_ed25519", "secret");
        ssh.setAuthConfig(keyConfig);
        assertEquals(keyConfig, authConfigOf(ssh));

        ssh.setAuthConfig(null);
        assertEquals(SshAuthConfig.password(), authConfigOf(ssh));
    }

    @Test
    @DisplayName("trusting a host key without a pending entry is safe")
    void trustPendingHostKeyWithoutPendingEntryIsSafe() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);

        assertDoesNotThrow(ssh::trustPendingHostKey);

        ssh.setAuthConfig(SshAuthConfig.key(MISSING_KEY_PATH, null));
        assertFalse(ssh.connect("key-auth.invalid", 22, "user", null));
        assertDoesNotThrow(ssh::trustPendingHostKey);
    }

    @Test
    @DisplayName("failed key auth records the reason, defaults the port and survives disconnect")
    void failedKeyAuthConnectRecordsReasonDefaultsPortAndSurvivesDisconnect() {
        SSHServiceImpl ssh = new SSHServiceImpl(sessionManager);
        ssh.setAuthConfig(SshAuthConfig.key(MISSING_KEY_PATH, null));

        assertFalse(ssh.connect("key-auth.invalid", 0, "user", null));

        assertEquals("key-auth.invalid", ssh.getHost());
        assertEquals(22, ssh.getPort());
        assertEquals("user", ssh.getUsername());
        assertFalse(ssh.isConnected());
        assertNotNull(ssh.getLastConnectError());
        assertTrue(ssh.getLastConnectError().contains("Private key file not found"),
                ssh.getLastConnectError());

        ssh.disconnect();

        assertTrue(ssh.getLastConnectError().contains("Private key file not found"),
                ssh.getLastConnectError());
    }
}
