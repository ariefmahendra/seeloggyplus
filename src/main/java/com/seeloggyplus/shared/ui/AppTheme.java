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
                return LIGHT;
            }
            String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "dark" -> DARK;
                case "light" -> LIGHT;
                default -> LIGHT;
            };
        }
    }

    private static final String BASE_STYLESHEET = "/style/theme.css";
    private static final String COMPONENTS = "/style/components.css";

    private static final WindowDecorationTheme windowDecoration = new WindowDecorationTheme();

    private static volatile Theme theme = Theme.LIGHT;

    private AppTheme() {
    }

    public static Scene scene(Parent root) {
        Scene scene = new Scene(root);
        install(scene);
        return scene;
    }

    public static void install(Scene scene) {
        if (scene == null) {
            return;
        }
        KeyboardFocusStyles.install(scene);
        addIfMissing(scene, BASE_STYLESHEET);
        addIfMissing(scene, COMPONENTS);
        Theme active = theme;
        addIfMissing(scene, active.stylesheet());
        applyState(scene.getRoot(),active);
    }

    public static void installWindowDecoration() {
        windowDecoration.install();
    }

    public static void disposeWindowDecoration() {
        windowDecoration.close();
    }

    public static boolean isDark() {
        return theme == Theme.DARK;
    }

    public static Theme getTheme() {
        return theme;
    }

    public static void setTheme(Theme newTheme) {
        Theme normalized = newTheme == null ? Theme.LIGHT : newTheme;
        if (normalized == theme) {
            return;
        }
        theme = normalized;
        applyToAllWindows(theme);
        windowDecoration.update(theme);
    }

    public static void setDark(boolean value) {
        setTheme(value ? Theme.DARK : Theme.LIGHT);
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
        addIfMissing(scene, active.stylesheet());
    }

    private static void applyState(Parent root, Theme active) {
        if (root == null) {
            return;
        }
        KeyboardFocusStyles.install(root);
        String baseStylesheet = toUrl(BASE_STYLESHEET);
        if (root.getStylesheets().contains(baseStylesheet)) {
            root.getStylesheets().removeIf(url -> url.equals(toUrl(Theme.LIGHT.stylesheet()))
                    || url.equals(toUrl(Theme.DARK.stylesheet())));
            root.getStylesheets().add(toUrl(active.stylesheet()));
        }
        for (Theme candidate : Theme.values()) {
            String styleClass = candidate.styleClass();
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
        addUrl(list, BASE_STYLESHEET);
        addUrl(list, COMPONENTS);
        Theme active = theme == null ? Theme.LIGHT : theme;
        addUrl(list, active.stylesheet());
        return list;
    }

    public static void applyThemeState(Parent root, Theme theme) {
        applyState(root, theme == null ? Theme.LIGHT : theme);
    }

    public static void refresh(Scene scene) {
        if (scene == null) {
            return;
        }
        KeyboardFocusStyles.install(scene);
        scene.getStylesheets().removeIf(url -> url.contains("theme-dark") || url.contains("theme-light"));
        addIfMissing(scene, BASE_STYLESHEET);
        addIfMissing(scene, COMPONENTS);
        addIfMissing(scene, theme.stylesheet());
        applyState(scene.getRoot(),theme);
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
        if (path == null) return null;
        URL resource = AppTheme.class.getResource(path);
        return resource == null ? null : resource.toExternalForm();
    }
}
