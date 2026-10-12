package com.seeloggyplus.shared.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

import java.util.Optional;

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

        Platform.runLater(passwordField::requestFocus);

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
