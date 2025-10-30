package com.seeloggyplus.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    public ParsingConfig(String name, String regexPattern) {
        this.name = name;
        this.description = "";
        this.regexPattern = regexPattern;
        this.groupNames = new ArrayList<>();
        this.isDefault = false;
        validatePattern();
    }

    /**
     * Validates the regex pattern and extracts named groups
     */
    public void validatePattern() {
        // Initialize groupNames if null
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
}
