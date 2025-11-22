package com.seeloggyplus.util;

/**
 * Utility class for file operations and formatting
 */
public class FileUtils {

    /**
     * Format file size from bytes to human readable format
     *
     * @param bytes Size in bytes
     * @return Human readable size (e.g., "1.5 MB", "256 KB", "10.2 GB")
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Format file size from string bytes to human readable format
     *
     * @param sizeStr Size in bytes as string
     * @return Human readable size or original string if parsing fails
     */
    public static String formatFileSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            return "0 B";
        }

        try {
            long bytes = Long.parseLong(sizeStr);
            return formatFileSize(bytes);
        } catch (NumberFormatException e) {
            return sizeStr;
        }
    }
}

