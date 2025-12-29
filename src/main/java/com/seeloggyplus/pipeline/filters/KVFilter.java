package com.seeloggyplus.pipeline.filters;

import com.seeloggyplus.pipeline.Event;
import com.seeloggyplus.pipeline.Filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Key-Value Filter
 * Parses "key=value" or "key:value" pairs from the log.
 */
public class KVFilter implements Filter {
    // Regex to capture k=v or k="v" or k='v'
    // Simplified: (\w+)=([^\s"']+)|(\w+)="([^"]*)"|(\w+)='([^']*)'
    private static final Pattern KV_PATTERN = Pattern.compile(
            "([\\w.-]+)=([\"'])(.*?)\\2|([\\w.-]+)=([^\\s]+)");

    @Override
    public boolean apply(Event event) {
        String raw = event.getRaw();
        if (raw == null)
            return true;

        Matcher matcher = KV_PATTERN.matcher(raw);
        while (matcher.find()) {
            String key;
            String value;

            if (matcher.group(1) != null) {
                // Quoted value
                key = matcher.group(1);
                value = matcher.group(3);
            } else {
                // Unquoted value
                key = matcher.group(4);
                value = matcher.group(5);
            }

            if (key != null) {
                event.addField(key, value);
            }
        }
        return true;
    }
}
