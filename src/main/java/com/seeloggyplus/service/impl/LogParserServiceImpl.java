package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.pipeline.Event;
import com.seeloggyplus.pipeline.Pipeline;
import com.seeloggyplus.pipeline.filters.RegexFilter;
import com.seeloggyplus.service.IndexerService;
import com.seeloggyplus.service.LogParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * High-performance implementation of {@link LogParser}.
 * <p>
 * Features:
 * <ul>
 * <li>Parallel chunk processing using Virtual Threads (Java 21).</li>
 * <li>Optimized regex matching and pre-compilation.</li>
 * <li>Streaming support for IndexerService to minimize RAM usage.</li>
 * <li>Robust error handling and progress reporting.</li>
 * </ul>
 */
public class LogParserServiceImpl implements LogParser {

    private static final Logger logger = LoggerFactory.getLogger(LogParserServiceImpl.class);
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int MAX_ENTRY_UNPARSED = 10000;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LogEntry> parseFileParallel(File file, ParsingConfig config, ProgressCallback callback) throws IOException {
        if (!file.exists() || !file.canRead()) {
            throw new IOException("File does not exist or cannot be read: " + file.getAbsolutePath());
        }

        long fileSize = file.length();
        List<ChunkInfo> chunkInfos = calculateChunkBoundaries(file);
        List<Future<List<LogEntry>>> futures = new ArrayList<>();
        AtomicLong bytesProcessed = new AtomicLong(0);

        final Pipeline pipeline = createPipeline(config);
        final DateTimeFormatter dateFormatter = createDateFormatter(config);

        for (ChunkInfo chunk : chunkInfos) {
            futures.add(executorService.submit(() -> {
                List<LogEntry> chunkEntries = processChunk(file, chunk, pipeline, dateFormatter);
                long chunkSize = chunk.endByte() - chunk.startByte();
                long totalProcessed = bytesProcessed.addAndGet(chunkSize);

                if (callback != null) {
                    double progress = (double) totalProcessed / fileSize;
                    callback.onProgress(progress, totalProcessed, fileSize);
                }
                return chunkEntries;
            }));
        }

        List<LogEntry> combinedEntries = new ArrayList<>();
        for (Future<List<LogEntry>> future : futures) {
            try {
                combinedEntries.addAll(future.get());
            } catch (InterruptedException e) {
                logger.info("Parsing interrupted (task cancelled by user)");
                Thread.currentThread().interrupt();
                futures.forEach(f -> f.cancel(true));
                break;
            } catch (ExecutionException e) {
                logger.error("Error processing a file chunk", e);
            }
        }

        if (callback != null) {
            callback.onComplete(combinedEntries.size());
        }

        logger.info("Parsed {} entries in parallel from file: {}", combinedEntries.size(), file.getName());
        return combinedEntries;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void indexFileParallel(File file, ParsingConfig config, IndexerService indexer, ProgressCallback callback)
            throws IOException {
        if (!file.exists() || !file.canRead()) {
            throw new IOException("File does not exist or cannot be read: " + file.getAbsolutePath());
        }

        long fileSize = file.length();
        List<ChunkInfo> chunkInfos = calculateChunkBoundaries(file);

        List<Future<Integer>> futures = new ArrayList<>();
        AtomicLong bytesProcessed = new AtomicLong(0);
        AtomicLong totalIndexed = new AtomicLong(0);

        final Pipeline pipeline = createPipeline(config);
        final DateTimeFormatter dateFormatter = createDateFormatter(config);

        for (ChunkInfo chunk : chunkInfos) {
            futures.add(executorService.submit(() -> processChunkAndIndex(file, chunk, pipeline, dateFormatter, indexer,
                    callback, fileSize, bytesProcessed)));
        }

        // Wait for all to complete
        for (Future<Integer> future : futures) {
            try {
                totalIndexed.addAndGet(future.get());
            } catch (InterruptedException e) {
                logger.info("Indexing interrupted");
                Thread.currentThread().interrupt();
                futures.forEach(f -> f.cancel(true));
                break;
            } catch (ExecutionException e) {
                logger.error("Error indexing chunk", e);
            }
        }

        if (callback != null) {
            callback.onComplete(totalIndexed.get());
        }

        logger.info("Indexed {} entries from file: {}", totalIndexed.get(), file.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogEntry parseLine(String line, long lineNumber, ParsingConfig config) {
        Pipeline pipeline = createPipeline(config);
        DateTimeFormatter dateFormatter = createDateFormatter(config);
        return parseLine(line, lineNumber, pipeline, dateFormatter);
    }

    /**
     * Parses a single line using an initialized Pipeline and Formatter.
     * Exposed for public use via interface (overloaded) or internal heavy-lifting.
     * 
     * @param line          Raw line text.
     * @param lineNumber    Line number context.
     * @param pipeline      Initialized Grok pipeline.
     * @param dateFormatter Optional date formatter.
     * @return LogEntry object.
     */
    public LogEntry parseLine(String line, long lineNumber, Pipeline pipeline, DateTimeFormatter dateFormatter) {
        if (line == null) return new LogEntry(lineNumber, "");
        if (pipeline == null) return new LogEntry(lineNumber, line);

        Event event = pipeline.process(line);

        if (event == null) {
            return new LogEntry(lineNumber, line);
        }

        // Check for parse failure tags
        boolean parsed = !event.hasTag("_grokparsefailure") && !event.hasTag("_pipeline_error");
        if (event.getFields().isEmpty()) {
            parsed = false;
        }

        if (parsed) {
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
                    logger.debug("Failed to parse timestamp: {}", stringFields.get("timestamp"));
                    // Leave null
                }
            }
            return logEntry;
        } else {
            return new LogEntry(lineNumber, line);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestResult testParsing(String sampleLog, ParsingConfig config) {
        TestResult result = new TestResult();

        if (sampleLog == null || sampleLog.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("Sample log is empty");
            return result;
        }

        if (config == null || !config.isValid()) {
            result.setSuccess(false);
            result.setMessage("Parsing configuration is invalid: " + (config != null ? config.getValidationError() : "null"));
            return result;
        }

        try {
            Pipeline pipeline = createPipeline(config);
            Event event = pipeline.process(sampleLog);

            if (event == null) {
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
                result.setSuccess(true);
                Map<String, String> stringFields = new HashMap<>();
                for (Map.Entry<String, Object> entry : event.getFields().entrySet()) {
                    stringFields.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                result.setParsedFields(stringFields);
                result.setGroupNames(config.getGroupNames());

                // Validate Timestamp Format if present
                if (config.getTimestampFormat() != null && !config.getTimestampFormat().isEmpty() && stringFields.containsKey("timestamp")) {
                    try {
                        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(config.getTimestampFormat());
                        java.time.LocalDateTime.parse(stringFields.get("timestamp"), dtf);
                        result.setMessage("Pattern matched successfully & Timestamp parsed validly.");
                    } catch (Exception e) {
                        result.setMessage("Pattern matched, BUT Timestamp format invalid: " + e.getMessage());
                        result.setSuccess(false);
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
     * {@inheritDoc}
     */
    @Override
    public List<LogEntry> search(List<LogEntry> entries, String searchText, boolean isRegex, boolean caseSensitive) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return new ArrayList<>(entries);
        }

        List<LogEntry> results = new ArrayList<>(Math.min(entries.size() / 10, 1000));

        if (isRegex) {
            Pattern pattern;
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                pattern = Pattern.compile(searchText, flags);
            } catch (Exception e) {
                logger.error("Invalid regex pattern: {}", e.getMessage());
                return new ArrayList<>();
            }

            for (LogEntry entry : entries) {
                if (pattern.matcher(entry.getRawLog()).find()) {
                    results.add(entry);
                }
            }
        } else {
            final String searchFor = caseSensitive ? searchText : searchText.toLowerCase();

            for (LogEntry entry : entries) {
                String rawLog = entry.getRawLog();
                if (caseSensitive) {
                    if (rawLog.contains(searchFor)) {
                        results.add(entry);
                    }
                } else {
                    if (rawLog.toLowerCase().contains(searchFor)) {
                        results.add(entry);
                    }
                }
            }
        }
        return results;
    }

    // --- Private Helper Methods ---

    private Pipeline createPipeline(ParsingConfig config) {
        Pipeline pipeline = new Pipeline();
        if (config != null && config.isValid() && config.getCompiledPattern() != null) {
            pipeline.addFilter(new RegexFilter(config.getCompiledPattern(), config.getGroupNames()));
        }
        return pipeline;
    }

    private DateTimeFormatter createDateFormatter(ParsingConfig config) {
        if (config != null && config.getTimestampFormat() != null && !config.getTimestampFormat().isEmpty()) {
            try {
                return DateTimeFormatter.ofPattern(config.getTimestampFormat());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid timestamp format in config: {}", config.getTimestampFormat());
            }
        }
        return null;
    }

    private List<LogEntry> processChunk(File file, ChunkInfo chunkInfo, Pipeline pipeline,
            DateTimeFormatter dateFormatter) {
        List<LogEntry> entries = new ArrayList<>();
        long currentLineNumber = chunkInfo.startLineNumber();
        int countUnparsedLine = 0;

        try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {
            channel.position(chunkInfo.startByte());
            BufferedReader reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));

            String line;
            long linesRead = 0;
            while (linesRead < chunkInfo.lineCount() && (line = reader.readLine()) != null) {
                LogEntry logEntry = parseLine(line, currentLineNumber, pipeline, dateFormatter);

                if (logEntry.getParsedFields().isEmpty()) {
                    countUnparsedLine++;
                } else {
                    countUnparsedLine = 0;
                }

                if (countUnparsedLine > MAX_ENTRY_UNPARSED) {
                    entries.add(new LogEntry(currentLineNumber, "*** WARNING: Parsing stopped for this chunk. Exceeded "
                            + MAX_ENTRY_UNPARSED + " consecutive unparsed lines. Check your regex configuration. ***"));
                    break;
                }

                entries.add(logEntry);
                currentLineNumber++;
                linesRead++;
            }
        } catch (IOException e) {
            logger.error("Error processing file chunk", e);
        }
        return entries;
    }

    private int processChunkAndIndex(File file, ChunkInfo chunkInfo, Pipeline pipeline, DateTimeFormatter dateFormatter,
            IndexerService indexer, ProgressCallback callback, long totalFileSize, AtomicLong globalBytesProcessed) {
        List<LogEntry> batch = new ArrayList<>(1000);
        int totalChunkIndexed = 0;
        long currentLineNumber = chunkInfo.startLineNumber();
        int countUnparsedLine = 0;

        try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {
            channel.position(chunkInfo.startByte());
            BufferedReader reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));

            String line;
            long startPos = channel.position();
            long linesRead = 0;

            while (linesRead < chunkInfo.lineCount() && (line = reader.readLine()) != null) {
                LogEntry logEntry = parseLine(line, currentLineNumber, pipeline, dateFormatter);

                if (logEntry.getParsedFields().isEmpty()) {
                    countUnparsedLine++;
                } else {
                    countUnparsedLine = 0;
                }

                if (countUnparsedLine > MAX_ENTRY_UNPARSED) {
                    batch.add(new LogEntry(currentLineNumber, "*** WARNING: Parsing stopped for this chunk. Exceeded "
                            + MAX_ENTRY_UNPARSED + " consecutive unparsed lines. Check your regex configuration. ***"));
                    break;
                }

                batch.add(logEntry);
                currentLineNumber++;
                linesRead++;

                if (batch.size() >= 1000) {
                    indexer.indexBatch(batch);
                    totalChunkIndexed += batch.size();
                    batch.clear();

                    long currentPos = channel.position();
                    long deltaBytes = currentPos - startPos;
                    if (deltaBytes > 0) {
                        long totalProcessed = globalBytesProcessed.addAndGet(deltaBytes);
                        if (callback != null) {
                            double progress = (double) totalProcessed / totalFileSize;
                            callback.onProgress(progress, totalProcessed, totalFileSize);
                        }
                        startPos = currentPos;
                    }
                }
            }

            if (!batch.isEmpty()) {
                indexer.indexBatch(batch);
                totalChunkIndexed += batch.size();
            }

        } catch (IOException e) {
            logger.error("Error processing file chunk for indexing", e);
        }
        return totalChunkIndexed;
    }

    private List<ChunkInfo> calculateChunkBoundaries(File file) throws IOException {
        List<ChunkInfo> chunks = new ArrayList<>();
        long fileSize = file.length();
        // Use a minimum chunk size to establish a baseline
        long targetChunkSize = Math.max(1024 * 1024, fileSize / MAX_THREADS);

        long currentByte = 0;
        long currentLine = 1;
        long chunkStartByte = 0;
        long chunkStartLine = 1;
        long nextSplitTarget = targetChunkSize;

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            int b;
            while ((b = bis.read()) != -1) {
                currentByte++;
                if (b == '\n') {
                    currentLine++;
                    if (currentByte >= nextSplitTarget && chunks.size() < MAX_THREADS - 1) {
                        chunks.add(new ChunkInfo(chunkStartByte, currentByte - 1, chunkStartLine,
                                currentLine - chunkStartLine));
                        chunkStartByte = currentByte;
                        chunkStartLine = currentLine;
                        nextSplitTarget += targetChunkSize;
                    }
                }
            }

            // Final chunk
            if (currentByte > chunkStartByte) {
                chunks.add(
                        new ChunkInfo(chunkStartByte, currentByte, chunkStartLine, currentLine - chunkStartLine + 1));
            }
        }

        if (chunks.isEmpty() && fileSize > 0) {
            chunks.add(new ChunkInfo(0, fileSize, 1, 0));
        }
        return chunks;
    }

    private record ChunkInfo(long startByte, long endByte, long startLineNumber, long lineCount) {
    }
}
