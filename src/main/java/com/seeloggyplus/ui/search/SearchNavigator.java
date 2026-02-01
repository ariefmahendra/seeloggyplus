package com.seeloggyplus.ui.search;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

public class SearchNavigator extends HBox {
    private final Label statusLabel;
    private final Button prevButton;
    private final Button nextButton;

    public SearchNavigator() {
        this.statusLabel = new Label("0 matches");
        this.statusLabel.setStyle("-fx-text-fill: #666; -fx-padding: 0 5 0 0;");

        this.prevButton = new Button();
        FontAwesomeIconView upIcon = new FontAwesomeIconView();
        upIcon.setGlyphName("CHEVRON_UP");
        upIcon.setSize("12");
        this.prevButton.setGraphic(upIcon);
        this.prevButton.setTooltip(new Tooltip("Previous Match (Shift+F3)"));
        this.prevButton.getStyleClass().add("icon-button");

        this.nextButton = new Button();
        FontAwesomeIconView downIcon = new FontAwesomeIconView();
        downIcon.setGlyphName("CHEVRON_DOWN");
        downIcon.setSize("12");
        this.nextButton.setGraphic(downIcon);
        this.nextButton.setTooltip(new Tooltip("Next Match (F3)"));
        this.nextButton.getStyleClass().add("icon-button");

        this.setAlignment(Pos.CENTER_LEFT);
        this.setSpacing(2);
        this.getChildren().addAll(statusLabel, prevButton, nextButton);

        this.setVisible(false);
        this.setManaged(false);
    }

    public void setMatchCount(int count) {
        if (count < 0) {
            statusLabel.setText("Scanning...");
        } else if (count == 0) {
            statusLabel.setText("No matches");
        } else {
            statusLabel.setText(count + " matches");
        }
        updateVisibility(true);
    }

    public void updateStatus(int current, int total) {
        if (total <= 0) {
            statusLabel.setText("No matches");
        } else {
            statusLabel.setText(current + " / " + total);
        }
        updateVisibility(true);
    }

    public void clear() {
        statusLabel.setText("");
        updateVisibility(false);
    }

    private void updateVisibility(boolean visible) {
        this.setVisible(visible);
        this.setManaged(visible);
    }

    public void setOnPrevious(Runnable handler) {
        prevButton.setOnAction(e -> handler.run());
    }

    public void setOnNext(Runnable handler) {
        nextButton.setOnAction(e -> handler.run());
    }
}
