package com.seeloggyplus.service;

import com.seeloggyplus.service.impl.LogParserService;
import com.seeloggyplus.service.impl.SSHServiceImpl;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface SSHService {
    boolean connect(String host, int port, String username, String password);
    boolean connect(String host, int port, String username, String password, long ttlMillis);
    void disconnect();
    boolean isConnected();
    void tailFile(String remotePath, int lines, Consumer<String> logConsumer, Consumer<String> errorConsumer);
    void stopTailing();
    String readFile(String remotePath) throws IOException;
    List<SSHServiceImpl.RemoteFileInfo> listFiles(String remotePath) throws IOException;
    String executeCommand(String command) throws IOException;
    List<String> readFileLines(String remotePath) throws IOException;
    boolean downloadFile(String remotePath, String localPath);
    boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount, LogParserService.ProgressCallback progressCallback);
}
