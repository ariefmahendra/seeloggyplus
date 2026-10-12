package com.seeloggyplus.shared.util;

public class FileUtils {

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
