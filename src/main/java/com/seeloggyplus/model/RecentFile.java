package com.seeloggyplus.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Model class representing a recently opened file
 * Used to track file history in the application
 */
public class RecentFile implements Serializable {

    private static final long serialVersionUID = 1L;

    private String filePath;
    private String fileName;
    private LocalDateTime lastOpened;
    private long fileSize;
    private boolean isRemote;
    private String remoteHost;
    private int remotePort;
    private String remoteUser;
    private String remotePath;
    private ParsingConfig parsingConfig;

    public RecentFile() {
        this.lastOpened = LocalDateTime.now();
        this.isRemote = false;
    }

    public RecentFile(String filePath, String fileName) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.lastOpened = LocalDateTime.now();
        this.isRemote = false;
    }

    /**
     * Constructor for local files
     */
    public RecentFile(String filePath, String fileName, long fileSize) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.lastOpened = LocalDateTime.now();
        this.isRemote = false;
    }

    /**
     * Constructor for remote files
     */
    public RecentFile(String fileName, String remoteHost, int remotePort, String remoteUser, String remotePath) {
        this.fileName = fileName;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.remoteUser = remoteUser;
        this.remotePath = remotePath;
        this.filePath = remoteUser + "@" + remoteHost + ":" + remotePath;
        this.lastOpened = LocalDateTime.now();
        this.isRemote = true;
    }

    // Getters and Setters

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LocalDateTime getLastOpened() {
        return lastOpened;
    }

    public void setLastOpened(LocalDateTime lastOpened) {
        this.lastOpened = lastOpened;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isRemote() {
        return isRemote;
    }

    public void setRemote(boolean remote) {
        isRemote = remote;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public String getRemoteUser() {
        return remoteUser;
    }

    public void setRemoteUser(String remoteUser) {
        this.remoteUser = remoteUser;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public ParsingConfig getParsingConfig() {
        return parsingConfig;
    }

    public void setParsingConfig(ParsingConfig parsingConfig) {
        this.parsingConfig = parsingConfig;
    }

    /**
     * Update the last opened timestamp to now
     */
    public void updateLastOpened() {
        this.lastOpened = LocalDateTime.now();
    }

    /**
     * Get formatted file size
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Get display name for the file
     */
    public String getDisplayName() {
        if (isRemote) {
            return fileName + " (" + remoteHost + ")";
        }
        return fileName;
    }

    /**
     * Get full path display
     */
    public String getFullPathDisplay() {
        if (isRemote) {
            return String.format("%s@%s:%s", remoteUser, remoteHost, remotePath);
        }
        return filePath;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecentFile that = (RecentFile) o;

        if (isRemote && that.isRemote) {
            return Objects.equals(remoteHost, that.remoteHost) &&
                   remotePort == that.remotePort &&
                   Objects.equals(remoteUser, that.remoteUser) &&
                   Objects.equals(remotePath, that.remotePath);
        } else if (!isRemote && !that.isRemote) {
            return Objects.equals(filePath, that.filePath);
        }

        return false;
    }

    @Override
    public int hashCode() {
        if (isRemote) {
            return Objects.hash(remoteHost, remotePort, remoteUser, remotePath);
        }
        return Objects.hash(filePath);
    }
}
