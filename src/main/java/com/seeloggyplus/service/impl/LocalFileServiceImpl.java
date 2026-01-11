package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.service.LocalFileService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link LocalFileService} for local file system operations.
 * <p>
 * Handles file listing, attribute reading (permissions, ownership), and basic
 * file system verification using NIO.2.
 */
@RequiredArgsConstructor
public class LocalFileServiceImpl implements LocalFileService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileServiceImpl.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHomeDirectory() {
        return System.getProperty("user.home");
    }

    /**
     * Lists files and directories in the specified path.
     * <p>
     * Converts each entry to a {@link FileInfo} object with populated metadata.
     * If attribute reading fails for a specific file, it degrades gracefully
     * by logging a warning and returning partial info.
     *
     * @param directoryPath Absolute path to list
     * @return List of FileInfo objects
     * @throws IOException if path is invalid or inaccessible
     */
    @Override
    public List<FileInfo> listFiles(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);

        if (!Files.exists(path)) {
            throw new IOException("Path does not exist: " + directoryPath);
        }
        if (!Files.isDirectory(path)) {
            throw new IOException("Path is not a directory: " + directoryPath);
        }

        try (Stream<Path> stream = Files.list(path)) {
            return stream
                    .map(this::mapPathToFileInfo)
                    .collect(Collectors.toList());
        } catch (SecurityException e) {
            throw new IOException("Permission denied accessing: " + directoryPath, e);
        }
    }

    /**
     * Maps a {@link Path} to a {@link FileInfo} object, extracting attributes.
     *
     * @param path The file path to process
     * @return Populated FileInfo object
     */
    private FileInfo mapPathToFileInfo(Path path) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setName(path.getFileName().toString());
        fileInfo.setPath(path.toAbsolutePath().toString());
        fileInfo.setSourceType(FileInfo.SourceType.LOCAL);

        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            fileInfo.setDirectory(attrs.isDirectory());
            fileInfo.setSize(attrs.isDirectory() ? 0 : attrs.size());
            fileInfo.setModifiedTime(attrs.lastModifiedTime().toMillis());

            setFileOwner(path, fileInfo);
            fileInfo.setPermissions(getPermissionsString(path));

        } catch (IOException e) {
            logger.warn("Failed to read attributes for file: {}", path, e);
            fileInfo.setDirectory(Files.isDirectory(path));
            fileInfo.setPermissions("???");
            fileInfo.setOwner("?");
        }

        return fileInfo;
    }

    /**
     * Attempts to set the file owner.
     * Gracefully handles cases where owner attribute is not supported or
     * accessible.
     */
    private void setFileOwner(Path path, FileInfo fileInfo) {
        try {
            UserPrincipal owner = Files.getOwner(path);
            fileInfo.setOwner(owner.getName());
        } catch (Exception e) {
            fileInfo.setOwner("-");
        }
    }

    /**
     * formatted permission string (e.g., "rwxr-xr-x" or "rw-r--r--").
     * Falls back to simple r/w/x check if POSIX attributes are not supported (e.g.
     * Windows).
     *
     * @param path File path
     * @return Permission string
     */
    private String getPermissionsString(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            return PosixFilePermissions.toString(perms);
        } catch (UnsupportedOperationException e) {
            return (Files.isReadable(path) ? "r" : "-") +
                    (Files.isWritable(path) ? "w" : "-") +
                    (Files.isExecutable(path) ? "x" : "-");
        } catch (IOException e) {
            return "---------";
        }
    }
}