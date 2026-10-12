package com.seeloggyplus.features.update.domain;

public final class   UpdateAwareness {

    public static final long CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000;

    public enum Snooze {
        ONE_HOUR,
        EIGHT_HOURS,
        TOMORROW
    }

    public enum IndicatorState {
        NONE,
        AVAILABLE,
        DOWNLOADING,
        READY,
        FAILED
    }

    private UpdateAwareness() {
    }

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

    public static boolean isCheckDue(long now, long lastCheck, long snoozeUntil, long intervalMillis) {
        return now >= lastCheck + intervalMillis && now >= snoozeUntil;
    }

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
