package com.seeloggyplus.shared.model;

import com.seeloggyplus.shared.dto.RemoteFileInfo;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.tail.TailService;
import com.seeloggyplus.shared.ssh.SSHService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedModelCoverageTest {

    @Test
    @DisplayName("SSHServerModel display string includes the friendly name when present")
    void sshServerDisplayString() {
        SSHServerModel named = new SSHServerModel("Prod", "10.0.0.1", 22, "root");
        assertEquals("Prod (root@10.0.0.1:22)", named.getDisplayString());
        assertEquals(named.getDisplayString(), named.toString());

        SSHServerModel unnamed = new SSHServerModel("", "10.0.0.1", 2222, "deploy");
        assertEquals("deploy@10.0.0.1:2222", unnamed.getDisplayString());

        SSHServerModel noName = new SSHServerModel("host", "10.0.0.1", 22, "deploy");
        noName.setName(null);
        assertEquals("deploy@10.0.0.1:22", noName.getDisplayString());
    }

    @Test
    @DisplayName("SSHServerModel validation flags missing host, user and invalid port")
    void sshServerValidation() {
        SSHServerModel valid = new SSHServerModel("Prod", "10.0.0.1", 22, "root");
        assertTrue(valid.isValid());
        assertNull(valid.getValidationError());

        valid.setHost("   ");
        assertFalse(valid.isValid());
        assertEquals("Host is required", valid.getValidationError());

        valid.setHost("10.0.0.1");
        valid.setUsername(null);
        assertFalse(valid.isValid());
        assertEquals("Username is required", valid.getValidationError());

        valid.setUsername("root");
        valid.setPort(0);
        assertFalse(valid.isValid());
        assertEquals("Port must be between 1 and 65535", valid.getValidationError());

        valid.setPort(65536);
        assertFalse(valid.isValid());
        assertEquals("Port must be between 1 and 65535", valid.getValidationError());
    }

    @Test
    @DisplayName("SSHServerModel equality ignores the display name and hash stays consistent")
    void sshServerEquality() {
        SSHServerModel first = new SSHServerModel("One", "host", 22, "user");
        SSHServerModel second = new SSHServerModel("Two", "host", 22, "user");

        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertFalse(first.equals(null));
        assertFalse(first.equals("host"));

        second.setPort(23);
        assertFalse(first.equals(second));
    }

    @Test
    @DisplayName("FileInfo compareTo puts directories first and compares names case-insensitively")
    void fileInfoCompareTo() {
        FileInfo folder = new FileInfo("Beta", "/tmp/beta", 0, true, 0, FileInfo.SourceType.LOCAL);
        FileInfo file = new FileInfo("alpha.log", "/tmp/alpha.log", 10, false, 0, FileInfo.SourceType.LOCAL);

        assertEquals(-1, folder.compareTo(file));
        assertEquals(1, file.compareTo(folder));
        assertEquals(0, file.compareTo(new FileInfo("ALPHA.LOG", "/tmp/other", 5, false, 0,
                FileInfo.SourceType.LOCAL)));
        assertTrue(file.compareTo(new FileInfo("beta.log", "/tmp/beta", 1, false, 0,
                FileInfo.SourceType.LOCAL)) < 0);

        FileInfo unnamed = new FileInfo(null, null, 0, false, 0, FileInfo.SourceType.LOCAL);
        FileInfo named = new FileInfo("x", "/x", 0, false, 0, FileInfo.SourceType.LOCAL);
        assertEquals(0, unnamed.compareTo(named));
        assertEquals(0, unnamed.compareTo(new FileInfo(null, null, 0, false, 0, FileInfo.SourceType.LOCAL)));
    }

    @Test
    @DisplayName("FileInfo extension handles dots, directories and missing names")
    void fileInfoExtension() {
        assertEquals("gz", file("archive.tar.gz").getExtension());
        assertEquals("", file(".gitignore").getExtension());
        assertEquals("", file("noextension").getExtension());
        assertEquals("", file("trailing.").getExtension());
        assertEquals("", new FileInfo("dir.log", "/dir", 0, true, 0, FileInfo.SourceType.LOCAL).getExtension());
        assertEquals("", new FileInfo(null, "/x", 0, false, 0, FileInfo.SourceType.LOCAL).getExtension());
    }

    @Test
    @DisplayName("FileInfo classifies log-like files and describes types")
    void fileInfoTypeHelpers() {
        assertTrue(file("app.log").isLogFile());
        assertTrue(file("notes.TXT").isLogFile());
        assertTrue(file("app.log.1").isLogFile());
        assertFalse(file("image.png").isLogFile());
        assertFalse(new FileInfo("app.log", "/dir", 0, true, 0, FileInfo.SourceType.LOCAL).isLogFile());
        assertFalse(new FileInfo(null, "/x", 0, false, 0, FileInfo.SourceType.LOCAL).isLogFile());

        assertEquals("LOG File", file("App.LOG").getTypeDescription());
        assertEquals("File", file("README").getTypeDescription());
        assertEquals("Folder", new FileInfo("logs", "/logs", 0, true, 0, FileInfo.SourceType.LOCAL)
                .getTypeDescription());
    }

    @Test
    @DisplayName("FileInfo formatted size covers every unit and invalidates its cache")
    void fileInfoFormattedSize() {
        assertEquals("512 B", file("a.log").getFormattedSize());

        FileInfo kb = new FileInfo("b.log", "/b", 2048, false, 0, FileInfo.SourceType.LOCAL);
        assertEquals("2.0 KB", kb.getFormattedSize());
        kb.setSize(1024);
        assertEquals("1.0 KB", kb.getFormattedSize());

        FileInfo mb = new FileInfo("c.log", "/c", 3L * 1024 * 1024, false, 0, FileInfo.SourceType.LOCAL);
        assertEquals("3.0 MB", mb.getFormattedSize());

        FileInfo gb = new FileInfo("d.log", "/d", 2L * 1024 * 1024 * 1024, false, 0, FileInfo.SourceType.LOCAL);
        assertEquals("2.0 GB", gb.getFormattedSize());

        FileInfo dir = new FileInfo("e", "/e", 4096, true, 0, FileInfo.SourceType.LOCAL);
        assertEquals("-", dir.getFormattedSize());
    }

    @Test
    @DisplayName("FileInfo modified timestamps expose LocalDateTime and a formatted string")
    void fileInfoModified() {
        FileInfo noTime = file("a.log");
        assertNull(noTime.getModified());
        assertEquals("-", noTime.getFormattedModified());

        long epochMillis = 1_700_000_000_000L;
        FileInfo timed = new FileInfo("b.log", "/b", 0, false, epochMillis, FileInfo.SourceType.LOCAL);
        LocalDateTime expected = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis),
                ZoneId.systemDefault());
        assertEquals(expected, timed.getModified());
        String formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(epochMillis));
        assertEquals(formatted, timed.getFormattedModified());

        timed.setModifiedTime(0);
        assertEquals("-", timed.getFormattedModified());
    }

    @Test
    @DisplayName("FileInfo toString marks directories and prints the file size")
    void fileInfoToString() {
        assertEquals("logs [DIR]", new FileInfo("logs", "/logs", 0, true, 0, FileInfo.SourceType.LOCAL).toString());
        assertEquals("app.log (512 B)", file("app.log").toString());
    }

    @Test
    @DisplayName("RemoteFileInfo compareTo puts directories first and ignores name case")
    void remoteFileInfoCompareTo() {
        RemoteFileInfo folder = new RemoteFileInfo();
        folder.setName("Zeta");
        folder.setDirectory(true);
        RemoteFileInfo file = new RemoteFileInfo();
        file.setName("alpha.log");

        assertEquals(-1, folder.compareTo(file));
        assertEquals(1, file.compareTo(folder));

        RemoteFileInfo sameName = new RemoteFileInfo();
        sameName.setName("ALPHA.LOG");
        assertEquals(0, file.compareTo(sameName));
    }

    @Test
    @DisplayName("LogSession exposes tail filter state and remote counter setters")
    void logSessionStateAccessors() {
        LogSession session = new LogSession("state.log", LogSession.SessionType.LOCAL);

        session.setSessionType(LogSession.SessionType.REMOTE);
        assertEquals(LogSession.SessionType.REMOTE, session.getSessionType());

        session.setUnreadTailLines(9);
        assertEquals(9, session.getUnreadTailLines());

        session.setFilteredCount(1234L);
        assertEquals(1234L, session.getFilteredCount());

        java.util.function.Predicate<LogEntry> predicate = entry -> true;
        session.setCurrentTailFilterPredicate(predicate);
        assertSame(predicate, session.getCurrentTailFilterPredicate());
    }

    @Test
    @DisplayName("LogSession close stops tail service, disconnects SSH and closes the reader")
    void logSessionCloseReleasesCollaborators() throws Exception {
        LogSession session = new LogSession("cleanup.log", LogSession.SessionType.REMOTE);
        AtomicBoolean tailStopped = new AtomicBoolean();
        session.setTailService(new TailService() {
            @Override
            public void startLocalTail(File file, java.util.function.Consumer<String> lineConsumer,
                    java.util.function.Consumer<Exception> errorHandler, boolean loadContext) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stopTail() {
                tailStopped.set(true);
                throw new IllegalStateException("stop failed");
            }

            @Override
            public boolean isRunning() {
                return true;
            }
        });

        AtomicBoolean disconnected = new AtomicBoolean();
        SSHService ssh = (SSHService) Proxy.newProxyInstance(SSHService.class.getClassLoader(),
                new Class<?>[] {SSHService.class},
                (proxy, method, args) -> {
                    if ("isConnected".equals(method.getName())) {
                        return true;
                    }
                    if ("disconnect".equals(method.getName())) {
                        disconnected.set(true);
                        return null;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                });
        session.setSshService(ssh);

        Path file = Files.createTempFile("seeloggyplus-session", ".log");
        Files.writeString(file, "line");
        file.toFile().deleteOnExit();
        session.setReader(new com.seeloggyplus.shared.util.MappedFileReader(file.toFile()));

        session.close();

        assertTrue(tailStopped.get());
        assertTrue(disconnected.get());
        assertNull(session.getTailService());
        assertNull(session.getSshService());
        assertNull(session.getReader());
        assertTrue(session.isClosed());
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static FileInfo file(String name) {
        return new FileInfo(name, "/tmp/" + name, 512, false, 0, FileInfo.SourceType.LOCAL);
    }
}
