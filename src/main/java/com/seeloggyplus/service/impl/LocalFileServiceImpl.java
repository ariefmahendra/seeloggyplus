package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.service.LocalFileService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Implementation of {@link LocalFileService} for local file system operations.
 * <p>
 * Optimized for performance: uses DirectoryStream instead of Files.list(),
 * reads only BasicFileAttributes in a single syscall per file, and detects
 * the OS once to avoid repeated POSIX permission checks on Windows.
 */
@RequiredArgsConstructor
public class LocalFileServiceImpl implements LocalFileService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileServiceImpl.class);
    private static final boolean IS_POSIX = isPosixFileSystem();

    private static boolean isPosixFileSystem() {
        try {
            return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getHomeDirectory() {
        return System.getProperty("user.home");
    }

    /**
     * Lists files and directories in the specified path.
     * <p>
     * Uses DirectoryStream for lower overhead than Files.list()/Stream.
     * Reads only BasicFileAttributes (single syscall per file).
     * Skips expensive owner lookup and permission checks for faster listing.
     */
    @Override
    public List<FileInfo> listFiles(String directoryPath) throws IOException {
        Path dirPath = Paths.get(directoryPath);

        if (!Files.exists(dirPath)) {
            throw new IOException("Path does not exist: " + directoryPath);
        }
        if (!Files.isDirectory(dirPath)) {
            throw new IOException("Path is not a directory: " + directoryPath);
        }

        List<FileInfo> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            for (Path entry : stream) {
                result.add(mapPathToFileInfo(entry));
            }
        } catch (SecurityException e) {
            throw new IOException("Permission denied accessing: " + directoryPath, e);
        }
        return result;
    }

    /**
     * Maps a Path to FileInfo using a single readAttributes call.
     * Owner and permissions are loaded lazily (set to defaults here).
     */
    private FileInfo mapPathToFileInfo(Path path) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setName(path.getFileName().toString());
        fileInfo.setPath(path.toAbsolutePath().toString());
        fileInfo.setSourceType(FileInfo.SourceType.LOCAL);

        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            fileInfo.setDirectory(attrs.isDirectory());
            fileInfo.setSize(attrs.isDirectory() ? 0 : attrs.size());
            fileInfo.setModifiedTime(attrs.lastModifiedTime().toMillis());
            // Skip owner and permissions for speed — set lightweight defaults
            fileInfo.setOwner("-");
            fileInfo.setPermissions(IS_POSIX ? getPermissionsStringFast(path) : "-");
        } catch (IOException e) {
            logger.warn("Failed to read attributes for file: {}", path, e);
            fileInfo.setDirectory(Files.isDirectory(path));
            fileInfo.setPermissions("-");
            fileInfo.setOwner("-");
        }

        return fileInfo;
    }

    /**
     * Fast POSIX permission string — only called on POSIX systems.
     */
    private String getPermissionsStringFast(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            return PosixFilePermissions.toString(perms);
        } catch (IOException e) {
            return "---------";
        }
    }
}