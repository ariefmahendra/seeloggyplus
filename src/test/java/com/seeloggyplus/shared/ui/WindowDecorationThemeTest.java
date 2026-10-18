package com.seeloggyplus.shared.ui;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class WindowDecorationThemeTest {
    private Stage owner;
    @Start void start(Stage stage) { owner = stage; }
    @org.junit.jupiter.api.AfterEach void resetTheme() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)).get();
    }

    @Test void updatesExistingAndNewWindowsAndStopsCleanly() throws Exception {
        var modes = new ArrayList<Boolean>();
        var decoration = new WindowDecorationTheme(modes::add);
        WaitForAsyncUtils.asyncFx(() -> {
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
            owner.setScene(new Scene(new StackPane()));
            owner.show();
            decoration.install();
            decoration.install();
            decoration.update(AppTheme.Theme.DARK);
            assertEquals(java.util.List.of(false, true), modes);
            decoration.update(null);
            assertEquals(java.util.List.of(false,true,false),modes);
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
            var child = new Stage();
            child.setTitle("Duplicate");
            child.setScene(new Scene(new StackPane()));
            child.show();
        }).get();
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.asyncFx(() -> {
            assertEquals(false, modes.getLast());
            decoration.close();
            int count = modes.size();
            decoration.update(AppTheme.Theme.LIGHT);
            assertEquals(count, modes.size());
            for (var window : new ArrayList<>(javafx.stage.Window.getWindows())) {
                if (window != owner) window.hide();
            }
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
        }).get();
    }

    @Test void failureDoesNotEscapeInDarkMode() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            AppTheme.setTheme(AppTheme.Theme.DARK);
            var decoration = new WindowDecorationTheme(dark -> {
                assertTrue(dark);
                throw new IllegalStateException("Unsupported native API");
            });
            assertDoesNotThrow(decoration::install);
            assertDoesNotThrow(() -> decoration.update(AppTheme.Theme.DARK));
            decoration.close();
        }).get();
    }

    @Test void dropdownPopupsDoNotReapplyNativeDecoration() throws Exception {
        var modes = new ArrayList<Boolean>();
        var decoration = new WindowDecorationTheme(modes::add);
        var combo = new javafx.scene.control.ComboBox<String>();
        WaitForAsyncUtils.asyncFx(() -> {
            combo.getItems().setAll("Light", "Dark");
            combo.setValue("Light");
            owner.setScene(AppTheme.scene(new StackPane(combo)));
            owner.show();
            decoration.install();
            combo.show();
        }).get();
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.asyncFx(() -> {
            try {
                assertEquals(1, modes.size(), "Opening a dropdown must not refresh window decoration");
            } finally {
                combo.hide();
            }
        }).get();
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.asyncFx(() -> {
            try {
                assertEquals(1, modes.size(), "Closing a dropdown must not refresh window decoration");
            } finally {
                decoration.close();
            }
        }).get();
    }

    @Test void nativeFilterExcludesOtherProcessesAndUndecoratedWindows() {
        assertTrue(WindowsWindowDecoration.accepts(42, 42, "GlassWndClass-GlassWindowClass-2", 0x00c00000));
        assertTrue(WindowsWindowDecoration.accepts(42, 42, "GlassWndClass-GlassWindowClass-7", 0x00c00000));
        assertFalse(WindowsWindowDecoration.accepts(43, 42, "GlassWndClass-GlassWindowClass-2", 0x00c00000));
        assertFalse(WindowsWindowDecoration.accepts(42, 42, "#32770", 0x00c00000));
        assertFalse(WindowsWindowDecoration.accepts(42, 42, "GlassWndClass-GlassToolkitWindowClass-1", 0x00c00000));
        assertFalse(WindowsWindowDecoration.accepts(42, 42, "GlassWndClass-GlassWindowClass-2", 0));
    }
}
