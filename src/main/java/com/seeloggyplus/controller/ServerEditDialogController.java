package com.seeloggyplus.controller;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

public class ServerEditDialogController {

    private static final Logger logger = LoggerFactory.getLogger(ServerEditDialogController.class);

    @FXML private TextField nameField;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField defaultPathField;
    @FXML private CheckBox savePasswordCheckBox;
    
    @FXML private Label nameErrorLabel;
    @FXML private Label hostErrorLabel;
    @FXML private Label portErrorLabel;
    @FXML private Label usernameErrorLabel;
    
    @FXML private Button testButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button showPasswordButton;

    @Setter
    private ServerManagementService serverService;
    private SSHServiceImpl sshService;
    private SSHServerModel sshServer;
    @Getter
    private boolean saved = false;
    private boolean passwordVisible = false;
    private TextField passwordTextField;

    @FXML
    public void initialize() {
        sshService = new SSHServiceImpl();

        setupPasswordField();
        setupValidation();
        setupEventHandlers();
    }
    
    /**
     * Setup password field with TextField for visibility toggle
     * Ensures both fields have identical size and layout properties
     */
    private void setupPasswordField() {
        passwordTextField = new TextField();
        passwordTextField.setPromptText(passwordField.getPromptText());
        passwordTextField.setManaged(false);
        passwordTextField.setVisible(false);

        passwordTextField.setPrefWidth(passwordField.getPrefWidth());
        passwordTextField.setMinWidth(passwordField.getMinWidth());
        passwordTextField.setMaxWidth(passwordField.getMaxWidth());
        passwordTextField.setPrefHeight(passwordField.getPrefHeight());
        passwordTextField.setMinHeight(passwordField.getMinHeight());
        passwordTextField.setMaxHeight(passwordField.getMaxHeight());

        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());

        var parent = passwordField.getParent();
        if (parent instanceof javafx.scene.layout.HBox hbox) {
            int index = hbox.getChildren().indexOf(passwordField);
            hbox.getChildren().add(index, passwordTextField);
            javafx.scene.layout.HBox.setHgrow(passwordTextField, javafx.scene.layout.HBox.getHgrow(passwordField));
        }
    }

    private void setupValidation() {
        hostField.textProperty().addListener((obs, old, val) -> clearError(hostErrorLabel));
        portField.textProperty().addListener((obs, old, val) -> clearError(portErrorLabel));
        usernameField.textProperty().addListener((obs, old, val) -> clearError(usernameErrorLabel));
    }

    private void setupEventHandlers() {
        testButton.setOnAction(e -> handleTest());
        saveButton.setOnAction(e -> handleSave());
        cancelButton.setOnAction(e -> handleCancel());
        showPasswordButton.setOnAction(e -> togglePasswordVisibility());
    }

    public void setServer(SSHServerModel server) {
        this.sshServer = server;
        nameField.setText(server.getName());
        hostField.setText(server.getHost());
        portField.setText(String.valueOf(server.getPort()));
        usernameField.setText(server.getUsername());
        passwordField.setText(server.getPassword() != null ? server.getPassword() : "");
        defaultPathField.setText(server.getDefaultPath() != null ? server.getDefaultPath() : "/");
        savePasswordCheckBox.setSelected(server.isSavePassword());
    }

    private void handleTest() {
        if (validateFields()) {
            return;
        }

        Alert progress = new Alert(Alert.AlertType.INFORMATION);
        progress.setTitle("Testing");
        progress.setHeaderText("Testing connection...");
        progress.setContentText("Please wait...");
        progress.show();

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                try {
                    return sshService.connect(hostField.getText(), Integer.parseInt(portField.getText()), usernameField.getText(), passwordField.getText());
                } catch (Exception e) {
                    logger.error("Connection test failed", e);
                    return false;
                }
            }
        };

        task.setOnSucceeded(e -> {
            progress.close();
            boolean success = task.getValue();
            
            Alert result = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            result.setTitle("Test Result");
            result.setHeaderText(success ? "Connection Successful" : "Connection Failed");
            result.setContentText(success ? 
                "Successfully connected to the server!" :
                "Failed to connect. Please check your credentials.");
            result.showAndWait();
        });

        new Thread(task).start();
    }

    private void handleSave() {
        if (validateFields()) {
            return;
        }

        SSHServerModel server;
        if (sshServer != null) {
            server = sshServer;
        } else {
            server = new SSHServerModel();
            server.setId(UUID.randomUUID().toString());
            server.setCreatedAt(LocalDateTime.now());
        }

        server.setName(nameField.getText().trim());
        server.setHost(hostField.getText().trim());
        server.setPort(Integer.parseInt(portField.getText().trim()));
        server.setUsername(usernameField.getText().trim());
        server.setDefaultPath(defaultPathField.getText().trim());
        server.setSavePassword(savePasswordCheckBox.isSelected());
        
        if (savePasswordCheckBox.isSelected()) {
            server.setPassword(passwordField.getText());
        } else {
            server.setPassword(null);
        }

        serverService.saveServer(server);

        saved = true;
        closeDialog();
    }

    private void handleCancel() {
        closeDialog();
    }

    /**
     * Toggle password visibility between PasswordField (hidden) and TextField (visible)
     * Updates button icon accordingly
     */
    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        
        if (passwordVisible) {
            // Show password (TextField visible, PasswordField hidden)
            passwordField.setManaged(false);
            passwordField.setVisible(false);
            passwordTextField.setManaged(true);
            passwordTextField.setVisible(true);
            passwordTextField.requestFocus();
            passwordTextField.positionCaret(passwordTextField.getText().length());
            
            // Change icon to EYE_SLASH
            updatePasswordButtonIcon("EYE_SLASH");
        } else {
            // Hide password (PasswordField visible, TextField hidden)
            passwordTextField.setManaged(false);
            passwordTextField.setVisible(false);
            passwordField.setManaged(true);
            passwordField.setVisible(true);
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
            
            // Change icon to EYE
            updatePasswordButtonIcon("EYE");
        }
    }
    
    /**
     * Update password button icon
     */
    private void updatePasswordButtonIcon(String iconName) {
        if (showPasswordButton.getGraphic() instanceof de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView icon) {
            icon.setGlyphName(iconName);
        }
    }

    private boolean validateFields() {
        boolean valid = true;

        if (hostField.getText().trim().isEmpty()) {
            showError(hostErrorLabel, "Host is required");
            valid = false;
        }

        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                showError(portErrorLabel, "Port must be between 1 and 65535");
                valid = false;
            }
        } catch (NumberFormatException e) {
            showError(portErrorLabel, "Port must be a number");
            valid = false;
        }

        if (usernameField.getText().trim().isEmpty()) {
            showError(usernameErrorLabel, "Username is required");
            valid = false;
        }

        return !valid;
    }

    private void showError(Label label, String message) {
        label.setText(message);
        label.setManaged(true);
        label.setVisible(true);
    }

    private void clearError(Label label) {
        label.setText("");
        label.setManaged(false);
        label.setVisible(false);
    }

    private void closeDialog() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
}
