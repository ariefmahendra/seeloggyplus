package com.seeloggyplus.pipeline;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

/**
 * Represents a log record being processed through the pipeline.
 * Contains the raw log data, parsed fields, and metadata tags.
 */
@Getter
@Setter
public class Event {
    private String raw;
    private Map<String, Object> fields;
    private Set<String> tags;
    private Instant timestamp;

    public Event(String raw) {
        this.raw = raw;
        this.fields = new HashMap<>();
        this.tags = new HashSet<>();
        this.timestamp = Instant.now();
    }

    public void addField(String key, Object value) {
        if (key != null && value != null) {
            this.fields.put(key, value);
        }
    }

    public Object getField(String key) {
        return this.fields.get(key);
    }

    public boolean hasField(String key) {
        return this.fields.containsKey(key);
    }

    public void addTag(String tag) {
        if (tag != null && !tag.isEmpty()) {
            this.tags.add(tag);
        }
    }

    public boolean hasTag(String tag) {
        return this.tags.contains(tag);
    }

    public void removeField(String key) {
        this.fields.remove(key);
    }
}
