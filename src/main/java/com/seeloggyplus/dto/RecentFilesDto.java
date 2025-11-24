package com.seeloggyplus.dto;

import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.ParsingConfig;

public record RecentFilesDto(
        LogFile logFile,
        ParsingConfig parsingConfig,
        String serverName
) {}
