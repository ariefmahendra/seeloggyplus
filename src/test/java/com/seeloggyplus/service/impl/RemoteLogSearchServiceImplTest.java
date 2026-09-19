package com.seeloggyplus.service.impl;

import com.seeloggyplus.dto.RemoteFileInfo;
import com.seeloggyplus.model.PreviewLine;
import com.seeloggyplus.model.RemoteLogSearchMatch;
import com.seeloggyplus.service.LogParser;
import com.seeloggyplus.service.SSHService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteLogSearchServiceImplTest {

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    @Test
    void searchParsesMatchesEscapesQueryAndCapsResults() throws Exception {
        RecordingSsh ssh = new RecordingSsh(
                "/var/log/app.log:14:request timeout\n"
                        + "/var/log/error.out:2:timeout after 30s\n"
                        + "/var/log/app.log:14:request timeout\n"
                        + "malformed-line-without-separator\n");
        List<RemoteLogSearchMatch> results = new RemoteLogSearchServiceImpl()
                .search(ssh, "/var/log", "order'123", false, false);

        assertEquals(2, results.size());
        assertEquals("/var/log/app.log", results.get(0).path());
        assertEquals(14, results.get(0).lineNumber());
        assertEquals("request timeout", results.get(0).content());
        assertEquals("/var/log/error.out", results.get(1).path());

        assertTrue(ssh.command.contains("-type f"));
        assertFalse(ssh.command.contains("-name"), "Search must cover all files, not only known extensions");
        assertTrue(ssh.command.contains("grep -niFH"));
        assertTrue(ssh.command.contains("order'\\''123"));
        assertTrue(ssh.command.contains("--binary-files=without-match"));
        assertTrue(ssh.command.contains("head -n 500"));
    }

    @Test
    void searchSupportsRegexAndCaseSensitiveFlags() throws Exception {
        RecordingSsh ssh = new RecordingSsh("/logs/server.log:1:ERROR.*timeout\n");
        new RemoteLogSearchServiceImpl().search(ssh, "/logs", "ERROR.*timeout", true, true);

        assertTrue(ssh.command.contains("grep -nEH"));
        assertFalse(ssh.command.contains("grep -niEH"));
    }

    @Test
    void parseKeepsColonInsidePathAndContent() {
        RemoteLogSearchMatch withColonPath = RemoteLogSearchServiceImpl
                .parseMatchLine("/var/log/my:app.log:42:timeout");
        assertEquals("/var/log/my:app.log", withColonPath.path());
        assertEquals(42, withColonPath.lineNumber());
        assertEquals("timeout", withColonPath.content());

        RemoteLogSearchMatch withColonContent = RemoteLogSearchServiceImpl
                .parseMatchLine("/var/log/app.log:42:port:8080:refused");
        assertEquals("/var/log/app.log", withColonContent.path());
        assertEquals(42, withColonContent.lineNumber());
        assertEquals("port:8080:refused", withColonContent.content());
    }

    @Test
    void parseIgnoresMalformedLines() {
        assertEquals(0, RemoteLogSearchServiceImpl.parseMatches("no separator here").size());
        assertEquals(0, RemoteLogSearchServiceImpl.parseMatches("/a/b.log:notanumber:text").size());
        assertEquals(0, RemoteLogSearchServiceImpl.parseMatches("/a/b.log:12").size());
        assertEquals(0, RemoteLogSearchServiceImpl.parseMatches("").size());
        assertEquals(0, RemoteLogSearchServiceImpl.parseMatches(null).size());
    }

    @Test
    void searchRejectsMissingInput() {
        RemoteLogSearchServiceImpl service = new RemoteLogSearchServiceImpl();
        assertThrows(IllegalArgumentException.class,
                () -> service.search(new RecordingSsh(""), "/logs", "", false, false));
        assertThrows(IllegalArgumentException.class,
                () -> service.search(new RecordingSsh(""), "", "error", false, false));
        assertThrows(IllegalArgumentException.class,
                () -> service.search(null, "/logs", "error", false, false));
    }

    @Test
    void searchEmptyOutputReturnsEmptyList() throws Exception {
        List<RemoteLogSearchMatch> results = new RemoteLogSearchServiceImpl()
                .search(new RecordingSsh(""), "/logs", "missing", false, false);
        assertTrue(results.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Preview
    // -------------------------------------------------------------------------

    @Test
    void previewBuildsRangeCommandAndMarksTargetLine() throws Exception {
        RecordingSsh ssh = new RecordingSsh("12799:connect\n12803:reset\n12804:request timeout\n12805:retry\n");
        List<PreviewLine> lines = new RemoteLogSearchServiceImpl()
                .preview(ssh, "/var/log/app.log", 12804, 5, 5);

        assertTrue(ssh.command.startsWith("awk 'NR>=12799 && NR<=12809"));
        assertTrue(ssh.command.contains("'/var/log/app.log'"));
        assertEquals(4, lines.size());
        assertEquals(12799, lines.get(0).lineNumber());
        assertEquals("connect", lines.get(0).text());
        assertFalse(lines.get(0).target());
        assertTrue(lines.get(2).target());
        assertEquals(12804, lines.get(2).lineNumber());
        assertEquals("request timeout", lines.get(2).text());
    }

    @Test
    void previewClampsStartAtFirstLine() throws Exception {
        RecordingSsh ssh = new RecordingSsh("1:first\n2:second\n");
        new RemoteLogSearchServiceImpl().preview(ssh, "/var/log/app.log", 2, 10, 2);
        assertTrue(ssh.command.contains("NR>=1 && NR<=4"));
    }

    @Test
    void previewEmptyOutputReturnsEmptyList() throws Exception {
        List<PreviewLine> lines = new RemoteLogSearchServiceImpl()
                .preview(new RecordingSsh(""), "/var/log/missing.log", 10, 5, 5);
        assertTrue(lines.isEmpty());
    }

    @Test
    void previewIgnoresMalformedLines() throws Exception {
        List<PreviewLine> lines = new RemoteLogSearchServiceImpl()
                .preview(new RecordingSsh("bad-line\n12:valid\n"), "/var/log/app.log", 12, 0, 0);
        assertEquals(1, lines.size());
        assertEquals(12, lines.get(0).lineNumber());
        assertTrue(lines.get(0).target());
    }

    @Test
    void previewRejectsInvalidInput() {
        RemoteLogSearchServiceImpl service = new RemoteLogSearchServiceImpl();
        assertThrows(IllegalArgumentException.class,
                () -> service.preview(new RecordingSsh(""), "", 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.preview(new RecordingSsh(""), "/a.log", 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.preview(new RecordingSsh(""), "/a.log", 1, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.preview(null, "/a.log", 1, 1, 1));
    }

    // -------------------------------------------------------------------------
    // Line count
    // -------------------------------------------------------------------------

    @Test
    void lineCountParsesWcOutput() throws Exception {
        RecordingSsh ssh = new RecordingSsh("  12345\n");
        long count = new RemoteLogSearchServiceImpl().lineCount(ssh, "/var/log/app.log");
        assertEquals(12345, count);
        assertTrue(ssh.command.contains("wc -l <"));
        assertTrue(ssh.command.contains("'/var/log/app.log'"));
    }

    @Test
    void lineCountReturnsMinusOneForInvalidOrEmptyOutput() throws Exception {
        RemoteLogSearchServiceImpl service = new RemoteLogSearchServiceImpl();
        assertEquals(-1, service.lineCount(new RecordingSsh(""), "/a.log"));
        assertEquals(-1, service.lineCount(new RecordingSsh("not-a-number"), "/a.log"));
        assertEquals(-1, service.lineCount(new RecordingSsh("abc\n"), "/a.log"));
    }

    @Test
    void lineCountRejectsNullInputs() throws Exception {
        RemoteLogSearchServiceImpl service = new RemoteLogSearchServiceImpl();
        assertEquals(-1, service.lineCount(null, "/a.log"));
        assertEquals(-1, service.lineCount(new RecordingSsh("1"), ""));
    }

    // -------------------------------------------------------------------------
    // Test double
    // -------------------------------------------------------------------------

    private static class RecordingSsh implements SSHService {
        private final String output;
        private String command;

        RecordingSsh(String output) {
            this.output = output;
        }

        @Override
        public String executeCommand(String command) {
            this.command = command;
            return output;
        }

        @Override
        public boolean connect(String host, int port, String username, String password) {
            return false;
        }

        @Override
        public boolean connect(String host, int port, String username, String password, long ttlMillis) {
            return false;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void tailFile(String remotePath, int lines, Consumer<String> logConsumer, Consumer<String> errorConsumer) {
        }

        @Override
        public void stopTailing() {
        }

        @Override
        public String readFile(String remotePath) {
            return "";
        }

        @Override
        public List<RemoteFileInfo> listFiles(String remotePath) {
            return List.of();
        }

        @Override
        public List<String> readFileLines(String remotePath) {
            return List.of();
        }

        @Override
        public List<String> readFileLines(String remotePath, int lineLimit) {
            return List.of();
        }

        @Override
        public boolean downloadFile(String remotePath, String localPath) {
            return false;
        }

        @Override
        public boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount,
                LogParser.ProgressCallback progressCallback) {
            return false;
        }
    }
}
