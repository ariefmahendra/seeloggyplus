package com.seeloggyplus.service;

import com.seeloggyplus.dto.RemoteFileInfo;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service interface for SSH operations.
 * <p>
 * Provides functionality for remote connection, command execution,
 * file system manipulation, and real-time log tailing over SSH.
 */
public interface SSHService {

    /**
     * Connects to a remote SSH server.
     *
     * @param host     Remote host address.
     * @param port     SSH port (default 22).
     * @param username SSH username.
     * @param password SSH password.
     * @return True if connection successful, false otherwise.
     */
    boolean connect(String host, int port, String username, String password);

    /**
     * Connects to a remote SSH server with a specific Time-To-Live.
     *
     * @param host      Remote host address.
     * @param port      SSH port.
     * @param username  SSH username.
     * @param password  SSH password.
     * @param ttlMillis Session time-to-live in milliseconds.
     * @return True if connection successful.
     */
    boolean connect(String host, int port, String username, String password, long ttlMillis);

    /**
     * Disconnects the current session and stops active tasks.
     */
    void disconnect();

    /**
     * Checks if currently connected.
     *
     * @return True if connected.
     */
    boolean isConnected();

    /**
     * Tails a remote file in real-time.
     *
     * @param remotePath    Path to the remote file.
     * @param lines         Number of initial lines to fetch.
     * @param logConsumer   Callback for new log lines.
     * @param errorConsumer Callback for errors.
     */
    void tailFile(String remotePath, int lines, Consumer<String> logConsumer, Consumer<String> errorConsumer);

    /**
     * Stops any active tailing operation.
     */
    void stopTailing();

    /**
     * Reads the entire content of a remote file.
     *
     * @param remotePath Path to the remote file.
     * @return File content as a String.
     * @throws IOException If read fails.
     */
    String readFile(String remotePath) throws IOException;

    /**
     * Lists files and directories in a remote path.
     *
     * @param remotePath Path to list.
     * @return List of {@link RemoteFileInfo} objects.
     * @throws IOException If listing fails.
     */
    List<RemoteFileInfo> listFiles(String remotePath) throws IOException;

    /**
     * Executes a command on the remote server.
     *
     * @param command Command to execute.
     * @return Command output (stdout).
     * @throws IOException If execution fails.
     */
    String executeCommand(String command) throws IOException;

    /**
     * Reads all lines from a remote file.
     *
     * @param remotePath Path to the remote file.
     * @return List of lines.
     * @throws IOException If read fails.
     */
    List<String> readFileLines(String remotePath) throws IOException;

    /**
     * Reads a limited number of lines from the beginning of a remote file.
     *
     * @param remotePath Path to the remote file.
     * @param lineLimit  Maximum lines to read.
     * @return List of lines.
     * @throws IOException If read fails.
     */
    List<String> readFileLines(String remotePath, int lineLimit) throws IOException;

    /**
     * Downloads a file from remote to local.
     *
     * @param remotePath Path to the remote file.
     * @param localPath  Destination local path.
     * @return True if successful.
     */
    boolean downloadFile(String remotePath, String localPath);

    /**
     * Downloads a file concurrently using multiple threads.
     *
     * @param remotePath       Path to the remote file.
     * @param localPath        Destination local path.
     * @param threadCount      Number of concurrent threads.
     * @param progressCallback Callback for progress reporting.
     * @return True if successful.
     */
    boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount,
            LogParser.ProgressCallback progressCallback);
}
