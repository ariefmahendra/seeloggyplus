package com.seeloggyplus.pipeline.filters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeloggyplus.pipeline.Event;
import com.seeloggyplus.pipeline.Filter;

import java.util.Map;

/**
 * Parses the raw log (or a field) as JSON and merges keys into the event.
 */
public class JsonFilter implements Filter {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final String sourceField;

    public JsonFilter() {
        this(null);
    }

    public JsonFilter(String sourceField) {
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
            return true;

        // Quick check
        input = input.trim();
        if (!input.startsWith("{"))
            return true;

        try {
            Map<String, Object> map = mapper.readValue(input, new TypeReference<Map<String, Object>>() {
            });
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                event.addField(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            event.addTag("_jsonparsefailure");
        }

        return true;
    }
}
