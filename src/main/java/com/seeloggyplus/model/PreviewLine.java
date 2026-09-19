package com.seeloggyplus.model;

/**
 * One line of a remote log preview.
 *
 * @param lineNumber 1-based absolute line number
 * @param text       the raw line content
 * @param target     true when this is the matched line being previewed
 */
public record PreviewLine(int lineNumber, String text, boolean target) {
}
