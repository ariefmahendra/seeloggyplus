package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.text.Text;
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
 * Regression: a selected row/item whose control is NOT focused must keep its
 * theme background so the theme-coloured text and icons stay contrasty
 * (Modena's default non-focused selection bar is light, hiding light icons).
 */
@ExtendWith(ApplicationExtension.class)
class SelectedRowInactiveContrastTest {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final double MIN_ICON = 3.0;
    private static final double MIN_TEXT = 4.5;

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
    void inactiveSelectedRowKeepsContrast() {
        onFxThread(() -> {
            TableView<String> table = buildTable();
            ListView<String> list = buildList();
            TextField focusSink = new TextField();

            VBox root = new VBox(10, table, list, focusSink);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.setWidth(520);
            stage.setHeight(520);
            stage.show();

            for (boolean dark : new boolean[] {false, true}) {
                AppTheme.setDark(dark);
                String mode = dark ? "dark" : "light";

                table.getSelectionModel().select(0);
                list.getSelectionModel().select(0);
                // Move focus away so the controls render the non-focused selection.
                focusSink.requestFocus();
                root.applyCss();
                root.layout();
                WaitForAsyncUtils.waitForFxEvents();

                assertSelectedRow(table, "TableView", mode);
                assertSelectedCell(list, "ListView", mode);
            }

            AppTheme.setDark(false);
        });
    }

    private void assertSelectedRow(TableView<?> table, String name, String mode) {
        Region row = selectedRegion(table, ".table-row-cell");
        assertNotNull(row, name + " must have a selected row");
        assertContentContrast(row, name, mode);
    }

    private void assertSelectedCell(ListView<?> list, String name, String mode) {
        Region cell = selectedRegion(list, ".list-cell");
        assertNotNull(cell, name + " must have a selected cell");
        assertContentContrast(cell, name, mode);
    }

    private static void assertContentContrast(Region container, String name, String mode) {
        Color bg = innerBackground(container);
        assertNotNull(bg, name + " selected background must be resolvable");

        Node iconNode = container.lookup(".glyph-icon");
        assertNotNull(iconNode, name + " selected row must show an icon");
        Color icon = ((FontAwesomeIconView) iconNode).getFill() instanceof Color c ? c : null;
        assertNotNull(icon, name + " icon fill must be a colour");
        assertTrue(contrast(icon, bg) >= MIN_ICON,
                String.format("%s [%s] selected icon contrast too low: %.2f:1 (icon=%s bg=%s)",
                        name, mode, contrast(icon, bg), icon, bg));

        Node textNode = container.lookup(".text");
        if (textNode instanceof Text text && text.getFill() instanceof Color textColor) {
            assertTrue(contrast(textColor, bg) >= MIN_TEXT,
                    String.format("%s [%s] selected text contrast too low: %.2f:1 (text=%s bg=%s)",
                            name, mode, contrast(textColor, bg), textColor, bg));
        }
    }

    private static Region selectedRegion(Region container, String selector) {
        for (Node node : container.lookupAll(selector)) {
            if (node instanceof Region region && node.getPseudoClassStates().contains(SELECTED)) {
                return region;
            }
        }
        return null;
    }

    private static Color innerBackground(Region region) {
        Background background = region.getBackground();
        if (background == null) {
            return null;
        }
        var fills = background.getFills();
        for (int i = fills.size() - 1; i >= 0; i--) {
            Color color = toColor(fills.get(i).getFill());
            if (color != null && color.getOpacity() > 0.5) {
                return color;
            }
        }
        return null;
    }

    private static Color toColor(Paint paint) {
        if (paint instanceof Color color) {
            return color;
        }
        if (paint instanceof LinearGradient gradient && !gradient.getStops().isEmpty()) {
            for (Stop stop : gradient.getStops()) {
                if (stop.getColor().getOpacity() > 0.5) {
                    return stop.getColor();
                }
            }
        }
        return null;
    }

    private static TableView<String> buildTable() {
        TableView<String> table = new TableView<>();
        table.getItems().addAll("alpha", "beta");
        table.setPrefHeight(120);

        TableColumn<String, String> column = new TableColumn<>("Name");
        column.setPrefWidth(300);
        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
        column.setCellFactory(col -> new TableCell<>() {
            private final FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.GLOBE);
            {
                icon.getStyleClass().add("glyph-icon");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setGraphic(icon);
                }
            }
        });
        table.getColumns().add(column);
        return table;
    }

    private static ListView<String> buildList() {
        ListView<String> list = new ListView<>();
        list.getItems().addAll("alpha", "beta");
        list.setPrefHeight(120);
        list.setCellFactory(view -> new ListCell<>() {
            private final FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.FOLDER);

            {
                icon.getStyleClass().add("glyph-icon");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setGraphic(icon);
                }
            }
        });
        return list;
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
