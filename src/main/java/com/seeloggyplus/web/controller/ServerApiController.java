package com.seeloggyplus.web.controller;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.SSHService;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.web.dto.ApiResponse;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ServerApiController {

    private static final Logger logger = LoggerFactory.getLogger(ServerApiController.class);
    private final ServerManagementService serverService;
    private final SSHService sshService;

    public ServerApiController(ServerManagementService serverService, SSHService sshService) {
        this.serverService = serverService;
        this.sshService = sshService;
    }

    public void getAllServers(Context ctx) {
        try {
            List<SSHServerModel> servers = serverService.getAllServers();
            ctx.json(ApiResponse.ok(servers));
        } catch (Exception e) {
            logger.error("Failed to fetch servers", e);
            ctx.json(ApiResponse.error("Failed to fetch servers: " + e.getMessage()));
        }
    }

    public void saveServer(Context ctx) {
        try {
            SSHServerModel server = ctx.bodyAsClass(SSHServerModel.class);
            if (server.getId() == null || server.getId().isBlank()) {
                server.setId(UUID.randomUUID().toString());
            }
            if (server.getPort() <= 0) {
                server.setPort(22);
            }
            serverService.saveServer(server);
            ctx.json(ApiResponse.ok("Server saved successfully", server));
        } catch (Exception e) {
            logger.error("Failed to save server", e);
            ctx.json(ApiResponse.error("Failed to save server: " + e.getMessage()));
        }
    }

    public void deleteServer(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            serverService.deleteServer(id);
            ctx.json(ApiResponse.ok("Server deleted successfully", null));
        } catch (Exception e) {
            logger.error("Failed to delete server", e);
            ctx.json(ApiResponse.error("Failed to delete server: " + e.getMessage()));
        }
    }

    public void testConnection(Context ctx) {
        try {
            Map<String, Object> req = ctx.bodyAsClass(Map.class);
            String host = (String) req.get("host");
            int port = req.get("port") instanceof Number ? ((Number) req.get("port")).intValue() : 22;
            String username = (String) req.get("username");
            String password = (String) req.get("password");

            boolean connected = sshService.connect(host, port, username, password, 10000);
            if (connected) {
                sshService.disconnect();
                ctx.json(ApiResponse.ok("Connection successful!", true));
            } else {
                ctx.json(ApiResponse.error("Connection failed. Please verify your host, port, and credentials."));
            }
        } catch (Exception e) {
            logger.error("SSH test connection error", e);
            ctx.json(ApiResponse.error("Connection failed: " + e.getMessage()));
        }
    }
}
