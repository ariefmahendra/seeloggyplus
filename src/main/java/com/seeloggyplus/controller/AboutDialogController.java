package com.seeloggyplus.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AboutDialogController {

    private static final Logger logger = LoggerFactory.getLogger(AboutDialogController.class);
    private static final String VERSION;

    static {
        String version = "DEV"; // Default version
        try (java.io.InputStream input = AboutDialogController.class.getResourceAsStream("/version.properties")) {
            java.util.Properties prop = new java.util.Properties();
            if (input == null) {
                logger.warn("Sorry, unable to find version.properties, defaulting to DEV version.");
            } else {
                prop.load(input);
                version = prop.getProperty("version", version);
            }
        } catch (IOException ex) {
            logger.error("Error reading version.properties", ex);
        }
        VERSION = version;
    }

    @FXML
    private Label versionLabel;

    @FXML
    private Button closeButton;


    @FXML
    public void initialize() {
        if (versionLabel != null) {
            versionLabel.setText("Version " + VERSION);
        }
        closeButton.setOnAction(e -> closeDialog());
    }

    private void closeDialog() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}