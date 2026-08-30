package com.seeloggyplus.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seeloggyplus.Main;
import com.seeloggyplus.repository.LogFileRepository;
import com.seeloggyplus.repository.impl.LogFileRepositoryImpl;
import com.seeloggyplus.service.*;
import com.seeloggyplus.service.impl.*;
import com.seeloggyplus.web.controller.*;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

public class WebServer {

    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    public static void start(String[] args) {
        int port = parsePort(args, 8080);
        String host = parseHost(args, "0.0.0.0");
        boolean autoBrowse = !hasFlag(args, "--no-browser");

        logger.info("Initializing SeeLoggy+ Web Services...");

        // 1. Instantiate Core Services & Repositories
        PreferenceService preferenceService = new PreferenceServiceImpl();
        ServerManagementService serverService = new ServerManagementServiceImpl();
        FavoriteFolderService favoriteFolderService = new FavoriteFolderServiceImpl();
        SavedFilterService filterService = new SavedFilterServiceImpl();
        ParsingConfigService parsingConfigService = new ParsingConfigServiceImpl();
        LocalFileService localFileService = new LocalFileServiceImpl();
        SSHService sshService = new SSHServiceImpl();
        LogParser logParser = new LogParserServiceImpl();
        RecentFileService recentFileService = new RecentConfigServiceImpl();
        LogFileRepository logFileRepository = new LogFileRepositoryImpl();

        // 2. Instantiate Controllers
        SystemApiController systemController = new SystemApiController(preferenceService);
        ServerApiController serverController = new ServerApiController(serverService, sshService);
        FileSystemApiController fsController = new FileSystemApiController(localFileService, sshService, serverService, favoriteFolderService);
        FilterApiController filterController = new FilterApiController(filterService);
        ParsingConfigApiController parsingController = new ParsingConfigApiController(parsingConfigService, logParser);
        RecentFilesApiController recentFilesController = new RecentFilesApiController(recentFileService);
        LogApiController logController = new LogApiController(logParser, parsingConfigService, sshService, serverService, recentFileService, logFileRepository);

        // 3. Configure Jackson ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 4. Configure Javalin
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/web";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.precompress = false;
            });

            // Enable CORS for flexibility
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });

            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.useVirtualThreads = true;
        });

        // 5. Register REST Endpoints
        // System
        app.get("/api/info", systemController::getInfo);
        app.get("/api/preferences", systemController::getPreferences);
        app.post("/api/preferences", systemController::savePreference);
        app.post("/api/system/gc", systemController::triggerGc);

        // Recent Files
        app.get("/api/recent-files", recentFilesController::getAllRecentFiles);
        app.delete("/api/recent-files", recentFilesController::clearAllRecentFiles);
        app.delete("/api/recent-files/{id}", recentFilesController::deleteRecentFile);

        // Servers (SSH)
        app.get("/api/servers", serverController::getAllServers);
        app.post("/api/servers", serverController::saveServer);
        app.delete("/api/servers/{id}", serverController::deleteServer);
        app.post("/api/servers/test", serverController::testConnection);

        // File System & Favorites
        app.get("/api/fs/home", fsController::getHomeDirectory);
        app.get("/api/fs/local", fsController::listLocalFiles);
        app.get("/api/fs/remote", fsController::listRemoteFiles);
        app.get("/api/fs/favorites", fsController::getFavorites);
        app.post("/api/fs/favorites", fsController::addFavorite);
        app.delete("/api/fs/favorites/{id}", fsController::removeFavorite);
        app.get("/api/fs/preview", fsController::previewFile);

        // Filters
        app.get("/api/filters", filterController::getAllFilters);
        app.post("/api/filters", filterController::saveFilter);
        app.delete("/api/filters/{id}", filterController::deleteFilter);

        // Parsing Configs
        app.get("/api/parsing-configs", parsingController::getAllConfigs);
        app.post("/api/parsing-configs", parsingController::saveConfig);
        app.post("/api/parsing-configs/detect", parsingController::detectFormat);
        app.post("/api/parsing-configs/test", parsingController::testConfig);

        // Logs
        app.post("/api/logs/open", logController::openLog);
        app.get("/api/logs/page", logController::getLogPage);
        app.sse("/api/logs/tail", logController::handleTailSse);
        app.post("/api/logs/stop-tail/{fileId}", logController::stopTail);
        app.get("/api/logs/export", logController::exportLogs);

        // 6. Start HTTP Server
        app.start(host, port);

        String displayUrl = "http://" + ("0.0.0.0".equals(host) ? "localhost" : host) + ":" + port;
        logger.info("==================================================================");
        logger.info("  SeeLoggy+ v{} Web Mode is running at: {}", Main.VERSION, displayUrl);
        logger.info("==================================================================");

        if (autoBrowse) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(displayUrl));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static int parsePort(String[] args, int defaultPort) {
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                try {
                    return Integer.parseInt(arg.substring(7));
                } catch (NumberFormatException ignored) {}
            }
        }
        return defaultPort;
    }

    private static String parseHost(String[] args, String defaultHost) {
        for (String arg : args) {
            if (arg.startsWith("--host=")) {
                return arg.substring(7).trim();
            }
        }
        return defaultHost;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
