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
    public List<LogEntry> parseFileParallel(File file, ParsingConfig config, ProgressCallback callback) throws IOException {
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
            long chunkEndLine = (i == MAX_THREADS - 1) ? totalLines : Math.min(totalLines, currentLine + linesPerChunk - 1);

            long startByte = lineStartOffsets.get(chunkStartLine);
            long endByte = (chunkEndLine == totalLines) ? fileSize : lineStartOffsets.get(chunkEndLine + 1) - 1; // End byte is just before the next line starts

            chunkInfos.add(new ChunkInfo(startByte, endByte, chunkStartLine));
            currentLine = chunkEndLine + 1;
        }

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
                    combined.add(new LogEntry(unparsedStartLine, unparsedEndLine, unparsedMap.put("message", unparsedBuffer.toString())));
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
                if (unparsedBuffer.length() < maxEntryUnparsed){
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

    private List<LogEntry> processChunk(File file, ChunkInfo chunkInfo, ParsingConfig config) {
        List<LogEntry> entries = new ArrayList<>();
        long currentLineNumber = chunkInfo.startLineNumber();
        int countUnparsedLine = 0;
        try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {
            channel.position(chunkInfo.startByte());
            BufferedReader reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null && channel.position() <= chunkInfo.endByte()) {
                LogEntry logEntry = parseLine(line, currentLineNumber, config);

                if (!logEntry.isParsed()){
                    countUnparsedLine++;
                } else {
                    countUnparsedLine = 0;
                }

                if (countUnparsedLine > maxEntryUnparsed && !logEntry.isParsed()){
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

    // Helper record for parallel chunk processing
    private record ChunkInfo(
            long startByte,
            long endByte,
            long startLineNumber
    ) {}

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
