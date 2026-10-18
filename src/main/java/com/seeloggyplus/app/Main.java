package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.ThemedAlerts;

import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.features.ssh.infrastructure.SSHSessionManagerImpl;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.util.StartupSplash;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import com.seeloggyplus.features.update.infrastructure.RootLauncherInstaller;
import com.seeloggyplus.features.update.infrastructure.SilentLauncherInstaller;
import com.seeloggyplus.features.update.infrastructure.UpdateLayout;
import com.seeloggyplus.shared.util.DevHotReloader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import javafx.scene.control.Alert;

public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String APP_TITLE = "SeeLoggyPlus - Log Viewer";
    public static final String VERSION;

    static {
        String version = "DEV";
        try (InputStream input = Main.class.getResourceAsStream("/version.properties")) {
            Properties prop = new Properties();
            if (input == null) {
                logger.warn("Sorry, unable to find version.properties, defaulting to DEV version.");
            } else {
                prop.load(input);
                version = prop.getProperty("version", version);
            }
        } catch (IOException ex) {
            logger.error("Error reading version.properties", ex);
        }
        VERSION = version;
    }

    private PreferenceService preferenceService;
    private final Map<String, String> startupPreferences = new HashMap<>();
    private Exception startupFailure;
    private Stage primaryStage;

    @Override
    public void init() {
        try {
            updateStartupStatus("Preparing workspace...");
            ensureLaunchers();
            preferenceService = new PreferenceServiceImpl();
            SSHSessionManagerImpl.getInstance().setPreferenceService(preferenceService);
            updateStartupStatus("Loading preferences...");
            for (Preference preference : preferenceService.getListPreferences()) {
                startupPreferences.put(preference.getCode(), preference.getValue());
            }
            ThemePreferenceMigration.normalize(startupPreferences, preferenceService);
            StartupSplash.show(AppTheme.Theme.fromPreference(startupPreferences.get("app_theme")));
        } catch (Exception e) {
            startupFailure = e;
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        try {
            if (startupFailure != null) {
                throw startupFailure;
            }

            AppTheme.setTheme(getStartupPreference("app_theme")
                    .map(AppTheme.Theme::fromPreference)
                    .orElse(AppTheme.Theme.LIGHT));

            AppTheme.installWindowDecoration();

            updateStartupStatus("Loading interface...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();

            updateStartupStatus("Opening main window...");

            Scene scene = AppTheme.scene(root);

            primaryStage.setTitle(APP_TITLE + " v" + VERSION);
            primaryStage.setScene(scene);

            restoreWindowPreferences(primaryStage);

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
            StartupSplash.close();
            logger.info("SeeLoggyPlus application started successfully");

            DevHotReloader.init(primaryStage, scene);
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            showErrorAndExit("Failed to start application: " + e.getMessage());
        }
    }

    private void ensureLaunchers() {
        if (Boolean.getBoolean("seeloggyplus.dev")) {
            return;
        }
        try {
            Path location = Paths.get(
                    getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(location)) {
                return;
            }
            com.seeloggyplus.features.update.infrastructure.UpdateInstaller.ensureUnixExecutables(location.getParent());
            UpdateLayout layout =
                    new UpdateLayout(
                            UpdateLayout.installationRoot());
            SilentLauncherInstaller.ensure(layout.root());
            if (RootLauncherInstaller.heal(layout.root())) {
                logger.info("Repaired the console-free launcher in {}", layout.root());
            }
            if (RootLauncherInstaller.healUnix(layout.root())) {
                logger.info("Repaired the installation-relative launcher in {}", layout.root());
            }
        } catch (Exception e) {
            logger.debug("Could not ensure the launch scripts", e);
        }
    }

    private void restoreWindowPreferences(Stage stage) {
        Optional<Double> windowX = getPreferenceAsDouble("window_x");
        Optional<Double> windowY = getPreferenceAsDouble("window_y");

        double windowWidth = getPreferenceAsDouble("window_width").orElse(1000.0);
        double windowHeight = getPreferenceAsDouble("window_height").orElse(800.0);

        boolean maximized = getStartupPreference("window_maximized")
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

    private Optional<Double> getPreferenceAsDouble(String code) {
        return getStartupPreference(code)
                .filter(Predicate.not(String::isBlank))
                .flatMap(s -> {
                    try {
                        return Optional.of(Double.parseDouble(s));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    void updateStartupStatus(String message) {
        StartupSplash.showStatus(message);
    }

    private Optional<String> getStartupPreference(String code) {
        return Optional.ofNullable(startupPreferences.get(code));
    }

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

    private void saveWindowPreferences() {
        if (primaryStage != null) {
            preferenceService
                    .saveOrUpdatePreferences(new Preference("window_width", String.valueOf(primaryStage.getWidth())));
            preferenceService
                    .saveOrUpdatePreferences(new Preference("window_height", String.valueOf(primaryStage.getHeight())));
            preferenceService.saveOrUpdatePreferences(new Preference("window_x", String.valueOf(primaryStage.getX())));
            preferenceService.saveOrUpdatePreferences(new Preference("window_y", String.valueOf(primaryStage.getY())));
            preferenceService.saveOrUpdatePreferences(
                    new Preference("window_maximized", String.valueOf(primaryStage.isMaximized())));
        }
    }

    private void cleanup() {
        logger.info("Cleaning up application resources");
        DevHotReloader.shutdown();
    }

    private void showErrorAndExit(String message) {
        StartupSplash.close();
        Alert alert = ThemedAlerts.create(Alert.AlertType.ERROR);
        alert.setTitle("Application Error");
        alert.setHeaderText("Failed to Start Application");
        alert.setContentText(message);
        alert.showAndWait();
        System.exit(1);
    }

    @Override
    public void stop() throws Exception {
        AppTheme.disposeWindowDecoration();
        cleanup();
        super.stop();
        System.exit(0);
        logger.info("SeeLoggyPlus application stopped");
    }

    public static void main(String[] args) {
        StartupSplash.showStatus("Starting SeeLoggyPlus...");
        System.setProperty("logback.configurationFile", "logback.xml");

        logger.info("Starting SeeLoggyPlus application v{}", VERSION);
        launch(args);
    }
}
