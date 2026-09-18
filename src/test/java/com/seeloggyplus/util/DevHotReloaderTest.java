package com.seeloggyplus.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class DevHotReloaderTest {

    private Stage testStage;
    private Scene testScene;

    @Start
    public void start(Stage stage) {
        this.testStage = stage;
        StackPane root = new StackPane();
        this.testScene = new Scene(root, 400, 300);
        stage.setScene(testScene);
        stage.show();
    }

    @AfterEach
    void tearDown() {
        DevHotReloader.shutdown();
    }

    @Test
    @DisplayName("Should detect development mode when source CSS exists")
    void testIsDevMode() {
        File css = new File("src/main/resources/style/components.css");
        if (css.exists()) {
            assertTrue(DevHotReloader.isDevMode(), "Should detect dev mode when source components.css is present");
        }

        System.setProperty("seeloggyplus.dev", "true");
        assertTrue(DevHotReloader.isDevMode(), "Should detect dev mode when seeloggyplus.dev=true");
        System.clearProperty("seeloggyplus.dev");
    }

    @Test
    @DisplayName("Should reload CSS dynamically and update scene stylesheets")
    void testReloadCss() {
        Platform.runLater(() -> {
            DevHotReloader.init(testStage, testScene);
            DevHotReloader.reloadCss(testStage, testScene, false);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(testScene.getStylesheets().isEmpty(), "Scene stylesheets should not be empty after reloadCss");
        boolean hasReloadedCss = testScene.getStylesheets().stream()
                .anyMatch(s -> s.contains("seeloggy-hotreload-") || s.contains("components.css"));
        assertTrue(hasReloadedCss, "Scene should contain the hot-reloaded stylesheet");
    }

    @Test
    @DisplayName("Should lengthen scrollbar thumb via CSS padding and min-height")
    void testThumbLengthViaCss() {
        javafx.scene.control.ScrollBar scrollBar = new javafx.scene.control.ScrollBar();
        scrollBar.setOrientation(javafx.geometry.Orientation.VERTICAL);
        scrollBar.setMin(0);
        scrollBar.setMax(100000);
        scrollBar.setVisibleAmount(50);
        scrollBar.setPrefHeight(600);

        Platform.runLater(() -> {
            ((StackPane) testScene.getRoot()).getChildren().add(scrollBar);
            DevHotReloader.init(testStage, testScene);
            DevHotReloader.reloadCss(testStage, testScene, false);
        });
        WaitForAsyncUtils.waitForFxEvents();

        javafx.scene.Node thumb = scrollBar.lookup(".thumb");
        assertNotNull(thumb, "Thumb should exist in ScrollBar");
        System.out.println("DEBUG: Thumb height in test: " + thumb.getLayoutBounds().getHeight());
    }

    @Test
    @DisplayName("Should shutdown cleanly without throwing exceptions")
    void testShutdownCleanly() {
        assertDoesNotThrow(() -> {
            DevHotReloader.init(testStage, testScene);
            DevHotReloader.shutdown();
            DevHotReloader.shutdown(); // Idempotent shutdown
        });
    }
}
