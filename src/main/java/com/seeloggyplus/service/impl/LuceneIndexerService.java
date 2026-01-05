package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.service.IndexerService;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementation of IndexerService using Apache Lucene 9.8.0.
 * Writes LogEntry objects into an on-disk inverted index.
 * 
 * Thread-safe with proper resource cleanup and lock handling.
 */
public class LuceneIndexerService implements IndexerService {

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexerService.class);
    private static final String INDEX_BASE_DIR = System.getProperty("java.io.tmpdir") + "/seeloggyplus/index/";

    private IndexWriter writer;
    private Directory directory;
    private Path indexPath;

    @Override
    public void initializeIndex(String runId) throws IOException {
        // Close any previous writer first (safety)
        closeQuietly();

        this.indexPath = Paths.get(INDEX_BASE_DIR, runId);

        // Clean existing index if any (re-indexing scenario)
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
            this.directory = FSDirectory.open(this.indexPath);
            StandardAnalyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);

            // Performance tuning for bulk indexing
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            config.setRAMBufferSizeMB(64.0);

            this.writer = new IndexWriter(directory, config);
            logger.info("Initialized Lucene Index at: {}", this.indexPath.toAbsolutePath());

        } catch (LockObtainFailedException e) {
            logger.warn("Lock file exists, attempting to force clear and retry...");

            // Force clear the lock and retry
            forceClearLock();

            try {
                this.directory = FSDirectory.open(this.indexPath);
                StandardAnalyzer analyzer = new StandardAnalyzer();
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
                config.setRAMBufferSizeMB(64.0);

                this.writer = new IndexWriter(directory, config);
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

    @Override
    public void indexBatch(List<LogEntry> entries) throws IOException {
        if (writer == null || !writer.isOpen()) {
            throw new IOException("IndexWriter is not open. Call initializeIndex first.");
        }

        try {
            for (LogEntry entry : entries) {
                Document doc = new Document();

                // 1. Line Number (Stored + DocValues for sorting + LongPoint for
                // range/counting)
                doc.add(new StoredField("line_number", entry.getLineNumber()));
                doc.add(new NumericDocValuesField("line_number_sort", entry.getLineNumber()));
                doc.add(new LongPoint("line_number_range", entry.getLineNumber()));

                // 2. Timestamp (LongPoint for Range Query, Stored for display, DocValues for
                // sorting)
                long epochMillis = 0;
                if (entry.getTimestamp() != null) {
                    epochMillis = entry.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
                doc.add(new LongPoint("timestamp", epochMillis));
                doc.add(new StoredField("timestamp_store", epochMillis));
                doc.add(new NumericDocValuesField("timestamp_sort", epochMillis));

                // 3. Level (StringField for exact match filters) - uppercase for consistency
                String level = entry.getLevel() != null ? entry.getLevel().toUpperCase() : "";
                doc.add(new StringField("level", level, Field.Store.YES));

                // 4. Message (TextField for Full-Text Search)
                String message = entry.getMessage() != null ? entry.getMessage() : "";
                doc.add(new TextField("message", message, Field.Store.YES));

                // 5. Raw Log (Stored only)
                String raw = entry.getRawLog() != null ? entry.getRawLog() : "";
                doc.add(new StoredField("raw_log", raw));

                writer.addDocument(doc);
            }
        } catch (IOException e) {
            logger.error("Failed to index batch of {} entries", entries.size(), e);
            throw e;
        }
    }

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

    @Override
    public void close() throws IOException {
        closeQuietly();
    }

    /**
     * Safely close writer and directory without throwing exceptions.
     * Always call this before re-initializing or when done.
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
     * Force clear stale lock files from previous crashed sessions.
     */
    private void forceClearLock() {
        if (indexPath == null)
            return;

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

    @Override
    public void clearIndex(String runId) {
        Path path = Paths.get(INDEX_BASE_DIR, runId);
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
                logger.info("Cleared index directory: {}", path);
            } catch (IOException e) {
                logger.warn("Failed to clear index directory: {}", path, e);
            }
        }
    }
}
