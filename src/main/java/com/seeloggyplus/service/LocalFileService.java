package com.seeloggyplus.service;

import com.seeloggyplus.model.FileInfo;

import java.io.IOException;
import java.util.List;

/**
 * Service interface for local file system operations.
 * <p>
 * Provides an abstraction for accessing local files and directories,
 * retrieving metadata, and platform-specific file attributes.
 */
public interface LocalFileService {

    /**
     * Gets the current user's home directory.
     *
     * @return Absolute path to the user's home directory.
     */
    String getHomeDirectory();

    /**
     * Lists all files and directories within a specified path.
     *
     * @param directoryPath The absolute path of the directory to list.
     * @return List of FileInfo objects representing content of the directory.
     * @throws IOException If the path does not exist, is not a directory, or cannot
     *                     be accessed.
     */
    List<FileInfo> listFiles(String directoryPath) throws IOException;
}
