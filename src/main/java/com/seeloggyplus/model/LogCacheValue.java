package com.seeloggyplus.model;

import java.util.List;

public record LogCacheValue(
        ParsingConfig config,
        List<LogEntry> entries
) {
}
