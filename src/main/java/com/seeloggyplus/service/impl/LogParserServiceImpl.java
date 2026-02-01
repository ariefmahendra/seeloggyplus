package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.pipeline.Event;
import com.seeloggyplus.pipeline.Pipeline;
import com.seeloggyplus.pipeline.filters.RegexFilter;
import com.seeloggyplus.service.LogParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
    // private static final int MAX_ENTRY_UNPARSED = 10000; // No longer used
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

        for (ChunkInfo chunk : chunkInfos) {
            futures.add(executorService.submit(() -> {
                List<LogEntry> chunkEntries = processChunk(file, chunk);
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

        logger.info("Read {} lines in parallel from file: {}", combinedEntries.size(), file.getName());
        return combinedEntries;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogEntry parseLine(String line, long lineNumber, ParsingConfig config) {
        // Parsing concept removed, returns raw entry
        return new LogEntry(lineNumber, line);
    }

    /**
     * Parses a single line.
     * Deprecated: Use simple LogEntry constructor instead.
     */
    public LogEntry parseLine(String line, long lineNumber, Pipeline pipeline, DateTimeFormatter dateFormatter) {
        return new LogEntry(lineNumber, line);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestResult testParsing(String sampleLog, ParsingConfig config) {
        TestResult result = new TestResult();
        // Return dummy success since parsing is disabled
        result.setSuccess(true);
        result.setMessage("Parsing is disabled. Treating as raw text.");
        Map<String, String> fields = new HashMap<>();
        fields.put("message", sampleLog);
        result.setParsedFields(fields);
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
                // Search in raw log
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

    private List<LogEntry> processChunk(File file, ChunkInfo chunkInfo) {
        List<LogEntry> entries = new ArrayList<>();
        long currentLineNumber = chunkInfo.startLineNumber();

        try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {
            channel.position(chunkInfo.startByte());
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            BufferedReader reader = new BufferedReader(Channels.newReader(channel, decoder, -1));

            String line;
            long linesRead = 0;
            while (linesRead < chunkInfo.lineCount() && (line = reader.readLine()) != null) {
                LogEntry logEntry = new LogEntry(currentLineNumber, line);
                entries.add(logEntry);
                currentLineNumber++;
                linesRead++;
            }
        } catch (IOException e) {
            logger.error("Error processing file chunk", e);
        }
        return entries;
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
