package com.seeloggyplus.web.controller;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.service.RecentFileService;
import com.seeloggyplus.web.dto.ApiResponse;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RecentFilesApiController {

    private static final Logger logger = LoggerFactory.getLogger(RecentFilesApiController.class);
    private final RecentFileService recentFileService;

    public RecentFilesApiController(RecentFileService recentFileService) {
        this.recentFileService = recentFileService;
    }

    public void getAllRecentFiles(Context ctx) {
        try {
            List<RecentFilesDto> list = recentFileService.findAll();
            ctx.json(ApiResponse.ok(list));
        } catch (Exception e) {
            logger.error("Failed to get recent files", e);
            ctx.json(ApiResponse.error("Failed to load recent files: " + e.getMessage()));
        }
    }

    public void clearAllRecentFiles(Context ctx) {
        try {
            recentFileService.deleteAll();
            ctx.json(ApiResponse.ok("Recent files cleared", null));
        } catch (Exception e) {
            logger.error("Failed to clear recent files", e);
            ctx.json(ApiResponse.error("Failed to clear recent files: " + e.getMessage()));
        }
    }

    public void deleteRecentFile(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            recentFileService.deleteByFileId(id);
            ctx.json(ApiResponse.ok("Recent file deleted", null));
        } catch (Exception e) {
            logger.error("Failed to delete recent file", e);
            ctx.json(ApiResponse.error("Failed to delete recent file: " + e.getMessage()));
        }
    }
}
