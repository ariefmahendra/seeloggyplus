package com.seeloggyplus.features.files.infrastructure;

import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.features.files.application.LocalFileService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    private FileInfo mapPathToFileInfo(Path path) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setName(path.getFileName().toString());
        fileInfo.setPath(path.toAbsolutePath().toString());
        fileInfo.setSourceType(FileInfo.SourceType.LOCAL);

        try {
            if (IS_POSIX) {
                PosixFileAttributes posix = Files.readAttributes(path, PosixFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                fileInfo.setDirectory(posix.isDirectory());
                fileInfo.setSize(posix.isDirectory() ? 0 : posix.size());
                fileInfo.setModifiedTime(posix.lastModifiedTime().toMillis());
                fileInfo.setPermissions(PosixFilePermissions.toString(posix.permissions()));
                fileInfo.setOwner(posix.owner() != null ? posix.owner().getName() : "-");
            } else {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                fileInfo.setDirectory(attrs.isDirectory());
                fileInfo.setSize(attrs.isDirectory() ? 0 : attrs.size());
                fileInfo.setModifiedTime(attrs.lastModifiedTime().toMillis());
                fileInfo.setPermissions("-");
                try {
                    UserPrincipal owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS);
                    fileInfo.setOwner(owner != null ? owner.getName() : "-");
                } catch (Exception e) {
                    fileInfo.setOwner("-");
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to read attributes for file: {}", path, e);
            fileInfo.setDirectory(Files.isDirectory(path));
            fileInfo.setPermissions("-");
            fileInfo.setOwner("-");
        }

        return fileInfo;
    }

    private String getPermissionsStringFast(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            return PosixFilePermissions.toString(perms);
        } catch (IOException e) {
            return "---------";
        }
    }
}
