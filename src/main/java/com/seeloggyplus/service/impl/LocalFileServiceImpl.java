package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.service.LocalFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class LocalFileServiceImpl implements LocalFileService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileServiceImpl.class);

    public LocalFileServiceImpl() {
    }

    @Override
    public String getHomeDirectory() {
        return System.getProperty("user.home");
    }

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

            try {
                UserPrincipal owner = Files.getOwner(path);
                fileInfo.setOwner(owner.getName());
            } catch (Exception e) {
                fileInfo.setOwner("-");
            }

            fileInfo.setPermissions(getPermissionsString(path));
        } catch (IOException e) {
            logger.warn("Failed to read attributes for file: {}", path, e);
            fileInfo.setDirectory(Files.isDirectory(path));
            fileInfo.setPermissions("???");
            fileInfo.setOwner("?");
        }

        return fileInfo;
    }

    private String getPermissionsString(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            return PosixFilePermissions.toString(perms);
        } catch (UnsupportedOperationException e) {
            return (Files.isReadable(path) ? "r" : "-") + (Files.isWritable(path) ? "w" : "-") + (Files.isExecutable(path) ? "x" : "-");
        } catch (IOException e) {
            return "---------";
        }
    }
}