package com.seeloggyplus.update;

/** Preference codes used by the update subsystem. */
public final class UpdatePreferences {

    public static final String DEFAULT_CHANNEL = "stable";

    public static final String MANIFEST_URL = "update_manifest_url";
    public static final String CHANNEL = "update_channel";
    public static final String LAST_CHECK = "update_last_check";
    public static final String AUTO_CHECK = "update_auto_check";
    public static final String SKIP_PREFIX = "update_skip_";

    private UpdatePreferences() {
    }

    public static String skipKey(String channel) {
        return SKIP_PREFIX + (channel == null || channel.isBlank() ? DEFAULT_CHANNEL : channel);
    }
}
