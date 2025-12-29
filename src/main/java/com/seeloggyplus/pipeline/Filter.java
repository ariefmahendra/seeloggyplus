package com.seeloggyplus.pipeline;

/**
 * Interface for a filter in the log processing pipeline.
 * Filters process an Event and determine if it should continue down the chain.
 */
public interface Filter {
    /**
     * Apply filter logic to the event.
     * 
     * @param event The log event to process.
     * @return true to pass the event to the next filter, false to drop the event.
     */
    boolean apply(Event event);
}
