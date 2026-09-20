package com.seeloggyplus.update;

import java.io.IOException;
import java.util.Optional;

/**
 * Activates staged versions, applies health markers and performs rollback.
 * Kept free of UI so it can run in a bootstrapper process or in tests.
 */
public class UpdateBootstrapper {

    private final UpdateLayout layout;

    public UpdateBootstrapper(UpdateLayout layout) {
        this.layout = layout;
    }

    /** Activates a staged version, remembering the current one for rollback. */
    public void activate(String version) throws UpdateException {
        if (!layout.isStaged(version)) {
            throw new UpdateException("Version is not staged: " + version);
        }
        try {
            Optional<String> current = layout.currentVersion();
            if (current.isPresent() && !current.get().equals(version)) {
                layout.writePrevious(current.get());
            }
            layout.writeCurrent(version);
        } catch (IOException e) {
            throw new UpdateException("Failed to activate version " + version + ": " + e.getMessage(), e);
        }
    }

    public void markHealthy(String version) throws IOException {
        layout.markHealthy(version);
    }

    /**
     * True when the active version is missing or has not reported a healthy start,
     * while a usable previous version exists.
     */
    public boolean shouldRollback() throws IOException {
        Optional<String> current = layout.currentVersion();
        if (current.isEmpty()) {
            return false;
        }
        if (!layout.isStaged(current.get())) {
            return true;
        }
        if (layout.isHealthy(current.get())) {
            return false;
        }
        Optional<String> previous = layout.previousVersion();
        return previous.isPresent() && layout.isStaged(previous.get());
    }

    /** Switches back to the previous version. @return the version restored, if any. */
    public Optional<String> rollback() throws IOException {
        Optional<String> previous = layout.previousVersion();
        if (previous.isEmpty() || !layout.isStaged(previous.get())) {
            return Optional.empty();
        }
        layout.writeCurrent(previous.get());
        return previous;
    }

    public int cleanup(int keep) throws IOException {
        return layout.cleanup(keep);
    }
}
