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
        this.executorService = Executors.newFixedThreadPool(MAX_THREADS);
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

        Map<Long, Long> lineStartOffsets = preCalculateLineOffsets(file);
        List<ChunkInfo> chunkInfos = new ArrayList<>();

        long totalLines = lineStartOffsets.size();
        long linesPerChunk = totalLines / MAX_THREADS;

        long currentLine = 1;
        for (int i = 0; i < MAX_THREADS; i++) {
            long chunkStartLine = currentLine;
            long chunkEndLine = (i == MAX_THREADS - 1) ? totalLines
                    : Math.min(totalLines, currentLine + linesPerChunk - 1);

            long startByte = lineStartOffsets.get(chunkStartLine);
            long endByte = (chunkEndLine == totalLines) ? fileSize : lineStartOffsets.get(chunkEndLine + 1) - 1; // End
                                                                                                                 // byte
                                                                                                                 // is
                                                                                                                 // just
                                                                                                                 // before
                                                                                                                 // the
                                                                                                                 // next
                                                                                                                 // line
                                                                                                                 // starts

            chunkInfos.add(new ChunkInfo(startByte, endByte, chunkStartLine));
            currentLine = chunkEndLine + 1;
        }

        List<Future<List<LogEntry>>> futures = new ArrayList<>();
        AtomicLong bytesProcessed = new AtomicLong(0);

        // Create pipeline once
        final Pipeline pipeline = createPipeline(config);

        for (ChunkInfo chunk : chunkInfos) {
            futures.add(executorService.submit(() -> {
                List<LogEntry> chunkEntries = processChunk(file, chunk, pipeline);
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

    private List<LogEntry> combineUnparsedEntries(List<LogEntry> rawEntries) {
        if (rawEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<LogEntry> combined = new ArrayList<>();
        StringBuilder unparsedBuffer = new StringBuilder(1000);
        Map<String, String> unparsedMap = new HashMap<>();
        long unparsedStartLine = -1;
        long unparsedEndLine = -1;

        for (LogEntry entry : rawEntries) {
            if (entry.isParsed()) {
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

    private List<LogEntry> processChunk(File file, ChunkInfo chunkInfo, Pipeline pipeline) {
        List<LogEntry> entries = new ArrayList<>();
        long currentLineNumber = chunkInfo.startLineNumber();
        int countUnparsedLine = 0;
        try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {
            channel.position(chunkInfo.startByte());
            BufferedReader reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null && channel.position() <= chunkInfo.endByte()) {
                LogEntry logEntry = parseLine(line, currentLineNumber, pipeline);

                if (!logEntry.isParsed()) {
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
    private Map<Long, Long> preCalculateLineOffsets(File file) throws IOException {
        Map<Long, Long> lineStartOffsets = new TreeMap<>(); // TreeMap to keep keys sorted
        long currentByteOffset = 0;
        long currentLineNumber = 1;

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            lineStartOffsets.put(currentLineNumber, currentByteOffset); // Offset for line 1

            while ((line = reader.readLine()) != null) {
                currentByteOffset += (line.getBytes(StandardCharsets.UTF_8).length
                        + System.lineSeparator().getBytes(StandardCharsets.UTF_8).length);
                currentLineNumber++;
                lineStartOffsets.put(currentLineNumber, currentByteOffset);
            }
        }
        return lineStartOffsets;
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
    /**
     * Parse a single line with the given configuration (Backward Compatibility)
     */
    public LogEntry parseLine(String line, long lineNumber, ParsingConfig config) {
        Pipeline pipeline = createPipeline(config);
        return parseLine(line, lineNumber, pipeline);
    }

    /**
     * Parse a single line using a Pipeline
     */
    public LogEntry parseLine(String line, long lineNumber, Pipeline pipeline) {
        if (line == null)
            return new LogEntry(lineNumber, "");
        if (pipeline == null)
            return new LogEntry(lineNumber, line);

        Event event = pipeline.process(line);

        if (event == null) {
            // Event was dropped by a filter
            // For now, we return it as unparsed or a specific dropped entry?
            // Logstash drops it. But here we might want to see it?
            // If pipeline returns null, it means Explicit Drop.
            // But existing logic combines unparsed entries.
            // If we drop it, it disappears.
            // Let's assume for now valid pipeline processing returns Event.
            // If null, we'll treat as unparsed/empty?
            // Actually, pipeline.process returns null if dropped.
            // Let's treating dropped logs as... not existing?
            // But for a Log Viewer, we usually want to see everything unless filtered out.
            // Let's assume validation failure in RegexFilter adds tag but returns true.
            // Only explicit DropFilter returns false.

            // If event is null (dropped), users probably don't want to see it.
            // But existing logic expects LogEntry.
            // If we return null here, caller might crash (e.g. processChunk adds to list).
            // Let's return a special LogEntry or just handle null in processChunk.
            // processChunk: entries.add(logEntry). List supports null? yes.
            // But combineUnparsedEntries iterates it.

            // Safer: return unparsed entry if null, assuming something went wrong or just
            // fallback.
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
            return new LogEntry(lineNumber, line, stringFields);
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
                result.setMessage("Pattern matched successfully");

                Map<String, String> stringFields = new HashMap<>();
                for (Map.Entry<String, Object> entry : event.getFields().entrySet()) {
                    stringFields.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                result.setParsedFields(stringFields);
                result.setGroupNames(config.getGroupNames());
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
