package com.seeloggyplus.controller;

import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.model.SSHServer;
import com.seeloggyplus.service.impl.SSHService;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.service.impl.ServerManagementServiceImpl;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for Remote File Dialog
 * Handles SSH connection and remote file browsing
 */
public class RemoteFileDialogController {

    private static final Logger logger = LoggerFactory.getLogger(
            RemoteFileDialogController.class
    );

    // FXML Components - Connection Tab
    @FXML
    private TabPane tabPane;

    @FXML
    private Tab remoteTab;

    @FXML
    private Tab localTab;

    // Saved Servers
    @FXML
    private ComboBox<SSHServer> savedServersComboBox;

    @FXML
    private Button loadServerButton;

    @FXML
    private Button saveServerButton;

    @FXML
    private Button deleteServerButton;

    // Remote Connection
    @FXML
    private TextField hostField;

    @FXML
    private TextField portField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox savePasswordCheckBox;

    @FXML
    private Button connectButton;

    @FXML
    private Button disconnectButton;

    @FXML
    private Label connectionStatusLabel;

    // Remote File Browser
    @FXML
    private VBox remoteBrowserPane;

    @FXML
    private TextField remotePathField;

    @FXML
    private Button goButton;

    @FXML
    private Button parentDirButton;

    @FXML
    private TableView<SSHService.RemoteFileInfo> remoteFilesTable;

    @FXML
    private TableColumn<SSHService.RemoteFileInfo, String> fileNameColumn;

    @FXML
    private TableColumn<SSHService.RemoteFileInfo, String> fileSizeColumn;

    @FXML
    private TableColumn<SSHService.RemoteFileInfo, String> fileTypeColumn;

    // Local File Browser
    @FXML
    private VBox localBrowserPane;

    @FXML
    private Button browseLocalButton;

    @FXML
    private TextField localFilePathField;

    // Action Buttons
    @FXML
    private Button okButton;

    @FXML
    private Button cancelButton;

    // Services and Data
    private ServerManagementService serverManagementService;
    private SSHService sshService;
    private ObservableList<SSHService.RemoteFileInfo> remoteFiles;
    private ObservableList<SSHServer> savedServers;
    private String currentRemotePath;
    private File selectedLocalFile;
    private RecentFile selectedRemoteFile;
    private SSHServer currentServer;
    private Consumer<File> onLocalFileSelected;
    private Consumer<RecentFile> onRemoteFileSelected;
    private boolean isConnected;

    @FXML
    public void initialize() {
        logger.info("Initializing RemoteFileDialogController");

        serverManagementService = new ServerManagementServiceImpl();
        sshService = new SSHService();
        remoteFiles = FXCollections.observableArrayList();
        savedServers = FXCollections.observableArrayList();
        isConnected = false;
        currentRemotePath = "/";

        setupSavedServers();
        setupConnectionPane();
        setupRemoteBrowser();
        setupLocalBrowser();
        setupButtons();

        // Start with local tab selected
        tabPane.getSelectionModel().select(localTab);
    }

    /**
     * Setup saved servers dropdown
     */
    private void setupSavedServers() {
        loadSavedServers();
        savedServersComboBox.setItems(savedServers);

        // Custom cell factory to display server name
        savedServersComboBox.setCellFactory(listView ->
                new ListCell<SSHServer>() {
                    @Override
                    protected void updateItem(SSHServer item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(item.getDisplayString());
                        }
                    }
                }
        );

        savedServersComboBox.setButtonCell(
                new ListCell<SSHServer>() {
                    @Override
                    protected void updateItem(SSHServer item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText("Select a saved server...");
                        } else {
                            setText(item.getDisplayString());
                        }
                    }
                }
        );

        // Setup buttons
        loadServerButton.setOnAction(e -> handleLoadServer());
        saveServerButton.setOnAction(e -> handleSaveServer());
        deleteServerButton.setOnAction(e -> handleDeleteServer());
    }

    /**
     * Load saved servers from database
     */
    private void loadSavedServers() {
        List<SSHServer> servers = serverManagementService.getAllServers();
        savedServers.setAll(servers);
        logger.info("Loaded {} saved SSH servers", servers.size());
    }

    /**
     * Setup connection pane
     */
    private void setupConnectionPane() {
        // Default port
        portField.setText("22");

        // Connection status
        connectionStatusLabel.setText("Not connected");
        connectionStatusLabel.setStyle("-fx-text-fill: gray;");

        // Disable remote browser initially
        remoteBrowserPane.setDisable(true);
        disconnectButton.setDisable(true);
    }

    /**
     * Setup remote file browser
     */
    private void setupRemoteBrowser() {
        // Configure table columns
        fileNameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getName())
        );

        fileSizeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFormattedSize())
        );

        fileTypeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        cellData.getValue().isDirectory() ? "Directory" : "File"
                )
        );

        remoteFilesTable.setItems(remoteFiles);

        // Handle double-click on directory
        remoteFilesTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                SSHService.RemoteFileInfo selected = remoteFilesTable
                        .getSelectionModel()
                        .getSelectedItem();
                if (selected != null && selected.isDirectory()) {
                    navigateToRemoteDirectory(selected.getPath());
                }
            }
        });

        // Setup navigation buttons
        goButton.setOnAction(e ->
                navigateToRemoteDirectory(remotePathField.getText())
        );
        parentDirButton.setOnAction(e -> navigateToParentDirectory());

        remotePathField.setOnAction(e ->
                navigateToRemoteDirectory(remotePathField.getText())
        );
    }

    /**
     * Setup local file browser
     */
    private void setupLocalBrowser() {
        browseLocalButton.setOnAction(e -> browseLocalFile());
    }

    /**
     * Setup action buttons
     */
    private void setupButtons() {
        connectButton.setOnAction(e -> handleConnect());
        disconnectButton.setOnAction(e -> handleDisconnect());
        okButton.setOnAction(e -> handleOk());
        cancelButton.setOnAction(e -> handleCancel());
    }

    /**
     * Handle load server from saved servers
     */
    private void handleLoadServer() {
        SSHServer server = savedServersComboBox.getSelectionModel().getSelectedItem();
        if (server == null) {
            showError("No Server Selected", "Please select a server from the dropdown");
            return;
        }

        hostField.setText(server.getHost());
        portField.setText(String.valueOf(server.getPort()));
        usernameField.setText(server.getUsername());

        if (server.isSavePassword()) {
            passwordField.setText(server.getPassword());
        }

        if (server.getDefaultPath() != null) {
            remotePathField.setText(server.getDefaultPath());
        }

        currentServer = server;
        logger.info("Loaded server: {}", server.getDisplayString());
    }

    /**
     * Handle save server
     */
    private void handleSaveServer() {
        String host = hostField.getText();
        String portStr = portField.getText();
        String username = usernameField.getText();

        if (host == null || host.trim().isEmpty()) {
            showError("Invalid Input", "Host is required");
            return;
        }

        if (username == null || username.trim().isEmpty()) {
            showError("Invalid Input", "Username is required");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Save Server");
        dialog.setHeaderText("Enter a name for this server");
        dialog.setContentText("Server Name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().trim().isEmpty()) {
            return;
        }

        String name = result.get().trim();

        try {
            int port = Integer.parseInt(portStr);

            SSHServer server = new SSHServer(name, host, port, username);
            server.setPassword(savePasswordCheckBox.isSelected() ? passwordField.getText() : null);
            server.setSavePassword(savePasswordCheckBox.isSelected());
            server.setDefaultPath(remotePathField.getText());

            if (!server.isValid()) {
                showError("Invalid Configuration", server.getValidationError());
                return;
            }

            serverManagementService.saveServer(server);
            loadSavedServers();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Server Saved");
            alert.setHeaderText("SSH Server Saved Successfully");
            alert.setContentText("Server '" + name + "' has been saved.");
            alert.showAndWait();

            logger.info("Saved SSH server: {}", name);
        } catch (NumberFormatException e) {
            showError("Invalid Input", "Port must be a number");
        }
    }

    /**
     * Handle delete server
     */
    private void handleDeleteServer() {
        SSHServer server = savedServersComboBox.getSelectionModel().getSelectedItem();
        if (server == null) {
            showError("No Server Selected", "Please select a server from the dropdown");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Server");
        alert.setHeaderText("Delete SSH Server?");
        alert.setContentText("Are you sure you want to delete '" + server.getName() + "'?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            serverManagementService.deleteServer(server.getId());
            loadSavedServers();
            savedServersComboBox.getSelectionModel().clearSelection();

            logger.info("Deleted SSH server: {}", server.getName());
        }
    }

    /**
     * Handle connect to SSH server
     */
    private void handleConnect() {
        String host = hostField.getText();
        String portStr = portField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Validation
        if (host == null || host.trim().isEmpty()) {
            showError("Invalid Input", "Host is required");
            return;
        }

        if (username == null || username.trim().isEmpty()) {
            showError("Invalid Input", "Username is required");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showError("Invalid Input", "Port must be a number");
            return;
        }

        if (password == null || password.isEmpty()) {
            showError("Invalid Input", "Password is required");
            return;
        }

        // Connect in background
        connectionStatusLabel.setText("Connecting...");
        connectionStatusLabel.setStyle("-fx-text-fill: orange;");
        connectButton.setDisable(true);

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() {
                return sshService.connect(host, port, username, password);
            }
        };

        connectTask.setOnSucceeded(e -> {
            boolean success = connectTask.getValue();
            if (success) {
                isConnected = true;
                connectionStatusLabel.setText("Connected to " + host);
                connectionStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                remoteBrowserPane.setDisable(false);
                disconnectButton.setDisable(false);
                connectButton.setDisable(true);

                if (currentServer != null && currentServer.getId() != null) {
                    serverManagementService.updateServerLastUsed(currentServer.getId());
                }

                String startPath = remotePathField.getText();
                if (startPath == null || startPath.trim().isEmpty()) {
                    startPath = "/";
                }

                navigateToRemoteDirectory(startPath);

                logger.info("Successfully connected to {}@{}", username, host);
            } else {
                connectionStatusLabel.setText("Connection failed");
                connectionStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                connectButton.setDisable(false);
                showError("Connection Failed", "Failed to connect to SSH server. Please check your credentials.");
            }
        });

        connectTask.setOnFailed(e -> {
            connectionStatusLabel.setText("Connection error");
            connectionStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            connectButton.setDisable(false);
            Throwable ex = connectTask.getException();
            logger.error("Connection failed", ex);
            showError("Connection Error", "Error: " + ex.getMessage());
        });

        new Thread(connectTask).start();
    }

    /**
     * Handle disconnect from SSH server
     */
    private void handleDisconnect() {
        if (sshService != null) {
            sshService.disconnect();
        }

        isConnected = false;
        connectionStatusLabel.setText("Disconnected");
        connectionStatusLabel.setStyle("-fx-text-fill: gray;");
        remoteBrowserPane.setDisable(true);
        disconnectButton.setDisable(true);
        connectButton.setDisable(false);
        remoteFiles.clear();

        logger.info("Disconnected from SSH server");
    }

    /**
     * Navigate to remote directory
     */
    private void navigateToRemoteDirectory(String path) {
        if (!isConnected) {
            return;
        }

        Task<List<SSHService.RemoteFileInfo>> task = new Task<>() {
            @Override
            protected List<SSHService.RemoteFileInfo> call() throws Exception {
                return sshService.listFiles(path);
            }
        };

        task.setOnSucceeded(e -> {
            List<SSHService.RemoteFileInfo> files = task.getValue();
            remoteFiles.setAll(files);
            currentRemotePath = path;
            remotePathField.setText(path);
            logger.debug("Loaded {} files from {}", files.size(), path);
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            logger.error("Failed to list remote directory", ex);
            showError("Directory Error", "Failed to list directory: " + ex.getMessage());
        });

        new Thread(task).start();
    }

    /**
     * Navigate to parent directory
     */
    private void navigateToParentDirectory() {
        if (currentRemotePath == null || currentRemotePath.equals("/")) {
            return;
        }

        String parentPath = currentRemotePath.substring(0, currentRemotePath.lastIndexOf('/'));
        if (parentPath.isEmpty()) {
            parentPath = "/";
        }

        navigateToRemoteDirectory(parentPath);
    }

    /**
     * Browse for local file
     */
    private void browseLocalFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Log File");
        fileChooser
                .getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("Log Files", "*.log", "*.txt"),
                        new FileChooser.ExtensionFilter("All Files", "*.*")
                );

        File file = fileChooser.showOpenDialog(browseLocalButton.getScene().getWindow());
        if (file != null) {
            selectedLocalFile = file;
            localFilePathField.setText(file.getAbsolutePath());
        }
    }

    /**
     * Handle OK button
     */
    private void handleOk() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();

        if (selectedTab == localTab) {
            if (selectedLocalFile != null && selectedLocalFile.exists()) {
                if (onLocalFileSelected != null) {
                    onLocalFileSelected.accept(selectedLocalFile);
                }
                closeDialog();
            } else {
                showError("No File Selected", "Please select a local file");
            }
        } else if (selectedTab == remoteTab) {
            SSHService.RemoteFileInfo selected = remoteFilesTable
                    .getSelectionModel()
                    .getSelectedItem();

            if (selected == null) {
                showError("No File Selected", "Please select a remote file");
                return;
            }

            if (selected.isDirectory()) {
                showError(
                        "Invalid Selection",
                        "Please select a file, not a directory"
                );
                return;
            }

//            // Create RecentFile for remote file
//            selectedRemoteFile = new RecentFile(
//                    selected.getName(),
//                    hostField.getText(),
//                    Integer.parseInt(portField.getText()),
//                    usernameField.getText(),
//                    selected.getPath()
//            );

            if (onRemoteFileSelected != null) {
                onRemoteFileSelected.accept(selectedRemoteFile);
            }

            closeDialog();
        }
    }

    /**
     * Handle cancel button
     */
    private void handleCancel() {
        if (isConnected) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Close Connection");
            alert.setHeaderText("You are still connected to SSH server");
            alert.setContentText("Do you want to disconnect and close?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                handleDisconnect();
                closeDialog();
            }
        } else {
            closeDialog();
        }
    }

    /**
     * Close dialog
     */
    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Show error alert
     */
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
