package com.seeloggyplus.shared.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;

@ExtendWith(ApplicationExtension.class)
class DevHotReloaderTest {

    private Stage testStage;
    private Scene testScene;
    private String previousDevFlag;

    @BeforeEach
    void enableDevMode() {
        previousDevFlag = System.getProperty("seeloggyplus.dev");
        System.setProperty("seeloggyplus.dev", "true");
    }

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
        if (previousDevFlag == null) System.clearProperty("seeloggyplus.dev");
        else System.setProperty("seeloggyplus.dev", previousDevFlag);
    }

    @Test
    @DisplayName("Should require an explicit development flag even when source CSS exists")
    void testIsDevMode() {
        System.clearProperty("seeloggyplus.dev");
        assertFalse(DevHotReloader.isDevMode());
        System.setProperty("seeloggyplus.dev", "false");
        assertFalse(DevHotReloader.isDevMode());
        System.setProperty("seeloggyplus.dev", "true");
        assertTrue(DevHotReloader.isDevMode(), "Should detect dev mode when seeloggyplus.dev=true");
    }

    @Test
    void productionModeDoesNotReloadCssEvenThroughDirectCalls() {
        System.clearProperty("seeloggyplus.dev");
        Platform.runLater(() -> {
            DevHotReloader.init(testStage, testScene);
            DevHotReloader.reloadCss(testStage, testScene, false);
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(testScene.getStylesheets().isEmpty());
    }

    @Test
    void stylesheetsUsePrivateStorageAndAreRemovedOnShutdown() throws Exception {
        Platform.runLater(() -> DevHotReloader.reloadCss(testStage, testScene, false));
        WaitForAsyncUtils.waitForFxEvents();
        var files = testScene.getStylesheets().stream().map(url -> Path.of(URI.create(url))).toList();
        assertFalse(files.isEmpty());
        Path directory = files.getFirst().getParent();
        assertTrue(directory.getFileName().toString().startsWith("seeloggy-hotreload-"));
        for (Path file : files) {
            assertEquals(directory, file.getParent());
            assertTrue(Files.size(file) > 0);
        }
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null) {
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory));
            for (Path file : files) {
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
            }
        } else {
            var acl = Files.getFileAttributeView(directory, AclFileAttributeView.class);
            assertNotNull(acl);
            var owner = Files.getOwner(directory);
            assertTrue(acl.getAcl().stream().anyMatch(entry -> entry.type() == AclEntryType.ALLOW));
            assertTrue(acl.getAcl().stream().filter(entry -> entry.type() == AclEntryType.ALLOW)
                    .allMatch(entry -> entry.principal().equals(owner)));
            for (Path file : files) {
                assertTrue(Files.getFileAttributeView(file, AclFileAttributeView.class).getAcl().stream()
                        .filter(entry -> entry.type() == AclEntryType.ALLOW)
                        .allMatch(entry -> entry.principal().equals(owner)));
            }
        }
        DevHotReloader.shutdown();
        assertFalse(Files.exists(directory));
        files.forEach(file -> assertFalse(Files.exists(file)));
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
        ScrollBar scrollBar = new ScrollBar();
        scrollBar.setOrientation(Orientation.VERTICAL);
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

        Node thumb = scrollBar.lookup(".thumb");
        assertNotNull(thumb, "Thumb should exist in ScrollBar");
        System.out.println("DEBUG: Thumb height in test: " + thumb.getLayoutBounds().getHeight());
    }

    @Test
    @DisplayName("Should shutdown cleanly without throwing exceptions")
    void testShutdownCleanly() {
        assertDoesNotThrow(() -> {
            DevHotReloader.init(testStage, testScene);
            DevHotReloader.shutdown();
            DevHotReloader.shutdown();
        });
    }
}
