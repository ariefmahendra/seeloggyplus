package com.seeloggyplus.shared.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class LogEntry {

    @Getter
    private final long lineNumber;
    @Getter
    private final long endLineNumber;
    @Getter
    private final String rawLog;
    private final Map<String, String> parsedFields;
    @Getter
    private LocalDateTime timestamp;
    private String level;
    private String message;
    private String thread;
    private String logger;
    @Setter
    @Getter
    private boolean isParsed;

    public LogEntry(long lineNumber, String rawLog) {
        this(lineNumber, lineNumber, rawLog, new HashMap<>(), false);
    }

    public LogEntry(long startLineNumber, long endLineNumber, String rawLog) {
        this(startLineNumber, endLineNumber, rawLog, createUnparsedFieldMap(rawLog), false);
    }

    public LogEntry(long lineNumber, String rawLog, Matcher matcher, List<String> groupNames) {
        this(lineNumber, lineNumber, rawLog, createMapFromMatcher(matcher, groupNames), true);
    }

    public LogEntry(long lineNumber, String rawLog, Map<String, String> parsedFields) {
        this(lineNumber, lineNumber, rawLog, parsedFields, true);
    }

    private LogEntry(long lineNumber, long endLineNumber, String rawLog, Map<String, String> parsedFields,
            boolean isParsed) {
        this.lineNumber = lineNumber;
        this.endLineNumber = endLineNumber;
        this.rawLog = rawLog;
        this.parsedFields = new HashMap<>(parsedFields);
        this.isParsed = isParsed;

        if (isParsed) {
            this.level = parsedFields.getOrDefault("level", "");
            this.message = parsedFields.getOrDefault("message", "");
            this.thread = parsedFields.getOrDefault("thread", "");
            this.logger = parsedFields.getOrDefault("logger", "");

            this.timestamp = null;
        } else {
            this.message = rawLog;
        }
    }

    public Map<String, String> getParsedFields() {
        return new HashMap<>(parsedFields);
    }

    public String getField(String fieldName) {
        return parsedFields.get(fieldName);
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

    public void addField(String key, String value) {
        parsedFields.put(key, value);
    }

    public void addFields(Map<String, String> fields) {
        parsedFields.putAll(fields);
    }

    private static Map<String, String> createMapFromMatcher(Matcher matcher, List<String> groupNames) {
        Map<String, String> fields = new HashMap<>();
        for (String groupName : groupNames) {
            fields.put(groupName, matcher.group(groupName));
        }
        return fields;
    }

    private static Map<String, String> createUnparsedFieldMap(String rawLog) {
        Map<String, String> fields = new HashMap<>();
        fields.put("unparsed", rawLog);
        return fields;
    }

    public boolean matches(String searchText, boolean caseSensitive) {
        if (searchText == null || searchText.isEmpty()) {
            return true;
        }

        String searchIn = caseSensitive ? rawLog : rawLog.toLowerCase();
        String searchFor = caseSensitive ? searchText : searchText.toLowerCase();

        return searchIn.contains(searchFor);
    }

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
        result = prime * result + Long.hashCode(lineNumber);
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
