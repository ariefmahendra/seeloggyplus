package com.seeloggyplus.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.WatchEvent;

/**
 * Service interface for watching file system changes.
 * <p>
 * Provides a high-level abstraction over the native file system watcher (NIO
 * WatchService),
 * allowing components to subscribe to file modification events (create,
 * modify).
 */
public interface FileWatcher {

    /**
     * Functional interface for handling file change events.
     */
    @FunctionalInterface
    interface FileChangeListener {
        /**
         * Invoked when a watched file is changed.
         *
         * @param file      The file that triggered the event.
         * @param eventKind The type of event (ENTRY_CREATE, ENTRY_MODIFY, etc.).
         */
        void onFileChanged(File file, WatchEvent.Kind<?> eventKind);
    }

    /**
     * Starts the asynchronous watcher service.
     * Must be called before watching any files.
     *
     * @throws IOException if the underlying WatchService cannot be initialized.
     */
    void start() throws IOException;

    /**
     * Stops the watcher service and releases all resources (threads, file handles).
     * It is safe to call this method multiple times.
     */
    void stop();

    /**
     * Registers a specific file to be watched.
     * <p>
     * Note: Most implementations watch the parent directory and filter events for
     * the specific file.
     *
     * @param file     The file to watch (must exist).
     * @param listener The callback to invoke on changes.
     * @throws IOException           if the file cannot be accessed or watched.
     * @throws IllegalStateException if the watcher is not started.
     */
    void watchFile(File file, FileChangeListener listener) throws IOException;

    /**
     * Unregisters a file from being watched.
     * No-op if the file is not currently watched.
     *
     * @param file The file to stop watching.
     */
    void unwatchFile(File file);

    /**
     * Checks if the watcher service is currently active.
     *
     * @return true if running, false otherwise.
     */
    boolean isRunning();

    /**
     * Returns the count of unique files currently being watched.
     *
     * @return Number of watched files.
     */
    int getWatchedFileCount();
}
