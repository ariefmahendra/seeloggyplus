package com.seeloggyplus.shared.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class AppTheme {

    private static final Logger logger = LoggerFactory.getLogger(AppTheme.class);

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

    public static Scene scene(Parent root) {
        Scene scene = new Scene(root);
        install(scene);
        applyState(root, theme);
        return scene;
    }

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

    public static void setTheme(Theme newTheme) {
        Theme normalized = newTheme == null ? Theme.GRAPHITE : newTheme;
        if (normalized == theme) {
            return;
        }
        theme = normalized;
        applyToAllWindows(theme);
    }

    public static void setDark(boolean value) {
        setTheme(value ? Theme.DARK : Theme.GRAPHITE);
    }

    private static void applyToAllWindows(Theme active) {
        for (Window window : new ArrayList<>(Window.getWindows())) {
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
                logger.warn("Failed to apply theme to window: {}", window, ex);
            }
        }
    }

    private static void applyStylesheets(Scene scene, Theme active) {
        scene.getStylesheets().removeIf(url -> url.contains("theme-dark") || url.contains("theme-light"));
        if (active.stylesheet() != null) {
            addIfMissing(scene, active.stylesheet());
        }
    }

    private static void applyState(Parent root, Theme active) {
        if (root == null) {
            return;
        }
        String baseStylesheet = toUrl(THEME_LIGHT_BASE);
        if (root.getStylesheets().contains(baseStylesheet)) {
            root.getStylesheets().removeIf(url -> url.equals(toUrl(Theme.LIGHT.stylesheet()))
                    || url.equals(toUrl(Theme.DARK.stylesheet())));
            if (active.stylesheet() != null) {
                String override = toUrl(active.stylesheet());
                if (override != null) root.getStylesheets().add(override);
            }
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

    public static List<String> sceneStylesheets(Theme theme) {
        List<String> list = new ArrayList<>();
        addUrl(list, THEME_LIGHT_BASE);
        addUrl(list, COMPONENTS);
        Theme active = theme == null ? Theme.GRAPHITE : theme;
        if (active.stylesheet() != null) {
            addUrl(list, active.stylesheet());
        }
        return list;
    }

    public static void applyThemeState(Parent root, Theme theme) {
        applyState(root, theme == null ? Theme.GRAPHITE : theme);
    }

    public static void refresh(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().removeIf(url -> url.contains("theme-dark") || url.contains("theme-light"));
        addIfMissing(scene, THEME_LIGHT_BASE);
        addIfMissing(scene, COMPONENTS);
        addIfMissing(scene, theme.stylesheet());
    }

    private static void addUrl(List<String> list, String path) {
        String url = toUrl(path);
        if (url != null && !list.contains(url)) {
            list.add(url);
        }
    }

    private static void addIfMissing(Scene scene, String path) {
        String url = toUrl(path);
        if (url != null && !scene.getStylesheets().contains(url)) {
            scene.getStylesheets().add(url);
        }
    }

    private static String toUrl(String path) {
        URL resource = AppTheme.class.getResource(path);
        return resource == null ? null : resource.toExternalForm();
    }
}
