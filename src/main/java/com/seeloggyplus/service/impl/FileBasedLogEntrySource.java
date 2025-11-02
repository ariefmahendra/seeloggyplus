package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.service.LogEntrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-based LogEntrySource implementation that reads entries on-demand.
 * Only loads entries when needed, avoiding memory issues with large files.
 * Uses RandomAccessFile for efficient line-by-line reading.
 * TRUE lazy loading - estimates total lines without scanning entire file.
 */
public class FileBasedLogEntrySource implements LogEntrySource {

    private static final Logger logger = LoggerFactory.getLogger(FileBasedLogEntrySource.class);
    private static final int CACHE_SIZE = 5000; // Cache 5000 entries
    private static final int SAMPLE_SIZE = 1000; // Sample first N lines to estimate average
    private static final int INDEX_INTERVAL = 1000; // Build index every N lines

    private final File file;
    private final ParsingConfig config;
    private final int estimatedTotalLines;
    private final int avgBytesPerLine;
    private final Map<Integer, LogEntry> entryCache; // Simple LRU-like cache
    
    // Line offset index: maps line number -> byte offset
    // This is built incrementally as we read the file
    private final Map<Long, Long> lineOffsetIndex; // TreeMap for ordered access

    public FileBasedLogEntrySource(File file, ParsingConfig config) throws IOException {
        this.file = file;
        this.config = config;

        // Quick estimation instead of full scan
        long startTime = System.currentTimeMillis();
        this.avgBytesPerLine = estimateAverageBytesPerLine(file);
        this.estimatedTotalLines = (int) (file.length() / avgBytesPerLine);

        this.entryCache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, LogEntry> eldest) {
                return size() > CACHE_SIZE;
            }
        };
        
        // Initialize line offset index
        this.lineOffsetIndex = new TreeMap<>();
        this.lineOffsetIndex.put(1L, 0L); // Line 1 starts at byte 0

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Created FileBasedLogEntrySource for file: {} - Estimated {} lines (avg {} bytes/line) in {}ms",
            file.getName(), estimatedTotalLines, avgBytesPerLine, elapsed);
    }

    @Override
    public int getTotalEntries() {
        return estimatedTotalLines;
    }

    // Cache for last entries to avoid re-reading
    private List<LogEntry> cachedLastEntries = null;
    private int cachedLastEntriesLimit = 0;
    
    /**
     * Get last N entries from END of file using FAST backward reading.
     * BEST PRACTICE from professional log viewers (glogg, LogExpert):
     * 1. Read backwards from end of file (no full scan needed!)
     * 2. Build index incrementally as user scrolls
     * 3. Fast initial load, progressive indexing
     */
    public List<LogEntry> getLastEntries(int limit) {
        // Return cached if available
        if (cachedLastEntries != null && cachedLastEntriesLimit == limit) {
            logger.debug("Returning cached last {} entries", limit);
            return new ArrayList<>(cachedLastEntries);
        }
        
        List<LogEntry> entries = new ArrayList<>();
        
        logger.info("Reading last {} entries using fast backward reading (like tail -n)...", limit);
        long startTime = System.currentTimeMillis();

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            
            // OPTIMIZATION: Read backwards from end to find last N lines
            // This is MUCH faster than scanning entire file!
            List<String> lines = readLastNLinesBackward(raf, fileLength, limit);
            
            long readTime = System.currentTimeMillis() - startTime;
            logger.info("Read {} lines from end in {}ms using backward reading", lines.size(), readTime);
            
            // Estimate starting line number (good enough for display)
            // Will be corrected when user scrolls up
            long estimatedStartLine = Math.max(1, estimatedTotalLines - lines.size() + 1);
            
            // Create LogEntry objects
            for (int i = 0; i < lines.size(); i++) {
                long lineNumber = estimatedStartLine + i;
                String line = lines.get(i);
                
                LogEntry entry = parseLogEntry(lineNumber, line);
                entries.add(entry);
                
                // Build index for these lines
                if (lineNumber % INDEX_INTERVAL == 0) {
                    // Store approximate byte offset (will be refined later)
                    long approxOffset = fileLength - ((lines.size() - i) * avgBytesPerLine);
                    lineOffsetIndex.put(lineNumber, approxOffset);
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("Loaded {} entries (estimated lines {} to {}) in {}ms - FAST backward reading ⚡", 
                entries.size(), 
                estimatedStartLine, 
                estimatedStartLine + entries.size() - 1,
                totalTime);
            
            // Cache the results
            cachedLastEntries = new ArrayList<>(entries);
            cachedLastEntriesLimit = limit;

        } catch (IOException e) {
            logger.error("Error reading last entries from file", e);
        }

        return entries;
    }
    
    /**
     * Read last N lines from file by reading BACKWARDS from end.
     * This is the key optimization used by professional log viewers.
     * Much faster than scanning entire file!
     */
    private List<String> readLastNLinesBackward(RandomAccessFile raf, long fileLength, int limit) throws IOException {
        List<String> lines = new ArrayList<>();
        
        // Read backwards in chunks
        int bufferSize = 8192; // 8KB buffer
        byte[] buffer = new byte[bufferSize];
        long currentPos = fileLength;
        StringBuilder currentLine = new StringBuilder();
        
        while (currentPos > 0 && lines.size() < limit) {
            // Calculate how much to read
            long startPos = Math.max(0, currentPos - bufferSize);
            int bytesToRead = (int) (currentPos - startPos);
            
            // Read chunk
            raf.seek(startPos);
            raf.readFully(buffer, 0, bytesToRead);
            
            // Process bytes backwards
            for (int i = bytesToRead - 1; i >= 0; i--) {
                byte b = buffer[i];
                
                if (b == '\n' || b == '\r') {
                    // Found line ending
                    if (currentLine.length() > 0) {
                        // Reverse the line (we built it backwards)
                        lines.add(0, currentLine.reverse().toString());
                        currentLine.setLength(0);
                        
                        if (lines.size() >= limit) {
                            break;
                        }
                    }
                } else {
                    currentLine.append((char) b);
                }
            }
            
            currentPos = startPos;
        }
        
        // Add last line if any
        if (currentLine.length() > 0 && lines.size() < limit) {
            lines.add(0, currentLine.reverse().toString());
        }
        
        return lines;
    }


    /**
     * Get entries BEFORE a specific line number (reading backwards).
     * OPTIMIZED: Uses incremental line offset index for fast seeking.
     * 
     * @param beforeLineNumber The line number to read before
     * @param limit Maximum number of entries to read
     * @return List of entries before the specified line number
     */
    public List<LogEntry> getEntriesBeforeLine(long beforeLineNumber, int limit) {
        List<LogEntry> entries = new ArrayList<>();
        
        if (beforeLineNumber <= 1) {
            logger.debug("Already at beginning of file, no entries before line {}", beforeLineNumber);
            return entries;
        }
        
        // Calculate target range
        long startLine = Math.max(1, beforeLineNumber - limit);
        long endLine = beforeLineNumber - 1;
        int expectedCount = (int) (endLine - startLine + 1);
        
        logger.debug("Reading entries before line {}: target range {} to {} ({} entries)", 
            beforeLineNumber, startLine, endLine, expectedCount);
        
        long operationStart = System.currentTimeMillis();
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // OPTIMIZATION: Find closest indexed line before startLine
            long seekFromLine = findClosestIndexedLine(startLine);
            long seekFromOffset = lineOffsetIndex.get(seekFromLine);
            
            logger.debug("Using index: seeking from line {} (offset {}) to reach line {}", 
                seekFromLine, seekFromOffset, startLine);
            
            // Seek to the closest indexed position
            raf.seek(seekFromOffset);
            long currentLineNum = seekFromLine;
            long currentByteOffset = seekFromOffset;
            
            // Skip lines from indexed position to startLine
            while (currentLineNum < startLine) {
                String line = raf.readLine();
                if (line == null) {
                    logger.warn("Reached EOF while seeking to line {} at line {}", startLine, currentLineNum);
                    return entries;
                }
                
                currentByteOffset = raf.getFilePointer();
                currentLineNum++;
                
                // Build index as we go (every INDEX_INTERVAL lines)
                if (currentLineNum % INDEX_INTERVAL == 0) {
                    lineOffsetIndex.put(currentLineNum, currentByteOffset);
                }
            }
            
            long seekTime = System.currentTimeMillis() - operationStart;
            logger.debug("Reached start line {} in {}ms (skipped {} lines)", 
                startLine, seekTime, startLine - seekFromLine);
            
            // Now read the target entries
            int entriesRead = 0;
            while (currentLineNum <= endLine && entriesRead < limit) {
                String line = raf.readLine();
                if (line == null) {
                    logger.info("Reached EOF at line {} (expected to read until line {})", 
                        currentLineNum, endLine);
                    break;
                }
                
                // Convert from ISO-8859-1 to UTF-8
                line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                
                LogEntry entry = parseLogEntry(currentLineNum, line);
                entries.add(entry);
                
                currentByteOffset = raf.getFilePointer();
                currentLineNum++;
                entriesRead++;
                
                // Build index as we go
                if (currentLineNum % INDEX_INTERVAL == 0) {
                    lineOffsetIndex.put(currentLineNum, currentByteOffset);
                }
            }
            
            long totalTime = System.currentTimeMillis() - operationStart;
            logger.info("Read {} entries: lines {} to {} in {}ms (index size: {})", 
                entries.size(),
                entries.isEmpty() ? 0 : entries.get(0).getLineNumber(),
                entries.isEmpty() ? 0 : entries.get(entries.size() - 1).getLineNumber(),
                totalTime,
                lineOffsetIndex.size());
                
        } catch (IOException e) {
            logger.error("Error reading entries before line {}", beforeLineNumber, e);
        }
        
        return entries;
    }
    
    /**
     * Find the closest indexed line that is <= targetLine
     */
    private long findClosestIndexedLine(long targetLine) {
        // TreeMap.floorKey() returns greatest key <= targetLine
        Long closestLine = ((TreeMap<Long, Long>) lineOffsetIndex).floorKey(targetLine);
        return closestLine != null ? closestLine : 1L;
    }

    @Override
    public List<LogEntry> getEntries(int offset, int limit) {
        // OPTIMIZATION: If requesting ALL entries (offset=0, limit>=total), use fast sequential read
        if (offset == 0 && limit >= estimatedTotalLines) {
            return getAllEntriesFast();
        }
        
        List<LogEntry> entries = new ArrayList<>();
        int endIndex = Math.min(offset + limit, estimatedTotalLines);

        logger.debug("Reading entries from offset {} (limit: {}, estimated total: {})", 
            offset, limit, estimatedTotalLines);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // IMPORTANT: offset is 0-based index, but line numbers are 1-based
            // offset 0 means we want to read from line 1
            // offset 5000 means we want to read from line 5001
            long startingLineNumber = offset + 1;
            
            // Estimate starting byte position based on offset
            long estimatedByteOffset = (long) offset * avgBytesPerLine;
            raf.seek(Math.max(0, estimatedByteOffset));

            // If not at start of file, we might be in the middle of a line
            // Skip to the next complete line
            if (offset > 0) {
                String partialLine = raf.readLine(); // Skip partial line
                logger.trace("Skipped partial line at offset {}", offset);
            }

            int linesRead = 0;
            int targetLines = Math.min(limit, estimatedTotalLines - offset);
            long currentLineNum = startingLineNumber;

            while (linesRead < targetLines) {
                // Check cache first
                int cacheIndex = offset + linesRead;
                LogEntry cachedEntry = entryCache.get(cacheIndex);

                if (cachedEntry != null) {
                    entries.add(cachedEntry);
                    linesRead++;
                    currentLineNum++;
                    continue;
                }

                // Read from file
                String line = raf.readLine();
                if (line == null) {
                    // Reached actual end of file
                    logger.info("Reached EOF after reading {} of {} expected lines (started from line {})", 
                        linesRead, targetLines, startingLineNumber);
                    break;
                }

                // Convert from ISO-8859-1 to UTF-8 (readLine uses ISO-8859-1)
                line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

                LogEntry entry = parseLogEntry(currentLineNum, line);
                entries.add(entry);

                // Add to cache
                entryCache.put(cacheIndex, entry);

                linesRead++;
                currentLineNum++;
            }
            
            if (!entries.isEmpty()) {
                logger.debug("Read {} entries: lines {} to {} (offset {} to {})", 
                    entries.size(), 
                    entries.get(0).getLineNumber(),
                    entries.get(entries.size() - 1).getLineNumber(),
                    offset,
                    offset + entries.size() - 1);
            } else {
                logger.debug("Read 0 entries from offset {}", offset);
            }
                
        } catch (IOException e) {
            logger.error("Error reading log entries from file", e);
        }

        return entries;
    }
    
    /**
     * Fast sequential read of ALL entries (for virtual scrolling)
     * Optimized for reading entire file linearly with progress reporting
     */
    private List<LogEntry> getAllEntriesFast() {
        List<LogEntry> entries = new ArrayList<>();
        
        logger.info("Loading ALL entries using fast sequential read...");
        long startTime = System.currentTimeMillis();
        long lastProgressLog = startTime;
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = file.length();
            raf.seek(0);
            long currentLineNum = 1;
            long currentByteOffset = 0;
            String line;
            
            while ((line = raf.readLine()) != null) {
                // Convert from ISO-8859-1 to UTF-8
                line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                
                LogEntry entry = parseLogEntry(currentLineNum, line);
                entries.add(entry);
                
                // Build index every INDEX_INTERVAL lines
                if (currentLineNum % INDEX_INTERVAL == 0) {
                    lineOffsetIndex.put(currentLineNum, currentByteOffset);
                }
                
                currentByteOffset = raf.getFilePointer();
                currentLineNum++;
                
                // Log progress every 2 seconds
                long now = System.currentTimeMillis();
                if (now - lastProgressLog > 2000) {
                    double progress = (double) currentByteOffset / fileLength * 100;
                    logger.info("Progress: {}/{} lines loaded ({:.1f}%)", 
                        entries.size(), estimatedTotalLines, progress);
                    lastProgressLog = now;
                }
            }
            
            long loadTime = System.currentTimeMillis() - startTime;
            logger.info("✅ Loaded ALL {} entries in {}ms ({} lines/sec, index size: {})", 
                entries.size(), 
                loadTime,
                (entries.size() * 1000 / Math.max(1, loadTime)),
                lineOffsetIndex.size());
                
        } catch (IOException e) {
            logger.error("Error reading all entries", e);
        }
        
        return entries;
    }

    @Override
    public LogEntrySource filter(Predicate<LogEntry> predicate) {
        // For filtering, we need to load all entries (or implement smarter filtering)
        // This is a trade-off: filtering requires scanning all entries
        logger.info("Filtering file-based source - loading all entries (estimated: {})", estimatedTotalLines);

        List<LogEntry> allEntries = getEntries(0, estimatedTotalLines);
        List<LogEntry> filtered = allEntries.stream()
                .filter(predicate)
                .toList();

        logger.info("Filtered {} entries to {} entries", allEntries.size(), filtered.size());
        return new ListLogEntrySourceImpl(filtered);
    }

    /**
     * Estimate average bytes per line by sampling first N lines
     * This is MUCH faster than scanning entire file
     */
    private int estimateAverageBytesPerLine(File file) throws IOException {
        long totalBytes = 0;
        int linesRead = 0;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long startOffset = 0;

            while (linesRead < SAMPLE_SIZE && raf.getFilePointer() < file.length()) {
                String line = raf.readLine();
                if (line == null) {
                    break;
                }

                long endOffset = raf.getFilePointer();
                totalBytes += (endOffset - startOffset);
                startOffset = endOffset;
                linesRead++;
            }
        }

        if (linesRead == 0) {
            return 100; // Fallback default
        }

        int avgBytes = (int) (totalBytes / linesRead);
        logger.debug("Sampled {} lines, average {} bytes per line", linesRead, avgBytes);
        return avgBytes;
    }

    /**
     * Parse a single log line into LogEntry
     */
    private LogEntry parseLogEntry(long lineNumber, String line) {
        if (config == null || config.getCompiledPattern() == null) {
            return new LogEntry(lineNumber, line);
        }

        Pattern pattern = config.getCompiledPattern();
        Matcher matcher = pattern.matcher(line);

        if (matcher.matches()) {
            Map<String, String> fields = new HashMap<>();
            List<String> groupNames = config.getGroupNames();

            for (String groupName : groupNames) {
                try {
                    String value = matcher.group(groupName);
                    if (value != null) {
                        fields.put(groupName, value);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("Group '{}' not found in pattern", groupName);
                }
            }

            return new LogEntry(lineNumber, line, fields);
        } else {
            return new LogEntry(lineNumber, line);
        }
    }
}

