package com.seeloggyplus;

import com.seeloggyplus.service.PreferenceService;
import com.seeloggyplus.service.impl.PreferenceServiceImpl;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import com.seeloggyplus.model.Preference;
import java.util.Objects;

import com.seeloggyplus.util.ResizeHelper;


public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String APP_TITLE = "SeeLoggyPlus - Log Viewer";
    private static final String VERSION;

    static {
        String version = "DEV";
        try (java.io.InputStream input = Main.class.getResourceAsStream("/version.properties")) {
            java.util.Properties prop = new java.util.Properties();
            if (input == null) {
                logger.warn("Sorry, unable to find version.properties, defaulting to DEV version.");
            } else {
                prop.load(input);
                version = prop.getProperty("version", version);
            }
        } catch (java.io.IOException ex) {
            logger.error("Error reading version.properties", ex);
        }
        VERSION = version;
    }

    private PreferenceService preferenceService;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        ResizeHelper.addResizeListener(primaryStage);

        this.preferenceService = new PreferenceServiceImpl();

        try {
            FXMLLoader splashLoader = new FXMLLoader(getClass().getResource("/fxml/SplashView.fxml"));
            Parent splashRoot = splashLoader.load();
            com.seeloggyplus.controller.SplashController splashController = splashLoader.getController();

            splashController.setVersion(VERSION);

            Stage splashStage = new Stage();
            splashStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            Scene splashScene = new Scene(splashRoot);
            splashScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            splashStage.setScene(splashScene);

            try {
                Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/app-icon.png")));
                splashStage.getIcons().add(icon);
            } catch (Exception e) {
                logger.warn(e.getMessage());
            }

            splashStage.show();

            class ViewContext {
                Parent root;
                Object controller;

                ViewContext(Parent root, Object controller) {
                    this.root = root;
                    this.controller = controller;
                }
            }

            Task<ViewContext> initTask = new Task<>() {
                @Override
                protected ViewContext call() throws Exception {
                    updateMessage("Loading core components...");
                    updateProgress(0.1, 1.0);
                    Thread.sleep(500);

                    updateMessage("Initializing preferences...");
                    updateProgress(0.3, 1.0);
                    Thread.sleep(500);

                    updateMessage("Loading user interface...");
                    updateProgress(0.6, 1.0);

                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
                    Parent root = loader.load();
                    Object controller = loader.getController();

                    updateMessage("Finalizing...");
                    updateProgress(0.9, 1.0);
                    Thread.sleep(400);

                    updateProgress(1.0, 1.0);
                    return new ViewContext(root, controller);
                }
            };

            splashController.setProgress(0, "Starting...");
            initTask.messageProperty().addListener((obs, oldVal, newVal) -> splashController.setProgress(initTask.getProgress(), newVal));
            initTask.progressProperty().addListener((obs, oldVal, newVal) -> splashController.setProgress(newVal.doubleValue(), initTask.getMessage()));

            // On Succeeded
            initTask.setOnSucceeded(e -> {
                ViewContext context = initTask.getValue();
                showMainStage(context.root, context.controller);
                splashStage.close();
            });

            // On Failed
            initTask.setOnFailed(e -> {
                splashStage.close();
                logger.error("Initialization failed", initTask.getException());
                showErrorAndExit("Initialization failed: " + initTask.getException().getMessage());
            });

            // Start Task
            new Thread(initTask).start();

        } catch (IOException e) {
            logger.error("Failed to load splash view", e);
            primaryStage.initStyle(StageStyle.TRANSPARENT);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            showErrorAndExit("Failed to start application: " + e.getMessage());
        }
    }

    private void showMainStage(Parent root, Object controller) {
        try {
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);

            primaryStage.setTitle(APP_TITLE + " v" + VERSION);
            primaryStage.setScene(scene);

            try {
                Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/app-icon.png")));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                logger.warn("Application icon not found");
            }

            primaryStage.setOnCloseRequest(event -> {
                saveWindowPreferences();
                cleanup();
            });

            primaryStage.show();
            logger.info("SeeLoggyPlus application started successfully");
        } catch (Exception e) {
            logger.error("Failed to show main stage", e);
            showErrorAndExit("Error showing main window: " + e.getMessage());
        }
    }


    /**
     * Save window size and position to preferences
     */
    private void saveWindowPreferences() {
        if (primaryStage != null) {
            preferenceService.saveOrUpdatePreferences(new Preference("window_width", String.valueOf(primaryStage.getWidth())));
            preferenceService.saveOrUpdatePreferences(new Preference("window_height", String.valueOf(primaryStage.getHeight())));
            preferenceService.saveOrUpdatePreferences(new Preference("window_x", String.valueOf(primaryStage.getX())));
            preferenceService.saveOrUpdatePreferences(new Preference("window_y", String.valueOf(primaryStage.getY())));
            preferenceService.saveOrUpdatePreferences(
                    new Preference("window_maximized", String.valueOf(primaryStage.isMaximized())));
        }
    }

    /**
     * Cleanup resources before exit
     */
    private void cleanup() {
        logger.info("Cleaning up application resources");
        // Add any cleanup tasks here
    }

    /**
     * Show error dialog and exit
     */
    private void showErrorAndExit(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Application Error");
        alert.setHeaderText("Failed to Start Application");
        alert.setContentText(message);
        alert.showAndWait();
        System.exit(1);
    }

    @Override
    public void stop() throws Exception {
        cleanup();
        super.stop();
        System.exit(0);
        logger.info("SeeLoggyPlus application stopped");
    }

    public static void main(String[] args) {
        System.setProperty("logback.configurationFile", "logback.xml");

        logger.info("Starting SeeLoggyPlus application v{}", VERSION);
        launch(args);
    }
}
