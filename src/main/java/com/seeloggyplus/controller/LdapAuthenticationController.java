package com.seeloggyplus.controller;

import com.seeloggyplus.exceptions.LdapException;
import com.seeloggyplus.service.LdapAuthenticator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LdapAuthenticationController {
    public TextField userIdTextField;
    public PasswordField passwordTextField;
    public ComboBox<String> listCountryComboBox;

    private LdapAuthenticator ldapAuthenticator;

    @FXML
    public void initialize(){
        ldapAuthenticator = new LdapAuthenticator();

        ObservableList<String> listCountries = FXCollections.observableArrayList("ID", "EN", "SG", "KR", "US", "AU", "NL", "GB", "CN", "IN", "JP");
        listCountryComboBox.setItems(listCountries);
        listCountryComboBox.getSelectionModel().selectFirst();
    }

    /**
     * Show info dialog
     */
    private void showInfo(Alert.AlertType alertType, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @FXML
    public void loginLdap(ActionEvent actionEvent) {
        String userId = userIdTextField.getText();
        String password = passwordTextField.getText();
        String country = listCountryComboBox.getSelectionModel().getSelectedItem();

        try {
            boolean authenticate = ldapAuthenticator.authenticate(userId, password, country);
            if (!authenticate){
                showInfo(Alert.AlertType.WARNING,"Login LDAP", "Failed To Login");
                return;
            }

            showInfo(Alert.AlertType.INFORMATION,"Login LDAP", "Success Login");

            ((Stage) passwordTextField.getScene().getWindow()).close();
        } catch (LdapException ex){
            showInfo(Alert.AlertType.WARNING,"Login LDAP", ex.getMessage());
        }
    }
}
