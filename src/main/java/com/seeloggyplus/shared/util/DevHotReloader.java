package com.seeloggyplus.shared.util;

import com.seeloggyplus.shared.ui.AppTheme;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DevHotReloader {

    private static final Logger logger = LoggerFactory.getLogger(DevHotReloader.class);

    private static final String CSS_SOURCE_PATH = "src/main/resources/style/components.css";
    private static final List<String> CSS_SOURCE_PATHS = List.of(
            "src/main/resources/style/theme.css",
            "src/main/resources/style/components.css",
            "src/main/resources/style/theme-light.css",
            "src/main/resources/style/theme-dark.css");
    private static final String FXML_SOURCE_PATH = "src/main/resources/fxml/MainView.fxml";

    private static ScheduledExecutorService watcherExecutor;
    private static final AtomicBoolean isReloading = new AtomicBoolean(false);
    private static long lastCssModified = 0;
    private static long lastFxmlModified = 0;
    private static final List<File> createdTempFiles = new ArrayList<>();

    public static boolean isDevMode() {
        if (Boolean.getBoolean("seeloggyplus.dev")) {
            return true;
        }
        File cssFile = new File(CSS_SOURCE_PATH);
        return cssFile.exists() && cssFile.isFile();
    }

    public static void init(Stage stage, Scene scene) {
        if (!isDevMode()) {
            logger.info("DevHotReloader: Production environment detected. Hot reload disabled.");
            return;
        }

        logger.info("DevHotReloader: Development environment detected. Initializing Desktop GUI Hot Reload...");

        lastCssModified = latestCssModified();

        File fxmlFile = new File(FXML_SOURCE_PATH);
        if (fxmlFile.exists()) {
            lastFxmlModified = fxmlFile.lastModified();
        }

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F5 || (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.R)) {
                logger.info("[DevHotReloader] Manual hot reload triggered via shortcut: {}", event.getCode());
                event.consume();
                reloadCss(stage, scene, true);
            }

            else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.F5) {
                logger.info("[DevHotReloader] Manual FXML reload triggered via Ctrl+Shift+F5");
                event.consume();
                reloadFxml(stage);
            }
        });

        startWatcher(stage, scene);

        reloadCss(stage, scene, false);
    }

    private static synchronized void startWatcher(Stage stage, Scene scene) {
        if (watcherExecutor != null && !watcherExecutor.isShutdown()) {
            return;
        }

        watcherExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DevHotReloader-Watcher");
            t.setDaemon(true);
            return t;
        });

        watcherExecutor.scheduleWithFixedDelay(() -> {
            try {
                long cssModified = latestCssModified();
                if (cssModified > lastCssModified) {
                    lastCssModified = cssModified;
                    logger.info("[DevHotReloader] Detected change in CSS sources");
                    Platform.runLater(() -> reloadCss(stage, scene, true));
                }

                File fxmlFile = new File(FXML_SOURCE_PATH);
                if (fxmlFile.exists()) {
                    long modified = fxmlFile.lastModified();
                    if (modified > lastFxmlModified) {
                        lastFxmlModified = modified;
                        logger.info("[DevHotReloader] Detected change in FXML: {}. Press Ctrl+Shift+F5 to reload layout.", fxmlFile.getName());
                        Platform.runLater(() -> showToast(stage, "⚡ FXML modified: Press Ctrl+Shift+F5 to reload layout"));
                    }
                }
            } catch (Exception e) {
                logger.debug("Error in DevHotReloader watcher: {}", e.getMessage());
            }
        }, 500, 350, TimeUnit.MILLISECONDS);

        logger.info("[DevHotReloader] File watcher active for {} (interval: 350ms)", CSS_SOURCE_PATH);
    }

    public static void reloadCss(Stage stage, Scene scene, boolean showToast) {
        if (!isReloading.compareAndSet(false, true)) {
            return;
        }

        long startTime = System.currentTimeMillis();
        List<String> newCssUrls = new ArrayList<>();

        try {
            for (String sourcePath : CSS_SOURCE_PATHS) {
                File sourceCss = new File(sourcePath);
                if (!sourceCss.exists()) {
                    continue;
                }

                if (sourcePath.contains("theme-dark.css") && AppTheme.getTheme() != AppTheme.Theme.DARK) {
                    continue;
                }
                if (sourcePath.contains("theme-light.css") && AppTheme.getTheme() != AppTheme.Theme.LIGHT) {
                    continue;
                }

                String base = sourceCss.getName().replaceAll("\\.css$", "");
                File tempCss = File.createTempFile("seeloggy-hotreload-" + base + "-", ".css");
                tempCss.deleteOnExit();
                Files.copy(sourceCss.toPath(), tempCss.toPath(), StandardCopyOption.REPLACE_EXISTING);

                createdTempFiles.add(tempCss);
                newCssUrls.add(tempCss.toURI().toURL().toExternalForm());
            }

            if (newCssUrls.isEmpty()) {
                isReloading.set(false);
                return;
            }

            while (createdTempFiles.size() > CSS_SOURCE_PATHS.size() * 3) {
                File oldFile = createdTempFiles.remove(0);
                try {
                    oldFile.delete();
                } catch (Exception ignored) {}
            }

            Platform.runLater(() -> {
                try {
                    Parent root = scene.getRoot();
                    if (root != null) {
                        root.getStylesheets().removeIf(DevHotReloader::isReloadableCss);
                    }
                    scene.getStylesheets().removeIf(DevHotReloader::isReloadableCss);

                    scene.getStylesheets().addAll(newCssUrls);
                    if (root != null) {
                        root.applyCss();
                    }

                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.info("[DevHotReloader] ⚡ CSS reloaded successfully in {} ms", elapsed);

                    if (showToast && stage != null && stage.isShowing()) {
                        showToast(stage, "⚡ CSS Hot-Reloaded (" + elapsed + "ms)");
                    }
                } finally {
                    isReloading.set(false);
                }
            });
        } catch (Exception e) {
            isReloading.set(false);
            logger.error("[DevHotReloader] Failed to reload CSS", e);
        }
    }

    private static boolean isReloadableCss(String url) {
        return url.contains("theme.css") || url.contains("theme-dark.css")
                || url.contains("theme-light.css")
                || url.contains("components.css") || url.contains("hotreload");
    }

    private static long latestCssModified() {
        long latest = 0;
        for (String path : CSS_SOURCE_PATHS) {
            File file = new File(path);
            if (file.exists()) {
                latest = Math.max(latest, file.lastModified());
            }
        }
        return latest;
    }

    public static void reloadFxml(Stage stage) {
        if (stage == null || !stage.isShowing()) return;

        File fxmlFile = new File(FXML_SOURCE_PATH);
        if (!fxmlFile.exists()) {
            logger.warn("[DevHotReloader] FXML file not found: {}", FXML_SOURCE_PATH);
            return;
        }

        try {
            logger.info("[DevHotReloader] Reloading FXML from: {}", fxmlFile.getAbsolutePath());
            URL fxmlUrl = fxmlFile.toURI().toURL();
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent newRoot = loader.load();

            Scene scene = stage.getScene();
            scene.setRoot(newRoot);

            reloadCss(stage, scene, false);
            showToast(stage, "⚡ Main Layout Reloaded");
            logger.info("[DevHotReloader] ⚡ FXML layout reloaded successfully!");
        } catch (IOException e) {
            logger.error("[DevHotReloader] Failed to reload FXML", e);
            showToast(stage, "❌ FXML Reload Error: " + e.getMessage());
        }
    }

    private static void showToast(Stage stage, String message) {
        if (stage == null || !stage.isShowing()) return;

        Popup popup = new Popup();
        popup.setAutoFix(true);

        Label label = new Label(message);
        label.setStyle(
            "-fx-background-color: rgba(33, 33, 33, 0.90);" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-family: 'Segoe UI', system-ui, sans-serif;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 6 12 6 12;" +
            "-fx-background-radius: 16;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.35), 6, 0, 0, 2);"
        );

        popup.getContent().add(label);

        double x = stage.getX() + stage.getWidth() - 250;
        double y = stage.getY() + stage.getHeight() - 75;
        if (x > 0 && y > 0) {
            popup.show(stage, x, y);

            FadeTransition fade = new FadeTransition(Duration.millis(1200), label);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setDelay(Duration.millis(1000));
            fade.setOnFinished(e -> popup.hide());
            fade.play();
        }
    }

    public static synchronized void shutdown() {
        if (watcherExecutor != null && !watcherExecutor.isShutdown()) {
            logger.info("[DevHotReloader] Shutting down watcher executor...");
            watcherExecutor.shutdownNow();
            watcherExecutor = null;
        }
        for (File temp : createdTempFiles) {
            try {
                temp.delete();
            } catch (Exception ignored) {}
        }
        createdTempFiles.clear();
    }
}
