package com.seeloggyplus.shared.ui;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Window;

public final class ThemedAlerts {
    private ThemedAlerts() {
    }

    public static Alert create(Alert.AlertType type) {
        return create(type, null);
    }

    public static Alert create(Alert.AlertType type, Window owner) {
        Alert alert = new Alert(type);
        DialogPane pane = alert.getDialogPane();
        DialogWindowSupport.styleDialog(alert, owner);
        setGraphic(alert);
        alert.alertTypeProperty().addListener((observable, oldType, newType) -> setGraphic(alert));
        alert.contentTextProperty().addListener((observable, oldText, text) -> setMessage(pane, text));
        return alert;
    }

    private static void setGraphic(Alert alert) {
        FontAwesomeIcon symbol = switch (alert.getAlertType()) {
            case ERROR -> FontAwesomeIcon.EXCLAMATION_CIRCLE;
            case WARNING -> FontAwesomeIcon.EXCLAMATION_TRIANGLE;
            case CONFIRMATION -> FontAwesomeIcon.QUESTION_CIRCLE;
            default -> FontAwesomeIcon.INFO_CIRCLE;
        };
        var glyph = new FontAwesomeIconView(symbol);
        glyph.setSize("20");
        glyph.getStyleClass().addAll("message-icon-glyph", "message-" + alert.getAlertType().name().toLowerCase(java.util.Locale.ROOT));
        var icon = new StackPane(glyph);
        icon.setMinSize(24, 24);
        icon.setPrefSize(24, 24);
        icon.setMaxSize(24, 24);
        icon.getStyleClass().add("message-icon");
        alert.setGraphic(icon);
    }

    private static void setMessage(DialogPane pane, String text) {
        if (text == null || text.isEmpty()) {
            pane.setContent(null);
            return;
        }
        Label message = new Label(text);
        message.setWrapText(true);
        message.setMinHeight(Region.USE_PREF_SIZE);
        message.getStyleClass().add("message-text");
        double limit = Math.min(320, Screen.getPrimary().getVisualBounds().getHeight() * .4);
        ScrollPane scroll = new ScrollPane(message) {
            @Override
            protected double computePrefHeight(double width) {
                double available = width < 0 ? Math.max(320, pane.getPrefWidth() - 48)
                        : Math.max(1, width - snappedLeftInset() - snappedRightInset());
                return Math.min(limit, Math.ceil(message.prefHeight(available))
                        + snappedTopInset() + snappedBottomInset() + 2);
            }
        };
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("message-scroll");
        scroll.setMaxHeight(limit);
        scroll.setMinHeight(Region.USE_PREF_SIZE);
        pane.setContent(new VBox(scroll));
    }
}
