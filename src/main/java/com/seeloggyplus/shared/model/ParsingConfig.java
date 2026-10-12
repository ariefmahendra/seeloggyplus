package com.seeloggyplus.shared.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ParsingConfig {

    private String id;
    private String name;
    private String description;
    private String regexPattern;
    private transient Pattern compiledPattern;
    private List<String> groupNames;
    private boolean isValid;
    private String validationError;
    private boolean isDefault;
    private String timestampFormat;

    public ParsingConfig(String name, String regexPattern) {
        this.name = name;
        this.description = "";
        this.regexPattern = regexPattern;
        this.groupNames = new ArrayList<>();
        this.isDefault = false;
        validatePattern();
    }

    public void validatePattern() {
        if (this.groupNames == null) {
            this.groupNames = new ArrayList<>();
        }

        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            this.isValid = false;
            this.validationError = "Regex pattern cannot be empty";
            this.groupNames.clear();
            this.compiledPattern = null;
            return;
        }

        try {
            this.compiledPattern = Pattern.compile(regexPattern);
            this.groupNames = extractGroupNames(regexPattern);

            if (groupNames.isEmpty()) {
                this.isValid = false;
                this.validationError = "Pattern must contain at least one named group. Use (?<groupName>...) syntax";
                return;
            }

            this.isValid = true;
            this.validationError = null;
        } catch (PatternSyntaxException e) {
            this.isValid = false;
            this.validationError = "Invalid regex pattern: " + e.getMessage();
            if (this.groupNames == null) {
                this.groupNames = new ArrayList<>();
            }
            this.groupNames.clear();
            this.compiledPattern = null;
        }
    }

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

    public Pattern getCompiledPattern() {
        if (compiledPattern == null && regexPattern != null && !regexPattern.trim().isEmpty()) {
            try {
                compiledPattern = Pattern.compile(regexPattern);
            } catch (PatternSyntaxException e) {
                return null;
            }
        }
        return compiledPattern;
    }

    public String autoDetectTimestampFormat() {
        if (regexPattern == null || regexPattern.isEmpty()) {
            return null;
        }

        Pattern timestampGroupPattern = Pattern.compile("\\(\\?<timestamp>([^)]+)\\)");
        Matcher matcher = timestampGroupPattern.matcher(regexPattern);

        if (!matcher.find()) {
            return null;
        }

        String timestampPattern = matcher.group(1);

        if (timestampPattern.contains("\\d{4}") && timestampPattern.contains("\\d{2}")
                && timestampPattern.contains("\\.\\d{3}")) {
            if (timestampPattern.indexOf("\\d{4}") < timestampPattern.indexOf("\\d{2}")) {
                return "yyyy-MM-dd HH:mm:ss.SSS";
            } else {
                return "dd-MM-yyyy HH:mm:ss.SSS";
            }
        } else if (timestampPattern.contains("\\d{4}") && timestampPattern.contains("\\d{2}")) {
            if (timestampPattern.indexOf("\\d{4}") < timestampPattern.indexOf("\\d{2}")) {
                return "yyyy-MM-dd HH:mm:ss";
            } else {
                return "dd-MM-yyyy HH:mm:ss";
            }
        }

        return "yyyy-MM-dd HH:mm:ss.SSS";
    }

    public ParsingConfig copy() {
        ParsingConfig copy = new ParsingConfig();
        copy.name = this.name;
        copy.description = this.description;
        copy.regexPattern = this.regexPattern;
        copy.timestampFormat = this.timestampFormat;
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
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ParsingConfig that = (ParsingConfig) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(regexPattern, that.regexPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, regexPattern);
    }

    public static final String RAW_CONFIG_ID = "raw_default";

    public static ParsingConfig createRawConfig() {
        ParsingConfig config = new ParsingConfig();
        config.setId(RAW_CONFIG_ID);
        config.setName("Raw Text");
        config.setDescription("Default configuration for raw text viewing");

        config.setRegexPattern("^(?<message>.*)$");
        config.setTimestampFormat(null);
        config.setDefault(true);
        config.validatePattern();
        return config;
    }
}
