package com.seeloggyplus.model;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Generic file model representing both local and remote files
 */
public class FileInfo implements Comparable<FileInfo> {
    private String name;
    private String path;
    private long size;
    private boolean isDirectory;
    private long modifiedTime;
    private String permissions;
    private String owner;
    private SourceType sourceType;

    public enum SourceType {
        LOCAL,
        REMOTE
    }

    public FileInfo() {
    }

    public FileInfo(String name, String path, long size, boolean isDirectory, long modifiedTime,
            SourceType sourceType) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.isDirectory = isDirectory;
        this.modifiedTime = modifiedTime;
        this.sourceType = sourceType;
    }

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

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDateTime getModified() {
        if (modifiedTime > 0) {
            return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(modifiedTime),
                    ZoneId.systemDefault());
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
        String lowerName = name != null ? name.toLowerCase() : "";
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
        if (isDirectory || name == null) {
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
    public int compareTo(FileInfo other) {
        // Directories first
        if (this.isDirectory && !other.isDirectory) {
            return -1;
        }
        if (!this.isDirectory && other.isDirectory) {
            return 1;
        }
        // Then by name
        if (this.name != null && other.name != null) {
            return this.name.compareToIgnoreCase(other.name);
        }
        return 0;
    }

    @Override
    public String toString() {
        return name + (isDirectory ? " [DIR]" : " (" + getFormattedSize() + ")");
    }
}
