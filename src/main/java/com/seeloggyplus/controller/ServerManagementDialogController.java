package com.seeloggyplus.controller;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.SSHService;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.service.impl.ServerManagementServiceImpl;
import com.seeloggyplus.util.PasswordPromptDialog;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * High-performance Server Management Dialog Controller
 * Manages SSH server configurations with professional UI
 */
public class ServerManagementDialogController {

    private static final Logger logger = LoggerFactory.getLogger(ServerManagementDialogController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private TableView<SSHServerModel> serverTable;
    @FXML private TableColumn<SSHServerModel, String> statusColumn;
    @FXML private TableColumn<SSHServerModel, String> nameColumn;
    @FXML private TableColumn<SSHServerModel, String> hostColumn;
    @FXML private TableColumn<SSHServerModel, String> portColumn;
    @FXML private TableColumn<SSHServerModel, String> usernameColumn;
    @FXML private TableColumn<SSHServerModel, String> defaultPathColumn;
    @FXML private TableColumn<SSHServerModel, String> lastUsedColumn;

    @FXML private TextField searchField;
    @FXML private Button addServerButton;
    @FXML private Button editServerButton;
    @FXML private Button deleteServerButton;
    @FXML private Button testConnectionButton;
    @FXML private Button refreshButton;
    @FXML private Button clearSearchButton;
    @FXML private Button closeButton;

    @FXML private Label detailNameLabel;
    @FXML private Label detailHostLabel;
    @FXML private Label detailUsernameLabel;
    @FXML private Label detailPortLabel;
    @FXML private Label detailPathLabel;
    @FXML private Label detailCreatedLabel;
    @FXML private Label detailLastUsedLabel;

    private ServerManagementService serverService;
    private SSHService sshService;
    private ObservableList<SSHServerModel> allServers;
    private ObservableList<SSHServerModel> filteredServers;
    private SSHServerModel selectedForConnection;

    @FXML
    public void initialize() {
        logger.info("Initializing ServerManagementDialogController");
        
        serverService = new ServerManagementServiceImpl();
        sshService = new SSHServiceImpl();
        allServers = FXCollections.observableArrayList();
        filteredServers = FXCollections.observableArrayList();

        setupTableColumns();
        setupEventHandlers();
        loadServers();
        updateButtonStates();
    }

    /**
     * Setup table columns with optimized cell factories
     */
    private void setupTableColumns() {
        // Status column with real-time connection indicators
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(""));
        statusColumn.setCellFactory(col -> new TableCell<>() {
            private final FontAwesomeIconView icon = new FontAwesomeIconView();
            
            {
                icon.setSize("16");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    SSHServerModel server = getTableRow().getItem();
                    updateStatusIcon(server);
                    setGraphic(icon);
                }
            }
            
            private void updateStatusIcon(SSHServerModel server) {
                if (!server.isValid()) {
                    // Invalid configuration
                    icon.setIcon(FontAwesomeIcon.EXCLAMATION_TRIANGLE);
                    icon.setFill(Color.web("#e74c3c"));
                    setTooltip(new Tooltip("Invalid: " + server.getValidationError()));
                    return;
                }
                
                // Show connection status
                switch (server.getConnectionStatus()) {
                    case CONNECTED:
                        icon.setIcon(FontAwesomeIcon.CHECK_CIRCLE);
                        icon.setFill(Color.web("#2ecc71")); // Green
                        setTooltip(new Tooltip("Connected"));
                        break;
                    case DISCONNECTED:
                        icon.setIcon(FontAwesomeIcon.TIMES_CIRCLE);
                        icon.setFill(Color.web("#e74c3c")); // Red
                        setTooltip(new Tooltip("Disconnected"));
                        break;
                    case TESTING:
                        icon.setIcon(FontAwesomeIcon.SPINNER);
                        icon.setFill(Color.web("#f39c12")); // Orange
                        setTooltip(new Tooltip("Testing connection..."));
                        break;
                    case UNKNOWN:
                    default:
                        icon.setIcon(FontAwesomeIcon.CIRCLE);
                        icon.setFill(Color.web("#95a5a6")); // Gray
                        setTooltip(new Tooltip("Status unknown - click 'Test Connection'"));
                        break;
                }
            }
        });

        nameColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getName() != null ? cellData.getValue().getName() : "-"));
        
        hostColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getHost()));
        
        portColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.valueOf(cellData.getValue().getPort())));
        
        usernameColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getUsername()));
        
        defaultPathColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getDefaultPath() != null ? 
                cellData.getValue().getDefaultPath() : "/"));
        
        lastUsedColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue().getLastUsed() != null) {
                return new SimpleStringProperty(cellData.getValue().getLastUsed().format(DATE_FORMATTER));
            }
            return new SimpleStringProperty("-");
        });

        serverTable.setItems(filteredServers);
        
        // Selection listener
        serverTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateDetailsPanel(newVal);
            updateButtonStates();
        });
    }

    /**
     * Setup event handlers with optimized callbacks
     */
    private void setupEventHandlers() {
        addServerButton.setOnAction(e -> handleAddServer());
        editServerButton.setOnAction(e -> handleEditServer());
        deleteServerButton.setOnAction(e -> handleDeleteServer());
        testConnectionButton.setOnAction(e -> handleTestConnection());
        refreshButton.setOnAction(e -> loadServers());
        closeButton.setOnAction(e -> handleClose());
        
        // Search functionality
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterServers(newVal));
        clearSearchButton.setOnAction(e -> searchField.clear());
    }

    /**
     * Load servers from database and check connection status
     */
    private void loadServers() {
        Task<List<SSHServerModel>> task = new Task<>() {
            @Override
            protected List<SSHServerModel> call() {
                return serverService.getAllServers();
            }
        };

        task.setOnSucceeded(e -> {
            allServers.clear();
            allServers.addAll(task.getValue());
            filterServers(searchField.getText());
            logger.info("Loaded {} servers", allServers.size());
            
            // Auto-check connection status for all servers (optional)
            // checkAllConnectionStatus();
        });

        task.setOnFailed(e -> {
            logger.error("Failed to load servers", task.getException());
            showError("Load Error", "Failed to load servers: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }
    
    /**
     * Check connection status for all servers in background
     * This is optional and can be triggered by user action
     */
    private void checkAllConnectionStatus() {
        for (SSHServerModel server : allServers) {
            if (server.isValid()) {
                checkConnectionStatus(server);
            }
        }
    }
    
    /**
     * Check connection status for a single server
     */
    private void checkConnectionStatus(SSHServerModel server) {
        // Set status to TESTING
        server.setConnectionStatus(SSHServerModel.ConnectionStatus.TESTING);
        serverTable.refresh();
        
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                try {
                    return sshService.connect(server.getHost(), server.getPort(), server.getUsername(), server.getPassword());
                } catch (Exception e) {
                    logger.debug("Connection test failed for {}: {}", server.getHost(), e.getMessage());
                    return false;
                }
            }
        };
        
        task.setOnSucceeded(e -> {
            boolean connected = task.getValue();
            server.setConnectionStatus(connected ? 
                SSHServerModel.ConnectionStatus.CONNECTED :
                SSHServerModel.ConnectionStatus.DISCONNECTED);
            serverTable.refresh();
            logger.debug("Server {} status: {}", server.getHost(), server.getConnectionStatus());
        });
        
        task.setOnFailed(e -> {
            server.setConnectionStatus(SSHServerModel.ConnectionStatus.DISCONNECTED);
            serverTable.refresh();
            logger.error("Connection check failed for {}", server.getHost(), task.getException());
        });
        
        new Thread(task).start();
    }

    /**
     * Filter servers based on search text
     */
    private void filterServers(String searchText) {
        filteredServers.clear();
        
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredServers.addAll(allServers);
            return;
        }

        String search = searchText.toLowerCase();
        for (SSHServerModel server : allServers) {
            if (matchesSearch(server, search)) {
                filteredServers.add(server);
            }
        }
    }

    /**
     * Check if server matches search criteria
     */
    private boolean matchesSearch(SSHServerModel server, String search) {
        return (server.getName() != null && server.getName().toLowerCase().contains(search)) ||
               server.getHost().toLowerCase().contains(search) ||
               server.getUsername().toLowerCase().contains(search) ||
               (server.getDefaultPath() != null && server.getDefaultPath().toLowerCase().contains(search));
    }

    /**
     * Handle add server action
     */
    private void handleAddServer() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerEditDialog.fxml"));
            Parent root = loader.load();
            
            ServerEditDialogController controller = loader.getController();
            controller.setServerService(serverService);
            
            Stage dialog = new Stage();
            dialog.setTitle("Add SSH Server");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(addServerButton.getScene().getWindow());
            dialog.setScene(new Scene(root));
            
            showAndWaitAndRestore(dialog);
            
            if (controller.isSaved()) {
                loadServers();
            }
        } catch (IOException e) {
            logger.error("Failed to open add server dialog", e);
            showError("Dialog Error", "Failed to open server editor: " + e.getMessage());
        }
    }

    /**
     * Handle edit server action
     */
    private void handleEditServer() {
        SSHServerModel selected = serverTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerEditDialog.fxml"));
            Parent root = loader.load();
            
            ServerEditDialogController controller = loader.getController();
            controller.setServerService(serverService);
            controller.setServer(selected);
            
            Stage dialog = new Stage();
            dialog.setTitle("Edit SSH Server");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(editServerButton.getScene().getWindow());
            dialog.setScene(new Scene(root));

            showAndWaitAndRestore(dialog);
            
            if (controller.isSaved()) {
                loadServers();
            }
        } catch (IOException e) {
            logger.error("Failed to open edit server dialog", e);
            showError("Dialog Error", "Failed to open server editor: " + e.getMessage());
        }
    }

    /**
     * Handle delete server action
     */
    private void handleDeleteServer() {
        SSHServerModel selected = serverTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Server");
        alert.setHeaderText("Delete SSH Server Configuration?");
        alert.setContentText(String.format("Are you sure you want to delete '%s'?\nThis action cannot be undone.", 
            selected.getDisplayString()));

        Optional<ButtonType> result = showAndWaitAndRestore(alert);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            serverService.deleteServer(selected.getId());
            loadServers();
            logger.info("Deleted server: {}", selected.getDisplayString());
        }
    }

    /**
     * Handle test connection action with real-time status update
     */
    private void handleTestConnection() {
        SSHServerModel selected = serverTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        if (!selected.isValid()) {
            showError("Invalid Configuration", selected.getValidationError());
            return;
        }

        // Prompt for password if it's not saved
        String password = selected.getPassword();
        if (password == null || password.isBlank()) {
            PasswordPromptDialog prompt = new PasswordPromptDialog(selected.getHost(), selected.getUsername());
            Optional<String> result = showAndWaitAndRestore(prompt);
            if (result.isPresent()) {
                password = result.get();
            } else {
                logger.info("User cancelled password prompt for test connection.");
                return; // Abort test
            }
        }
        final String finalPassword = password;

        // Set status to TESTING
        selected.setConnectionStatus(SSHServerModel.ConnectionStatus.TESTING);
        serverTable.refresh();

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                try {
                    return sshService.connect(selected.getHost(), selected.getPort(), selected.getUsername(), finalPassword);
                } catch (Exception e) {
                    logger.error("Connection test failed", e);
                    return false;
                }
            }
        };

        task.setOnSucceeded(e -> {
            boolean success = task.getValue();
            
            // Update status
            selected.setConnectionStatus(success ? 
                SSHServerModel.ConnectionStatus.CONNECTED :
                SSHServerModel.ConnectionStatus.DISCONNECTED);
            serverTable.refresh();
            
            // Show result dialog
            Alert resultDialog = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            resultDialog.setTitle("Connection Test");
            resultDialog.setHeaderText(success ? "Connection Successful" : "Connection Failed");
            resultDialog.setContentText(success ? 
                "Successfully connected to " + selected.getDisplayString() :
                "Failed to connect. Please check your credentials and network connection.");
            showAndWaitAndRestore(resultDialog);
        });

        task.setOnFailed(e -> {
            selected.setConnectionStatus(SSHServerModel.ConnectionStatus.DISCONNECTED);
            serverTable.refresh();
            showError("Test Failed", "Connection test failed: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    /**
     * Update details panel with server information
     */
    private void updateDetailsPanel(SSHServerModel server) {
        if (server == null) {
            detailNameLabel.setText("-");
            detailHostLabel.setText("-");
            detailUsernameLabel.setText("-");
            detailPortLabel.setText("-");
            detailPathLabel.setText("-");
            detailCreatedLabel.setText("-");
            detailLastUsedLabel.setText("-");
            return;
        }

        detailNameLabel.setText(server.getName() != null ? server.getName() : "-");
        detailHostLabel.setText(server.getHost());
        detailUsernameLabel.setText(server.getUsername());
        detailPortLabel.setText(String.valueOf(server.getPort()));
        detailPathLabel.setText(server.getDefaultPath() != null ? server.getDefaultPath() : "/");
        detailCreatedLabel.setText(server.getCreatedAt() != null ? 
            server.getCreatedAt().format(DATE_FORMATTER) : "-");
        detailLastUsedLabel.setText(server.getLastUsed() != null ? 
            server.getLastUsed().format(DATE_FORMATTER) : "Never");
    }

    /**
     * Update button states based on selection
     */
    private void updateButtonStates() {
        boolean hasSelection = serverTable.getSelectionModel().getSelectedItem() != null;
        editServerButton.setDisable(!hasSelection);
        deleteServerButton.setDisable(!hasSelection);
        testConnectionButton.setDisable(!hasSelection);
    }

    /**
     * Handle close action
     */
    private void handleClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Show error dialog
     */
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(message);
            showAndWaitAndRestore(alert);
        });
    }

    public SSHServerModel getSelectedServer() {
        return selectedForConnection;
    }

    // --- Window State Restoration Workaround ---

    private Stage getUltimateOwner(Stage stage) {
        Stage owner = stage;
        while (owner.getOwner() != null) {
            owner = (Stage) owner.getOwner();
        }
        return owner;
    }
    
    private void showAndWaitAndRestore(Stage dialog) {
        Stage owner = getUltimateOwner((Stage) dialog.getOwner());
        boolean wasMaximized = owner.isMaximized();
        double oldX = owner.getX(), oldY = owner.getY(), oldW = owner.getWidth(), oldH = owner.getHeight();

        dialog.showAndWait();

        Platform.runLater(() -> {
            if (wasMaximized) {
                owner.setMaximized(true);
            } else {
                owner.setX(oldX);
                owner.setY(oldY);
                owner.setWidth(oldW);
                owner.setHeight(oldH);
            }
        });
    }

    private <T> Optional<T> showAndWaitAndRestore(Dialog<T> dialog) {
        Stage owner = getUltimateOwner((Stage) dialog.getOwner());
        boolean wasMaximized = owner.isMaximized();
        double oldX = owner.getX(), oldY = owner.getY(), oldW = owner.getWidth(), oldH = owner.getHeight();

        if (dialog.getOwner() == null) {
            dialog.initOwner(owner);
        }

        Optional<T> result = dialog.showAndWait();

        Platform.runLater(() -> {
            if (wasMaximized) {
                owner.setMaximized(true);
            } else {
                owner.setX(oldX);
                owner.setY(oldY);
                owner.setWidth(oldW);
                owner.setHeight(oldH);
            }
        });

        return result;
    }
}