package com.seeloggyplus.service;

import com.seeloggyplus.model.PreviewLine;
import com.seeloggyplus.model.RemoteLogSearchMatch;

import java.io.IOException;
import java.util.List;

/**
 * Searches remote log files by content and loads line context for previews.
 * Implementations must be safe to cancel via {@link SSHService#disconnect()} /
 * {@code SSHServiceImpl#cancelActiveCommand()}.
 */
public interface RemoteLogSearchService {

    /**
     * Finds matching lines inside log-like files under {@code rootPath}.
     *
     * @return matches ordered by file, then line number
     */
    List<RemoteLogSearchMatch> search(SSHService ssh, String rootPath, String query,
                                      boolean regex, boolean caseSensitive) throws IOException;

    /**
     * Loads {@code before} and {@code after} lines around {@code centerLine} for preview.
     * The returned list always uses absolute 1-based line numbers and marks the target line.
     */
    List<PreviewLine> preview(SSHService ssh, String path, int centerLine,
                              int before, int after) throws IOException;

    /**
     * Returns the number of lines of a remote file, or {@code -1} when it cannot be
     * determined. Used to decide whether a match is reachable in tail mode.
     */
    long lineCount(SSHService ssh, String path) throws IOException;
}
