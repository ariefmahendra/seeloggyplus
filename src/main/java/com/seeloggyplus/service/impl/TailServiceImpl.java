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
    private static final int TAIL_WINDOW_ESTIMATED_BYTES = 20000 * 150; // Approx 20k lines

    private Tailer tailer;
    private Thread tailerThread;
    private volatile boolean isRunning = false;

    @Override
    public void startLocalTail(File file, Consumer<String> lineConsumer, Consumer<Exception> errorHandler,
            boolean loadContext) {
        stopTail(); // Ensure previous session is closed

        if (file == null || !file.exists()) {
            if (errorHandler != null) {
                errorHandler.accept(new IllegalArgumentException("File does not exist or is null"));
            }
            return;
        }

        logger.info("Starting local tail service for: {} (loadContext={})", file.getAbsolutePath(), loadContext);
        isRunning = true;

        // 1. Load Initial Context (Optional)
        if (loadContext) {
            loadInitialTailContext(file, lineConsumer);
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
        tailerThread = new Thread(tailer, "TailService-Thread");
        tailerThread.setDaemon(true);
        tailerThread.start();
    }

    @Override
    public void stopTail() {
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

    private void loadInitialTailContext(File file, Consumer<String> lineConsumer) {
        long len = file.length();
        long startPos = Math.max(0, len - TAIL_WINDOW_ESTIMATED_BYTES);

        logger.info("Loading initial tail context from offset: {}", startPos);

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
