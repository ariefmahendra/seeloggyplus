package com.seeloggyplus.util;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installs the SeeLoggyPlus theme stylesheets on a {@link Scene} and manages the
 * theme switch.
 * <p>
 * Three themes are available:
 * <ul>
 *   <li>{@link Theme#GRAPHITE} (default) — light content surfaces with a dark
 *       graphite chrome (menu/toolbar/status bar).</li>
 *   <li>{@link Theme#LIGHT} — a fully light look, including the chrome.</li>
 *   <li>{@link Theme#DARK} — the dark palette.</li>
 * </ul>
 * Stylesheets added to the scene (rather than only to the root node) are also
 * inherited by popup windows such as menus and context menus, which otherwise
 * fall back to the default JavaFX theme.
 */
public final class AppTheme {

    private static final Logger logger = LoggerFactory.getLogger(AppTheme.class);

    /** The selectable application themes. */
    public enum Theme {
        GRAPHITE("graphite", null, null),
        LIGHT("light", "/style/theme-light.css", "theme-light"),
        DARK("dark", "/style/theme-dark.css", "theme-dark");

        private final String preferenceValue;
        private final String stylesheet;
        private final String styleClass;

        Theme(String preferenceValue, String stylesheet, String styleClass) {
            this.preferenceValue = preferenceValue;
            this.stylesheet = stylesheet;
            this.styleClass = styleClass;
        }

        public String preferenceValue() {
            return preferenceValue;
        }

        public String stylesheet() {
            return stylesheet;
        }

        public String styleClass() {
            return styleClass;
        }

        /**
         * Maps a persisted preference value to a theme. Legacy installs stored
         * {@code "light"} for the graphite theme; unknown values fall back to GRAPHITE.
         */
        public static Theme fromPreference(String value) {
            if (value == null || value.isBlank()) {
                return GRAPHITE;
            }
            String normalized = value.trim().toLowerCase();
            return switch (normalized) {
                case "dark" -> DARK;
                case "light" -> LIGHT;
                default -> GRAPHITE;
            };
        }
    }

    private static final String THEME_LIGHT_BASE = "/style/theme.css";
    private static final String COMPONENTS = "/style/components.css";

    private static volatile Theme theme = Theme.GRAPHITE;

    private AppTheme() {
    }

    /**
     * Creates a themed {@link Scene} for the given root.
     */
    public static Scene scene(Parent root) {
        Scene scene = new Scene(root);
        install(scene);
        applyState(root, theme);
        return scene;
    }

    /**
     * Adds the theme stylesheets to the scene if they are not already present.
     */
    public static void install(Scene scene) {
        if (scene == null) {
            return;
        }
        addIfMissing(scene, THEME_LIGHT_BASE);
        addIfMissing(scene, COMPONENTS);
        Theme active = theme;
        if (active.stylesheet() != null) {
            addIfMissing(scene, active.stylesheet());
        }
    }

    public static boolean isDark() {
        return theme == Theme.DARK;
    }

    public static Theme getTheme() {
        return theme;
    }

    /**
     * Switches the palette on every open window and marks the scene roots so
     * Canvas-based views (which ignore CSS) can react too.
     */
    public static void setTheme(Theme newTheme) {
        theme = newTheme == null ? Theme.GRAPHITE : newTheme;
        applyToAllWindows(theme);
    }

    /**
     * Backwards-compatible toggle: {@code true} selects the dark theme, {@code false}
     * restores the default graphite theme.
     */
    public static void setDark(boolean value) {
        setTheme(value ? Theme.DARK : Theme.GRAPHITE);
    }

    private static void applyToAllWindows(Theme active) {
        // Snapshot: applying CSS can create/destroy popup windows.
        for (Window window : new java.util.ArrayList<>(Window.getWindows())) {
            try {
                Scene scene = window.getScene();
                if (scene == null || scene.getRoot() == null) {
                    continue;
                }
                applyStylesheets(scene, active);
                Parent root = scene.getRoot();
                applyState(root, active);
                root.applyCss();
            } catch (RuntimeException ex) {
                // One problematic window (e.g., a popup mid-teardown) must not
                // prevent the remaining windows from switching theme.
                logger.warn("Failed to apply theme to window: {}", window, ex);
            }
        }
    }

    private static void applyStylesheets(Scene scene, Theme active) {
        // Remove any variant stylesheet, including hot-reload temp copies (their
        // temp file name still contains "theme-dark"/"theme-light").
        scene.getStylesheets().removeIf(url -> url.contains("theme-dark") || url.contains("theme-light"));
        if (active.stylesheet() != null) {
            addIfMissing(scene, active.stylesheet());
        }
    }

    private static void applyState(Parent root, Theme active) {
        if (root == null) {
            return;
        }
        for (Theme candidate : Theme.values()) {
            String styleClass = candidate.styleClass();
            if (styleClass == null) {
                continue;
            }
            if (candidate == active) {
                if (!root.getStyleClass().contains(styleClass)) {
                    root.getStyleClass().add(styleClass);
                }
            } else {
                root.getStyleClass().remove(styleClass);
            }
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
