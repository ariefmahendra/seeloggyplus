package com.seeloggyplus.controller;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.service.impl.ServerManagementServiceImpl;
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

    @FXML
    private TableView<SSHServerModel> serverTable;
    @FXML
    private TableColumn<SSHServerModel, String> nameColumn;
    @FXML
    private TableColumn<SSHServerModel, String> hostColumn;
    @FXML
    private TableColumn<SSHServerModel, String> portColumn;
    @FXML
    private TableColumn<SSHServerModel, String> usernameColumn;
    @FXML
    private TableColumn<SSHServerModel, String> defaultPathColumn;
    @FXML
    private TableColumn<SSHServerModel, String> lastUsedColumn;

    @FXML
    private TextField searchField;
    @FXML
    private Button addServerButton;
    @FXML
    private Button editServerButton;
    @FXML
    private Button cloneServerButton;
    @FXML
    private Button deleteServerButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Button clearSearchButton;
    @FXML
    private Button closeButton;

    @FXML
    private Label detailNameLabel;
    @FXML
    private Label detailHostLabel;
    @FXML
    private Label detailUsernameLabel;
    @FXML
    private Label detailPortLabel;
    @FXML
    private Label detailPathLabel;
    @FXML
    private Label detailCreatedLabel;
    @FXML
    private Label detailLastUsedLabel;

    private ServerManagementService serverService;
    private ObservableList<SSHServerModel> allServers;
    private ObservableList<SSHServerModel> filteredServers;
    private SSHServerModel selectedForConnection;

    @FXML
    public void initialize() {
        logger.info("Initializing ServerManagementDialogController");

        serverService = new ServerManagementServiceImpl();
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
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getName() != null ? cellData.getValue().getName() : "-"));

        hostColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getHost()));

        portColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(String.valueOf(cellData.getValue().getPort())));

        usernameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUsername()));

        defaultPathColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getDefaultPath() != null ? cellData.getValue().getDefaultPath() : "/"));

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

        // Row factory for double click to edit
        serverTable.setRowFactory(tv -> {
            TableRow<SSHServerModel> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleEditServer();
                }
            });
            return row;
        });

        // Context Menu for table operations
        ContextMenu contextMenu = new ContextMenu();

        MenuItem addMenuItem = new MenuItem("Add Server...");
        addMenuItem.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.PLUS));
        addMenuItem.setOnAction(e -> handleAddServer());

        MenuItem editMenuItem = new MenuItem("Edit...");
        editMenuItem.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.EDIT));
        editMenuItem.setOnAction(e -> handleEditServer());

        MenuItem cloneMenuItem = new MenuItem("Clone...");
        cloneMenuItem.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.COPY));
        cloneMenuItem.setOnAction(e -> handleCloneServer());

        MenuItem deleteMenuItem = new MenuItem("Delete");
        deleteMenuItem.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.TRASH));
        deleteMenuItem.setOnAction(e -> handleDeleteServer());

        contextMenu.getItems().addAll(
                addMenuItem,
                new SeparatorMenuItem(),
                editMenuItem,
                cloneMenuItem,
                deleteMenuItem
        );

        contextMenu.setOnShowing(e -> {
            boolean hasRow = serverTable.getSelectionModel().getSelectedItem() != null;
            editMenuItem.setDisable(!hasRow);
            cloneMenuItem.setDisable(!hasRow);
            deleteMenuItem.setDisable(!hasRow);
        });

        serverTable.setContextMenu(contextMenu);
    }

    /**
     * Setup event handlers with optimized callbacks
     */
    private void setupEventHandlers() {
        addServerButton.setOnAction(e -> handleAddServer());
        editServerButton.setOnAction(e -> handleEditServer());
        if (cloneServerButton != null) {
            cloneServerButton.setOnAction(e -> handleCloneServer());
        }
        deleteServerButton.setOnAction(e -> handleDeleteServer());
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
        loadServers(null);
    }

    /**
     * Load servers from database and optionally select a specific server by ID
     */
    private void loadServers(String selectServerId) {
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
            serverTable.refresh();
            logger.info("Loaded {} servers", allServers.size());

            if (selectServerId != null) {
                for (SSHServerModel s : filteredServers) {
                    if (selectServerId.equals(s.getId())) {
                        serverTable.getSelectionModel().select(s);
                        serverTable.scrollTo(s);
                        break;
                    }
                }
            }
        });

        task.setOnFailed(e -> {
            logger.error("Failed to load servers", task.getException());
            showError("Load Error", "Failed to load servers: " + task.getException().getMessage());
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
                SSHServerModel saved = controller.getSavedServer();
                loadServers(saved != null ? saved.getId() : null);
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
                SSHServerModel saved = controller.getSavedServer();
                loadServers(saved != null ? saved.getId() : (selected != null ? selected.getId() : null));
            }
        } catch (IOException e) {
            logger.error("Failed to open edit server dialog", e);
            showError("Dialog Error", "Failed to open server editor: " + e.getMessage());
        }
    }

    /**
     * Handle clone server action
     */
    private void handleCloneServer() {
        SSHServerModel selected = serverTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerEditDialog.fxml"));
            Parent root = loader.load();

            ServerEditDialogController controller = loader.getController();
            controller.setServerService(serverService);
            controller.setCloneServer(selected);

            Stage dialog = new Stage();
            dialog.setTitle("Clone SSH Server");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(cloneServerButton.getScene().getWindow());
            dialog.setScene(new Scene(root));

            showAndWaitAndRestore(dialog);

            if (controller.isSaved()) {
                SSHServerModel saved = controller.getSavedServer();
                loadServers(saved != null ? saved.getId() : null);
                logger.info("Cloned server successfully from: {}", selected.getDisplayString());
            }
        } catch (IOException e) {
            logger.error("Failed to open clone server dialog", e);
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
        detailCreatedLabel.setText(server.getCreatedAt() != null ? server.getCreatedAt().format(DATE_FORMATTER) : "-");
        detailLastUsedLabel
                .setText(server.getLastUsed() != null ? server.getLastUsed().format(DATE_FORMATTER) : "Never");
    }

    /**
     * Update button states based on selection
     */
    private void updateButtonStates() {
        boolean hasSelection = serverTable.getSelectionModel().getSelectedItem() != null;
        editServerButton.setDisable(!hasSelection);
        if (cloneServerButton != null) {
            cloneServerButton.setDisable(!hasSelection);
        }
        deleteServerButton.setDisable(!hasSelection);
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
        if (stage == null)
            return null;
        Stage owner = stage;
        while (owner.getOwner() != null) {
            owner = (Stage) owner.getOwner();
        }
        return owner;
    }

    private void showAndWaitAndRestore(Stage dialog) {
        if (dialog.getOwner() == null && serverTable.getScene() != null) {
            dialog.initOwner(serverTable.getScene().getWindow());
        }
        addAppIcon(dialog);

        Stage owner = getUltimateOwner((Stage) dialog.getOwner());
        if (owner == null) {
            dialog.showAndWait();
            return;
        }

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
        if (dialog.getOwner() == null && serverTable.getScene() != null) {
            dialog.initOwner(serverTable.getScene().getWindow());
        }

        try {
            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            addAppIcon(stage);
        } catch (Exception e) {
            // Ignore if stage not ready
        }

        Stage owner = getUltimateOwner((Stage) dialog.getOwner());
        if (owner == null) {
            return dialog.showAndWait();
        }

        boolean wasMaximized = owner.isMaximized();
        double oldX = owner.getX(), oldY = owner.getY(), oldW = owner.getWidth(), oldH = owner.getHeight();

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

    private void addAppIcon(Stage stage) {
        try {
            javafx.scene.image.Image icon = new javafx.scene.image.Image(
                    java.util.Objects.requireNonNull(getClass().getResourceAsStream("/images/app-icon.png")));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            logger.warn("Failed to load app icon for dialog", e);
        }
    }
}