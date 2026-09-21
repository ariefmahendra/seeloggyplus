package com.seeloggyplus.controller;

import com.seeloggyplus.ui.SelectionColors;
import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
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
 * Defect fix: in the Find in Files preview, the selected/highlighted line must
 * use the shared selection colour and keep readable, theme-matched text in both
 * light and dark mode.
 */
@ExtendWith(ApplicationExtension.class)
class PreviewSelectionThemeTest {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

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
    void selectedPreviewLineUsesSharedSelectionColour() {
        onFxThread(() -> {
            ListView<String> list = buildPreviewList();
            VBox root = new VBox(list);
            root.getStyleClass().add("remote-log-search-dialog");
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.setWidth(520);
            stage.setHeight(280);
            stage.show();

            for (boolean dark : new boolean[] {false, true}) {
                AppTheme.setDark(dark);
                list.getSelectionModel().select(1);
                root.applyCss();
                root.layout();
                WaitForAsyncUtils.waitForFxEvents();
                String mode = dark ? "dark" : "light";

                Region cell = selectedCell(list);
                assertNotNull(cell, "preview must have a selected cell");

                Color selection = firstFill(cell.getBackground());
                assertEquals(SelectionColors.background(dark), selection,
                        "preview selection [" + mode + "] must use the shared selection colour");

                Label label = (Label) cell.lookup(".label");
                assertNotNull(label, "preview cell must contain a text label");
                Color text = label.getTextFill() instanceof Color c ? c : null;
                assertNotNull(text, "preview text fill must be a colour");

                Color surface = dark ? Color.web("#1e2226") : Color.WHITE;
                assertTrue(contrast(text, surface) >= 4.5,
                        String.format("preview selected text [%s] contrast too low: %.2f:1 (text=%s)",
                                mode, contrast(text, surface), text));
            }

            AppTheme.setDark(false);
        });
    }

    private static ListView<String> buildPreviewList() {
        ListView<String> list = new ListView<>();
        list.getItems().addAll("first line", "highlighted target line", "third line");
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    setGraphic(new Label(item));
                }
            }
        });
        return list;
    }

    private static Region selectedCell(ListView<?> list) {
        for (Node node : list.lookupAll(".list-cell")) {
            if (node instanceof Region region && node.getPseudoClassStates().contains(SELECTED)) {
                return region;
            }
        }
        return null;
    }

    private static Color firstFill(Background background) {
        if (background == null || background.getFills().isEmpty()) {
            return null;
        }
        Paint paint = background.getFills().get(0).getFill();
        return paint instanceof Color color ? color : null;
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
