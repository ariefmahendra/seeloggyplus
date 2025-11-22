package com.seeloggyplus.service.impl;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * File watcher service using Java NIO WatchService
 * Monitors log files for changes and notifies listeners (like 'tail -f' in Linux)
 *
 * Based on: https://docs.oracle.com/javase/tutorial/essential/io/notification.html
 */
public class LogFileWatcher {

    private static final Logger logger = LoggerFactory.getLogger(LogFileWatcher.class);

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;

    // Map of watched files to their listeners
    private final Map<Path, FileChangeListener> listeners = new HashMap<>();

    // Map of WatchKey to directory path
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();

    /**
     * Callback interface for file change notifications
     */
    @FunctionalInterface
    public interface FileChangeListener {
        /**
         * Called when the watched file is modified
         *
         * @param file The file that was modified
         * @param eventKind The type of event (ENTRY_MODIFY, ENTRY_CREATE, etc.)
         */
        void onFileChanged(File file, WatchEvent.Kind<?> eventKind);
    }

    /**
     * Start the file watcher service
     */
    public void start() throws IOException {
        if (running) {
            logger.warn("LogFileWatcher is already running");
            return;
        }

        watchService = FileSystems.getDefault().newWatchService();
        running = true;

        watchThread = new Thread(this::watchLoop, "LogFileWatcher-Thread");
        watchThread.setDaemon(true);
        watchThread.start();

        logger.info("LogFileWatcher started successfully");
    }

    /**
     * Stop the file watcher service
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (watchThread != null) {
            watchThread.interrupt();
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Error closing watch service", e);
            }
        }

        listeners.clear();
        watchKeys.clear();

        logger.info("LogFileWatcher stopped");
    }

    /**
     * Watch a specific file for changes
     *
     * @param file The file to watch
     * @param listener The listener to notify on changes
     */
    public void watchFile(File file, FileChangeListener listener) throws IOException {
        if (!running) {
            throw new IllegalStateException("LogFileWatcher is not running. Call start() first.");
        }

        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }

        // Watch the parent directory (WatchService watches directories, not files)
        Path directoryPath = file.toPath().getParent();
        Path filePath = file.toPath();

        synchronized (listeners) {
            // Check if directory is already being watched
            boolean directoryWatched = watchKeys.values().stream()
                .anyMatch(p -> p.equals(directoryPath));

            if (!directoryWatched) {
                // Register directory for MODIFY and CREATE events
                WatchKey key = directoryPath.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE
                );

                watchKeys.put(key, directoryPath);
                logger.info("Registered directory for watching: {}", directoryPath);
            }

            // Add listener for this specific file
            listeners.put(filePath, listener);
            logger.info("Added file watcher for: {}", file.getName());
        }
    }

    /**
     * Stop watching a specific file
     *
     * @param file The file to stop watching
     */
    public void unwatchFile(File file) {
        Path filePath = file.toPath();

        synchronized (listeners) {
            listeners.remove(filePath);
            logger.info("Removed file watcher for: {}", file.getName());
        }
    }

    /**
     * Main watch loop that processes file system events
     */
    private void watchLoop() {
        logger.info("Watch loop started");

        while (running) {
            try {
                // Wait for events (with timeout to allow checking 'running' flag)
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);

                if (key == null) {
                    continue; // Timeout, check if still running
                }

                Path directory = watchKeys.get(key);

                if (directory == null) {
                    logger.warn("WatchKey not recognized, ignoring events");
                    key.reset();
                    continue;
                }

                // Process all events for this key
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // Skip overflow events
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        logger.warn("WatchEvent OVERFLOW - some events may have been lost");
                        continue;
                    }

                    // Get the filename from the event
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path fullPath = directory.resolve(filename);

                    // Check if this file has a listener
                    synchronized (listeners) {
                        FileChangeListener listener = listeners.get(fullPath);

                        if (listener != null) {
                            File modifiedFile = fullPath.toFile();

                            logger.debug("File change detected: {} - Event: {}",
                                modifiedFile.getName(), kind.name());

                            // Notify listener on JavaFX Application Thread
                            Platform.runLater(() -> {
                                try {
                                    listener.onFileChanged(modifiedFile, kind);
                                } catch (Exception e) {
                                    logger.error("Error in file change listener", e);
                                }
                            });
                        }
                    }
                }

                // Reset the key - important!
                boolean valid = key.reset();
                if (!valid) {
                    logger.warn("WatchKey no longer valid, removing from map");
                    watchKeys.remove(key);
                }

            } catch (InterruptedException e) {
                logger.info("Watch loop interrupted, stopping...");
                break;
            } catch (Exception e) {
                logger.error("Error in watch loop", e);
            }
        }

        logger.info("Watch loop stopped");
    }

    /**
     * Check if the watcher is currently running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get number of files currently being watched
     */
    public int getWatchedFileCount() {
        synchronized (listeners) {
            return listeners.size();
        }
    }
}

