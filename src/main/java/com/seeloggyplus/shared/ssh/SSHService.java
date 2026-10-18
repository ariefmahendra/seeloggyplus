package com.seeloggyplus.shared.ssh;

import com.seeloggyplus.shared.dto.RemoteFileInfo;
import com.seeloggyplus.shared.progress.ProgressCallback;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import com.seeloggyplus.shared.ssh.SshAuthConfig;

public interface SSHService {

    default void setAuthConfig(SshAuthConfig config) {
    }

    default void trustPendingHostKey() {
    }

    default String getLastConnectError() {
        return null;
    }

    boolean connect(String host, int port, String username, String password);

    boolean connect(String host, int port, String username, String password, long ttlMillis);

    void disconnect();

    void cancelActiveCommand();

    boolean isConnected();

    void tailFile(String remotePath, int lines, Consumer<String> logConsumer, Consumer<String> errorConsumer);

    void stopTailing();

    String readFile(String remotePath) throws IOException;

    List<RemoteFileInfo> listFiles(String remotePath) throws IOException;

    String executeCommand(String command) throws IOException;

    List<String> readFileLines(String remotePath) throws IOException;

    List<String> readFileLines(String remotePath, int lineLimit) throws IOException;

    boolean downloadFile(String remotePath, String localPath);

    boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount,
            ProgressCallback progressCallback);
}
