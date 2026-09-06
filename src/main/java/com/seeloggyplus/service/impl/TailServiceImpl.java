package com.seeloggyplus.service.impl;

import com.seeloggyplus.service.TailService;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Implementation of {@link TailService} using Apache Commons IO {@link Tailer}.
 */
public class TailServiceImpl implements TailService {

    private static final Logger logger = LoggerFactory.getLogger(TailServiceImpl.class);

    // Constants for behavior
    private static final int TAIL_DELAY_MILLIS = 500;
    private static final int DEFAULT_CONTEXT_ROWS = 20000;
    private static final int ESTIMATED_BYTES_PER_LINE = 150;

    private Tailer tailer;
    private Thread tailerThread;
    private volatile boolean isRunning = false;

    @Override
    public void startLocalTail(File file, Consumer<String> lineConsumer, Consumer<Exception> errorHandler,
            boolean loadContext) {
        startLocalTail(file, lineConsumer, errorHandler, loadContext, DEFAULT_CONTEXT_ROWS);
    }

    @Override
    public synchronized void startLocalTail(File file, Consumer<String> lineConsumer, Consumer<Exception> errorHandler,
            boolean loadContext, int contextRows) {
        stopTail(); // Ensure previous session is closed

        if (file == null || !file.exists()) {
            if (errorHandler != null) {
                errorHandler.accept(new IllegalArgumentException("File does not exist or is null"));
            }
            return;
        }

        logger.info("Starting local tail service for: {} (loadContext={}, contextRows={})",
                file.getAbsolutePath(), loadContext, contextRows);
        isRunning = true;

        // 1. Load Initial Context (Optional)
        if (loadContext) {
            loadInitialTailContext(file, lineConsumer, contextRows);
        }

        // 2. Start Tailer
        TailerListener listener = new TailerListenerAdapter() {
            @Override
            public void handle(String line) {
                if (lineConsumer != null) {
                    lineConsumer.accept(line);
                }
            }

            @Override
            public void handle(Exception ex) {
                // InterruptedException is expected when stopTail() interrupts the thread
                if (ex instanceof InterruptedException) {
                    logger.debug("Tailer thread interrupted (normal shutdown)");
                    return;
                }
                logger.error("Tailer error", ex);
                if (errorHandler != null) {
                    errorHandler.accept(ex);
                }
            }

            @Override
            public void fileNotFound() {
                if (errorHandler != null) {
                    errorHandler.accept(new java.io.FileNotFoundException("File not found or renamed during tailing"));
                }
            }
        };

        // start from end = true because we already loaded context
        tailer = new Tailer(file, listener, TAIL_DELAY_MILLIS, true);
        Thread thread = new Thread(tailer, "TailService-Thread");
        thread.setDaemon(true);
        tailerThread = thread;
        thread.start();
    }

    @Override
    public synchronized void stopTail() {
        isRunning = false;
        if (tailer != null) {
            tailer.stop();
            tailer = null;
        }
        if (tailerThread != null) {
            tailerThread.interrupt();
            tailerThread = null;
        }
        logger.info("Tail service stopped");
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    private void loadInitialTailContext(File file, Consumer<String> lineConsumer, int contextRows) {
        long estimatedBytes = (long) contextRows * ESTIMATED_BYTES_PER_LINE;
        long len = file.length();
        long startPos = Math.max(0, len - estimatedBytes);

        logger.info("Loading initial tail context from offset: {} (contextRows={})", startPos, contextRows);

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.skip(startPos);
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (lineConsumer != null) {
                        lineConsumer.accept(line);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error reading initial context", e);
        }
    }
}
