package com.seeloggyplus.service.impl;

import com.seeloggyplus.dto.RemoteFileInfo;
import com.seeloggyplus.model.FileInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("File Manager Owner Resolution and Extraction Tests")
class FileManagerOwnerTest {

    @Nested
    @DisplayName("RemoteFileInfo DTO Tests")
    class RemoteFileInfoDtoTests {

        private RemoteFileInfo fileInfo;

        @BeforeEach
        void setUp() {
            fileInfo = new RemoteFileInfo();
            fileInfo.setName("sample.log");
            fileInfo.setPath("/var/log/sample.log");
            fileInfo.setSize(2048L);
            fileInfo.setDirectory(false);
            fileInfo.setModifiedTime(1700000000000L);
            fileInfo.setPermissions("-rw-r--r--");
        }

        @Test
        @DisplayName("Should correctly set and get owner property")
        void testOwnerGetterAndSetter() {
            assertNull(fileInfo.getOwner(), "Owner should default to null");
            fileInfo.setOwner("deploy_user");
            assertEquals("deploy_user", fileInfo.getOwner());
        }

        @Test
        @DisplayName("Should support equality and hash code based on Lombok @Data including owner")
        void testEqualsAndHashCode() {
            RemoteFileInfo fileInfo2 = new RemoteFileInfo();
            fileInfo2.setName(fileInfo.getName());
            fileInfo2.setPath(fileInfo.getPath());
            fileInfo2.setSize(fileInfo.getSize());
            fileInfo2.setDirectory(fileInfo.isDirectory());
            fileInfo2.setModifiedTime(fileInfo.getModifiedTime());
            fileInfo2.setPermissions(fileInfo.getPermissions());

            fileInfo.setOwner("appuser");
            fileInfo2.setOwner("appuser");
            assertEquals(fileInfo, fileInfo2);
            assertEquals(fileInfo.hashCode(), fileInfo2.hashCode());

            fileInfo2.setOwner("different_user");
            assertNotEquals(fileInfo, fileInfo2);
        }
    }

    @Nested
    @DisplayName("SFTP Owner Extraction Tests")
    class SftpOwnerExtractionTests {

        private SSHServiceImpl sshService;

        @BeforeEach
        void setUp() {
            sshService = new SSHServiceImpl();
        }

        @AfterEach
        void tearDown() {
            sshService.disconnect();
        }

        @Test
        @DisplayName("Should extract textual owner from standard Unix SFTP longname")
        void testStandardUnixLsLongname() {
            String longname = "drwxr-xr-x 2 arief staff 4096 Jan 1 12:00 my_folder";
            String owner = sshService.extractOwner(longname, 1000);
            assertEquals("arief", owner);
        }

        @Test
        @DisplayName("Should extract owner when longname contains irregular whitespace or tabs")
        void testIrregularWhitespaceInLongname() {
            String longname = "-rw-r--r-- \t  1 \t  www-data   www-data \t 1024 Jan 15 10:30 app.log";
            String owner = sshService.extractOwner(longname, 33);
            assertEquals("www-data", owner);
        }

        @Test
        @DisplayName("Should handle numeric owner in longname column gracefully")
        void testNumericOwnerInLongname() {
            String longname = "-rw-r--r-- 1 1001 1001 512 Jan 15 10:30 test.txt";
            String owner = sshService.extractOwner(longname, 1001);
            assertEquals("1001", owner);
        }

        @Test
        @DisplayName("Should fallback to numeric UID when longname is null")
        void testNullLongnameFallsBackToUid() {
            String owner = sshService.extractOwner(null, 1005);
            assertEquals("1005", owner);
        }

        @Test
        @DisplayName("Should fallback to numeric UID when longname is empty or whitespace")
        void testBlankLongnameFallsBackToUid() {
            String owner1 = sshService.extractOwner("", 500);
            assertEquals("500", owner1);

            String owner2 = sshService.extractOwner("   \t  ", 501);
            assertEquals("501", owner2);
        }

        @Test
        @DisplayName("Should fallback to numeric UID when longname has fewer than 3 tokens")
        void testMalformedShortLongnameFallsBackToUid() {
            String owner = sshService.extractOwner("drwxr-xr-x 2", 1000);
            assertEquals("1000", owner);
        }

        @Test
        @DisplayName("Should return '-' when longname is null and UID is negative")
        void testNullLongnameWithNegativeUid() {
            String owner = sshService.extractOwner(null, -1);
            assertEquals("-", owner);
        }
    }

    @Nested
    @DisplayName("SSH Exec (ls -la) Output Parsing Tests")
    class SshExecLsParsingTests {

        private SSHServiceImpl sshService;

        @BeforeEach
        void setUp() {
            sshService = new SSHServiceImpl();
        }

        @AfterEach
        void tearDown() {
            sshService.disconnect();
        }

        @Test
        @DisplayName("Should parse owner and attributes from standard ls -la output")
        void testStandardLsLaLine() {
            String line = "-rw-r--r-- 1 devops developers 2048 Jan 15 10:30 config.yaml";
            RemoteFileInfo fileInfo = sshService.parseLsLine(line, "/etc/app");

            assertNotNull(fileInfo);
            assertEquals("config.yaml", fileInfo.getName());
            assertEquals("/etc/app/config.yaml", fileInfo.getPath());
            assertEquals("devops", fileInfo.getOwner());
            assertEquals("-rw-r--r--", fileInfo.getPermissions());
            assertEquals(2048L, fileInfo.getSize());
            assertFalse(fileInfo.isDirectory());
        }

        @Test
        @DisplayName("Should parse owner from ls --full-time output")
        void testFullTimeLsLine() {
            String line = "drwxr-xr-x 2 root root 4096 2026-03-24 18:54:31.000000000 +0700 logs";
            RemoteFileInfo fileInfo = sshService.parseLsLine(line, "/var");

            assertNotNull(fileInfo);
            assertEquals("logs", fileInfo.getName());
            assertEquals("/var/logs", fileInfo.getPath());
            assertEquals("root", fileInfo.getOwner());
            assertEquals("drwxr-xr-x", fileInfo.getPermissions());
            assertTrue(fileInfo.isDirectory());
        }

        @Test
        @DisplayName("Should parse owner and clean name from symlink line")
        void testSymlinkLineOwnerAndName() {
            String line = "lrwxrwxrwx 1 admin wheel 15 Mar 01 10:00 current_log.log -> archive.log";
            RemoteFileInfo fileInfo = sshService.parseLsLine(line, "/var/log");

            assertNotNull(fileInfo);
            assertEquals("current_log.log", fileInfo.getName());
            assertEquals("/var/log/current_log.log", fileInfo.getPath());
            assertEquals("admin", fileInfo.getOwner());
            assertEquals("lrwxrwxrwx", fileInfo.getPermissions());
        }

        @Test
        @DisplayName("Should preserve owner and full filename for names with multiple spaces")
        void testFilenameWithSpaces() {
            String line = "-rw-r--r-- 1 postgres postgres 500 Jan 05 12:00 my server error log.log";
            RemoteFileInfo fileInfo = sshService.parseLsLine(line, "/var/log");

            assertNotNull(fileInfo);
            assertEquals("my server error log.log", fileInfo.getName());
            assertEquals("/var/log/my server error log.log", fileInfo.getPath());
            assertEquals("postgres", fileInfo.getOwner());
            assertEquals(500L, fileInfo.getSize());
        }

        @Test
        @DisplayName("Should ignore total header and blank lines")
        void testIgnoreTotalAndBlankLines() {
            assertNull(sshService.parseLsLine("total 128", "/var/log"));
            assertNull(sshService.parseLsLine("   ", "/var/log"));
            assertNull(sshService.parseLsLine("", "/var/log"));
        }

        @Test
        @DisplayName("Should ignore '.' and '..' directory entries")
        void testIgnoreDotDirectories() {
            assertNull(sshService.parseLsLine("drwxr-xr-x 2 root root 4096 Jan 01 12:00 .", "/var/log"));
            assertNull(sshService.parseLsLine("drwxr-xr-x 3 root root 4096 Jan 01 12:00 ..", "/var/log"));
        }

        @Test
        @DisplayName("Should return null for lines with fewer than 9 tokens")
        void testIgnoreMalformedShortLine() {
            assertNull(sshService.parseLsLine("not a valid ls output line", "/var/log"));
        }
    }

    @Nested
    @DisplayName("Local File System Owner Resolution Tests")
    class LocalFileOwnerTests {

        private LocalFileServiceImpl localFileService;

        @BeforeEach
        void setUp() {
            localFileService = new LocalFileServiceImpl();
        }

        @Test
        @DisplayName("Should populate owner for regular files in local directory listing")
        void testLocalFileListingPopulatesOwner(@TempDir Path tempDir) throws IOException {
            Path file1 = Files.createFile(tempDir.resolve("sample1.log"));
            Path file2 = Files.createFile(tempDir.resolve("sample2.txt"));

            List<FileInfo> files = localFileService.listFiles(tempDir.toString());

            assertNotNull(files);
            assertEquals(2, files.size());

            for (FileInfo file : files) {
                assertNotNull(file.getOwner(), "File owner must not be null");
                assertFalse(file.getOwner().isBlank(), "File owner must not be blank");
                assertNotNull(file.getPermissions(), "Permissions must not be null");
                assertEquals(FileInfo.SourceType.LOCAL, file.getSourceType());
            }
        }

        @Test
        @DisplayName("Should populate owner for subdirectories in local directory listing")
        void testLocalDirectoryListingPopulatesOwner(@TempDir Path tempDir) throws IOException {
            Path subDir = Files.createDirectory(tempDir.resolve("sub_folder"));

            List<FileInfo> files = localFileService.listFiles(tempDir.toString());

            assertNotNull(files);
            assertEquals(1, files.size());

            FileInfo dirInfo = files.get(0);
            assertEquals("sub_folder", dirInfo.getName());
            assertTrue(dirInfo.isDirectory());
            assertNotNull(dirInfo.getOwner(), "Directory owner must not be null");
            assertFalse(dirInfo.getOwner().isBlank(), "Directory owner must not be blank");
        }

        @Test
        @DisplayName("Should throw IOException when directory path does not exist")
        void testNonExistentDirectoryThrowsIOException(@TempDir Path tempDir) {
            Path nonExistent = tempDir.resolve("does_not_exist");
            assertThrows(IOException.class, () -> localFileService.listFiles(nonExistent.toString()));
        }
    }
}
