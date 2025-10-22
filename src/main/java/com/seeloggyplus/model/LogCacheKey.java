package com.seeloggyplus.model;

public record LogCacheKey(
        String filePath,
        long lastModified,
        long fileSize,
        String configId
) {
}
