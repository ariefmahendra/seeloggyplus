package com.seeloggyplus.util;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installs the SeeLoggyPlus theme stylesheets on a {@link Scene} and manages the
 * light/dark theme switch.
 * <p>
 * Stylesheets added to the scene (rather than only to the root node) are also
 * inherited by popup windows such as menus and context menus, which otherwise
 * fall back to the default JavaFX theme.
 */
public final class AppTheme {

    private static final Logger logger = LoggerFactory.getLogger(AppTheme.class);

    private static final String THEME_LIGHT = "/style/theme.css";
    private static final String THEME_DARK = "/style/theme-dark.css";
    private static final String COMPONENTS = "/style/components.css";
    private static final String DARK_CLASS = "theme-dark";

    private static volatile boolean dark = false;

    private AppTheme() {
    }

    /**
     * Creates a themed {@link Scene} for the given root.
     */
    public static Scene scene(Parent root) {
        Scene scene = new Scene(root);
        install(scene);
        applyState(root);
        return scene;
    }

    /**
     * Adds the theme stylesheets to the scene if they are not already present.
     */
    public static void install(Scene scene) {
        if (scene == null) {
            return;
        }
        addIfMissing(scene, THEME_LIGHT);
        addIfMissing(scene, COMPONENTS);
        if (dark) {
            addIfMissing(scene, THEME_DARK);
        }
    }

    public static boolean isDark() {
        return dark;
    }

    /**
     * Switches between the light and dark palette on every open window and marks
     * the scene roots so Canvas-based views (which ignore CSS) can react too.
     */
    public static void setDark(boolean value) {
        dark = value;
        // Snapshot: applying CSS can create/destroy popup windows.
        for (Window window : new java.util.ArrayList<>(Window.getWindows())) {
            try {
                Scene scene = window.getScene();
                if (scene == null || scene.getRoot() == null) {
                    continue;
                }
                if (value) {
                    addIfMissing(scene, THEME_DARK);
                } else {
                    // Remove any dark stylesheet, including hot-reload temp copies
                    // (their temp file name still contains "theme-dark").
                    scene.getStylesheets().removeIf(url -> url.contains("theme-dark"));
                }
                Parent root = scene.getRoot();
                applyState(root);
                root.applyCss();
            } catch (RuntimeException ex) {
                // One problematic window (e.g., a popup mid-teardown) must not
                // prevent the remaining windows from switching theme.
                logger.warn("Failed to apply theme to window: {}", window, ex);
            }
        }
    }

    private static void applyState(Parent root) {
        if (root == null) {
            return;
        }
        if (dark) {
            if (!root.getStyleClass().contains(DARK_CLASS)) {
                root.getStyleClass().add(DARK_CLASS);
            }
        } else {
            root.getStyleClass().remove(DARK_CLASS);
        }
    }

    private static void addIfMissing(Scene scene, String path) {
        String url = toUrl(path);
        if (url != null && !scene.getStylesheets().contains(url)) {
            scene.getStylesheets().add(url);
        }
    }

    private static String toUrl(String path) {
        java.net.URL resource = AppTheme.class.getResource(path);
        return resource == null ? null : resource.toExternalForm();
    }
}
