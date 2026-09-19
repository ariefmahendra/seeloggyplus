package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.PreviewLine;
import com.seeloggyplus.model.RemoteLogSearchMatch;
import com.seeloggyplus.service.RemoteLogSearchService;
import com.seeloggyplus.service.SSHService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Remote log search using {@code find}/{@code grep} on the server.
 * <p>
 * Design notes (performance):
 * <ul>
 *   <li>Search returns matching lines with numbers via {@code grep -n}, one remote pass.</li>
 *   <li>Results are capped to avoid flooding the UI.</li>
 *   <li>Preview is lazy and only loads a small line range per selected match.</li>
 * </ul>
 */
public class RemoteLogSearchServiceImpl implements RemoteLogSearchService {

    static final int MAX_RESULTS = 500;

    @Override
    public List<RemoteLogSearchMatch> search(SSHService ssh, String rootPath, String query,
                                             boolean regex, boolean caseSensitive) throws IOException {
        if (ssh == null || rootPath == null || rootPath.isBlank() || query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Search server, root path, and query are required");
        }
        String grepFlags = "n" + (caseSensitive ? "" : "i") + (regex ? "E" : "F") + "H";
        // Search every regular file, not just known log extensions.
        String command = "find " + quote(rootPath)
                + " -type f -exec grep -" + grepFlags + " --binary-files=without-match " + quote(query) + " {} + 2>/dev/null"
                + " | head -n " + MAX_RESULTS;
        return parseMatches(ssh.executeCommand(command));
    }

    @Override
    public List<PreviewLine> preview(SSHService ssh, String path, int centerLine,
                                     int before, int after) throws IOException {
        if (ssh == null || path == null || path.isBlank() || centerLine <= 0 || before < 0 || after < 0) {
            throw new IllegalArgumentException("Preview path, line, and context must be valid");
        }
        int start = Math.max(1, centerLine - before);
        int end = centerLine + after;
        String command = "awk 'NR>=" + start + " && NR<=" + end + " {print NR \":\" $0}' " + quote(path);
        return parsePreview(ssh.executeCommand(command), centerLine);
    }

    @Override
    public long lineCount(SSHService ssh, String path) throws IOException {
        if (ssh == null || path == null || path.isBlank()) {
            return -1;
        }
        try {
            String output = ssh.executeCommand("wc -l < " + quote(path));
            if (output == null) {
                return -1;
            }
            String trimmed = output.trim();
            if (trimmed.isEmpty()) {
                return -1;
            }
            String first = trimmed.split("\\s+")[0];
            return Long.parseLong(first);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static List<RemoteLogSearchMatch> parseMatches(String output) {
        if (output == null || output.isEmpty()) {
            return List.of();
        }
        Set<RemoteLogSearchMatch> unique = new LinkedHashSet<>();
        for (String raw : output.split("\\R")) {
            RemoteLogSearchMatch match = parseMatchLine(raw);
            if (match != null) {
                unique.add(match);
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * Parses {@code path:lineNumber:content}.
     * <p>
     * Scanning from the left for the first {@code :<digits>:} keeps content that
     * contains colons and digits intact (e.g. {@code "...port:8080:refused"}).
     */
    static RemoteLogSearchMatch parseMatchLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        int separator = findLineSeparator(line);
        if (separator < 0) {
            return null;
        }
        int end = separator + 1;
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }
        if (end >= line.length() || line.charAt(end) != ':') {
            return null;
        }
        try {
            int lineNumber = Integer.parseInt(line.substring(separator + 1, end));
            return new RemoteLogSearchMatch(line.substring(0, separator), lineNumber, line.substring(end + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int findLineSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != ':') {
                continue;
            }
            int j = i + 1;
            while (j < line.length() && Character.isDigit(line.charAt(j))) {
                j++;
            }
            if (j > i + 1 && j < line.length() && line.charAt(j) == ':') {
                return i;
            }
        }
        return -1;
    }

    static List<PreviewLine> parsePreview(String output, int targetLine) {
        if (output == null || output.isEmpty()) {
            return List.of();
        }
        List<PreviewLine> lines = new ArrayList<>();
        for (String raw : output.split("\\R")) {
            if (raw.isEmpty()) {
                continue;
            }
            int separator = raw.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            try {
                int lineNumber = Integer.parseInt(raw.substring(0, separator).trim());
                String text = raw.substring(separator + 1);
                lines.add(new PreviewLine(lineNumber, text, lineNumber == targetLine));
            } catch (NumberFormatException ignored) {
                // skip malformed preview lines defensively
            }
        }
        return lines;
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
