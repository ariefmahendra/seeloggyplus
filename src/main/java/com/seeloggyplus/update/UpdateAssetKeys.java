package com.seeloggyplus.update;

import java.nio.file.Files;

/**
 * Resolves which manifest asset key fits the running client.
 * <pre>
 * windows-nojre | windows-jre | linux-nojre | linux-jre
 * </pre>
 */
public final class UpdateAssetKeys {

    public static final String WINDOWS_NOJRE = "windows-nojre";
    public static final String WINDOWS_JRE = "windows-jre";
    public static final String LINUX_NOJRE = "linux-nojre";
    public static final String LINUX_JRE = "linux-jre";

    private UpdateAssetKeys() {
    }

    public static String preferred() {
        return forPlatform(isWindows(), hasBundledJre());
    }

    public static String forPlatform(boolean windows, boolean hasJre) {
        return (windows ? "windows" : "linux") + (hasJre ? "-jre" : "-nojre");
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** True when the installation ships its own {@code jre/} folder. */
    public static boolean hasBundledJre() {
        try {
            return Files.isDirectory(UpdateLayout.installationRoot().resolve("jre"));
        } catch (Exception e) {
            return false;
        }
    }
}
