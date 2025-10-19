package com.seeloggyplus.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Model class representing a single log entry
 * Designed for performance with lazy loading and efficient memory usage
 */
public class LogEntry {

    private final long lineNumber;
    private final String rawLog;
    private final Map<String, String> parsedFields;
    private LocalDateTime timestamp;
    private String level;
    private String message;
    private String thread;
    private String logger;
    private boolean isParsed;

    public LogEntry(long lineNumber, String rawLog) {
        this.lineNumber = lineNumber;
        this.rawLog = rawLog;
        this.parsedFields = new HashMap<>();
        this.isParsed = false;
    }

    public LogEntry(long lineNumber, String rawLog, Map<String, String> parsedFields) {
        this.lineNumber = lineNumber;
        this.rawLog = rawLog;
        this.parsedFields = new HashMap<>(parsedFields);
        this.isParsed = true;

        // Extract common fields
        this.level = parsedFields.getOrDefault("level", "");
        this.message = parsedFields.getOrDefault("message", "");
        this.thread = parsedFields.getOrDefault("thread", "");
        this.logger = parsedFields.getOrDefault("logger", "");

        // Parse timestamp if available
        String timestampStr = parsedFields.get("timestamp");
        if (timestampStr != null) {
            try {
                this.timestamp = LocalDateTime.parse(timestampStr);
            } catch (Exception e) {
                this.timestamp = null;
            }
        }
    }

    // Getters
    public long getLineNumber() {
        return lineNumber;
    }

    public String getRawLog() {
        return rawLog;
    }

    public Map<String, String> getParsedFields() {
        return new HashMap<>(parsedFields);
    }

    public String getField(String fieldName) {
        return parsedFields.get(fieldName);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
        if (timestamp != null) {
            parsedFields.put("timestamp", timestamp.toString());
        }
    }

    public String getLevel() {
        return level != null ? level : "";
    }

    public void setLevel(String level) {
        this.level = level;
        parsedFields.put("level", level);
    }

    public String getMessage() {
        return message != null ? message : "";
    }

    public void setMessage(String message) {
        this.message = message;
        parsedFields.put("message", message);
    }

    public String getThread() {
        return thread != null ? thread : "";
    }

    public void setThread(String thread) {
        this.thread = thread;
        parsedFields.put("thread", thread);
    }

    public String getLogger() {
        return logger != null ? logger : "";
    }

    public void setLogger(String logger) {
        this.logger = logger;
        parsedFields.put("logger", logger);
    }

    public boolean isParsed() {
        return isParsed;
    }

    public void setParsed(boolean parsed) {
        isParsed = parsed;
    }

    public void addField(String key, String value) {
        parsedFields.put(key, value);
    }

    public void addFields(Map<String, String> fields) {
        parsedFields.putAll(fields);
    }

    /**
     * Check if log entry matches search criteria
     */
    public boolean matches(String searchText, boolean caseSensitive) {
        if (searchText == null || searchText.isEmpty()) {
            return true;
        }

        String searchIn = caseSensitive ? rawLog : rawLog.toLowerCase();
        String searchFor = caseSensitive ? searchText : searchText.toLowerCase();

        return searchIn.contains(searchFor);
    }

    /**
     * Check if log entry matches regex pattern
     */
    public boolean matchesRegex(String regex) {
        if (regex == null || regex.isEmpty()) {
            return true;
        }

        try {
            return rawLog.matches(regex);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return rawLog;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (lineNumber ^ (lineNumber >>> 32));
        result = prime * result + ((rawLog == null) ? 0 : rawLog.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LogEntry other = (LogEntry) obj;
        if (lineNumber != other.lineNumber)
            return false;
        if (rawLog == null) {
            if (other.rawLog != null)
                return false;
        } else if (!rawLog.equals(other.rawLog))
            return false;
        return true;
    }
}
