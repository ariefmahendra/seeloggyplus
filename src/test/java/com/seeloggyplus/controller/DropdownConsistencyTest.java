package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Background;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Find-in-Files "Context" dropdown must look and behave like the Preferences
 * dropdowns: the pref-list highlight rule used to leak onto the ComboBox button
 * cell, making that one field look different (translucent dark/blue selection)
 * from every other themed field.
 */
@ExtendWith(ApplicationExtension.class)
class DropdownConsistencyTest {

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
    @DisplayName("Find-in-Files context dropdown matches the Preferences dropdown in every theme")
    void contextDropdownMatchesPreferencesDropdown() throws Exception {
        for (AppTheme.Theme theme : AppTheme.Theme.values()) {
            Parent searchRoot = loadRoot("/fxml/RemoteLogSearchDialog.fxml");
            Parent prefsRoot = loadRoot("/fxml/PreferencesDialog.fxml");

            onFxThread(() -> {
                useTheme(theme);
                Scene searchScene = AppTheme.scene(searchRoot);
                Scene prefsScene = AppTheme.scene(prefsRoot);
                stage.setScene(searchScene);
                stage.setWidth(900);
                stage.setHeight(650);
                stage.show();
                searchRoot.applyCss();
                searchRoot.layout();
                prefsScene.getRoot().applyCss();
                prefsScene.getRoot().layout();
                WaitForAsyncUtils.waitForFxEvents();
            });

            ComboBox<?> context = (ComboBox<?>) searchRoot.lookup("#contextSelector");
            ComboBox<?> prefsTheme = (ComboBox<?>) prefsRoot.lookup("#themeComboBox");
            assertNotNull(context, theme + ": context dropdown must exist");
            assertNotNull(prefsTheme, theme + ": preferences dropdown must exist");

            Color contextSurface = firstFill(context.getBackground());
            Color prefsSurface = firstFill(prefsTheme.getBackground());
            assertNotNull(contextSurface);
            assertNotNull(prefsSurface);
            assertEquals(prefsSurface, contextSurface,
                    theme + ": both dropdowns must share the same field surface");
            assertTrue(luminance(contextSurface) > 0.7 || theme == AppTheme.Theme.DARK,
                    theme + ": dropdown fields must be light outside Dark");

            ListCell<?> contextCell = (ListCell<?>) context.lookup(".list-cell");
            ListCell<?> prefsCell = (ListCell<?>) prefsTheme.lookup(".list-cell");
            assertNotNull(contextCell);
            assertNotNull(prefsCell);
            assertEquals(firstFill(prefsCell.getBackground()), firstFill(contextCell.getBackground()),
                    theme + ": the context button cell must not inherit the preview-list highlight");

            Color text = (Color) contextCell.getTextFill();
            assertNotNull(text);
            assertTrue(contrast(text, contextSurface) >= 4.5,
                    theme + ": context dropdown text must be readable");
        }
    }

    @Test
    @DisplayName("the preview list keeps its own selection highlight scope")
    void previewListKeepsItsHighlight() throws Exception {
        Parent root = loadRoot("/fxml/RemoteLogSearchDialog.fxml");
        onFxThread(() -> {
            stage.setScene(AppTheme.scene(root));
            stage.show();
            root.applyCss();
            root.layout();
            WaitForAsyncUtils.waitForFxEvents();
        });

        javafx.scene.Node previewList = root.lookup("#previewList");
        assertNotNull(previewList);
        assertTrue(previewList.getStyleClass().contains("preview-list"),
                "the preview list needs its own style class so its highlight cannot leak");
    }

    private Parent loadRoot(String fxml) throws Exception {
        AtomicReference<Parent> root = new AtomicReference<>();
        onFxThread(() -> {
            try {
                root.set(new FXMLLoader(getClass().getResource(fxml)).load());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return root.get();
    }

    private void useTheme(AppTheme.Theme theme) {
        AppTheme.setTheme(AppTheme.Theme.DARK);
        AppTheme.setTheme(theme);
    }

    private static Color firstFill(Background background) {
        if (background == null || background.getFills().isEmpty()) {
            return null;
        }
        Paint fill = background.getFills().get(0).getFill();
        return fill instanceof Color c ? c : null;
    }

    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
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
