package com.seeloggyplus.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for generating pagination page numbers.
 * Follows UX best practices similar to Google/GitHub pagination.
 */
public final class PaginationUtils {

    /**
     * Special constant to represent ellipsis ("...") in the page list.
     */
    public static final int ELLIPSIS = -1;

    private PaginationUtils() {
        // Utility class - no instantiation
    }

    /**
     * Generates a list of page numbers to display in the pagination bar.
     * Uses ellipsis (-1) to represent skipped pages.
     *
     * Logic:
     * - If totalPages <= 7: show all pages
     * - At start (currentPage <= 3): show 1,2,3,4 ... lastPage-1, lastPage
     * - At end (currentPage >= totalPages-2): show 1,2 ... lastPage-3, lastPage-2,
     * lastPage-1, lastPage
     * - At middle: show 1,2 ... currentPage-1, currentPage, currentPage+1 ...
     * lastPage-1, lastPage
     *
     * @param currentPage Current active page (1-indexed)
     * @param totalPages  Total number of pages
     * @return List of page numbers, where -1 represents ellipsis
     */
    public static List<Integer> getPageNumbers(int currentPage, int totalPages) {
        List<Integer> pages = new ArrayList<>();

        if (totalPages <= 0) {
            return pages;
        }

        if (totalPages <= 7) {
            // Show all pages
            for (int i = 1; i <= totalPages; i++) {
                pages.add(i);
            }
            return pages;
        }

        // Always show first 2 pages
        pages.add(1);
        pages.add(2);

        if (currentPage <= 3) {
            // At Start: 1, 2, 3, 4, ..., lastPage-1, lastPage
            pages.add(3);
            pages.add(4);
            pages.add(ELLIPSIS);
            pages.add(totalPages - 1);
            pages.add(totalPages);
        } else if (currentPage >= totalPages - 2) {
            // At End: 1, 2, ..., lastPage-3, lastPage-2, lastPage-1, lastPage
            pages.add(ELLIPSIS);
            pages.add(totalPages - 3);
            pages.add(totalPages - 2);
            pages.add(totalPages - 1);
            pages.add(totalPages);
        } else {
            // At Middle: 1, 2, ..., currentPage-1, currentPage, currentPage+1, ...,
            // lastPage-1, lastPage
            pages.add(ELLIPSIS);
            pages.add(currentPage - 1);
            pages.add(currentPage);
            pages.add(currentPage + 1);
            pages.add(ELLIPSIS);
            pages.add(totalPages - 1);
            pages.add(totalPages);
        }

        return pages;
    }

    /**
     * Calculates total number of pages based on total entries and page size.
     *
     * @param totalEntries Total number of log entries
     * @param pageSize     Number of entries per page
     * @return Total number of pages
     */
    public static int calculateTotalPages(int totalEntries, int pageSize) {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalEntries / pageSize);
    }

    /**
     * Calculates current page number based on start index and page size.
     *
     * @param startIndex Current window start index (0-indexed)
     * @param pageSize   Number of entries per page
     * @return Current page number (1-indexed)
     */
    public static int calculateCurrentPage(int startIndex, int pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return (startIndex / pageSize) + 1;
    }

    /**
     * Calculates start index for a given page number.
     *
     * @param pageNumber Page number (1-indexed)
     * @param pageSize   Number of entries per page
     * @return Start index for that page (0-indexed)
     */
    public static int calculateStartIndex(int pageNumber, int pageSize) {
        return (pageNumber - 1) * pageSize;
    }
}
