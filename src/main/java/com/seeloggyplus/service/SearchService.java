package com.seeloggyplus.service;

import com.seeloggyplus.model.LogEntry;
import java.util.List;

public interface SearchService {

    /**
     * Searches the index based on a query string and filters.
     * 
     * @param queryStr      The Lucene query string (e.g. "error AND timeout").
     * @param fromTimestamp Start timestamp (epoch millis). Use 0 for unbounded.
     * @param toTimestamp   End timestamp (epoch millis). Use Long.MAX_VALUE for
     *                      unbounded.
     * @param limit         Maximum number of results to return.
     * @return List of LogEntry objects matching the criteria.
     */
    List<LogEntry> search(String queryStr, long fromTimestamp, long toTimestamp, int limit);

    /**
     * Retrieves a specific page of results (virtual scrolling support).
     * 
     * @param queryStr The Lucene query string.
     * @param offset   Starting index (0-based).
     * @param limit    Number of records to retrieve.
     * @return List of LogEntry objects.
     */
    List<LogEntry> searchPage(String queryStr, long fromTimestamp, long toTimestamp, int offset, int limit);

    /**
     * Retrieves a page of results starting AFTER the given token.
     * This is strictly for optimization of "Next Page" logic.
     * 
     * @param afterToken The opaque token returned from previous search (ScoreDoc).
     * @param limit      Number of records to retrieve.
     * @return List of LogEntry objects.
     */
    List<LogEntry> searchPageAfter(String queryStr, long fromTimestamp, long toTimestamp, Object afterToken, int limit);

    /**
     * Gets the last search token (ScoreDoc) from the most recent search execution
     * on this service.
     * This helps stateful clients retrieve the cursor.
     */
    Object getLastSearchAfterToken();

    /**
     * Gets the total hit count for a query.
     * 
     * @param queryStr The Lucene query string.
     * @return Total number of matching documents.
     */
    long getTotalHits(String queryStr, long fromTimestamp, long toTimestamp);

    /**
     * Closes the searcher and releases resources.
     */
    void close();

    /**
     * Gets the offset (position) of a line number in the sorted results.
     * This counts how many entries have line_number less than the target.
     * 
     * @param lineNumber The target line number.
     * @return The offset (0-indexed position) of this line in sorted results.
     */
    long getOffsetForLineNumber(long lineNumber);

    /**
     * Opens the index for the given run ID.
     * 
     * @param runId The unique run ID of the index.
     * @throws java.io.IOException If the index cannot be opened.
     */
    void openIndex(String runId) throws java.io.IOException;
}
