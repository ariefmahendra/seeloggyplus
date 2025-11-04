package com.seeloggyplus.service.impl;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Service for SSH remote file access
 * Provides functionality to connect to remote servers and read log files
 */
public class SSHService {

    private static final Logger logger = LoggerFactory.getLogger(SSHService.class);
    private static final int DEFAULT_SSH_PORT = 22;
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final int SESSION_TIMEOUT = 60000; // 60 seconds

    private Session session;
    private String host;
    private int port;
    private String username;
    private boolean isConnected;

    public SSHService() {
        this.port = DEFAULT_SSH_PORT;
        this.isConnected = false;
    }

    /**
     * Connect to SSH server with password authentication
     */
    public boolean connect(String host, int port, String username, String password) {
        this.host = host;
        this.port = port > 0 ? port : DEFAULT_SSH_PORT;
        this.username = username;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, this.port);
            session.setPassword(password);

            // Configure session properties
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password");
            config.put("MaxAuthTries", "3");
            session.setConfig(config);

            session.setTimeout(SESSION_TIMEOUT);
            session.connect(CONNECTION_TIMEOUT);

            isConnected = session.isConnected();
            if (isConnected) {
                logger.info("Successfully connected to {}@{}:{}", username, host, this.port);
            }

            return isConnected;
        } catch (JSchException e) {
            logger.error("Failed to connect to SSH server: {}", e.getMessage());
            isConnected = false;
            return false;
        }
    }

    /**
     * Connect to SSH server with private key authentication
     */
    public boolean connectWithKey(String host, int port, String username, String privateKeyPath, String passphrase) {
        this.host = host;
        this.port = port > 0 ? port : DEFAULT_SSH_PORT;
        this.username = username;

        try {
            JSch jsch = new JSch();

            if (passphrase != null && !passphrase.isEmpty()) {
                jsch.addIdentity(privateKeyPath, passphrase);
            } else {
                jsch.addIdentity(privateKeyPath);
            }

            session = jsch.getSession(username, host, this.port);

            // Configure session properties
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "publickey");
            session.setConfig(config);

            session.setTimeout(SESSION_TIMEOUT);
            session.connect(CONNECTION_TIMEOUT);

            isConnected = session.isConnected();
            if (isConnected) {
                logger.info("Successfully connected to {}@{}:{} with key", username, host, this.port);
            }

            return isConnected;
        } catch (JSchException e) {
            logger.error("Failed to connect to SSH server with key: {}", e.getMessage());
            isConnected = false;
            return false;
        }
    }

    /**
     * Disconnect from SSH server
     */
    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            isConnected = false;
            logger.info("Disconnected from SSH server");
        }
    }

    /**
     * Check if connected to SSH server
     */
    public boolean isConnected() {
        return isConnected && session != null && session.isConnected();
    }

    /**
     * Read remote file content
     */
    public String readFile(String remotePath) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to SSH server");
        }

        StringBuilder content = new StringBuilder();

        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("cat " + escapeShellArgument(remotePath));
            channel.setErrStream(System.err);

            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            reader.close();
            channel.disconnect();

            return content.toString();
        } catch (JSchException e) {
            throw new IOException("Failed to read remote file: " + e.getMessage(), e);
        }
    }

    /**
     * Read remote file with progress callback
     */
    public List<String> readFileLines(String remotePath, ProgressCallback callback) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to SSH server");
        }

        List<String> lines = new ArrayList<>();

        try {
            // First get file size
            long fileSize = getFileSize(remotePath);

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("cat " + escapeShellArgument(remotePath));
            channel.setErrStream(System.err);

            InputStream in = channel.getInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            long bytesRead = 0;
            long lineCount = 0;

            while ((line = reader.readLine()) != null) {
                lines.add(line);
                bytesRead += line.getBytes(StandardCharsets.UTF_8).length + 1; // +1 for newline
                lineCount++;

                if (callback != null && lineCount % 100 == 0) {
                    double progress = fileSize > 0 ? (double) bytesRead / fileSize : 0;
                    callback.onProgress(progress, lineCount);
                }
            }

            if (callback != null) {
                callback.onComplete(lineCount);
            }

            reader.close();
            channel.disconnect();

            return lines;
        } catch (JSchException e) {
            throw new IOException("Failed to read remote file: " + e.getMessage(), e);
        }
    }

    /**
     * Download remote file to local path
     */
    public boolean downloadFile(String remotePath, String localPath) {
        if (!isConnected()) {
            logger.error("Not connected to SSH server");
            return false;
        }

        try {
            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            sftpChannel.get(remotePath, localPath);
            sftpChannel.disconnect();

            logger.info("Downloaded file from {} to {}", remotePath, localPath);
            return true;
        } catch (JSchException | SftpException e) {
            logger.error("Failed to download file: {}", e.getMessage());
            return false;
        }
    }

    /**
     * List files in remote directory
     */
    public List<RemoteFileInfo> listFiles(String remotePath) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to SSH server");
        }

        List<RemoteFileInfo> files = new ArrayList<>();

        try {
            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            @SuppressWarnings("unchecked")
            java.util.Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(remotePath);

            for (ChannelSftp.LsEntry entry : entries) {
                String filename = entry.getFilename();
                if (!filename.equals(".") && !filename.equals("..")) {
                    SftpATTRS attrs = entry.getAttrs();
                    RemoteFileInfo fileInfo = new RemoteFileInfo();
                    fileInfo.setName(filename);
                    fileInfo.setPath(remotePath.endsWith("/") ? remotePath + filename : remotePath + "/" + filename);
                    fileInfo.setSize(attrs.getSize());
                    fileInfo.setDirectory(attrs.isDir());
                    fileInfo.setModifiedTime(attrs.getMTime() * 1000L); // Convert to milliseconds
                    fileInfo.setPermissions(attrs.getPermissionsString());
                    fileInfo.setOwner(String.valueOf(attrs.getUId()));
                    fileInfo.setGroup(String.valueOf(attrs.getGId()));
                    files.add(fileInfo);
                }
            }

            sftpChannel.disconnect();
            return files;
        } catch (JSchException | SftpException e) {
            throw new IOException("Failed to list remote files: " + e.getMessage(), e);
        }
    }

    /**
     * Get remote file size
     */
    public long getFileSize(String remotePath) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to SSH server");
        }

        try {
            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            SftpATTRS attrs = sftpChannel.stat(remotePath);
            long size = attrs.getSize();
            sftpChannel.disconnect();
            return size;
        } catch (JSchException | SftpException e) {
            throw new IOException("Failed to get file size: " + e.getMessage(), e);
        }
    }

    /**
     * Check if remote file exists
     */
    public boolean fileExists(String remotePath) {
        if (!isConnected()) {
            return false;
        }

        try {
            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            sftpChannel.stat(remotePath);
            sftpChannel.disconnect();
            return true;
        } catch (JSchException | SftpException e) {
            return false;
        }
    }

    /**
     * Execute remote command
     */
    public String executeCommand(String command) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to SSH server");
        }

        StringBuilder output = new StringBuilder();

        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            while ((line = errReader.readLine()) != null) {
                output.append("ERROR: ").append(line).append("\n");
            }

            reader.close();
            errReader.close();
            channel.disconnect();

            return output.toString();
        } catch (JSchException e) {
            throw new IOException("Failed to execute command: " + e.getMessage(), e);
        }
    }

    /**
     * Test SSH connection
     */
    public static CompletableFuture<Boolean> testConnection(String host, int port, String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            SSHService testService = new SSHService();
            boolean result = testService.connect(host, port, username, password);
            testService.disconnect();
            return result;
        });
    }

    /**
     * Escape shell argument to prevent command injection
     */
    private String escapeShellArgument(String arg) {
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    // Getters
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Progress callback interface
     */
    public interface ProgressCallback {
        void onProgress(double progress, long linesRead);
        void onComplete(long totalLines);
    }

    /**
     * Remote file information class
     */
    public static class RemoteFileInfo implements Comparable<RemoteFileInfo> {
        private String name;
        private String path;
        private long size;
        private boolean isDirectory;
        private long modifiedTime;
        private String permissions;
        private String owner;
        private String group;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public void setDirectory(boolean directory) {
            isDirectory = directory;
        }

        public long getModifiedTime() {
            return modifiedTime;
        }

        public void setModifiedTime(long modifiedTime) {
            this.modifiedTime = modifiedTime;
        }

        public String getPermissions() {
            return permissions;
        }

        public void setPermissions(String permissions) {
            this.permissions = permissions;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public java.time.LocalDateTime getModified() {
            if (modifiedTime > 0) {
                return java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(modifiedTime), 
                    java.time.ZoneId.systemDefault()
                );
            }
            return null;
        }

        public boolean isFile() {
            return !isDirectory;
        }

        public boolean isLogFile() {
            if (!isFile()) {
                return false;
            }
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".log") || 
                   lowerName.endsWith(".txt") ||
                   lowerName.contains(".log.");
        }

        public String getTypeDescription() {
            if (isDirectory) {
                return "Folder";
            }
            String ext = getExtension();
            if (!ext.isEmpty()) {
                return ext.toUpperCase() + " File";
            }
            return "File";
        }

        public String getExtension() {
            if (isDirectory) {
                return "";
            }
            int lastDot = name.lastIndexOf('.');
            if (lastDot > 0 && lastDot < name.length() - 1) {
                return name.substring(lastDot + 1).toLowerCase();
            }
            return "";
        }

        public String getFormattedSize() {
            if (isDirectory) {
                return "-";
            }
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format("%.1f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", size / (1024.0 * 1024.0));
            } else {
                return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
            }
        }

        @Override
        public int compareTo(RemoteFileInfo other) {
            // Directories first
            if (this.isDirectory && !other.isDirectory) {
                return -1;
            }
            if (!this.isDirectory && other.isDirectory) {
                return 1;
            }
            // Then by name
            return this.name.compareToIgnoreCase(other.name);
        }

        @Override
        public String toString() {
            return name + (isDirectory ? " [DIR]" : " (" + getFormattedSize() + ")");
        }
    }
}
