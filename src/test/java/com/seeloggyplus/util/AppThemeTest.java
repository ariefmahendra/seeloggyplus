package com.seeloggyplus.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class AppThemeTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void resetTheme() {
        AppTheme.setDark(false);
    }

    @Test
    void sceneInstallsBaseStylesheets() {
        onFxThread(() -> {
            Scene scene = AppTheme.scene(new StackPane());
            assertTrue(hasStylesheet(scene, "theme.css"), "theme.css must be installed");
            assertTrue(hasStylesheet(scene, "components.css"), "components.css must be installed");
            assertFalse(hasStylesheet(scene, "theme-dark.css"), "dark stylesheet must be absent by default");
        });
    }

    @Test
    void setDarkTogglesStylesheetAndRootClass() {
        onFxThread(() -> {
            Scene scene = AppTheme.scene(new StackPane());
            stage.setScene(scene);
            stage.show();

            AppTheme.setDark(true);
            assertTrue(hasStylesheet(scene, "theme-dark.css"), "dark stylesheet must be added");
            assertTrue(scene.getRoot().getStyleClass().contains("theme-dark"),
                    "root must carry the theme-dark class");

            AppTheme.setDark(false);
            assertFalse(hasStylesheet(scene, "theme-dark.css"), "dark stylesheet must be removed");
            assertFalse(scene.getRoot().getStyleClass().contains("theme-dark"),
                    "theme-dark class must be removed");
        });
    }

    @Test
    void sceneCreatedWhileDarkIncludesDarkStylesheet() {
        onFxThread(() -> {
            AppTheme.setDark(true);
            Scene scene = AppTheme.scene(new StackPane());
            assertTrue(hasStylesheet(scene, "theme-dark.css"),
                    "scenes created while dark must include theme-dark.css");
            assertTrue(scene.getRoot().getStyleClass().contains("theme-dark"),
                    "scenes created while dark must carry the theme-dark class");
            AppTheme.setDark(false);
        });
    }

    @Test
    void setDarkFalseRemovesDarkStylesheetIncludingHotReloadCopies() {
        onFxThread(() -> {
            Scene scene = AppTheme.scene(new StackPane());
            stage.setScene(scene);
            stage.show();

            // Simulate hot-reload temp copies. Their names contain the source
            // basename but NOT the ".css" extension (regression: a stale dark
            // copy previously stayed applied, leaving components dark).
            scene.getStylesheets().add("file:/tmp/seeloggy-hotreload-theme-dark-123.css");
            scene.getStylesheets().add("file:/tmp/theme-dark-hot reload.css");

            AppTheme.setDark(true);
            AppTheme.setDark(false);

            assertFalse(scene.getStylesheets().stream().anyMatch(url -> url.contains("theme-dark")),
                    "every dark stylesheet (including hot-reload copies) must be removed when switching to light");
        });
    }

    private static boolean hasStylesheet(Scene scene, String suffix) {
        return scene.getStylesheets().stream().anyMatch(url -> url.endsWith(suffix));
    }

    private static void onFxThread(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }
}
