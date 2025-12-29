package com.seeloggyplus.pipeline.filters;

import com.seeloggyplus.pipeline.Event;
import com.seeloggyplus.pipeline.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter that applies a Regex pattern to the raw log (or a specific field).
 * Extracts named groups into event fields.
 */
public class RegexFilter implements Filter {
    private final Pattern pattern;
    private final List<String> groupNames;
    private final String sourceField; // Default is null, meaning use raw log

    public RegexFilter(Pattern pattern, List<String> groupNames) {
        this(pattern, groupNames, null);
    }

    public RegexFilter(Pattern pattern, List<String> groupNames, String sourceField) {
        this.pattern = pattern;
        this.groupNames = groupNames != null ? groupNames : new ArrayList<>();
        this.sourceField = sourceField;
    }

    @Override
    public boolean apply(Event event) {
        String input;

        if (sourceField == null) {
            input = event.getRaw();
        } else {
            Object val = event.getField(sourceField);
            input = val != null ? val.toString() : null;
        }

        if (input == null)
            return true; // Nothing to match, just pass through

        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            for (String group : groupNames) {
                try {
                    String value = matcher.group(group);
                    if (value != null) {
                        event.addField(group, value.trim());
                    }
                } catch (IllegalArgumentException ignored) {
                    // Group might not exist in this pattern variant
                }
            }
        } else {
            event.addTag("_grokparsefailure");
        }

        return true;
    }
}
