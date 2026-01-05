package com.seeloggyplus.service;

import com.seeloggyplus.model.LogEntry;
import java.io.IOException;
import java.util.List;

public interface IndexerService {
    /**
     * Initializes a new index for a specific run or file.
     * 
     * @param runId Unique identifier for this indexing session (e.g., file UUID).
     * @throws IOException If index directory creation fails.
     */
    void initializeIndex(String runId) throws IOException;

    /**
     * Indexes a batch of log entries.
     * 
     * @param entries List of LogEntry objects to index.
     * @throws IOException If writing to index fails.
     */
    void indexBatch(List<LogEntry> entries) throws IOException;

    /**
     * Commits pending changes to the index.
     * 
     * @throws IOException If commit fails.
     */
    void commit() throws IOException;

    /**
     * Closes the index writer and releases resources.
     * 
     * @throws IOException If close fails.
     */
    void close() throws IOException;

    /**
     * Clears/Deletes the index for a given runId (cleanup).
     * 
     * @param runId Unique identifier to clear.
     */
    void clearIndex(String runId);
}
