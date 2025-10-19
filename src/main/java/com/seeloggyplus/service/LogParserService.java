package com.seeloggyplus.service;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.ParsingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * High-performance log parser service
 * Optimized for large files with parallel processing and lazy loading
 */
public class LogParserService {

    private static final Logger logger = LoggerFactory.getLogger(LogParserService.class);
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    private ExecutorService executorService;
    private ParsingConfig currentConfig;

    public LogParserService() {
        this.executorService = Executors.newFixedThreadPool(MAX_THREADS);
    }

    /**
     * Parse a log file with the given configuration
     * Uses lazy loading for performance with large files
     */
    public List<LogEntry> parseFile(File file, ParsingConfig config) throws IOException {
        return parseFile(file, config, null);
    }

    /**
     * Parse a log file with progress callback
     */
    public List<LogEntry> parseFile(File file, ParsingConfig config, ProgressCallback callback) throws IOException {
        if (!file.exists() || !file.canRead()) {
            throw new IOException("File does not exist or cannot be read: " + file.getAbsolutePath());
        }

        this.currentConfig = config;
        List<LogEntry> entries = new ArrayList<>();

        long totalLines = countLines(file);
        long currentLine = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),
                8192 * 4)) { // 32KB buffer for better performance

            String line;
            while ((line = reader.readLine()) != null) {
                currentLine++;
                LogEntry entry = parseLine(line, currentLine, config);
                entries.add(entry);

                if (callback != null && currentLine % 100 == 0) {
                    double progress = (double) currentLine / totalLines;
                    callback.onProgress(progress, currentLine, totalLines);
                }
            }
        }

        if (callback != null) {
            callback.onComplete(entries.size());
        }

        logger.info("Parsed {} lines from file: {}", entries.size(), file.getName());
        return entries;
    }

    /**
     * Parse file in parallel for better performance with large files
     */
    public List<LogEntry> parseFileParallel(File file, ParsingConfig config, ProgressCallback callback) throws IOException {
        if (!file.exists() || !file.canRead()) {
            throw new IOException("File does not exist or cannot be read: " + file.getAbsolutePath());
        }

        this.currentConfig = config;
        List<LogEntry> entries = Collections.synchronizedList(new ArrayList<>());

        long totalLines = countLines(file);
        long[] currentLine = {0};

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),
                8192 * 4)) {

            List<String> batch = new ArrayList<>(BATCH_SIZE);
            List<Future<List<LogEntry>>> futures = new ArrayList<>();
            String line;
            long batchStartLine = 1;

            while ((line = reader.readLine()) != null) {
                batch.add(line);

                if (batch.size() >= BATCH_SIZE) {
                    final List<String> batchToProcess = new ArrayList<>(batch);
                    final long startLine = batchStartLine;

                    futures.add(executorService.submit(() ->
                        processBatch(batchToProcess, startLine, config)
                    ));

                    batch.clear();
                    batchStartLine += BATCH_SIZE;
                }
            }

            // Process remaining lines
            if (!batch.isEmpty()) {
                final List<String> batchToProcess = new ArrayList<>(batch);
                final long startLine = batchStartLine;
                futures.add(executorService.submit(() ->
                    processBatch(batchToProcess, startLine, config)
                ));
            }

            // Collect results
            for (Future<List<LogEntry>> future : futures) {
                try {
                    entries.addAll(future.get());
                    currentLine[0] += BATCH_SIZE;

                    if (callback != null) {
                        double progress = Math.min(1.0, (double) currentLine[0] / totalLines);
                        callback.onProgress(progress, currentLine[0], totalLines);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error processing batch", e);
                }
            }
        }

        // Sort entries by line number to maintain order
        entries.sort(Comparator.comparingLong(LogEntry::getLineNumber));

        if (callback != null) {
            callback.onComplete(entries.size());
        }

        logger.info("Parsed {} lines from file in parallel: {}", entries.size(), file.getName());
        return entries;
    }

    /**
     * Process a batch of lines
     */
    private List<LogEntry> processBatch(List<String> lines, long startLine, ParsingConfig config) {
        List<LogEntry> entries = new ArrayList<>(lines.size());
        long lineNumber = startLine;

        for (String line : lines) {
            LogEntry entry = parseLine(line, lineNumber++, config);
            entries.add(entry);
        }

        return entries;
    }

    /**
     * Parse a single line with the given configuration
     */
    public LogEntry parseLine(String line, long lineNumber, ParsingConfig config) {
        if (line == null || line.isEmpty()) {
            return new LogEntry(lineNumber, line);
        }

        if (config == null || !config.isValid()) {
            return new LogEntry(lineNumber, line);
        }

        try {
            Pattern pattern = config.getCompiledPattern();
            if (pattern == null) {
                return new LogEntry(lineNumber, line);
            }

            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                Map<String, String> fields = new HashMap<>();

                for (String groupName : config.getGroupNames()) {
                    try {
                        String value = matcher.group(groupName);
                        fields.put(groupName, value != null ? value : "");
                    } catch (IllegalArgumentException e) {
                        fields.put(groupName, "");
                    }
                }

                return new LogEntry(lineNumber, line, fields);
            } else {
                // Pattern didn't match, return raw entry
                return new LogEntry(lineNumber, line);
            }
        } catch (Exception e) {
            logger.warn("Error parsing line {}: {}", lineNumber, e.getMessage());
            return new LogEntry(lineNumber, line);
        }
    }

    /**
     * Test parsing configuration with sample log
     */
    public TestResult testParsing(String sampleLog, ParsingConfig config) {
        TestResult result = new TestResult();

        if (sampleLog == null || sampleLog.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("Sample log is empty");
            return result;
        }

        if (config == null || !config.isValid()) {
            result.setSuccess(false);
            result.setMessage("Parsing configuration is invalid: " +
                (config != null ? config.getValidationError() : "null"));
            return result;
        }

        try {
            Pattern pattern = config.getCompiledPattern();
            Matcher matcher = pattern.matcher(sampleLog);

            if (matcher.find()) {
                Map<String, String> fields = new HashMap<>();
                List<String> groupNames = config.getGroupNames();

                for (String groupName : groupNames) {
                    try {
                        String value = matcher.group(groupName);
                        fields.put(groupName, value != null ? value : "");
                    } catch (IllegalArgumentException e) {
                        fields.put(groupName, "");
                    }
                }

                result.setSuccess(true);
                result.setMessage("Pattern matched successfully");
                result.setParsedFields(fields);
                result.setGroupNames(groupNames);
            } else {
                result.setSuccess(false);
                result.setMessage("Pattern did not match the sample log");
                result.setGroupNames(config.getGroupNames());
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Error testing pattern: " + e.getMessage());
        }

        return result;
    }

    /**
     * Search entries with text or regex
     */
    public List<LogEntry> search(List<LogEntry> entries, String searchText, boolean isRegex, boolean caseSensitive) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return new ArrayList<>(entries);
        }

        List<LogEntry> results = new ArrayList<>();

        if (isRegex) {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                Pattern pattern = Pattern.compile(searchText, flags);

                for (LogEntry entry : entries) {
                    Matcher matcher = pattern.matcher(entry.getRawLog());
                    if (matcher.find()) {
                        results.add(entry);
                    }
                }
            } catch (Exception e) {
                logger.error("Invalid regex pattern: {}", e.getMessage());
                return new ArrayList<>();
            }
        } else {
            String searchFor = caseSensitive ? searchText : searchText.toLowerCase();

            for (LogEntry entry : entries) {
                String searchIn = caseSensitive ? entry.getRawLog() : entry.getRawLog().toLowerCase();
                if (searchIn.contains(searchFor)) {
                    results.add(entry);
                }
            }
        }

        return results;
    }

    /**
     * Count lines in file efficiently
     */
    private long countLines(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            long count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        }
    }

    /**
     * Get file preview (first N lines)
     */
    public List<String> getFilePreview(File file, int maxLines) throws IOException {
        List<String> preview = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null && count < maxLines) {
                preview.add(line);
                count++;
            }
        }

        return preview;
    }

    /**
     * Shutdown executor service
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Progress callback interface
     */
    public interface ProgressCallback {
        void onProgress(double progress, long currentLine, long totalLines);
        void onComplete(long totalLines);
    }

    /**
     * Test result class
     */
    public static class TestResult {
        private boolean success;
        private String message;
        private Map<String, String> parsedFields;
        private List<String> groupNames;

        public TestResult() {
            this.parsedFields = new HashMap<>();
            this.groupNames = new ArrayList<>();
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, String> getParsedFields() {
            return parsedFields;
        }

        public void setParsedFields(Map<String, String> parsedFields) {
            this.parsedFields = parsedFields;
        }

        public List<String> getGroupNames() {
            return groupNames;
        }

        public void setGroupNames(List<String> groupNames) {
            this.groupNames = groupNames;
        }
    }
}
