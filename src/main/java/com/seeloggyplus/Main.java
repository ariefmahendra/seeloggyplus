package com.seeloggyplus;


import com.seeloggyplus.service.PreferenceService;
import com.seeloggyplus.service.impl.PreferenceServiceImpl;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import com.seeloggyplus.model.Preference;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Main application entry point for SeeLoggyPlus
 * High-performance log viewer with advanced parsing capabilities
 */

public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String APP_TITLE = "SeeLoggyPlus - Log Viewer";
    private static final String VERSION = "1.0.0";

    private PreferenceService preferenceService;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.preferenceService = new PreferenceServiceImpl();
        
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
        Optional<Double> windowX = getPreferenceAsDouble("window_x");
        Optional<Double> windowY = getPreferenceAsDouble("window_y");

        double windowWidth = getPreferenceAsDouble("window_width").orElse(1000.0);
        double windowHeight = getPreferenceAsDouble("window_height").orElse(800.0);

        boolean maximized = preferenceService.getPreferencesByCode("window_maximized")
                .filter(Predicate.not(String::isBlank))
                .map(Boolean::parseBoolean)
                .orElse(false);

        stage.setWidth(windowWidth);
        stage.setHeight(windowHeight);

        if (windowX.isPresent() && windowY.isPresent()) {
            double x = windowX.get();
            double y = windowY.get();

            if (isBoundsVisibleOnScreen(x, y, windowWidth, windowHeight)) {
                stage.setX(x);
                stage.setY(y);
            } else {
                stage.centerOnScreen();
            }
        } else {
            stage.centerOnScreen();
        }

        stage.setMaximized(maximized);
    }

    /**
     * Helper for robust get preference as double data type
     */
    private Optional<Double> getPreferenceAsDouble(String code) {
        return preferenceService.getPreferencesByCode(code)
                .filter(Predicate.not(String::isBlank))
                .flatMap(s -> {
                    try {
                        return Optional.of(Double.parseDouble(s));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    /**
     * Helper for checking coordinate only in screen
     */
    private boolean isBoundsVisibleOnScreen(double x, double y, double width, double height) {
        Rectangle2D windowBounds = new Rectangle2D(x, y, width, height);

        for (Screen screen : Screen.getScreens()) {
            Rectangle2D screenBounds = screen.getVisualBounds();

            if (screenBounds.intersects(windowBounds)) {
                return true;
            }
        }

        return false;
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
            preferenceService.saveOrUpdatePreferences(new Preference("window_maximized", String.valueOf(primaryStage.isMaximized())));
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
        System.exit(0);
        logger.info("SeeLoggyPlus application stopped");
    }

    public static void main(String[] args) {
        System.setProperty("logback.configurationFile", "logback.xml");

        logger.info("Starting SeeLoggyPlus application v{}", VERSION);
        launch(args);
    }
}
