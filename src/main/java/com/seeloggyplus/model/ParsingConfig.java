package com.seeloggyplus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Model class for log parsing configuration using regex patterns with named groups
 * The named groups in the regex pattern will be used as column headers in the log table viewer
 */
public class ParsingConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private String description;
    private String regexPattern;
    private transient Pattern compiledPattern;
    private List<String> groupNames;
    private boolean isValid;
    private String validationError;
    private boolean isDefault;

    public ParsingConfig() {
        this.name = "New Configuration";
        this.description = "";
        this.regexPattern = "";
        this.groupNames = new ArrayList<>();
        this.isValid = false;
        this.isDefault = false;
    }

    public ParsingConfig(String name, String regexPattern) {
        this.name = name;
        this.description = "";
        this.regexPattern = regexPattern;
        this.groupNames = new ArrayList<>();
        this.isDefault = false;
        validatePattern();
    }

    public ParsingConfig(String name, String description, String regexPattern) {
        this.name = name;
        this.description = description;
        this.regexPattern = regexPattern;
        this.groupNames = new ArrayList<>();
        this.isDefault = false;
        validatePattern();
    }

    /**
     * Validates the regex pattern and extracts named groups
     */
    public boolean validatePattern() {
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            this.isValid = false;
            this.validationError = "Regex pattern cannot be empty";
            this.groupNames.clear();
            this.compiledPattern = null;
            return false;
        }

        try {
            this.compiledPattern = Pattern.compile(regexPattern);
            this.groupNames = extractGroupNames(regexPattern);

            if (groupNames.isEmpty()) {
                this.isValid = false;
                this.validationError = "Pattern must contain at least one named group. Use (?<groupName>...) syntax";
                return false;
            }

            this.isValid = true;
            this.validationError = null;
            return true;
        } catch (PatternSyntaxException e) {
            this.isValid = false;
            this.validationError = "Invalid regex pattern: " + e.getMessage();
            this.groupNames.clear();
            this.compiledPattern = null;
            return false;
        }
    }

    /**
     * Extract named group names from regex pattern
     */
    private List<String> extractGroupNames(String pattern) {
        List<String> names = new ArrayList<>();
        Pattern namedGroupPattern = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");
        Matcher matcher = namedGroupPattern.matcher(pattern);

        while (matcher.find()) {
            String groupName = matcher.group(1);
            if (!names.contains(groupName)) {
                names.add(groupName);
            }
        }

        return names;
    }

    /**
     * Get compiled pattern, compiling if necessary
     */
    public Pattern getCompiledPattern() {
        if (compiledPattern == null && regexPattern != null && !regexPattern.isEmpty()) {
            try {
                compiledPattern = Pattern.compile(regexPattern);
            } catch (PatternSyntaxException e) {
                return null;
            }
        }
        return compiledPattern;
    }

    /**
     * Test the pattern against a sample log line
     */
    public boolean testPattern(String sampleLog) {
        if (!isValid || compiledPattern == null) {
            return false;
        }

        try {
            Matcher matcher = compiledPattern.matcher(sampleLog);
            return matcher.find();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse a log line and extract named group values
     */
    public java.util.Map<String, String> parse(String logLine) {
        java.util.Map<String, String> result = new java.util.HashMap<>();

        if (!isValid || compiledPattern == null || logLine == null) {
            return result;
        }

        try {
            Matcher matcher = compiledPattern.matcher(logLine);
            if (matcher.find()) {
                for (String groupName : groupNames) {
                    try {
                        String value = matcher.group(groupName);
                        result.put(groupName, value != null ? value : "");
                    } catch (IllegalArgumentException e) {
                        // Group doesn't exist in this match
                        result.put(groupName, "");
                    }
                }
            }
        } catch (Exception e) {
            // Return empty map on error
        }

        return result;
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
        validatePattern();
    }

    public List<String> getGroupNames() {
        return new ArrayList<>(groupNames);
    }

    public boolean isValid() {
        return isValid;
    }

    public String getValidationError() {
        return validationError;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * Create a copy of this configuration
     */
    public ParsingConfig copy() {
        ParsingConfig copy = new ParsingConfig();
        copy.name = this.name;
        copy.description = this.description;
        copy.regexPattern = this.regexPattern;
        copy.isDefault = false;
        copy.validatePattern();
        return copy;
    }

    @Override
    public String toString() {
        return name + (isDefault ? " (Default)" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParsingConfig that = (ParsingConfig) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(regexPattern, that.regexPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, regexPattern);
    }

    /**
     * Factory method to create common log format configurations
     */
    public static ParsingConfig createDefaultConfig() {
        ParsingConfig config = new ParsingConfig();
        config.setName("Default Log Format");
        config.setDescription("Standard log format with timestamp, level, logger, and message");
        config.setRegexPattern(
            "(?<timestamp>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})\\s+" +
            "(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+" +
            "\\[(?<thread>[^\\]]+)\\]\\s+" +
            "(?<logger>[^\\s]+)\\s+-\\s+" +
            "(?<message>.*)"
        );
        config.setDefault(true);
        config.validatePattern();
        return config;
    }

    public static ParsingConfig createApacheAccessLogConfig() {
        ParsingConfig config = new ParsingConfig();
        config.setName("Apache Access Log");
        config.setDescription("Apache/Nginx access log format");
        config.setRegexPattern(
            "(?<ip>[\\d.]+)\\s+" +
            "(?<identity>\\S+)\\s+" +
            "(?<user>\\S+)\\s+" +
            "\\[(?<timestamp>[^\\]]+)\\]\\s+" +
            "\"(?<method>\\S+)\\s+(?<path>\\S+)\\s+(?<protocol>\\S+)\"\\s+" +
            "(?<status>\\d+)\\s+" +
            "(?<size>\\S+)\\s+" +
            "\"(?<referer>[^\"]*)\"\\s+" +
            "\"(?<useragent>[^\"]*)\""
        );
        config.validatePattern();
        return config;
    }

    public static ParsingConfig createJsonLogConfig() {
        ParsingConfig config = new ParsingConfig();
        config.setName("JSON Log Format");
        config.setDescription("Structured JSON log format");
        config.setRegexPattern(
            "\\{.*\"timestamp\"\\s*:\\s*\"(?<timestamp>[^\"]+)\".*" +
            "\"level\"\\s*:\\s*\"(?<level>[^\"]+)\".*" +
            "\"message\"\\s*:\\s*\"(?<message>[^\"]+)\".*\\}"
        );
        config.validatePattern();
        return config;
    }
}
