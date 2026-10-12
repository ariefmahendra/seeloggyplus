package com.seeloggyplus.features.recent.domain;

import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.model.ParsingConfig;

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
