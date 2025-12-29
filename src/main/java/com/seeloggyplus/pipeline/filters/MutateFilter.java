package com.seeloggyplus.pipeline.filters;

import com.seeloggyplus.pipeline.Event;
import com.seeloggyplus.pipeline.Filter;

import java.util.List;
import java.util.Map;

/**
 * Filter for mutation operations: Rename, Uppercase, Lowercase, Remove.
 */
public class MutateFilter implements Filter {
    private List<String> uppercaseFields;
    private List<String> lowercaseFields;
    private Map<String, String> renameFields;
    private List<String> removeFields;

    public MutateFilter uppercase(List<String> fields) {
        this.uppercaseFields = fields;
        return this;
    }

    public MutateFilter lowercase(List<String> fields) {
        this.lowercaseFields = fields;
        return this;
    }

    public MutateFilter rename(Map<String, String> renames) {
        this.renameFields = renames;
        return this;
    }

    public MutateFilter remove(List<String> fields) {
        this.removeFields = fields;
        return this;
    }

    @Override
    public boolean apply(Event event) {
        if (uppercaseFields != null) {
            for (String field : uppercaseFields) {
                Object val = event.getField(field);
                if (val instanceof String) {
                    event.addField(field, ((String) val).toUpperCase());
                }
            }
        }

        if (lowercaseFields != null) {
            for (String field : lowercaseFields) {
                Object val = event.getField(field);
                if (val instanceof String) {
                    event.addField(field, ((String) val).toLowerCase());
                }
            }
        }

        if (renameFields != null) {
            for (Map.Entry<String, String> entry : renameFields.entrySet()) {
                String oldName = entry.getKey();
                String newName = entry.getValue();
                if (event.hasField(oldName)) {
                    Object val = event.getField(oldName);
                    event.addField(newName, val);
                    event.removeField(oldName);
                }
            }
        }

        if (removeFields != null) {
            for (String field : removeFields) {
                event.removeField(field);
            }
        }

        return true;
    }
}
