package com.seeloggyplus.service;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.ParsingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
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

    public List<LogEntry> parseFile(File file, ParsingConfig config, ProgressCallback callback) throws IOException {
        if (!file.exists() || !file.canRead()) {
            throw new IOException("File does not exist or cannot be read: " + file.getAbsolutePath());
        }
        this.currentConfig = config;

        final long fileSize = file.length();
        List<LogEntry> entries = new ArrayList<>();
        Pattern pattern = (config != null && config.isValid()) ? config.getCompiledPattern() : null;

        long bytesRead = 0L; // Declare bytesRead outside the try block
        int lineNumber = 0;
        StringBuilder unparsedBuffer = new StringBuilder();
        long unparsedStartLine = -1;

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                bytesRead += line.getBytes(StandardCharsets.UTF_8).length + 1; // +1 for newline

                Matcher matcher = (pattern != null) ? pattern.matcher(line) : null;

                if (matcher != null && matcher.find()) {
                    // If there's content in the unparsed buffer, flush it first
                    if (!unparsedBuffer.isEmpty()) {
                        entries.add(new LogEntry(unparsedStartLine, unparsedBuffer.toString()));
                        unparsedBuffer.setLength(0);
                    }

                    // Add the successfully parsed entry
                    entries.add(new LogEntry(lineNumber, line, matcher, config.getGroupNames()));
                    unparsedStartLine = -1; // Reset unparsed line tracking

                } else {
                    // Line did not match, append to the unparsed buffer
                    if (unparsedBuffer.isEmpty()) {
                        unparsedStartLine = lineNumber; // Mark the start of this unparsed block
                    }
                    unparsedBuffer.append(line).append(System.lineSeparator());
                }

                if (callback != null && lineNumber % 1000 == 0) { // Update progress every 1000 lines
                    double progress = fileSize > 0 ? (double) bytesRead / fileSize : 0.0;
                    callback.onProgress(progress, bytesRead, fileSize); // Use bytesRead consistently
                }
            }

            // After the loop, if there's anything left in the unparsed buffer, add it
            if (!unparsedBuffer.isEmpty()) {
                entries.add(new LogEntry(unparsedStartLine, unparsedBuffer.toString()));
            }
        }

        if (callback != null) {
            // Final progress update should use the actual bytes read and total file size
            callback.onProgress(1.0, bytesRead, fileSize);
            callback.onComplete(entries.size());
        }

        logger.info("Parsed {} entries from file: {}", entries.size(), file.getName());
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
        long fileSize = file.length();

        // Step 1: Pre-calculate line offsets to determine chunk boundaries and starting line numbers
        Map<Long, Long> lineStartOffsets = preCalculateLineOffsets(file);
        List<ChunkInfo> chunkInfos = new ArrayList<>();

        long totalLines = lineStartOffsets.size();
        long linesPerChunk = totalLines / MAX_THREADS;

        long currentLine = 1;
        for (int i = 0; i < MAX_THREADS; i++) {
            long chunkStartLine = currentLine;
            long chunkEndLine = (i == MAX_THREADS - 1) ? totalLines : Math.min(totalLines, currentLine + linesPerChunk - 1);

            long startByte = lineStartOffsets.get(chunkStartLine);
            long endByte = (chunkEndLine == totalLines) ? fileSize : lineStartOffsets.get(chunkEndLine + 1) - 1; // End byte is just before the next line starts

            chunkInfos.add(new ChunkInfo(startByte, endByte, chunkStartLine));
            currentLine = chunkEndLine + 1;
        }

        // Step 2: Submit chunks for parallel processing
        List<Future<List<LogEntry>>> futures = new ArrayList<>();
        AtomicLong bytesProcessed = new AtomicLong(0);

        for (ChunkInfo chunk : chunkInfos) {
            futures.add(executorService.submit(() -> {
                List<LogEntry> chunkEntries = processChunk(file, chunk, config);
                bytesProcessed.addAndGet(chunk.endByte() - chunk.startByte());
                if (callback != null) {
                    double progress = (double) bytesProcessed.get() / fileSize;
                    callback.onProgress(progress, bytesProcessed.get(), fileSize);
                }
                return chunkEntries;
            }));
        }

        // Step 3: Collect results in order
        List<LogEntry> allEntries = new ArrayList<>();
        for (Future<List<LogEntry>> future : futures) {
            try {
                allEntries.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error processing a file chunk", e);
            }
        }

        // Step 4: Post-process to combine non-matching lines
        List<LogEntry> combinedEntries = combineUnparsedEntries(allEntries);

        if (callback != null) {
            callback.onComplete(combinedEntries.size());
        }

        logger.info("Parsed {} entries in parallel from file: {}", combinedEntries.size(), file.getName());
        return combinedEntries;
    }

    private List<LogEntry> processBatch(List<LineWithNumber> batch, ParsingConfig config) {
        List<LogEntry> entries = new ArrayList<>(batch.size());
        for (LineWithNumber lineWithNumber : batch) {
            entries.add(parseLine(lineWithNumber.line(), lineWithNumber.lineNumber(), config));
        }
        return entries;
    }

    private List<LogEntry> combineUnparsedEntries(List<LogEntry> rawEntries) {
        if (rawEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<LogEntry> combined = new ArrayList<>();
        StringBuilder unparsedBuffer = new StringBuilder();
        long unparsedStartLine = -1;
        long unparsedEndLine = -1;

        for (LogEntry entry : rawEntries) {
            if (entry.isParsed()) {
                if (unparsedBuffer.length() > 0) {
                    combined.add(new LogEntry(unparsedStartLine, unparsedEndLine, unparsedBuffer.toString()));
                    unparsedBuffer.setLength(0);
                }
                combined.add(entry);
                unparsedStartLine = -1;
                unparsedEndLine = -1;
            } else {
                if (unparsedStartLine == -1) {
                    unparsedStartLine = entry.getLineNumber();
                }
                unparsedEndLine = entry.getLineNumber(); // Update end line with current entry's line number
                if (unparsedBuffer.length() > 0) {
                    unparsedBuffer.append(System.lineSeparator());
                }
                unparsedBuffer.append(entry.getRawLog());
            }
        }

        if (unparsedBuffer.length() > 0) {
            combined.add(new LogEntry(unparsedStartLine, unparsedEndLine, unparsedBuffer.toString()));
        }
        return combined;
    }

    private List<LogEntry> processChunk(File file, ChunkInfo chunkInfo, ParsingConfig config) {
        List<LogEntry> entries = new ArrayList<>();
        long currentLineNumber = chunkInfo.startLineNumber();
        try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {
            channel.position(chunkInfo.startByte());
            BufferedReader reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));

            String line;
            // Read lines until the end of the chunk or end of file
            while ((line = reader.readLine()) != null && channel.position() <= chunkInfo.endByte()) {
                entries.add(parseLine(line, currentLineNumber++, config));
            }

        } catch (IOException e) {
            logger.error("Error processing file chunk", e);
        }
        return entries;
    }

    /**
     * Pre-calculates the starting byte offset for each line in the file.
     * This is used to accurately determine chunk boundaries and starting line numbers for parallel processing.
     */
    private Map<Long, Long> preCalculateLineOffsets(File file) throws IOException {
        Map<Long, Long> lineStartOffsets = new TreeMap<>(); // TreeMap to keep keys sorted
        long currentByteOffset = 0;
        long currentLineNumber = 1;

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            lineStartOffsets.put(currentLineNumber, currentByteOffset); // Offset for line 1

            while ((line = reader.readLine()) != null) {
                currentByteOffset += (line.getBytes(StandardCharsets.UTF_8).length + System.lineSeparator().getBytes(StandardCharsets.UTF_8).length);
                currentLineNumber++;
                lineStartOffsets.put(currentLineNumber, currentByteOffset);
            }
        }
        return lineStartOffsets;
    }

    // Helper record for batch processing
    private record LineWithNumber(long lineNumber, String line) {}

    // Helper record for parallel chunk processing
    private record ChunkInfo(long startByte, long endByte, long startLineNumber) {}

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
        void onProgress(double progress, long bytesProcessed, long totalBytes);

        void onComplete(long totalEntries);
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
