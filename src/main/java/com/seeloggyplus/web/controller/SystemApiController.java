package com.seeloggyplus.web.controller;

import com.seeloggyplus.Main;
import com.seeloggyplus.model.Preference;
import com.seeloggyplus.service.PreferenceService;
import com.seeloggyplus.web.dto.ApiResponse;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SystemApiController {

    private static final Logger logger = LoggerFactory.getLogger(SystemApiController.class);
    private final PreferenceService preferenceService;

    public SystemApiController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    public void getInfo(Context ctx) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsedMb = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMaxMb = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);

        Map<String, Object> info = new HashMap<>();
        info.put("name", "SeeLoggy+");
        info.put("version", Main.VERSION);
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("heapUsedMb", heapUsedMb);
        info.put("heapMaxMb", heapMaxMb);
        info.put("processors", Runtime.getRuntime().availableProcessors());

        ctx.json(ApiResponse.ok(info));
    }

    public void getPreferences(Context ctx) {
        try {
            List<Preference> list = preferenceService.getListPreferences();
            Map<String, String> prefMap = new HashMap<>();
            for (Preference p : list) {
                prefMap.put(p.getCode(), p.getValue());
            }
            ctx.json(ApiResponse.ok(prefMap));
        } catch (Exception e) {
            logger.error("Failed to load preferences", e);
            ctx.json(ApiResponse.error("Failed to load preferences: " + e.getMessage()));
        }
    }

    public void savePreference(Context ctx) {
        try {
            Map<String, String> req = ctx.bodyAsClass(Map.class);
            for (Map.Entry<String, String> entry : req.entrySet()) {
                preferenceService.saveOrUpdatePreferences(new Preference(entry.getKey(), entry.getValue()));
            }
            ctx.json(ApiResponse.ok("Preferences saved successfully", null));
        } catch (Exception e) {
            logger.error("Failed to save preference", e);
            ctx.json(ApiResponse.error("Failed to save preference: " + e.getMessage()));
        }
    }

    public void triggerGc(Context ctx) {
        System.gc();
        getInfo(ctx);
    }
}
