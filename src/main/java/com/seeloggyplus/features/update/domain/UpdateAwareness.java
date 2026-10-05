package com.seeloggyplus.features.update.domain;

/**
 * Pure scheduling/notification policy for update awareness, kept free of UI and
 * preferences so it can be unit tested.
 */
public final class   UpdateAwareness {

    /** How often the app re-checks for updates (at startup and while running). */
    public static final long CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000;

    /** Snooze choices offered by the update dialog. */
    public enum Snooze {
        ONE_HOUR,
        EIGHT_HOURS,
        TOMORROW
    }

    /** What the status-bar update indicator is currently showing. */
    public enum IndicatorState {
        NONE,
        AVAILABLE,
        DOWNLOADING,
        READY,
        FAILED
    }

    private UpdateAwareness() {
    }

    /** Status-bar text for the given indicator state ({@code version} may be null for NONE). */
    public static String indicatorText(IndicatorState state, String version, int percent) {
        if (state == null || state == IndicatorState.NONE || version == null || version.isBlank()) {
            return "";
        }
        return switch (state) {
            case AVAILABLE -> "\u2191 Update " + version + " available";
            case DOWNLOADING -> "\u2193 Downloading update " + version + " ("
                    + Math.max(0, Math.min(100, percent)) + "%)";
            case READY -> "\u21bb Restart to update to " + version;
            case FAILED -> "\u26a0 Update " + version + " failed - click for details";
            case NONE -> "";
        };
    }

    /** True when a new check is allowed now (interval elapsed and not snoozed). */
    public static boolean isCheckDue(long now, long lastCheck, long snoozeUntil, long intervalMillis) {
        return now >= lastCheck + intervalMillis && now >= snoozeUntil;
    }

    /** True when the update dialog may pop up (not skipped for this version, not snoozed). */
    public static boolean shouldPopup(long now, long snoozeUntil, String latest, String skippedVersion) {
        return latest != null && !latest.isBlank()
                && !latest.equals(skippedVersion)
                && now >= snoozeUntil;
    }

    public static long snoozeMillis(Snooze snooze) {
        if (snooze == null) {
            return 0;
        }
        return switch (snooze) {
            case ONE_HOUR -> 60L * 60 * 1000;
            case EIGHT_HOURS -> 8L * 60 * 60 * 1000;
            case TOMORROW -> 24L * 60 * 60 * 1000;
        };
    }
}
