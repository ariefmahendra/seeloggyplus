package com.seeloggyplus.shared.util;

import java.util.regex.Pattern;

/** Shared checks for server log locations; a filename never establishes local provenance. */
public final class RemoteLogPaths {

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final String RECOVERY = "Open File Manager, select the original file on your server, "
            + "and choose Tail to follow new log entries.";

    public static final String LOCAL_LOCATION_MESSAGE =
            "The saved file location points to this computer instead of your server.\n\n" + RECOVERY;
    public static final String MISSING_LOCATION_MESSAGE =
            "The file's location on the server is missing.\n\n" + RECOVERY;

    private RemoteLogPaths() {
    }

    public static boolean isWindowsLocalPath(String path) {
        return path != null && (WINDOWS_DRIVE.matcher(path).matches() || path.contains("\\"));
    }
}
