package com.seeloggyplus.app;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import com.seeloggyplus.app.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.util.function.Consumer;

public class AboutDialogController {

    private static final Logger logger = LoggerFactory.getLogger(AboutDialogController.class);

    /** Maintainer contact shown in About; the hyperlink opens the default mail client. */
    public static final String CONTACT_EMAIL = "mahend.arief@gmail.com";

    @FXML
    private Label versionLabel;

    @FXML
    private Hyperlink contactLink;

    @FXML
    private Button closeButton;

    private Consumer<String> browser = AboutDialogController::openMailClient;

    @FXML
    public void initialize() {
        if (versionLabel != null) {
            versionLabel.setText("Version " + Main.VERSION);
        }
        if (contactLink != null) {
            contactLink.setOnAction(e -> browser.accept("mailto:" + CONTACT_EMAIL));
        }
        closeButton.setOnAction(e -> closeDialog());
    }

    /** Injected for tests to avoid opening a real mail client. */
    void setBrowser(Consumer<String> browser) {
        this.browser = browser != null ? browser : AboutDialogController::openMailClient;
    }

    private void closeDialog() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    private static void openMailClient(String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(uri));
            }
        } catch (Exception e) {
            logger.warn("Failed to open contact link {}: {}", uri, e.getMessage());
        }
    }
}
