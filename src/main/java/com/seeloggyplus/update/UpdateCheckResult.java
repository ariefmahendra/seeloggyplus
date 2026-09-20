package com.seeloggyplus.update;

/** Result of an update check. */
public record UpdateCheckResult(Status status, UpdateManifest manifest, String currentVersion, String message) {

    public enum Status {
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        FORCED,
        ERROR
    }

    public static UpdateCheckResult upToDate(String current, UpdateManifest manifest) {
        return new UpdateCheckResult(Status.UP_TO_DATE, manifest, current, "You are on the latest version");
    }

    public static UpdateCheckResult updateAvailable(String current, UpdateManifest manifest) {
        return new UpdateCheckResult(Status.UPDATE_AVAILABLE, manifest, current,
                "Version " + manifest.latest() + " is available");
    }

    public static UpdateCheckResult forced(String current, UpdateManifest manifest) {
        return new UpdateCheckResult(Status.FORCED, manifest, current,
                "Version " + manifest.latest() + " is required");
    }

    public static UpdateCheckResult error(String current, String message) {
        return new UpdateCheckResult(Status.ERROR, null, current, message);
    }

    public boolean hasUpdate() {
        return status == Status.UPDATE_AVAILABLE || status == Status.FORCED;
    }

    public boolean isForced() {
        return status == Status.FORCED;
    }
}
