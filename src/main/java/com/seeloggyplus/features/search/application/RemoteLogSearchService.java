package com.seeloggyplus.features.search.application;

import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.model.PreviewLine;
import com.seeloggyplus.features.search.domain.RemoteLogSearchMatch;

import java.io.IOException;
import java.util.List;

public interface RemoteLogSearchService {

    List<RemoteLogSearchMatch> search(SSHService ssh, String rootPath, String query,
                                      boolean regex, boolean caseSensitive) throws IOException;

    List<PreviewLine> preview(SSHService ssh, String path, int centerLine,
                              int before, int after) throws IOException;

    long lineCount(SSHService ssh, String path) throws IOException;
}
