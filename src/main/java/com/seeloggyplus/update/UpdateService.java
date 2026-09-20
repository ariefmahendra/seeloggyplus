package com.seeloggyplus.update;

/** Checks the remote manifest for a newer application version. */
public interface UpdateService {

    /** Preferred asset key used when the caller has no platform preference. */
    String DEFAULT_ASSET_KEY = "portable-nojre";

    /**
     * Performs a single update check. Implementations must never throw; failures are
     * reported as {@link UpdateCheckResult.Status#ERROR}.
     *
     * @param channel release channel to check (e.g. {@code stable} or {@code beta})
     */
    UpdateCheckResult check(String channel);
}
