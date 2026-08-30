package com.seeloggyplus.web.controller;

import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.service.LogParser;
import com.seeloggyplus.service.ParsingConfigService;
import com.seeloggyplus.web.dto.ApiResponse;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ParsingConfigApiController {

    private static final Logger logger = LoggerFactory.getLogger(ParsingConfigApiController.class);
    private final ParsingConfigService configService;
    private final LogParser logParser;

    public ParsingConfigApiController(ParsingConfigService configService, LogParser logParser) {
        this.configService = configService;
        this.logParser = logParser;
    }

    public void getAllConfigs(Context ctx) {
        try {
            List<ParsingConfig> configs = configService.findAll();
            ctx.json(ApiResponse.ok(configs));
        } catch (Exception e) {
            logger.error("Failed to load parsing configs", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    public void saveConfig(Context ctx) {
        try {
            ParsingConfig config = ctx.bodyAsClass(ParsingConfig.class);
            if (config.getId() == null || config.getId().isBlank()) {
                config.setId(UUID.randomUUID().toString());
            }
            configService.save(config);
            ctx.json(ApiResponse.ok("Config saved", config));
        } catch (Exception e) {
            logger.error("Failed to save parsing config", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    public void detectFormat(Context ctx) {
        try {
            Map<String, Object> req = ctx.bodyAsClass(Map.class);
            List<String> sampleLines = (List<String>) req.get("sampleLines");
            if (sampleLines == null || sampleLines.isEmpty()) {
                ctx.json(ApiResponse.error("sampleLines is required"));
                return;
            }
            ParsingConfig detected = configService.detectLogFormat(sampleLines);
            ctx.json(ApiResponse.ok(detected));
        } catch (Exception e) {
            logger.error("Failed to detect log format", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    public void testConfig(Context ctx) {
        try {
            Map<String, Object> req = ctx.bodyAsClass(Map.class);
            String sample = (String) req.get("sampleLog");
            Map<String, Object> cfgMap = (Map<String, Object>) req.get("config");

            ParsingConfig config = new ParsingConfig();
            if (cfgMap != null) {
                config.setRegexPattern((String) cfgMap.get("regexPattern"));
                config.setTimestampFormat((String) cfgMap.get("timestampFormat"));
            }

            LogParser.TestResult result = logParser.testParsing(sample, config);
            ctx.json(ApiResponse.ok(result));
        } catch (Exception e) {
            logger.error("Failed to test config", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }
}
