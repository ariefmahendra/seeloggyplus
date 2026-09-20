package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
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
 * End-to-end switching between light and dark mode through the real entry point
 * (the Preferences "Dark mode" checkbox) and verifies every open window updates
 * and reverts correctly.
 */
@ExtendWith(ApplicationExtension.class)
class ThemeSwitchingTest {

    private Stage mainStage;
    private Stage prefsStage;
    private Scene mainScene;
    private Scene prefsScene;
    private CheckBox darkModeCheckBox;

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
        Field field = PreferencesDialogController.class.getDeclaredField("darkModeCheckBox");
        field.setAccessible(true);
        darkModeCheckBox = (CheckBox) field.get(prefsController);

        prefsScene = AppTheme.scene(prefsRoot);
        prefsStage = new Stage();
        prefsStage.setScene(prefsScene);
        prefsStage.show();
    }

    @AfterEach
    void resetTheme() {
        // Cleanup touches live nodes/scenes, so it must run on the FX thread.
        onFxThread(() -> {
            if (darkModeCheckBox != null) {
                darkModeCheckBox.setSelected(false);
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
            darkModeCheckBox.setSelected(true);
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
    void switchingBackToLightRevertsAllWindows() {
        onFxThread(() -> {
            darkModeCheckBox.setSelected(true);
            darkModeCheckBox.setSelected(false);

            assertFalse(AppTheme.isDark(), "theme state must be light again");
            assertFalse(hasDark(mainScene), "main scene must drop theme-dark.css");
            assertFalse(hasDark(prefsScene), "preferences scene must drop theme-dark.css");
            assertFalse(mainScene.getRoot().getStyleClass().contains("theme-dark"),
                    "main root must drop theme-dark");
            assertFalse(prefsScene.getRoot().getStyleClass().contains("theme-dark"),
                    "preferences root must drop theme-dark");
        });
    }

    @Test
    void switchingBackToLightRemovesHotReloadDarkCopy() {
        onFxThread(() -> {
            darkModeCheckBox.setSelected(true);

            // Simulate a hot-reload temp copy of the dark stylesheet on every scene.
            mainScene.getStylesheets().add("file:/tmp/seeloggy-hotreload-theme-dark-999.css");
            prefsScene.getStylesheets().add("file:/tmp/seeloggy-hotreload-theme-dark-999.css");

            darkModeCheckBox.setSelected(false);

            assertFalse(mainScene.getStylesheets().stream().anyMatch(url -> url.contains("theme-dark")),
                    "main scene must drop the hot-reload dark copy");
            assertFalse(prefsScene.getStylesheets().stream().anyMatch(url -> url.contains("theme-dark")),
                    "preferences scene must drop the hot-reload dark copy");
        });
    }

    @Test
    void multipleTogglesStayConsistent() {
        onFxThread(() -> {
            for (int i = 0; i < 3; i++) {
                darkModeCheckBox.setSelected(true);
                assertTrue(hasDark(mainScene));
                assertTrue(hasDark(prefsScene));

                darkModeCheckBox.setSelected(false);
                assertFalse(hasDark(mainScene));
                assertFalse(hasDark(prefsScene));
            }
        });
    }

    private static boolean hasDark(Scene scene) {
        return scene.getStylesheets().stream().anyMatch(url -> url.endsWith("theme-dark.css"));
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
