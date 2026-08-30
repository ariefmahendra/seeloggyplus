package com.seeloggyplus.web.controller;

import com.seeloggyplus.dto.RemoteFileInfo;
import com.seeloggyplus.model.FavoriteFolder;
import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.FavoriteFolderService;
import com.seeloggyplus.service.LocalFileService;
import com.seeloggyplus.service.SSHService;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.web.dto.ApiResponse;
import io.javalin.http.Context;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FileSystemApiController {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemApiController.class);
    private final LocalFileService localFileService;
    private final SSHService sshService;
    private final ServerManagementService serverService;
    private final FavoriteFolderService favoriteFolderService;

    public FileSystemApiController(LocalFileService localFileService, SSHService sshService,
                                   ServerManagementService serverService, FavoriteFolderService favoriteFolderService) {
        this.localFileService = localFileService;
        this.sshService = sshService;
        this.serverService = serverService;
        this.favoriteFolderService = favoriteFolderService;
    }

    public void getHomeDirectory(Context ctx) {
        try {
            String home = localFileService.getHomeDirectory();
            ctx.json(ApiResponse.ok(home));
        } catch (Exception e) {
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    public void listLocalFiles(Context ctx) {
        try {
            String path = ctx.queryParam("path");
            if (path == null || path.isBlank()) {
                path = localFileService.getHomeDirectory();
            }
            List<FileInfo> files = localFileService.listFiles(path);
            Collections.sort(files);
            ctx.json(ApiResponse.ok(files));
        } catch (Exception e) {
            logger.error("Failed to list local files", e);
            ctx.json(ApiResponse.error("Failed to list files: " + e.getMessage()));
        }
    }

    public void listRemoteFiles(Context ctx) {
        try {
            String serverId = ctx.queryParam("serverId");
            String path = ctx.queryParam("path");

            if (serverId == null || serverId.isBlank()) {
                ctx.json(ApiResponse.error("serverId parameter is required"));
                return;
            }

            SSHServerModel server = serverService.getServerById(serverId);
            if (server == null) {
                ctx.json(ApiResponse.error("Server not found with ID: " + serverId));
                return;
            }

            if (path == null || path.isBlank()) {
                path = (server.getDefaultPath() != null && !server.getDefaultPath().isBlank())
                        ? server.getDefaultPath() : "/";
            }

            // Ensure connection to THIS specific server
            boolean needConnect = !sshService.isConnected()
                    || !java.util.Objects.equals(server.getHost(), sshService.getHost())
                    || server.getPort() != sshService.getPort()
                    || !java.util.Objects.equals(server.getUsername(), sshService.getUsername());

            if (needConnect) {
                if (sshService.isConnected()) {
                    sshService.disconnect();
                }
                boolean ok = sshService.connect(server.getHost(), server.getPort(), server.getUsername(), server.getPassword());
                if (!ok) {
                    ctx.json(ApiResponse.error("Failed to connect to SSH server: " + server.getHost() + " (Connection refused or unreachable)"));
                    return;
                }
            }

            List<RemoteFileInfo> remoteFiles = sshService.listFiles(path);
            Collections.sort(remoteFiles);
            ctx.json(ApiResponse.ok(remoteFiles));
        } catch (Exception e) {
            logger.error("Failed to list remote files", e);
            ctx.json(ApiResponse.error("Failed to list remote files: " + e.getMessage()));
        }
    }

    public void getFavorites(Context ctx) {
        try {
            String locationId = ctx.queryParam("locationId");
            if (locationId == null || locationId.isBlank()) {
                locationId = "LOCAL";
            }
            List<FavoriteFolder> favorites = favoriteFolderService.getFavoritesForLocation(locationId);
            ctx.json(ApiResponse.ok(favorites));
        } catch (Exception e) {
            logger.error("Failed to load favorites", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    public void addFavorite(Context ctx) {
        try {
            Map<String, String> req = ctx.bodyAsClass(Map.class);
            String name = req.get("name");
            String path = req.get("path");
            String locationId = req.getOrDefault("locationId", "LOCAL");

            favoriteFolderService.addFavorite(name, path, locationId);
            ctx.json(ApiResponse.ok("Favorite added", null));
        } catch (Exception e) {
            logger.error("Failed to add favorite", e);
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    public void removeFavorite(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            favoriteFolderService.removeFavorite(id);
            ctx.json(ApiResponse.ok("Favorite removed", null));
        } catch (Exception e) {
            ctx.json(ApiResponse.error(e.getMessage()));
        }
    }

    public void previewFile(Context ctx) {
        try {
            String source = ctx.queryParam("source"); // LOCAL or REMOTE
            String path = ctx.queryParam("path");
            String serverId = ctx.queryParam("serverId");
            int lines = ctx.queryParamAsClass("lines", Integer.class).getOrDefault(100);
            boolean fromEnd = ctx.queryParamAsClass("fromEnd", Boolean.class).getOrDefault(true);

            if (path == null || path.isBlank()) {
                ctx.json(ApiResponse.error("path is required"));
                return;
            }

            List<String> previewLines = new ArrayList<>();

            if ("REMOTE".equalsIgnoreCase(source)) {
                if (serverId != null && !serverId.isBlank()) {
                    SSHServerModel server = serverService.getServerById(serverId);
                    if (server != null) {
                        boolean needConnect = !sshService.isConnected()
                                || !java.util.Objects.equals(server.getHost(), sshService.getHost())
                                || server.getPort() != sshService.getPort()
                                || !java.util.Objects.equals(server.getUsername(), sshService.getUsername());
                        if (needConnect) {
                            if (sshService.isConnected()) {
                                sshService.disconnect();
                            }
                            boolean ok = sshService.connect(server.getHost(), server.getPort(), server.getUsername(), server.getPassword());
                            if (!ok) {
                                ctx.json(ApiResponse.error("Failed to connect to SSH server: " + server.getHost()));
                                return;
                            }
                        }
                    }
                }
                previewLines = sshService.readFileLines(path, lines);
            } else {

                File file = new File(path);
                if (!file.exists() || !file.isFile()) {
                    ctx.json(ApiResponse.error("File not found: " + path));
                    return;
                }

                if (fromEnd) {
                    try (ReversedLinesFileReader reader = new ReversedLinesFileReader(file, StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = reader.readLine()) != null && previewLines.size() < lines) {
                            previewLines.add(line);
                        }
                        Collections.reverse(previewLines);
                    }
                } else {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null && previewLines.size() < lines) {
                            previewLines.add(line);
                        }
                    }
                }
            }

            ctx.json(ApiResponse.ok(previewLines));
        } catch (Exception e) {
            logger.error("Failed to preview file", e);
            ctx.json(ApiResponse.error("Preview failed: " + e.getMessage()));
        }
    }
}
