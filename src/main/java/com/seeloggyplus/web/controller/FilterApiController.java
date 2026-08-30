package com.seeloggyplus.web.controller;

import com.seeloggyplus.model.SavedFilter;
import com.seeloggyplus.service.SavedFilterService;
import com.seeloggyplus.web.dto.ApiResponse;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class FilterApiController {

    private static final Logger logger = LoggerFactory.getLogger(FilterApiController.class);
    private final SavedFilterService filterService;

    public FilterApiController(SavedFilterService filterService) {
        this.filterService = filterService;
    }

    public void getAllFilters(Context ctx) {
        try {
            List<SavedFilter> filters = filterService.getAllFilters();
            ctx.json(ApiResponse.ok(filters));
        } catch (Exception e) {
            logger.error("Failed to load filters", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    public void saveFilter(Context ctx) {
        try {
            SavedFilter filter = ctx.bodyAsClass(SavedFilter.class);
            if (filter.getId() == null || filter.getId().isBlank()) {
                filter.setId(UUID.randomUUID().toString());
            }
            filterService.saveFilter(filter);
            ctx.json(ApiResponse.ok("Filter saved successfully", filter));
        } catch (Exception e) {
            logger.error("Failed to save filter", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    public void deleteFilter(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            filterService.deleteFilter(id);
            ctx.json(ApiResponse.ok("Filter deleted successfully", null));
        } catch (Exception e) {
            logger.error("Failed to delete filter", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }
}
