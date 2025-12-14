package com.seeloggyplus.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

import java.net.URL;
import java.util.ResourceBundle;

public class SplashController implements Initializable {

    @FXML
    private Label versionLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressBar progressBar;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Version will be set from Main or Properties
        // For now, hardcoded or potentially set via a setter if needed
    }

    public void setProgress(double progress, String message) {
        // Update UI on JavaFX Application Thread
        javafx.application.Platform.runLater(() -> {
            progressBar.setProgress(progress);
            if (message != null) {
                statusLabel.setText(message);
            }
        });
    }

    public void setVersion(String version) {
        versionLabel.setText("Version " + version);
    }
}
