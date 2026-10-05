package com.seeloggyplus.features.update.presentation;


import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The What's New dialog must be fully themed: app icon header, theme tokens on
 * every control, a primary "Got it" button and the embedded release notes text.
 * Renders snapshots for Graphite/Light/Dark under {@code build/whats-new/}.
 */
@ExtendWith(ApplicationExtension.class)
class WhatsNewDialogTest {

    private static final String NOTES = "## Features\n- Private-key authentication\n- Nested server groups\n\n## Fixed\n- Self-update from 0.5.x";

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    @Test
    @DisplayName("dialog opens with themed content, icon and closes cleanly")
    void showsThemedContentAndCloses() throws Exception {
        Window window = openDialog("0.6.4", NOTES);

        assertNotNull(window, "the What's New dialog must be showing");
        assertFalse(((Stage) window).getIcons().isEmpty(), "the dialog must carry the app icon");

        var root = window.getScene().getRoot();
        Label version = (Label) root.lookup("#" + WhatsNewDialog.VERSION_LABEL_ID);
        TextArea notes = (TextArea) root.lookup("#" + WhatsNewDialog.NOTES_TEXT_ID);
        assertNotNull(version, "version label must exist");
        assertNotNull(notes, "notes area must exist");
        assertTrue(version.getText().contains("0.6.4"));
        assertTrue(notes.getText().contains("Private-key authentication"));
        assertFalse(notes.getText().contains("##"), "markdown headings must be cleaned for display");
        assertFalse(notes.isEditable(), "the notes area must be read-only");

        assertNotNull(root.lookup(".whats-new-badge"), "the accent update badge must be present");
        assertNotNull(root.lookup(".whats-new-notes"), "the notes area must use the themed style");
        assertNotNull(root.lookup(".btn-primary"), "the closing action must be the themed primary button");
        assertFalse(root.getStylesheets().isEmpty(), "the dialog must carry the theme stylesheets");

        closeDialog(window);
    }

    @Test
    @DisplayName("renders correctly in Graphite, Light and Dark")
    void rendersInAllThemes() throws Exception {
        for (AppTheme.Theme theme : AppTheme.Theme.values()) {
            // Switch the theme BEFORE opening: a modal dialog cannot outlive a theme
            // change in the real app either.
            onFxThread(() -> {
                AppTheme.setTheme(AppTheme.Theme.DARK);
                AppTheme.setTheme(theme);
            });

            Window window = openDialog("0.6.4", NOTES);
            assertNotNull(window, theme + ": dialog must open");

            Image image = WaitForAsyncUtils.asyncFx(() -> window.getScene().getRoot().snapshot(null, null)).get();
            Path out = Path.of("build", "whats-new", theme.name().toLowerCase() + ".png");
            Files.createDirectories(out.getParent());
            BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) {
                for (int x = 0; x < png.getWidth(); x++) {
                    png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            ImageIO.write(png, "png", out.toFile());
            assertTrue(image.getWidth() > 300 && image.getHeight() > 200,
                    theme + ": rendered dialog looks too small");

            closeDialog(window);
        }
    }

    /** Opens the modal dialog on a helper thread and returns its window. */
    private Window openDialog(String version, String notes) throws Exception {
        Thread dialogThread = new Thread(() -> WhatsNewDialog.show(version, notes));
        dialogThread.setDaemon(true);
        dialogThread.start();
        WaitForAsyncUtils.waitForFxEvents();

        long deadline = System.currentTimeMillis() + 3000;
        AtomicReference<Window> found = new AtomicReference<>();
        while (System.currentTimeMillis() < deadline) {
            onFxThread(() -> found.set(Window.getWindows().stream()
                    .filter(w -> w.isShowing() && w instanceof Stage s && "What's New".equals(s.getTitle()))
                    .findFirst().orElse(null)));
            if (found.get() != null) {
                return found.get();
            }
            Thread.sleep(40);
        }
        return null;
    }

    private static void closeDialog(Window window) {
        onFxThread(window::hide);
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
