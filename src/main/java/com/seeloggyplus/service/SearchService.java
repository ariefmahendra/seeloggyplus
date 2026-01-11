package com.seeloggyplus.service;

import com.seeloggyplus.model.LogEntry;

import java.io.IOException;
import java.util.List;

/**
 * Interface for searching indexed log data.
 * <p>
 * Provides an abstraction for full-text search, filtering, and pagination
 * over a persistence layer (e.g., Lucene, Database).
 * Extends {@link AutoCloseable} to ensuring resources are released.
 */
public interface SearchService extends AutoCloseable {

    /**
     * Opens the index for the given run ID.
     * <p>
     * Must be called before any search operations.
     *
     * @param runId The unique run ID of the index to open.
     * @throws IOException If the index cannot be accessed.
     */
    void openIndex(String runId) throws IOException;

    /**
     * Searches the index based on a query string and filters.
     *
     * @param queryStr      The query string (e.g. "error AND timeout",
     *                      "level:ERROR").
     * @param fromTimestamp Start timestamp (epoch millis). Use 0 for unbounded.
     * @param toTimestamp   End timestamp (epoch millis). Use Long.MAX_VALUE for
     *                      unbounded.
     * @param limit         Maximum number of results to return.
     * @return List of matching {@link LogEntry} objects.
     */
    List<LogEntry> search(String queryStr, long fromTimestamp, long toTimestamp, int limit);

    /**
     * Retrieves a specific page of results (support for virtual scrolling).
     *
     * @param queryStr      The query string.
     * @param fromTimestamp Start timestamp filter.
     * @param toTimestamp   End timestamp filter.
     * @param offset        Starting index (0-based).
     * @param limit         Number of records to retrieve.
     * @return List of {@link LogEntry} objects.
     */
    List<LogEntry> searchPage(String queryStr, long fromTimestamp, long toTimestamp, int offset, int limit);

    /**
     * Retrieves a page of results starting AFTER the given token.
     * <p>
     * Optimization for "Next Page" logic, avoiding deep paging performance hits.
     *
     * @param queryStr      The query string.
     * @param fromTimestamp Start timestamp filter.
     * @param toTimestamp   End timestamp filter.
     * @param afterToken    The opaque token returned from a previous search (e.g.,
     *                      Lucene ScoreDoc).
     * @param limit         Number of records to retrieve.
     * @return List of {@link LogEntry} objects.
     */
    List<LogEntry> searchPageAfter(String queryStr, long fromTimestamp, long toTimestamp, Object afterToken, int limit);

    /**
     * Gets the last search token (cursor) from the most recent search execution.
     * <p>
     * Intended for stateful clients to retrieve the cursor for subsequent requests.
     *
     * @return The cursor token, or null if none available.
     */
    Object getLastSearchAfterToken();

    /**
     * Gets the total hit count for a query.
     *
     * @param queryStr      The query string.
     * @param fromTimestamp Start timestamp filter.
     * @param toTimestamp   End timestamp filter.
     * @return Total number of matching documents.
     */
    long getTotalHits(String queryStr, long fromTimestamp, long toTimestamp);

    /**
     * Gets the offset (position) of a specific line number within the sorted search
     * results.
     * <p>
     * Useful for synchronization between views (e.g. jumping to a line).
     *
     * @param lineNumber The target line number.
     * @return The 0-indexed position of this line in the result set, or -1 if not
     *         found/error.
     */
    long getOffsetForLineNumber(long lineNumber);

    /**
     * Closes the searcher and leases any system resources.
     */
    @Override
    void close();
}
