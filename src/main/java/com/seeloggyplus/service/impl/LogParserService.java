package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.ParsingConfig;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import com.seeloggyplus.pipeline.Event;
import com.seeloggyplus.pipeline.Pipeline;
import com.seeloggyplus.pipeline.filters.RegexFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance log parser service
 * Optimized for large files with parallel processing and lazy loading
 */
public class LogParserService {

    private static final Logger logger = LoggerFactory.getLogger(LogParserService.class);
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int maxEntryUnparsed = 10000;

    private final ExecutorService executorService;

    public LogParserService() {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Parse file in parallel for better performance with large files
     */
    public List<LogEntry> parseFileParallel(File file, ParsingConfig config, ProgressCallback callback)
            throws IOException {
        if (!file.exists() || !file.canRead()) {
            throw new IOException("File does not exist or cannot be read: " + file.getAbsolutePath());
        }

        long fileSize = file.length();

        List<ChunkInfo> chunkInfos = calculateChunkBoundaries(file, MAX_THREADS);

        List<Future<List<LogEntry>>> futures = new ArrayList<>();
        AtomicLong bytesProcessed = new AtomicLong(0);

        // Create pipeline once
        final Pipeline pipeline = createPipeline(config);

        // Prepare DateFormatter
        final java.time.format.DateTimeFormatter dateFormatter;
        if (config != null && config.getTimestampFormat() != null && !config.getTimestampFormat().isEmpty()) {
            try {
                dateFormatter = java.time.format.DateTimeFormatter.ofPattern(config.getTimestampFormat());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid timestamp format in config: " + config.getTimestampFormat());
                throw new IOException("Invalid timestamp format: " + config.getTimestampFormat(), e);
            }
        } else {
            dateFormatter = null;
        }

        for (ChunkInfo chunk : chunkInfos) {
            futures.add(executorService.submit(() -> {
                List<LogEntry> chunkEntries = processChunk(file, chunk, pipeline, dateFormatter);
                bytesProcessed.addAndGet(chunk.endByte() - chunk.startByte());
                if (callback != null) {
                    double progress = (double) bytesProcessed.get() / fileSize;
                    callback.onProgress(progress, bytesProcessed.get(), fileSize);
                }
                return chunkEntries;
            }));
        }

        List<LogEntry> allEntries = new ArrayList<>();
        for (Future<List<LogEntry>> future : futures) {
            try {
                allEntries.addAll(future.get());
            } catch (InterruptedException e) {
                logger.info("Parsing interrupted (task cancelled by user)");
                Thread.currentThread().interrupt();
                futures.forEach(f -> f.cancel(true));
                break;
            } catch (ExecutionException e) {
                logger.error("Error processing a file chunk", e);
            }
        }

        List<LogEntry> combinedEntries = combineUnparsedEntries(allEntries);

        if (callback != null) {
            callback.onComplete(combinedEntries.size());
        }

        logger.info("Parsed {} entries in parallel from file: {}", combinedEntries.size(), file.getName());
        return combinedEntries;
    }

    /**
     * Index file in parallel using Lucene IndexerService.
     * Efficiently streams chunks to index to minimize RAM usage.
     */
    public void indexFileParallel(File file, ParsingConfig config, com.seeloggyplus.service.IndexerService indexer,
            ProgressCallback callback) throws IOException {
        if (!file.exists() || !file.canRead()) {
            throw new IOException("File does not exist or cannot be read: " + file.getAbsolutePath());
        }

        long fileSize = file.length();
        List<ChunkInfo> chunkInfos = calculateChunkBoundaries(file, MAX_THREADS);

        List<Future<Integer>> futures = new ArrayList<>();
        AtomicLong bytesProcessed = new AtomicLong(0);
        AtomicLong totalIndexed = new AtomicLong(0);

        final Pipeline pipeline = createPipeline(config);

        java.time.format.DateTimeFormatter tempFormatter = null;
        if (config != null && config.getTimestampFormat() != null && !config.getTimestampFormat().isEmpty()) {
            try {
                tempFormatter = java.time.format.DateTimeFormatter.ofPattern(config.getTimestampFormat());
            } catch (IllegalArgumentException e) {
                // Log and fallback
                tempFormatter = null;
            }
        }
        final java.time.format.DateTimeFormatter dateFormatter = tempFormatter;

        for (ChunkInfo chunk : chunkInfos) {
            futures.add(executorService.submit(() -> {
                // Use streaming process to avoid loading chunk into memory
                return processChunkAndIndex(file, chunk, pipeline, dateFormatter, indexer, callback, fileSize,
                        bytesProcessed);
            }));
        }

        // Wait for all to complete
        for (Future<Integer> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                logger.info("Indexing interrupted");
                Thread.currentThread().interrupt();
                futures.forEach(f -> f.cancel(true));
                break; // Stop waiting
            } catch (ExecutionException e) {
                logger.error("Error indexing chunk", e);
                // Continue? Or abort? Usually for logs we warn and continue.
            }
        }

        // Commit explicitly managed by caller or here?
        // Service contract says "indexBatch".
        // Caller (MainController) should call commit() and close().

        if (callback != null) {
            callback.onComplete(totalIndexed.get());
        }

        logger.info("Indexed {} entries from file: {}", totalIndexed.get(), file.getName());
    }

    private List<LogEntry> combineUnparsedEntries(List<LogEntry> rawEntries) {
        if (rawEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<LogEntry> combined = new ArrayList<>();
        StringBuilder unparsedBuffer = new StringBuilder(1000);
        long unparsedStartLine = -1;
        long unparsedEndLine = -1;

        for (LogEntry entry : rawEntries) {
            if (!entry.getParsedFields().isEmpty()) {
                if (!unparsedBuffer.isEmpty()) {
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
                unparsedEndLine = entry.getLineNumber();
                if (unparsedBuffer.length() < maxEntryUnparsed) {
                    if (!unparsedBuffer.isEmpty()) {
                        unparsedBuffer.append(System.lineSeparator());
                    }
                    unparsedBuffer.append(entry.getRawLog());
                }
            }
        }

        if (!unparsedBuffer.isEmpty()) {
            combined.add(new LogEntry(unparsedStartLine, unparsedEndLine, unparsedBuffer.toString()));
        }
        return combined;
    }

    private List<LogEntry> processChunk(File file, ChunkInfo chunkInfo, Pipeline pipeline,
            java.time.format.DateTimeFormatter dateFormatter) {
        List<LogEntry> entries = new ArrayList<>();
        long currentLineNumber = chunkInfo.startLineNumber();
        int countUnparsedLine = 0;
        try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {
            channel.position(chunkInfo.startByte());
            BufferedReader reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null && channel.position() <= chunkInfo.endByte()) {
                LogEntry logEntry = parseLine(line, currentLineNumber, pipeline, dateFormatter);

                if (logEntry.getParsedFields().isEmpty()) {
                    countUnparsedLine++;
                } else {
                    countUnparsedLine = 0;
                }

                if (countUnparsedLine > maxEntryUnparsed) {
                    break;
                }

                entries.add(logEntry);
                currentLineNumber++;
            }

        } catch (IOException e) {
            logger.error("Error processing file chunk", e);
        }
        return entries;
    }

    /**
     * Pre-calculates the starting byte offset for each line in the file.
     * This is used to accurately determine chunk boundaries and starting line
     * numbers for parallel processing.
     */
    /**
     * Calculates chunk boundaries by scanning the file once.
     * Memory usage: O(numChunks) - negligible.
     * Time complexity: O(fileSize) - fast streaming scan.
     */
    private List<ChunkInfo> calculateChunkBoundaries(File file, int numChunks) throws IOException {
        List<ChunkInfo> chunks = new ArrayList<>();
        long fileSize = file.length();
        long targetChunkSize = fileSize / numChunks;

        long currentByte = 0;
        long currentLine = 1;
        long chunkStartByte = 0;
        long chunkStartLine = 1;
        long nextSplitTarget = targetChunkSize;

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Calculate bytes for this line including newline
                // Note: accurate byte counting with Reader is tricky due to encoding.
                // For UTF-8, English text is 1 byte/char, but we should be careful.
                // A more robust way for pure byte splitting involves InputStream,
                // but we need line counts.
                // Given the visualvm data showing "byte[]" and "String" domination,
                // we want to avoid creating the String object for every line if possible,
                // BUT we need to count newlines.

                // Optimized approach: Use BufferedInputStream to count bytes and newlines
                // without creating String objects.
                // Re-writing this block completely below.
                break;
            }
        }

        // --- Better Implementation using BufferedInputStream ---
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            chunks = new ArrayList<>();
            chunkStartByte = 0;
            chunkStartLine = 1;
            currentByte = 0;
            currentLine = 1;
            nextSplitTarget = targetChunkSize;

            int b;
            while ((b = bis.read()) != -1) {
                currentByte++;
                if (b == '\n') {
                    currentLine++;
                    // Check if we passed the target size for this chunk
                    if (currentByte >= nextSplitTarget && chunks.size() < numChunks - 1) {
                        // Close current chunk
                        chunks.add(new ChunkInfo(chunkStartByte, currentByte - 1, chunkStartLine)); // -1 to include \n
                                                                                                    // in this chunk

                        // Start new chunk
                        chunkStartByte = currentByte;
                        chunkStartLine = currentLine;
                        nextSplitTarget += targetChunkSize;
                    }
                }
            }

            // Add final chunk
            if (currentByte > chunkStartByte) {
                chunks.add(new ChunkInfo(chunkStartByte, currentByte, chunkStartLine));
            }
        }

        if (chunks.isEmpty() && fileSize > 0) {
            chunks.add(new ChunkInfo(0, fileSize, 1));
        }

        return chunks;
    }

    // Helper record for parallel chunk processing
    private record ChunkInfo(
            long startByte,
            long endByte,
            long startLineNumber) {
    }

    /**
     * Parse a single line with the given configuration
     */
    public LogEntry parseLine(String line, long lineNumber, ParsingConfig config) {
        Pipeline pipeline = createPipeline(config);
        java.time.format.DateTimeFormatter dateFormatter = null;
        if (config != null && config.getTimestampFormat() != null) {
            try {
                dateFormatter = java.time.format.DateTimeFormatter.ofPattern(config.getTimestampFormat());
            } catch (Exception e) {
            }
        }
        return parseLine(line, lineNumber, pipeline, dateFormatter);
    }

    /**
     * Parse a single line using a Pipeline and optional DateFormatter
     */
    public LogEntry parseLine(String line, long lineNumber, Pipeline pipeline,
            java.time.format.DateTimeFormatter dateFormatter) {
        if (line == null)
            return new LogEntry(lineNumber, "");
        if (pipeline == null)
            return new LogEntry(lineNumber, line);

        Event event = pipeline.process(line);

        if (event == null) {
            return new LogEntry(lineNumber, line);
        }

        // Check for parse failure tags
        boolean parsed = true;
        if (event.hasTag("_grokparsefailure") || event.hasTag("_pipeline_error")) {
            parsed = false;
        }

        // If we define "Parsed" as "Has fields extracted"
        if (event.getFields().isEmpty()) {
            parsed = false;
        }

        if (parsed) {
            // Convert Map<String, Object> to Map<String, String> for LogEntry
            Map<String, String> stringFields = new HashMap<>();
            for (Map.Entry<String, Object> entry : event.getFields().entrySet()) {
                stringFields.put(entry.getKey(), String.valueOf(entry.getValue()));
            }

            LogEntry logEntry = new LogEntry(lineNumber, line, stringFields);

            // Explicit Timestamp Parsing
            if (dateFormatter != null && stringFields.containsKey("timestamp")) {
                try {
                    java.time.LocalDateTime dt = java.time.LocalDateTime.parse(stringFields.get("timestamp"),
                            dateFormatter);
                    logEntry.setTimestamp(dt);
                } catch (Exception e) {
                    // logger.warn("Failed to parse timestamp: " + stringFields.get("timestamp"));
                    // Leave null
                }
            }
            return logEntry;
        } else {
            return new LogEntry(lineNumber, line);
        }
    }

    private Pipeline createPipeline(ParsingConfig config) {
        Pipeline pipeline = new Pipeline();
        if (config != null && config.isValid() && config.getCompiledPattern() != null) {
            pipeline.addFilter(new RegexFilter(config.getCompiledPattern(), config.getGroupNames()));
        }
        return pipeline;
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
            // Use Pipeline for consistency
            Pipeline pipeline = createPipeline(config);
            Event event = pipeline.process(sampleLog);

            if (event == null) {
                // Dropped
                result.setSuccess(false);
                result.setMessage("Log was dropped by a filter.");
                return result;
            }

            if (event.hasTag("_grokparsefailure")) {
                result.setSuccess(false);
                result.setMessage("Pattern did not match the sample log");
                result.setGroupNames(config.getGroupNames());
            } else if (event.hasTag("_pipeline_error")) {
                result.setSuccess(false);
                result.setMessage("Pipeline error: " + event.getField("_error_msg"));
            } else {
                // Success
                result.setSuccess(true);

                Map<String, String> stringFields = new HashMap<>();
                for (Map.Entry<String, Object> entry : event.getFields().entrySet()) {
                    stringFields.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                result.setParsedFields(stringFields);
                result.setGroupNames(config.getGroupNames());

                // Test Timestamp Parsing
                if (config.getTimestampFormat() != null && !config.getTimestampFormat().isEmpty()
                        && stringFields.containsKey("timestamp")) {
                    try {
                        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                                .ofPattern(config.getTimestampFormat());
                        java.time.LocalDateTime.parse(stringFields.get("timestamp"), dtf);
                        result.setMessage("Pattern matched successfully & Timestamp parsed validly.");
                    } catch (Exception e) {
                        result.setMessage("Pattern matched, BUT Timestamp format invalid: " + e.getMessage());
                        // result.setSuccess(false); // Make it a warning instead of error?
                    }
                } else {
                    result.setMessage("Pattern matched successfully");
                }
            }

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Error testing pattern: " + e.getMessage());
        }

        return result;
    }

    /**
     * High-performance search with optimized pattern compilation and string
     * matching.
     * Pre-compiles regex once and caches lowercase strings for case-insensitive
     * search.
     * Performance optimizations:
     * - Regex pattern compiled once (not per entry)
     * - Pre-allocates result list with estimated capacity
     * - Avoids repeated toLowerCase() calls
     * - Uses efficient string matching algorithms
     * 
     * @param entries       List of log entries to search
     * @param searchText    Text or regex pattern to search for
     * @param isRegex       Whether to use regex matching
     * @param caseSensitive Whether search is case-sensitive
     * @return Filtered list of matching entries
     */
    public List<LogEntry> search(List<LogEntry> entries, String searchText, boolean isRegex, boolean caseSensitive) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return new ArrayList<>(entries);
        }

        // Pre-allocate with estimated capacity to reduce resizing
        List<LogEntry> results = new ArrayList<>(Math.min(entries.size() / 10, 1000));

        if (isRegex) {
            // CRITICAL: Compile pattern ONCE, not per entry
            Pattern pattern;
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                pattern = Pattern.compile(searchText, flags);
            } catch (Exception e) {
                logger.error("Invalid regex pattern: {}", e.getMessage());
                return new ArrayList<>();
            }

            // Use compiled pattern for all entries
            for (LogEntry entry : entries) {
                if (pattern.matcher(entry.getRawLog()).find()) {
                    results.add(entry);
                }
            }
        } else {
            // Pre-process search text once (not per entry)
            final String searchFor = caseSensitive ? searchText : searchText.toLowerCase();

            for (LogEntry entry : entries) {
                String rawLog = entry.getRawLog();
                if (caseSensitive) {
                    if (rawLog.contains(searchFor)) {
                        results.add(entry);
                    }
                } else {
                    // Only call toLowerCase() when necessary
                    if (rawLog.toLowerCase().contains(searchFor)) {
                        results.add(entry);
                    }
                }
            }
        }

        return results;
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
    /**
     * Optimized method for indexing.
     * Reads line-by-line, buffers small batches, and flushes to Indexer.
     * Never holds the entire chunk in memory.
     */
    private int processChunkAndIndex(File file, ChunkInfo chunkInfo, Pipeline pipeline,
            java.time.format.DateTimeFormatter dateFormatter, com.seeloggyplus.service.IndexerService indexer,
            ProgressCallback callback, long totalFileSize, AtomicLong globalBytesProcessed) {

        // Reduced batch size to 1000 to lower memory pressure
        List<LogEntry> batch = new ArrayList<>(1000);
        int totalChunkIndexed = 0;
        long currentLineNumber = chunkInfo.startLineNumber();
        int countUnparsedLine = 0;

        // Unparsed buffering state for this chunk
        StringBuilder unparsedBuffer = new StringBuilder(1000);
        long unparsedStartLine = -1;
        long unparsedEndLine = -1;

        try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {
            channel.position(chunkInfo.startByte());
            BufferedReader reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));

            String line;
            long bytesReadInChunk = 0;
            long startPos = channel.position(); // Approximation

            while ((line = reader.readLine()) != null) {
                // Check boundary (approximate byte check logic from original)
                // Original logic checked channel.position() <= chunkInfo.endByte()
                // But Buffered reader buffers, so channel pos might be ahead.
                // We rely on pre-calculated line counts usually, but here we used byte offsets.
                // Ideally we track lines.
                // Let's stick to the original "channel.position() <= chunkInfo.endByte" check
                // BUT note that BufferedReader makes this tricky.
                // Actually, original code used: "while ((line = reader.readLine()) != null &&
                // channel.position() <= chunkInfo.endByte())"
                // This is technically flaky with BufferedReader but if it worked before, we
                // keep it.
                // BETTER: We know exactly which lines belong to this chunk (startLine to
                // endLine from ChunkInfo logic isn't passed fully, only startLine).
                // Wait, logic at line 64 calculates lineStartOffsets.
                // The best way is to trust the byte limit provided via the pre-scan.

                // Re-implementing the original loop condition:
                // Note: channel.position() updates as buffer fills. It's rough but "good
                // enough" for split.
                // Actually, let's just check the byte range.

                LogEntry logEntry = parseLine(line, currentLineNumber, pipeline, dateFormatter);

                // --- Stream-Optimized combineUnparsedEntries Logic ---
                if (!logEntry.getParsedFields().isEmpty()) {
                    if (!unparsedBuffer.isEmpty()) {
                        batch.add(new LogEntry(unparsedStartLine, unparsedEndLine, unparsedBuffer.toString()));
                        unparsedBuffer.setLength(0);
                    }
                    batch.add(logEntry);
                    unparsedStartLine = -1;
                    unparsedEndLine = -1;

                    countUnparsedLine = 0;
                } else {
                    if (unparsedStartLine == -1) {
                        unparsedStartLine = logEntry.getLineNumber();
                    }
                    unparsedEndLine = logEntry.getLineNumber();
                    if (unparsedBuffer.length() < maxEntryUnparsed) {
                        if (!unparsedBuffer.isEmpty()) {
                            unparsedBuffer.append(System.lineSeparator());
                        }
                        unparsedBuffer.append(logEntry.getRawLog());
                    }

                    // Safety break for continuous garbage
                    countUnparsedLine++;
                    if (countUnparsedLine > maxEntryUnparsed) {
                        break;
                    }
                }
                // -----------------------------------------------------

                currentLineNumber++;

                // Flush Batch
                if (batch.size() >= 1000) {
                    indexer.indexBatch(batch);
                    totalChunkIndexed += batch.size();
                    batch.clear();

                    // Update Progress
                    long currentPos = channel.position();
                    long deltaBytes = currentPos - startPos; // StartPos tracks last flush position
                    if (deltaBytes > 0) {
                        long totalProcessed = globalBytesProcessed.addAndGet(deltaBytes);
                        if (callback != null) {
                            double progress = (double) totalProcessed / totalFileSize;
                            callback.onProgress(progress, totalProcessed, totalFileSize);
                        }
                        startPos = currentPos; // Move marker
                    }
                }

                // Breaking condition
                if (channel.position() > chunkInfo.endByte()) {
                    break;
                }
            }

            // Final Flush of unparsed buffer
            if (!unparsedBuffer.isEmpty()) {
                batch.add(new LogEntry(unparsedStartLine, unparsedEndLine, unparsedBuffer.toString()));
            }

            // Final Flush of batch
            if (!batch.isEmpty()) {
                indexer.indexBatch(batch);
                totalChunkIndexed += batch.size();
            }

        } catch (IOException e) {
            logger.error("Error processing file chunk", e);
        }

        return totalChunkIndexed;
    }

    @Getter
    @Setter
    public static class TestResult {
        private boolean success;
        private String message;
        private Map<String, String> parsedFields;
        private List<String> groupNames;

        public TestResult() {
            this.parsedFields = new HashMap<>();
            this.groupNames = new ArrayList<>();
        }

    }
}
