package com.seeloggyplus.service.impl;

import com.jcraft.jsch.*;
import com.seeloggyplus.dto.RemoteFileInfo;
import com.seeloggyplus.service.LogParser;
import com.seeloggyplus.service.SSHService;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Implementation of {@link SSHService} using JSch library.
 * <p>
 * Handles secure shell connections, file transfers (SFTP), and remote command
 * executions.
 */
public class SSHServiceImpl implements SSHService {

    private static final Logger logger = LoggerFactory.getLogger(SSHServiceImpl.class);
    private static final int DEFAULT_PORT = 22;
    private static final long DEFAULT_TTL = 10 * 60 * 1000;

    @Getter
    private String host;
    @Getter
    private int port;
    @Getter
    private String username;
    private String password;

    private Session currentSession;
    private ChannelSftp reusableSftpChannel;
    private volatile Boolean sftpAvailable = null; // null = not tested, true/false = tested
    private ChannelExec activeTailChannel;
    private final AtomicBoolean isTailing = new AtomicBoolean(false);
    private final ExecutorService tailExecutor = Executors.newSingleThreadExecutor();

    /**
     * Default constructor.
     */
    public SSHServiceImpl() {
    }

    @Override
    public boolean connect(String host, int port, String username, String password) {
        return connect(host, port, username, password, DEFAULT_TTL);
    }

    @Override
    public boolean connect(String host, int port, String username, String password, long ttlMillis) {
        this.host = host;
        this.port = port > 0 ? port : DEFAULT_PORT;
        this.username = username;
        this.password = password;

        try {
            this.currentSession = SSHSessionManagerImpl.getInstance().getSession(this.host, this.port, this.username,
                    this.password, ttlMillis);
            return this.currentSession.isConnected();
        } catch (JSchException e) {
            logger.error("Connection failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect() {
        stopTailing();
        closeSftpChannel();
        sftpAvailable = null; // Reset for next connection
        if (host != null) {
            SSHSessionManagerImpl.getInstance().closeSession(host, port, username);
        }
        this.currentSession = null;
    }

    @Override
    public boolean isConnected() {
        return currentSession != null && currentSession.isConnected();
    }

    @Override
    public String executeCommand(String command) throws IOException {
        Session session = getSessionOrThrow();
        StringBuilder output = new StringBuilder();
        ChannelExec channel = null;

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();

            channel.connect();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            if (output.length() == 0) {
                try (BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(err, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        logger.warn("SSH Stderr: {}", line);
                    }
                }
            }

            return output.toString();
        } catch (JSchException e) {
            throw new IOException("Failed to execute command: " + command, e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    private Session getSessionOrThrow() throws IOException {
        if (isConnected()) {
            try {
                // Refresh TTL
                SSHSessionManagerImpl.getInstance().getSession(host, port, username, password, DEFAULT_TTL);
            } catch (JSchException ignored) {
            }
            return currentSession;
        }
        if (password != null && connect(host, port, username, password)) {
            return currentSession;
        }
        throw new IOException("Not connected to SSH server");
    }

    @Override
    public void tailFile(String remotePath, int lines, Consumer<String> logConsumer, Consumer<String> errorConsumer) {
        try {
            Session session = getSessionOrThrow();
            stopTailing();

            tailExecutor.submit(() -> {
                isTailing.set(true);
                ChannelExec channel = null;
                try {
                    channel = (ChannelExec) session.openChannel("exec");
                    activeTailChannel = channel;

                    // Force PTY allocation to prevent output buffering on some servers
                    channel.setPty(true);

                    // Add -s 0.2 to force check every 200ms (bypassing broken inotify on WSL)
                    String command = String.format("tail -n %d -F -s 0.2 %s", lines, escapeShellArgument(remotePath));
                    channel.setCommand(command);

                    InputStream in = channel.getInputStream();
                    channel.connect();
                    logger.info("Tail started: {}", remotePath);

                    byte[] buffer = new byte[8192];
                    java.io.ByteArrayOutputStream lineBuffer = new java.io.ByteArrayOutputStream();

                    int bytesRead;
                    while (isTailing.get()) {
                        bytesRead = in.read(buffer);
                        if (bytesRead == -1) {
                            logger.info("SSH Tail: EOF reached (bytesRead=-1).");
                            break;
                        }

                        for (int i = 0; i < bytesRead; i++) {
                            byte b = buffer[i];
                            if (b == '\n') {
                                // Flush line
                                String l = lineBuffer.toString(StandardCharsets.UTF_8);
                                logConsumer.accept(l);
                                lineBuffer.reset();
                            } else if (b != '\r') {
                                lineBuffer.write(b);
                            }
                        }
                    }

                    if (lineBuffer.size() > 0) {
                        logger.info("SSH Tail: Flushing remaining buffer.");
                        logConsumer.accept(lineBuffer.toString(StandardCharsets.UTF_8));
                    }

                    int exit = channel.getExitStatus();
                    logger.info("Tail channel closed, exit status={}", exit);

                } catch (Exception e) {
                    if (isTailing.get()) {
                        logger.error("Tail error", e);
                        errorConsumer.accept("Connection Error: " + e.getMessage());
                    }
                } finally {
                    isTailing.set(false);
                    cleanupTailChannel();
                }
            });

        } catch (IOException e) {
            errorConsumer.accept("Init Error: " + e.getMessage());
        }
    }

    @Override
    public void stopTailing() {
        if (isTailing.get()) {
            isTailing.set(false);
            cleanupTailChannel();
        }
    }

    private void cleanupTailChannel() {
        if (activeTailChannel != null) {
            activeTailChannel.disconnect();
            activeTailChannel = null;
        }
    }

    @Override
    public String readFile(String remotePath) throws IOException {
        Session session = getSessionOrThrow();
        StringBuilder content = new StringBuilder();
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("cat " + escapeShellArgument(remotePath));

            InputStream in = channel.getInputStream();
            channel.connect();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            return content.toString();
        } catch (JSchException e) {
            throw new IOException(e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    @Override
    public List<RemoteFileInfo> listFiles(String remotePath) throws IOException {
        // If SFTP already known to be unavailable, go straight to exec
        if (Boolean.FALSE.equals(sftpAvailable)) {
            return listFilesExec(remotePath);
        }
        // Try SFTP first, fallback to exec-based ls
        try {
            List<RemoteFileInfo> result = listFilesSftp(remotePath);
            sftpAvailable = true;
            return result;
        } catch (IOException e) {
            logger.warn("SFTP listFiles failed, falling back to exec: {}", e.getMessage());
            sftpAvailable = false;
            return listFilesExec(remotePath);
        }
    }

    private List<RemoteFileInfo> listFilesSftp(String remotePath) throws IOException {
        try {
            ChannelSftp sftpChannel = getSftpChannel();

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(remotePath);
            List<RemoteFileInfo> files = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : entries) {
                String filename = entry.getFilename();
                if (!filename.equals(".") && !filename.equals("..")) {
                    files.add(mapToFileInfo(entry, remotePath));
                }
            }
            return files;
        } catch (SftpException e) {
            throw new IOException(e);
        }
    }

    private List<RemoteFileInfo> listFilesExec(String remotePath) throws IOException {
        Session session = getSessionOrThrow();
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            // WinSCP-style: clear locale, use ls --full-time for precise timestamps
            // Fallback chain: ls --full-time → ls -la
            String escapedPath = escapeShellArgument(remotePath);
            String cmd = "unset LANG LC_ALL LC_TIME LC_MESSAGES 2>/dev/null; "
                    + "LANG=C LC_ALL=C; export LANG LC_ALL; "
                    + "ls -la --full-time " + escapedPath + " 2>/dev/null || ls -la " + escapedPath;
            channel.setCommand(cmd);

            InputStream in = channel.getInputStream();
            channel.connect(10000);

            List<RemoteFileInfo> files = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    RemoteFileInfo info = parseLsLine(line, remotePath);
                    if (info != null) {
                        files.add(info);
                    }
                }
            }
            return files;
        } catch (JSchException e) {
            throw new IOException("exec listFiles failed: " + e.getMessage(), e);
        } finally {
            if (channel != null) channel.disconnect();
        }
    }



    private RemoteFileInfo parseLsLine(String line, String parentPath) {
        // Skip total line and empty lines
        if (line.startsWith("total ") || line.isBlank()) return null;

        // ls -la format:       drwxr-xr-x 2 root root 4096 Jan 01 12:00 dirname
        // ls --full-time:      drwxr-xr-x 2 root root 4096 2026-01-15 10:30:45.000000000 +0700 dirname
        // symlink:             lrwxrwxrwx 1 root root   11 Jan 01 12:00 link -> target

        String[] parts = line.split("\\s+");
        if (parts.length < 9) return null;

        String permissions = parts[0];
        // parts[1] = link count, parts[2] = owner, parts[3] = group
        String sizeStr = parts[4];

        // Detect --full-time format (date looks like 2026-01-15)
        String name;
        long modifiedTime = 0;

        if (parts.length >= 10 && parts[5].matches("\\d{4}-\\d{2}-\\d{2}")) {
            // --full-time format: ... size 2026-01-15 10:30:45.xxx +0700 name
            // Timestamp parts: parts[5]=date, parts[6]=time, parts[7]=timezone
            try {
                String dateTimeStr = parts[5] + " " + parts[6];
                // Truncate nanoseconds if present
                if (dateTimeStr.contains(".")) {
                    dateTimeStr = dateTimeStr.substring(0, dateTimeStr.indexOf('.'));
                }
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dateTimeStr,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                modifiedTime = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignored) {
            }
            // Name starts after timezone at index 8 (timezone at 7)
            // Check if parts[7] looks like a timezone (+0700, -0500, +0000)
            int nameStart = parts[7].matches("[+-]\\d{4}") ? 8 : 7;
            name = joinFrom(parts, nameStart);
        } else {
            // Standard ls -la: ... size Mon DD HH:MM name  OR  ... size Mon DD YYYY name
            // parts[5]=month, parts[6]=day, parts[7]=time/year
            try {
                if (parts[7].contains(":")) {
                    // "Jan 15 10:30" — current year
                    java.time.LocalDateTime ldt = java.time.MonthDay.parse(parts[5] + " " + parts[6],
                            java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH))
                            .atYear(java.time.Year.now().getValue())
                            .atTime(java.time.LocalTime.parse(parts[7]));
                    modifiedTime = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                } else {
                    // "Jan 15 2025" — year specified
                    String monthDayYear = parts[5] + " " + parts[6] + " " + parts[7];
                    java.time.LocalDate ld = java.time.LocalDate.parse(monthDayYear,
                            java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy", java.util.Locale.ENGLISH));
                    modifiedTime = ld.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
            } catch (Exception ignored) {
            }
            // Name starts at index 8
            name = joinFrom(parts, 8);
        }

        // Handle symlinks: "link -> target" — extract just the link name
        if (name.contains(" -> ")) {
            name = name.substring(0, name.indexOf(" -> "));
        }

        if (name.equals(".") || name.equals("..")) return null;

        RemoteFileInfo info = new RemoteFileInfo();
        info.setName(name);
        info.setPath((parentPath.endsWith("/") ? parentPath : parentPath + "/") + name);
        info.setDirectory(permissions.startsWith("d"));
        try {
            info.setSize(Long.parseLong(sizeStr));
        } catch (NumberFormatException e) {
            info.setSize(0);
        }
        info.setPermissions(permissions);
        info.setModifiedTime(modifiedTime);
        return info;
    }

    private String joinFrom(String[] parts, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private void reconnectSession() throws IOException {
        logger.info("Forcing SSH session reconnect for {}@{}:{}", username, host, port);
        closeSftpChannel();
        sftpAvailable = null; // Reset for re-detection
        SSHSessionManagerImpl.getInstance().closeSession(host, port, username);
        this.currentSession = null;
        if (password != null && connect(host, port, username, password)) {
            return;
        }
        throw new IOException("Failed to reconnect SSH session");
    }

    private synchronized ChannelSftp getSftpChannel() throws IOException {
        if (reusableSftpChannel != null && reusableSftpChannel.isConnected()) {
            return reusableSftpChannel;
        }
        Session session = getSessionOrThrow();
        // Single attempt with short timeout — if SFTP subsystem is unavailable, fail fast
        try {
            ChannelSftp ch = (ChannelSftp) session.openChannel("sftp");
            ch.connect(3000);
            reusableSftpChannel = ch;
            logger.info("SFTP channel opened for {}@{}:{}", username, host, port);
            return reusableSftpChannel;
        } catch (JSchException e) {
            reusableSftpChannel = null;
            throw new IOException("SFTP channel unavailable: " + e.getMessage(), e);
        }
    }

    private synchronized void closeSftpChannel() {
        if (reusableSftpChannel != null) {
            try {
                reusableSftpChannel.disconnect();
            } catch (Exception ignored) {
            }
            reusableSftpChannel = null;
        }
    }

    @Override
    public List<String> readFileLines(String remotePath) throws IOException {
        Session session = getSessionOrThrow();
        List<String> lines = new ArrayList<>();
        ChannelExec channel = null;

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("cat " + escapeShellArgument(remotePath));

            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            channel.connect();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            if (lines.isEmpty()) {
                try (BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(err, StandardCharsets.UTF_8))) {
                    StringBuilder errMsg = new StringBuilder();
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) {
                        errMsg.append(errLine).append("\n");
                    }
                    if (errMsg.length() > 0) {
                        throw new IOException("Remote error: " + errMsg.toString().trim());
                    }
                }
            }

            return lines;

        } catch (JSchException e) {
            throw new IOException("SSH execution failed: " + e.getMessage(), e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    @Override
    public List<String> readFileLines(String remotePath, int lineLimit) throws IOException {
        if (lineLimit <= 0) {
            return readFileLines(remotePath);
        }

        Session session = getSessionOrThrow();
        List<String> lines = new ArrayList<>();
        ChannelExec channel = null;

        try {
            String command = "head -n " + lineLimit + " " + escapeShellArgument(remotePath);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            channel.connect();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            if (lines.isEmpty()) {
                try (BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(err, StandardCharsets.UTF_8))) {
                    StringBuilder errMsg = new StringBuilder();
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) {
                        errMsg.append(errLine).append("\n");
                    }
                    if (errMsg.length() > 0) {
                        throw new IOException("Remote error: " + errMsg.toString().trim());
                    }
                }
            }

            return lines;

        } catch (JSchException e) {
            throw new IOException("SSH execution failed: " + e.getMessage(), e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    @Override
    public boolean downloadFile(String remotePath, String localPath) {
        Session session;
        try {
            session = getSessionOrThrow();
        } catch (IOException e) {
            logger.error("Download failed - not connected: {}", e.getMessage());
            return false;
        }

        // Try SFTP if available
        if (!Boolean.FALSE.equals(sftpAvailable)) {
            try {
                ChannelSftp sftpChannel = getSftpChannel();
                try (OutputStream outputStream = new FileOutputStream(localPath)) {
                    sftpChannel.get(remotePath, outputStream);
                }
                sftpAvailable = true;
                return true;
            } catch (SftpException | IOException e) {
                logger.warn("SFTP download failed, falling back to exec cat: {}", e.getMessage());
                sftpAvailable = false;
            }
        }

        // Fallback: exec cat > local file
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("cat " + escapeShellArgument(remotePath));
            InputStream in = channel.getInputStream();
            channel.connect(10000);

            try (OutputStream out = new FileOutputStream(localPath)) {
                byte[] buf = new byte[32 * 1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
            }
            return true;
        } catch (JSchException | IOException e) {
            logger.error("Download (exec fallback) failed: {}", e.getMessage());
            return false;
        } finally {
            if (channel != null) channel.disconnect();
        }
    }

    @Override
    public boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount,
            LogParser.ProgressCallback progressCallback) {
        try {
            long fileSize = getFileSize(remotePath);
            if (fileSize <= 0)
                return false;

            // Always try simple download first if small
            if (fileSize < 5 * 1024 * 1024) {
                return downloadFile(remotePath, localPath);
            }

            Session session;
            try {
                session = getSessionOrThrow();
            } catch (IOException e) {
                logger.error("Download failed - not connected: {}", e.getMessage());
                return false;
            }

            // If SFTP not available, fallback to simple exec download
            if (Boolean.FALSE.equals(sftpAvailable)) {
                return downloadFile(remotePath, localPath);
            }

            // Test if SFTP works
            try {
                getSftpChannel();
            } catch (IOException e) {
                sftpAvailable = false;
                logger.warn("SFTP not available for concurrent download, using single-thread exec fallback");
                return downloadFile(remotePath, localPath);
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            try (RandomAccessFile raf = new RandomAccessFile(localPath, "rw")) {
                raf.setLength(fileSize);
            }

            long chunkSize = fileSize / threadCount;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicLong totalBytesDownloaded = new AtomicLong(0);
            AtomicBoolean hasError = new AtomicBoolean(false);

            logger.info("Starting concurrent download: {} threads, Total Size: {}", threadCount, fileSize);

            for (int i = 0; i < threadCount; i++) {
                final long start = i * chunkSize;
                final long end = (i == threadCount - 1) ? fileSize : (start + chunkSize);
                final long length = end - start;
                final int threadId = i;

                executor.submit(() -> {
                    ChannelSftp channel = null;

                    try (RandomAccessFile raf = new RandomAccessFile(localPath, "rw")) {
                        if (hasError.get())
                            return;

                        channel = (ChannelSftp) session.openChannel("sftp");
                        channel.connect(30_000);

                        raf.seek(start);
                        InputStream is = channel.get(remotePath, null, start);
                        byte[] buffer = new byte[32 * 1024];
                        long bytesReadThisThread = 0;
                        int read;

                        while (bytesReadThisThread < length && (read = is.read(buffer)) != -1) {
                            if (hasError.get())
                                break;

                            long remaining = length - bytesReadThisThread;
                            int toWrite = (int) Math.min(read, remaining);

                            raf.write(buffer, 0, toWrite);
                            bytesReadThisThread += toWrite;

                            long total = totalBytesDownloaded.addAndGet(toWrite);
                            if (progressCallback != null) {
                                progressCallback.onProgress((double) total / fileSize, total, fileSize);
                            }
                        }

                        is.close();
                        logger.debug("Thread {} finished downloading {} bytes", threadId, bytesReadThisThread);

                    } catch (Exception e) {
                        logger.error("Error in download thread {}: {}", threadId, e.getMessage());
                        hasError.set(true);
                    } finally {
                        if (channel != null)
                            channel.disconnect();
                        latch.countDown();
                    }
                });
            }

            long timeoutSec = Math.max(60, Math.min(3600, (fileSize / 102_400) + 60));
            boolean completed = latch.await(timeoutSec, TimeUnit.SECONDS);
            if (!completed) {
                hasError.set(true);
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
            boolean success = !hasError.get();
            executor.shutdown();
            if (hasError.get()) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(localPath));
                } catch (java.nio.file.NoSuchFileException ignored) {
                } catch (IOException e2) {
                    logger.warn("Failed to delete partial download file: {}", localPath, e2);
                }
            }
            return success;
        } catch (Exception e) {
            logger.error("Concurrent download failed", e);
            return false;
        }
    }


    private long getFileSize(String remotePath) throws IOException {
        if (Boolean.FALSE.equals(sftpAvailable)) {
            return getFileSizeExec(remotePath);
        }
        try {
            ChannelSftp sftpChannel = getSftpChannel();
            SftpATTRS attrs = sftpChannel.lstat(remotePath);
            sftpAvailable = true;
            return attrs.getSize();
        } catch (SftpException | IOException e) {
            logger.warn("SFTP getFileSize failed, falling back to exec: {}", e.getMessage());
            sftpAvailable = false;
            return getFileSizeExec(remotePath);
        }
    }

    private long getFileSizeExec(String remotePath) throws IOException {
        Session session = getSessionOrThrow();
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("stat -c %s " + escapeShellArgument(remotePath));
            InputStream in = channel.getInputStream();
            channel.connect(10000);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return Long.parseLong(line.trim());
                }
            }
            return 0;
        } catch (JSchException | NumberFormatException e) {
            throw new IOException("Failed to get file size: " + e.getMessage(), e);
        } finally {
            if (channel != null) channel.disconnect();
        }
    }

    private String escapeShellArgument(String arg) {
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    private RemoteFileInfo mapToFileInfo(ChannelSftp.LsEntry entry, String parentPath) {
        SftpATTRS attrs = entry.getAttrs();
        RemoteFileInfo info = new RemoteFileInfo();
        info.setName(entry.getFilename());
        info.setPath((parentPath.endsWith("/") ? parentPath : parentPath + "/") + entry.getFilename());
        info.setSize(attrs.getSize());
        info.setDirectory(attrs.isDir());
        info.setModifiedTime(attrs.getMTime() * 1000L);
        info.setPermissions(attrs.getPermissionsString());
        return info;
    }
}