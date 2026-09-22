package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end switching between Graphite / Light / Dark through the real entry
 * point (the Preferences "Theme" combo box) and verifies every open window
 * updates and reverts correctly.
 */
@ExtendWith(ApplicationExtension.class)
class ThemeSwitchingTest {

    private Stage mainStage;
    private Stage prefsStage;
    private Scene mainScene;
    private Scene prefsScene;
    private ComboBox<String> themeComboBox;

    @Start
    void start(Stage stage) throws Exception {
        this.mainStage = stage;

        Parent mainRoot = FXMLLoader.load(getClass().getResource("/fxml/MainView.fxml"));
        mainScene = AppTheme.scene(mainRoot);
        mainStage.setScene(mainScene);
        mainStage.show();

        FXMLLoader prefsLoader = new FXMLLoader(getClass().getResource("/fxml/PreferencesDialog.fxml"));
        Parent prefsRoot = prefsLoader.load();
        PreferencesDialogController prefsController = prefsLoader.getController();
        Field field = PreferencesDialogController.class.getDeclaredField("themeComboBox");
        field.setAccessible(true);
        themeComboBox = (ComboBox<String>) field.get(prefsController);

        prefsScene = AppTheme.scene(prefsRoot);
        prefsStage = new Stage();
        prefsStage.setScene(prefsScene);
        prefsStage.show();
    }

    @AfterEach
    void resetTheme() {
        // Cleanup touches live nodes/scenes, so it must run on the FX thread.
        onFxThread(() -> {
            if (themeComboBox != null) {
                themeComboBox.setValue("Graphite");
            }
            AppTheme.setDark(false);
            if (prefsStage != null) {
                prefsStage.close();
            }
        });
    }

    @Test
    void switchingToDarkUpdatesAllWindows() {
        onFxThread(() -> {
            themeComboBox.setValue("Dark");
            assertTrue(AppTheme.isDark(), "theme state must be dark");

            assertTrue(hasDark(mainScene), "main scene must include theme-dark.css");
            assertTrue(hasDark(prefsScene), "preferences scene must include theme-dark.css");
            assertTrue(mainScene.getRoot().getStyleClass().contains("theme-dark"),
                    "main root must carry theme-dark");
            assertTrue(prefsScene.getRoot().getStyleClass().contains("theme-dark"),
                    "preferences root must carry theme-dark");
        });
    }

    @Test
    void switchingToLightAppliesLightThemeEverywhere() {
        onFxThread(() -> {
            themeComboBox.setValue("Light");

            assertFalse(AppTheme.isDark(), "light theme is not dark");
            assertEquals(AppTheme.Theme.LIGHT, AppTheme.getTheme());
            assertTrue(hasLight(mainScene), "main scene must include theme-light.css");
            assertTrue(hasLight(prefsScene), "preferences scene must include theme-light.css");
            assertFalse(hasDark(mainScene), "light theme must not keep theme-dark.css");
            assertTrue(mainScene.getRoot().getStyleClass().contains("theme-light"),
                    "main root must carry theme-light");
        });
    }

    @Test
    void switchingBackToGraphiteRevertsAllWindows() {
        onFxThread(() -> {
            themeComboBox.setValue("Dark");
            themeComboBox.setValue("Graphite");

            assertFalse(AppTheme.isDark(), "theme state must be graphite again");
            assertFalse(hasDark(mainScene), "main scene must drop theme-dark.css");
            assertFalse(hasDark(prefsScene), "preferences scene must drop theme-dark.css");
            assertFalse(hasLight(mainScene), "graphite must not include theme-light.css");
            assertFalse(mainScene.getRoot().getStyleClass().contains("theme-dark"),
                    "main root must drop theme-dark");
            assertFalse(prefsScene.getRoot().getStyleClass().contains("theme-dark"),
                    "preferences root must drop theme-dark");
        });
    }

    @Test
    void switchingBackToGraphiteRemovesHotReloadDarkCopy() {
        onFxThread(() -> {
            themeComboBox.setValue("Dark");

            // Simulate a hot-reload temp copy of the dark stylesheet on every scene.
            mainScene.getStylesheets().add("file:/tmp/seeloggy-hotreload-theme-dark-999.css");
            prefsScene.getStylesheets().add("file:/tmp/seeloggy-hotreload-theme-dark-999.css");

            themeComboBox.setValue("Graphite");

            assertFalse(mainScene.getStylesheets().stream().anyMatch(url -> url.contains("theme-dark")),
                    "main scene must drop the hot-reload dark copy");
            assertFalse(prefsScene.getStylesheets().stream().anyMatch(url -> url.contains("theme-dark")),
                    "preferences scene must drop the hot-reload dark copy");
        });
    }

    private static boolean hasDark(Scene scene) {
        return scene.getStylesheets().stream().anyMatch(url -> url.endsWith("theme-dark.css"));
    }

    private static boolean hasLight(Scene scene) {
        return scene.getStylesheets().stream().anyMatch(url -> url.endsWith("theme-light.css"));
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
