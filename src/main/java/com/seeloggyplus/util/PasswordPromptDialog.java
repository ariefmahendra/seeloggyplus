package com.seeloggyplus.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * A simple dialog to prompt the user for a password.
 */
public class PasswordPromptDialog extends Dialog<String> {

    private final PasswordField passwordField;

    public PasswordPromptDialog(String host, String username) {
        setTitle("Password Required");
        setHeaderText("Enter password for " + username + "@" + host);

        this.passwordField = new PasswordField();
        this.passwordField.setPromptText("Password");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getChildren().addAll(
                new Label("The saved connection does not have a password."),
                this.passwordField
        );

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Request focus on the password field by default.
        Platform.runLater(passwordField::requestFocus);

        // Convert the result to the password string when the OK button is clicked.
        this.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return passwordField.getText();
            }
            return null;
        });
    }

    public Optional<String> showAndWaitWithResult() {
        return super.showAndWait();
    }
}
