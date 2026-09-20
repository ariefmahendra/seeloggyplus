package com.seeloggyplus.update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tiny CLI option parser supporting repeated flags (e.g. {@code --asset a=b}). */
final class CliOptions {

    private final Map<String, List<String>> values;

    private CliOptions(Map<String, List<String>> values) {
        this.values = values;
    }

    static CliOptions parse(String[] args) {
        Map<String, List<String>> values = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + key);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            values.computeIfAbsent(key.substring(2), k -> new ArrayList<>()).add(args[++i]);
        }
        return new CliOptions(values);
    }

    String get(String key, String fallback) {
        List<String> list = values.get(key);
        return (list == null || list.isEmpty()) ? fallback : list.get(0);
    }

    String require(String key) {
        String value = get(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + key);
        }
        return value;
    }

    List<String> all(String key) {
        return values.getOrDefault(key, List.of());
    }
}
