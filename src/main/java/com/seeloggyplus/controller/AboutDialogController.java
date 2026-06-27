package com.seeloggyplus.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import com.seeloggyplus.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AboutDialogController {

    private static final Logger logger = LoggerFactory.getLogger(AboutDialogController.class);

    @FXML
    private Label versionLabel;

    @FXML
    private Button closeButton;


    @FXML
    public void initialize() {
        if (versionLabel != null) {
            versionLabel.setText("Version " + Main.VERSION);
        }
        closeButton.setOnAction(e -> closeDialog());
    }

    private void closeDialog() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}