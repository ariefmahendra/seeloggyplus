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
import com.seeloggyplus.model.Preference;
import java.util.Objects;

/**
 * Main application entry point for SeeLoggyPlus
 * High-performance log viewer with advanced parsing capabilities
 */
import com.seeloggyplus.repository.PreferenceRepository;
import com.seeloggyplus.repository.PreferenceRepositoryImpl;

public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String APP_TITLE = "SeeLoggyPlus - Log Viewer";
    private static final String VERSION = "1.0.0";

    private PreferenceRepository preferenceRepository;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.preferenceRepository = new PreferenceRepositoryImpl();
        
        try {
            // Load main view
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();

            // Create scene
            Scene scene = new Scene(root);

            // Configure stage
            primaryStage.setTitle(APP_TITLE + " v" + VERSION);
            primaryStage.setScene(scene);

            // Restore window preferences
            restoreWindowPreferences(primaryStage);

            // Set application icon (if available)
            try {
                Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/icon.png")));
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
        double width = Double.parseDouble(preferenceRepository.findByKey("window_width").map(Preference::getValue).orElse("1000"));
        double height = Double.parseDouble(preferenceRepository.findByKey("window_height").map(Preference::getValue).orElse("800"));
        double x = Double.parseDouble(preferenceRepository.findByKey("window_x").map(Preference::getValue).orElse("100"));
        double y = Double.parseDouble(preferenceRepository.findByKey("window_y").map(Preference::getValue).orElse("100"));
        boolean maximized = Boolean.parseBoolean(preferenceRepository.findByKey("window_maximized").map(Preference::getValue).orElse("false"));

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
            preferenceRepository.saveOrUpdate(new Preference("window_width", String.valueOf(primaryStage.getWidth())));
            preferenceRepository.saveOrUpdate(new Preference("window_height", String.valueOf(primaryStage.getHeight())));
            preferenceRepository.saveOrUpdate(new Preference("window_x", String.valueOf(primaryStage.getX())));
            preferenceRepository.saveOrUpdate(new Preference("window_y", String.valueOf(primaryStage.getY())));
            preferenceRepository.saveOrUpdate(new Preference("window_maximized", String.valueOf(primaryStage.isMaximized())));
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
        System.setProperty("logback.configurationFile", "logback.xml");

        logger.info("Starting SeeLoggyPlus application v{}", VERSION);
        launch(args);
    }
}
