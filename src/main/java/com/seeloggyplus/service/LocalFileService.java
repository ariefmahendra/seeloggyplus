package com.seeloggyplus.service;

import com.seeloggyplus.model.FileInfo;

import java.io.IOException;
import java.util.List;

public interface LocalFileService {
    String getHomeDirectory();
    List<FileInfo> listFiles(String directoryPath) throws IOException;
}
