package com.seeloggyplus.service.impl;

import com.jcraft.jsch.*;
import com.seeloggyplus.service.SSHService;
import lombok.Getter;
import lombok.Setter;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class SSHServiceImpl implements SSHService {
    private static final Logger logger = LoggerFactory.getLogger(SSHServiceImpl.class);
    private static final int DEFAULT_PORT = 22;
    private static final long DEFAULT_TTL = 10 * 60 * 1000;

    @Getter private String host;
    @Getter private int port;
    @Getter private String username;
    private String password;

    private Session currentSession;

    private ChannelExec activeTailChannel;
    private final AtomicBoolean isTailing = new AtomicBoolean(false);
    private final ExecutorService tailExecutor = Executors.newSingleThreadExecutor();

    public SSHServiceImpl() {}

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
            this.currentSession = SSHSessionManagerImpl.getInstance().getSession(this.host, this.port, this.username, this.password, ttlMillis);
            return this.currentSession.isConnected();
        } catch (JSchException e) {
            logger.error("Connection failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect() {
        stopTailing();
        if (host != null) {
            SSHSessionManagerImpl.getInstance().closeSession(host, port, username);
        }
        this.currentSession = null;
    }

    @Override
    public boolean isConnected() {
        return currentSession != null && currentSession.isConnected();
    }

    /**
     * Execute remote command and return the output as String.
     */
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

            if (output.isEmpty()) {
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        logger.warn("SSH Stderr: " + line);
                    }
                }
            }

            return output.toString();
        } catch (JSchException e) {
            throw new IOException("Failed to execute command: " + command, e);
        } finally {
            if (channel != null) channel.disconnect();
        }
    }

    private Session getSessionOrThrow() throws IOException {
        if (isConnected()) {
            try {
                SSHSessionManagerImpl.getInstance().getSession(host, port, username, password, DEFAULT_TTL);
            } catch (JSchException ignored) {}
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
                try {
                    activeTailChannel = (ChannelExec) session.openChannel("exec");
                    String command = String.format("tail -n %d -f %s", lines, escapeShellArgument(remotePath));
                    activeTailChannel.setCommand(command);

                    InputStream in = activeTailChannel.getInputStream();
                    InputStream err = activeTailChannel.getErrStream();

                    activeTailChannel.connect();
                    logger.info("Tail started: {}", remotePath);

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while (isTailing.get() && (line = reader.readLine()) != null) {
                            logConsumer.accept(line);
                        }
                    }

                    try (BufferedReader errReader = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
                        while (errReader.ready()) {
                            errorConsumer.accept("SSH Error: " + errReader.readLine());
                        }
                    }

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
                while ((line = reader.readLine()) != null) content.append(line).append("\n");
            }
            return content.toString();
        } catch (JSchException e) {
            throw new IOException(e);
        } finally {
            if (channel != null) channel.disconnect();
        }
    }

    @Override
    public List<RemoteFileInfo> listFiles(String remotePath) throws IOException {
        Session session = getSessionOrThrow();
        List<RemoteFileInfo> files = new ArrayList<>();
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(remotePath);
            for (ChannelSftp.LsEntry entry : entries) {
                String filename = entry.getFilename();
                if (!filename.equals(".") && !filename.equals("..")) {
                    files.add(mapToFileInfo(entry, remotePath));
                }
            }
            return files;
        } catch (JSchException | SftpException e) {
            throw new IOException(e);
        } finally {
            if (sftpChannel != null) sftpChannel.disconnect();
        }
    }

    /**
     * Read remote file content as a list of strings (lines).
     * Safe for use with TableView.
     */
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
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
                    StringBuilder errMsg = new StringBuilder();
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) {
                        errMsg.append(errLine).append("\n");
                    }
                    if (!errMsg.isEmpty()) {
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

        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            try (OutputStream outputStream = new FileOutputStream(localPath)) {
                sftpChannel.get(remotePath, outputStream);
            }

            return true;
        } catch (JSchException | SftpException | IOException e) {
            logger.error("Download failed: {}", e.getMessage());
            return false;
        } finally {
            if (sftpChannel != null) sftpChannel.disconnect();
        }
    }

    @Override
    public boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount, LogParserService.ProgressCallback progressCallback) {
        Session session;
        try {
            session = getSessionOrThrow();
        } catch (IOException e) {
            logger.error("Download failed - not connected: {}", e.getMessage());
            return false;
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            long fileSize = getFileSize(remotePath);
            if (fileSize <= 0) return false;


            if (fileSize < 5 * 1024 * 1024) {
                return downloadFile(remotePath, localPath);
            }

            try (RandomAccessFile raf = new RandomAccessFile(localPath, "rw")) {
                raf.setLength(fileSize);
            }

            long chunkSize = fileSize / threadCount;
            long remainder = fileSize % threadCount;

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
                        if (hasError.get()) return;

                        channel = (ChannelSftp) session.openChannel("sftp");
                        channel.connect();

                        raf.seek(start);

                        InputStream is = channel.get(remotePath, null, start);

                        byte[] buffer = new byte[32 * 1024];
                        long bytesReadThisThread = 0;
                        int read;

                        while (bytesReadThisThread < length && (read = is.read(buffer)) != -1) {
                            if (hasError.get()) break;

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
                        if (channel != null) channel.disconnect();
                        latch.countDown();
                    }
                });
            }

            latch.await();
            return !hasError.get();
        } catch (Exception e) {
            logger.error("Concurrent download failed", e);
            return false;
        } finally {
            executor.shutdown();
        }
    }

    private long getFileSize(String remotePath) throws IOException {
        Session session = getSessionOrThrow();
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            SftpATTRS attrs = sftpChannel.lstat(remotePath);
            return attrs.getSize();
        } catch (JSchException | SftpException e) {
            throw new IOException("Failed to get file size: " + e.getMessage(), e);
        } finally {
            if (sftpChannel != null) sftpChannel.disconnect();
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

    @Setter @Getter
    public static class RemoteFileInfo implements Comparable<RemoteFileInfo> {
        private String name;
        private String path;
        private long size;
        private boolean isDirectory;
        private long modifiedTime;
        private String permissions;

        @Override
        public int compareTo(RemoteFileInfo o) {
            if (this.isDirectory && !o.isDirectory) return -1;
            if (!this.isDirectory && o.isDirectory) return 1;
            return this.name.compareToIgnoreCase(o.name);
        }
    }
}