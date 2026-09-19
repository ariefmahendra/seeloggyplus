package com.seeloggyplus.model;

/**
 * A single matching line found inside a remote log file.
 *
 * @param path       absolute remote file path
 * @param lineNumber 1-based line number of the match
 * @param content    the matching line content (without line number)
 */
public record RemoteLogSearchMatch(String path, int lineNumber, String content) {
}
