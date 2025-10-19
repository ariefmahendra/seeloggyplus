package com.seeloggyplus;


import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main application entry point for SeeLoggyPlus
 * High-performance log viewer with advanced parsing capabilities
 */
public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String APP_TITLE = "SeeLoggyPlus - Log Viewer";
    private static final String VERSION = "1.0.0";


    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;


        try {
            // Load main view
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();

            // Create scene
            Scene scene = new Scene(root);

            // Load CSS
            String css = getClass().getResource("/css/style.css").toExternalForm();
            scene.getStylesheets().add(css);

            // Configure stage
            primaryStage.setTitle(APP_TITLE + " v" + VERSION);
            primaryStage.setScene(scene);

            // Restore window preferences
            restoreWindowPreferences(primaryStage);

            // Set application icon (if available)
            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/icon.png"));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                logger.warn("Application icon not found");
            }

            // Save window preferences on close
            primaryStage.setOnCloseRequest(event -> {
                saveWindowPreferences();
                cleanup();
            });

            primaryStage.show();
            logger.info("SeeLoggyPlus application started successfully");

        } catch (IOException e) {
            logger.error("Failed to load main view", e);
            showErrorAndExit("Failed to start application: " + e.getMessage());
        }
    }

    /**
     * Restore window size and position from preferences
     */
    private void restoreWindowPreferences(Stage stage) {
        double width = 800;
        double height = 600;
        double x = 100;
        double y = 100;
        boolean maximized = false;


        stage.setWidth(width);
        stage.setHeight(height);

        if (x >= 0 && y >= 0) {
            stage.setX(x);
            stage.setY(y);
        }

        stage.setMaximized(maximized);
    }

    /**
     * Save window size and position to preferences
     */
    private void saveWindowPreferences() {
        if (primaryStage != null) {

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
            javafx.scene.control.Alert.AlertType.ERROR
        );
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
        logger.info("SeeLoggyPlus application stopped");
    }

    public static void main(String[] args) {
        // Configure logging
        System.setProperty("logback.configurationFile", "logback.xml");

        logger.info("Starting SeeLoggyPlus application v{}", VERSION);
        launch(args);
    }
}
