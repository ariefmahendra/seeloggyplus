package com.seeloggyplus.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline engine that executes a chain of filters on log events.
 */
public class Pipeline {
    private static final Logger logger = LoggerFactory.getLogger(Pipeline.class);
    private final List<Filter> filters;

    public Pipeline() {
        this.filters = new ArrayList<>();
    }

    public void addFilter(Filter filter) {
        this.filters.add(filter);
    }

    public Event process(String rawLog) {
        if (rawLog == null)
            return null;

        Event event = new Event(rawLog);

        for (Filter filter : filters) {
            try {
                // If a filter returns false, we drop the event
                if (!filter.apply(event)) {
                    return null;
                }
            } catch (Exception e) {
                // We catch exceptions so one bad filter doesn't crash the whole parsing thread
                logger.warn("Error in filter execution: {}", e.getMessage());
                event.addTag("_pipeline_error");
                event.addField("_error_msg", e.getMessage());
            }
        }

        return event;
    }
}
