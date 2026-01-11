package com.seeloggyplus.service.impl;

import com.seeloggyplus.service.FileWatcher;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * NIO-based implementation of {@link FileWatcher}.
 * <p>
 * Uses Java's {@link WatchService} to monitor file system events.
 * Notifications are dispatched to listeners on the JavaFX Application Thread.
 */
@RequiredArgsConstructor
public class LogFileWatcherImpl implements FileWatcher {

    private static final Logger logger = LoggerFactory.getLogger(LogFileWatcherImpl.class);

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;
    private final Map<Path, FileChangeListener> listeners = new HashMap<>();
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
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
     * {@inheritDoc}
     */
    @Override
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
     * {@inheritDoc}
     */
    @Override
    public void watchFile(File file, FileChangeListener listener) throws IOException {
        if (!running) {
            throw new IllegalStateException("LogFileWatcher is not running. Call start() first.");
        }

        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }

        Path directoryPath = file.toPath().getParent();
        Path filePath = file.toPath();

        synchronized (listeners) {
            boolean directoryWatched = watchKeys.containsValue(directoryPath);

            if (!directoryWatched) {
                WatchKey key = directoryPath.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);

                watchKeys.put(key, directoryPath);
                logger.info("Registered directory for watching: {}", directoryPath);
            }

            listeners.put(filePath, listener);
            logger.info("Added file watcher for: {}", file.getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unwatchFile(File file) {
        Path filePath = file.toPath();

        synchronized (listeners) {
            listeners.remove(filePath);
            logger.info("Removed file watcher for: {}", file.getName());
        }
    }

    /**
     * Main watch loop that processes file system events.
     * Runs on a separate daemon thread.
     */
    private void watchLoop() {
        logger.info("Watch loop started");

        while (running) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);

                if (key == null) {
                    continue;
                }

                Path directory = watchKeys.get(key);

                if (directory == null) {
                    logger.warn("WatchKey not recognized, ignoring events");
                    key.reset();
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        logger.warn("WatchEvent OVERFLOW - some events may have been lost");
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path fullPath = directory.resolve(filename);

                    synchronized (listeners) {
                        FileChangeListener listener = listeners.get(fullPath);

                        if (listener != null) {
                            File modifiedFile = fullPath.toFile();
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
     * {@inheritDoc}
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getWatchedFileCount() {
        synchronized (listeners) {
            return listeners.size();
        }
    }
}
