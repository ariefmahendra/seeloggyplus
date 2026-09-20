package com.seeloggyplus.ui;

import com.seeloggyplus.util.AppTheme;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Icons must remain visible in dark mode: the default icon color follows the
 * theme text color, while explicitly colored icons keep their color.
 */
@ExtendWith(ApplicationExtension.class)
class IconThemeTest {

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
    void defaultIconColorFollowsTheme() {
        onFxThread(() -> {
            FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.FILE);
            StackPane root = new StackPane(icon);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();
            root.applyCss();

            assertEquals(Color.web("#1f2329"), icon.getFill(),
                    "light theme default icon must be dark");

            AppTheme.setDark(true);
            root.applyCss();
            assertEquals(Color.web("#d7dbe0"), icon.getFill(),
                    "dark theme default icon must be light (contrasting)");

            AppTheme.setDark(false);
        });
    }

    @Test
    void explicitlyColoredIconKeepsColorInDark() {
        onFxThread(() -> {
            FontAwesomeIconView star = new FontAwesomeIconView(FontAwesomeIcon.STAR);
            star.getStyleClass().add("favorite-star");
            StackPane root = new StackPane(star);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();
            root.applyCss();

            AppTheme.setDark(true);
            root.applyCss();
            assertEquals(Color.web("#fbbf24"), star.getFill(),
                    "favorite star must use the dark theme warning tone");

            AppTheme.setDark(false);
        });
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
