package com.seeloggyplus.update;

/** Preference codes used by the update subsystem. */
public final class UpdatePreferences {

    public static final String DEFAULT_CHANNEL = "stable";

    public static final String MANIFEST_URL = "update_manifest_url";
    public static final String CHANNEL = "update_channel";
    public static final String LAST_CHECK = "update_last_check";
    public static final String AUTO_CHECK = "update_auto_check";
    public static final String SNOOZE_UNTIL = "update_snooze_until";
    public static final String AUTO_DOWNLOAD = "update_auto_download";
    /** Last application version whose "What's New" dialog was shown. */
    public static final String SEEN_VERSION = "update_seen_version";
    public static final String SKIP_PREFIX = "update_skip_";

    private UpdatePreferences() {
    }

    public static String skipKey(String channel) {
        return SKIP_PREFIX + (channel == null || channel.isBlank() ? DEFAULT_CHANNEL : channel);
    }
}
