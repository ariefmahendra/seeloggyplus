package com.seeloggyplus.service;

import com.seeloggyplus.model.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for local file access
 */
public class LocalFileService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileService.class);

    /**
     * List files in a local directory
     */
    public List<FileInfo> listFiles(String path) {
        List<FileInfo> files = new ArrayList<>();
        File directory = new File(path);

        if (!directory.exists() || !directory.isDirectory()) {
            logger.error("Path is not a directory: {}", path);
            return files;
        }

        File[] fileList = directory.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                try {
                    FileInfo fileInfo = new FileInfo();
                    fileInfo.setName(file.getName());
                    fileInfo.setPath(file.getAbsolutePath());
                    fileInfo.setDirectory(file.isDirectory());
                    fileInfo.setSize(file.length());
                    fileInfo.setModifiedTime(file.lastModified());
                    fileInfo.setSourceType(FileInfo.SourceType.LOCAL);

                    // Try to get more details if possible
                    try {
                        Path filePath = file.toPath();
                        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
                        // We could use creation time etc if needed

                        // Try to get permissions on POSIX systems
                        try {
                            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(filePath);
                            fileInfo.setPermissions(formatPermissions(perms));
                        } catch (UnsupportedOperationException e) {
                            // Not a POSIX system, ignore
                            fileInfo.setPermissions(file.canRead() ? "r"
                                    : "-" + (file.canWrite() ? "w" : "-") + (file.canExecute() ? "x" : "-"));
                        }
                    } catch (IOException e) {
                        // Ignore attribute errors
                    }

                    files.add(fileInfo);
                } catch (Exception e) {
                    logger.warn("Error processing file: " + file.getName(), e);
                }
            }
        }

        return files;
    }

    /**
     * Get system root directories
     */
    public List<FileInfo> getRoots() {
        List<FileInfo> roots = new ArrayList<>();
        File[] fileRoots = File.listRoots();

        if (fileRoots != null) {
            for (File root : fileRoots) {
                FileInfo fileInfo = new FileInfo();
                String path = root.getAbsolutePath();
                fileInfo.setName(path);
                fileInfo.setPath(path);
                fileInfo.setDirectory(true);
                fileInfo.setSourceType(FileInfo.SourceType.LOCAL);
                roots.add(fileInfo);
            }
        }
        return roots;
    }

    /**
     * Get user home directory
     */
    public String getHomeDirectory() {
        return System.getProperty("user.home");
    }

    private String formatPermissions(Set<PosixFilePermission> perms) {
        StringBuilder sb = new StringBuilder();
        sb.append(perms.contains(PosixFilePermission.OWNER_READ) ? "r" : "-");
        sb.append(perms.contains(PosixFilePermission.OWNER_WRITE) ? "w" : "-");
        sb.append(perms.contains(PosixFilePermission.OWNER_EXECUTE) ? "x" : "-");
        sb.append(perms.contains(PosixFilePermission.GROUP_READ) ? "r" : "-");
        sb.append(perms.contains(PosixFilePermission.GROUP_WRITE) ? "w" : "-");
        sb.append(perms.contains(PosixFilePermission.GROUP_EXECUTE) ? "x" : "-");
        sb.append(perms.contains(PosixFilePermission.OTHERS_READ) ? "r" : "-");
        sb.append(perms.contains(PosixFilePermission.OTHERS_WRITE) ? "w" : "-");
        sb.append(perms.contains(PosixFilePermission.OTHERS_EXECUTE) ? "x" : "-");
        return sb.toString();
    }
}
