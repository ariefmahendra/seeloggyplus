package com.seeloggyplus.service;

import com.seeloggyplus.model.LogEntry;

import java.io.IOException;
import java.util.List;

/**
 * Interface for log indexing services.
 * <p>
 * Provides an abstraction for creating, writing to, and managing a search index
 * (e.g., Lucene).
 * Supports batch operations for performance and extends {@link AutoCloseable}
 * for
 * try-with-resources support.
 */
public interface IndexerService extends AutoCloseable {

    /**
     * Initializes the index structure for a specific run ID.
     * <p>
     * Should handle directory creation, clearing old data, and preparing the
     * writer.
     *
     * @param runId Unique identifier for the indexing session (e.g., file hash or
     *              UUID).
     * @throws IOException If initialization fails (e.g., permission issues).
     */
    void initializeIndex(String runId) throws IOException;

    /**
     * Indexes a batch of log entries.
     * <p>
     * Implementation should optimize for bulk writes.
     *
     * @param entries List of {@link LogEntry} objects to index.
     * @throws IOException If writing to the index fails.
     */
    void indexBatch(List<LogEntry> entries) throws IOException;

    /**
     * Commits all pending changes to the index.
     * <p>
     * Flushes buffers to persistent storage.
     *
     * @throws IOException If commit fails.
     */
    void commit() throws IOException;

    /**
     * Closes the index service and releases system resources (files, locks).
     *
     * @throws IOException If closure fails.
     */
    @Override
    void close() throws IOException;

    /**
     * Deletes the index data for the specified run ID.
     * <p>
     * Useful for cleanup or re-indexing.
     *
     * @param runId The unique identifier of the index to clear.
     */
    void clearIndex(String runId);
}
