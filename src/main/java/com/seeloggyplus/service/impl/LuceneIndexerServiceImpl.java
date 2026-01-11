package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.service.IndexerService;
import lombok.NoArgsConstructor;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Apache Lucene 9.8.0 implementation of {@link IndexerService}.
 * 
 * <p>
 * This service creates and manages an on-disk inverted index for log entries,
 * enabling high-performance full-text search and filtering capabilities.
 * 
 * <h2>Features:</h2>
 * <ul>
 * <li>Full-text search on log messages</li>
 * <li>Range queries on timestamps and line numbers</li>
 * <li>Exact match filtering on log levels</li>
 * <li>Optimized for bulk indexing with 64MB RAM buffer</li>
 * <li>Automatic lock file recovery</li>
 * </ul>
 * 
 * <h2>Index Schema:</h2>
 * <ul>
 * <li><b>line_number</b>: Stored, sortable, range-queryable</li>
 * <li><b>timestamp</b>: Stored, sortable, range-queryable (epoch millis)</li>
 * <li><b>level</b>: Stored, exact-match filterable (uppercase)</li>
 * <li><b>message</b>: Stored, full-text searchable</li>
 * <li><b>raw_log</b>: Stored only (original line)</li>
 * </ul>
 * 
 * <h2>Thread Safety:</h2>
 * <p>
 * This implementation is thread-safe for single-writer scenarios.
 * Multiple concurrent indexing operations should use separate instances.
 * 
 * <h2>Resource Management:</h2>
 * <p>
 * Always call {@link #close()} when done to release resources.
 * The service automatically handles lock file cleanup and recovery.
 * 
 * @author SeeLoggy+ Team
 * @version 1.0
 * @see IndexerService
 * @see org.apache.lucene.index.IndexWriter
 */
@NoArgsConstructor
public class LuceneIndexerServiceImpl implements IndexerService {

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexerServiceImpl.class);

    /**
     * Base directory for all Lucene indexes.
     * Uses system temp directory to avoid permission issues.
     */
    private static final String INDEX_BASE_DIR = System.getProperty("java.io.tmpdir") + "/seeloggyplus/index/";

    /**
     * RAM buffer size for bulk indexing optimization (in MB).
     * Higher values improve indexing speed but use more memory.
     */
    private static final double RAM_BUFFER_SIZE_MB = 64.0;

    /** Lucene index writer for adding documents */
    private IndexWriter writer;

    /** Lucene directory for index storage */
    private Directory directory;

    /** Path to the current index directory */
    private Path indexPath;

    /**
     * Initializes a new Lucene index for the given run ID.
     * 
     * <p>
     * This method:
     * <ol>
     * <li>Closes any existing writer (safety cleanup)</li>
     * <li>Creates index directory if needed</li>
     * <li>Clears existing index if present (re-indexing scenario)</li>
     * <li>Configures and opens a new IndexWriter</li>
     * <li>Handles lock file recovery automatically</li>
     * </ol>
     * 
     * <p>
     * <b>Performance Configuration:</b>
     * <ul>
     * <li>OpenMode: CREATE (overwrites existing index)</li>
     * <li>RAM Buffer: 64MB (optimized for bulk indexing)</li>
     * <li>Analyzer: StandardAnalyzer (tokenization and normalization)</li>
     * </ul>
     * 
     * @param runId unique identifier for this indexing session
     * @throws IOException               if index initialization fails
     * @throws LockObtainFailedException if lock file cannot be cleared (rare)
     */
    @Override
    public void initializeIndex(String runId) throws IOException {
        closeQuietly();

        this.indexPath = Paths.get(INDEX_BASE_DIR, runId);

        if (Files.exists(this.indexPath)) {
            clearIndex(runId);
        }

        try {
            Files.createDirectories(this.indexPath);
        } catch (IOException e) {
            logger.error("Failed to create index directory: {}", indexPath, e);
            throw e;
        }

        try {
            openIndexWriter();
            logger.info("Initialized Lucene Index at: {}", this.indexPath.toAbsolutePath());

        } catch (LockObtainFailedException e) {
            logger.warn("Lock file exists, attempting to force clear and retry...");
            forceClearLock();

            try {
                openIndexWriter();
                logger.info("Successfully recovered and initialized index after lock clear");
            } catch (IOException retryEx) {
                logger.error("Failed to initialize index even after lock clear", retryEx);
                closeQuietly();
                throw retryEx;
            }
        } catch (IOException e) {
            logger.error("Failed to initialize Lucene Index", e);
            closeQuietly();
            throw e;
        }
    }

    /**
     * Opens and configures the Lucene IndexWriter.
     * 
     * @throws IOException if writer cannot be opened
     */
    private void openIndexWriter() throws IOException {
        this.directory = FSDirectory.open(this.indexPath);
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        config.setRAMBufferSizeMB(RAM_BUFFER_SIZE_MB);
        this.writer = new IndexWriter(directory, config);
    }

    /**
     * Indexes a batch of log entries into the Lucene index.
     * 
     * <p>
     * Each log entry is converted to a Lucene Document with the following fields:
     * <ul>
     * <li><b>line_number</b>: StoredField + NumericDocValuesField + LongPoint</li>
     * <li><b>timestamp</b>: LongPoint + StoredField + NumericDocValuesField</li>
     * <li><b>level</b>: StringField (uppercase, exact match)</li>
     * <li><b>message</b>: TextField (full-text searchable)</li>
     * <li><b>raw_log</b>: StoredField (original line)</li>
     * </ul>
     * 
     * <p>
     * <b>Performance:</b> Batching improves indexing speed by reducing I/O
     * operations.
     * Recommended batch size: 500-1000 entries.
     * 
     * @param entries list of log entries to index
     * @throws IOException           if indexing fails or writer is not initialized
     * @throws IllegalStateException if {@link #initializeIndex(String)} was not
     *                               called
     */
    @Override
    public void indexBatch(List<LogEntry> entries) throws IOException {
        if (writer == null || !writer.isOpen()) {
            throw new IllegalStateException("IndexWriter is not open. Call initializeIndex() first.");
        }

        try {
            for (LogEntry entry : entries) {
                Document doc = createDocument(entry);
                writer.addDocument(doc);
            }
        } catch (IOException e) {
            logger.error("Failed to index batch of {} entries", entries.size(), e);
            throw e;
        }
    }

    /**
     * Creates a Lucene Document from a LogEntry.
     * 
     * @param entry the log entry to convert
     * @return Lucene document ready for indexing
     */
    private Document createDocument(LogEntry entry) {
        Document doc = new Document();

        // Line Number: stored, sortable, range-queryable
        doc.add(new StoredField("line_number", entry.getLineNumber()));
        doc.add(new NumericDocValuesField("line_number_sort", entry.getLineNumber()));
        doc.add(new LongPoint("line_number_range", entry.getLineNumber()));

        // Timestamp: range-queryable, stored, sortable
        long epochMillis = 0;
        if (entry.getTimestamp() != null) {
            epochMillis = entry.getTimestamp()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        }
        doc.add(new LongPoint("timestamp", epochMillis));
        doc.add(new StoredField("timestamp_store", epochMillis));
        doc.add(new NumericDocValuesField("timestamp_sort", epochMillis));

        // Level: exact match filter (uppercase for consistency)
        String level = entry.getLevel() != null ? entry.getLevel().toUpperCase() : "";
        doc.add(new StringField("level", level, Field.Store.YES));

        // Message: full-text searchable
        String message = entry.getMessage() != null ? entry.getMessage() : "";
        doc.add(new TextField("message", message, Field.Store.YES));

        // Raw Log: stored only (original line)
        String raw = entry.getRawLog() != null ? entry.getRawLog() : "";
        doc.add(new StoredField("raw_log", raw));

        return doc;
    }

    /**
     * Commits all pending changes to the index.
     * 
     * <p>
     * This method flushes the RAM buffer to disk, making all indexed
     * documents searchable. Call this after batch indexing is complete.
     * 
     * <p>
     * <b>Note:</b> Commit is expensive. Avoid calling too frequently.
     * 
     * @throws IOException if commit fails
     */
    @Override
    public void commit() throws IOException {
        if (writer != null && writer.isOpen()) {
            try {
                writer.commit();
                logger.debug("Committed index changes.");
            } catch (IOException e) {
                logger.error("Failed to commit index changes", e);
                throw e;
            }
        }
    }

    /**
     * Closes the index writer and releases all resources.
     * 
     * <p>
     * This method should always be called when indexing is complete.
     * It safely closes the writer and directory without throwing exceptions.
     * 
     * @throws IOException if close fails (rare, usually swallowed)
     */
    @Override
    public void close() throws IOException {
        closeQuietly();
    }

    /**
     * Safely closes writer and directory without throwing exceptions.
     * 
     * <p>
     * This method is called internally for cleanup and can be called
     * multiple times safely. It ensures resources are released even if
     * errors occur during closing.
     * 
     * <p>
     * <b>Cleanup Order:</b>
     * <ol>
     * <li>Close IndexWriter (flushes pending changes)</li>
     * <li>Close Directory (releases file handles)</li>
     * <li>Nullify references (prevent reuse)</li>
     * </ol>
     */
    private void closeQuietly() {
        if (writer != null) {
            try {
                if (writer.isOpen()) {
                    writer.close();
                    logger.info("Closed Lucene IndexWriter.");
                }
            } catch (IOException e) {
                logger.warn("Error closing IndexWriter (will continue)", e);
            } finally {
                writer = null;
            }
        }

        if (directory != null) {
            try {
                directory.close();
                logger.debug("Closed Lucene Directory.");
            } catch (IOException e) {
                logger.warn("Error closing Directory (will continue)", e);
            } finally {
                directory = null;
            }
        }
    }

    /**
     * Force clears stale lock files from previous crashed sessions.
     * 
     * <p>
     * Lucene uses a Write. Lock file to prevent concurrent writes.
     * If the application crashes, this lock file may remain and prevent
     * new IndexWriter instances from opening. This method forcibly removes
     * the lock file.
     * 
     * <p>
     * <b>Warning:</b> Only call this if you're certain no other process
     * is writing to the index.
     */
    private void forceClearLock() {
        if (indexPath == null) {
            return;
        }

        Path lockFile = indexPath.resolve("write.lock");
        try {
            if (Files.exists(lockFile)) {
                Files.delete(lockFile);
                logger.info("Force deleted stale lock file: {}", lockFile);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete lock file: {}", lockFile, e);
        }
    }

    /**
     * Clears (deletes) the entire index directory for the given run ID.
     * 
     * <p>
     * This method recursively deletes all files and subdirectories
     * in the index directory. Use with caution as this operation is
     * irreversible.
     * 
     * <p>
     * <b>Use Cases:</b>
     * <ul>
     * <li>Re-indexing from scratch</li>
     * <li>Cleanup after analysis is complete</li>
     * <li>Freeing disk space</li>
     * </ul>
     * 
     * @param runId the run ID whose index should be cleared
     */
    @Override
    public void clearIndex(String runId) {
        Path path = Paths.get(INDEX_BASE_DIR, runId);
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                logger.info("Cleared index directory: {}", path);
            } catch (IOException e) {
                logger.warn("Failed to clear index directory: {}", path, e);
            }
        }
    }
}
