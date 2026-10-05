package com.seeloggyplus.features.recent.domain;

import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.model.ParsingConfig;

/**
 * A recent-file row plus the mode it was last opened in.
 *
 * <p>{@code openMode} is either {@link com.seeloggyplus.features.recent.domain.RecentFile#MODE_OPEN}
 * (normal view / downloaded copy) or {@link com.seeloggyplus.features.recent.domain.RecentFile#MODE_TAIL}
 * (live streaming). The 3-argument constructor is retained for callers/tests that do
 * not care about the mode; it defaults to {@code null} and is treated as OPEN.
 */
public record RecentFilesDto(
        LogFile logFile,
        ParsingConfig parsingConfig,
        String serverName,
        String openMode
) {

    public RecentFilesDto(LogFile logFile, ParsingConfig parsingConfig, String serverName) {
        this(logFile, parsingConfig, serverName, null);
    }
}
