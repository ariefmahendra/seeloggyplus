package com.seeloggyplus.service;

import java.io.File;
import java.util.function.Consumer;

/**
 * Service specifically for handling local file tailing operations.
 * Encapsulates the complexity of file watching/polling/tailing logic.
 */
public interface TailService {

    /**
     * Starts tailing a local file.
     * <p>
     * This method should normally handle initial context loading (reading the end
     * of the file)
     * before starting the real-time tailer, to provide immediate feedback to the
     * user.
     * </p>
     *
     * @param file         The local file to tail.
     * @param lineConsumer Consumer that will receive each new line (or initial
     *                     lines).
     * @param errorHandler Consumer that will receive any exceptions during tailing.
     */
    void startLocalTail(File file, Consumer<String> lineConsumer, Consumer<Exception> errorHandler,
            boolean loadContext);

    /**
     * Starts tailing a local file with a configurable initial context window.
     *
     * @param file             The local file to tail.
     * @param lineConsumer     Consumer that will receive each new line.
     * @param errorHandler     Consumer that will receive any exceptions.
     * @param loadContext      Whether to load initial context from the end of file.
     * @param contextRows      Number of initial rows to load as context.
     */
    default void startLocalTail(File file, Consumer<String> lineConsumer, Consumer<Exception> errorHandler,
            boolean loadContext, int contextRows) {
        startLocalTail(file, lineConsumer, errorHandler, loadContext);
    }

    /**
     * Stops the currently active tailing process, if any.
     * Cleanly shuts down threads and releases resources.
     */
    void stopTail();

    /**
     * Checks if a tailing process is currently active.
     *
     * @return true if running, false otherwise.
     */
    boolean isRunning();
}
