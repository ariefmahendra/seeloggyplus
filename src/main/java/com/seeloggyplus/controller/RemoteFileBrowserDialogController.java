package com.seeloggyplus.controller;

import com.seeloggyplus.model.SSHServer;
import com.seeloggyplus.service.impl.SSHService;
import com.seeloggyplus.service.impl.SSHService.RemoteFileInfo;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * High-performance Remote File Browser Controller
 * 
 * Features:
 * - Async file loading with progress indication
 * - Efficient table rendering with cell factories
 * - Navigation history (back/forward)
 * - Search & filter capabilities
 * - Smart caching for performance
 * - Professional UI with icons
 */
public class RemoteFileBrowserDialogController {

    private static final Logger logger = LoggerFactory.getLogger(RemoteFileBrowserDialogController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // FXML Components
    @FXML private Label serverInfoLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private Button disconnectButton;
    
    @FXML private Button backButton;
    @FXML private Button forwardButton;
    @FXML private Button upButton;
    @FXML private Button homeButton;
    @FXML private Button refreshButton;
    @FXML private Button goButton;
    @FXML private TextField pathField;
    
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterComboBox;
    
    @FXML private TableView<RemoteFileInfo> fileTable;
    @FXML private TableColumn<RemoteFileInfo, String> iconColumn;
    @FXML private TableColumn<RemoteFileInfo, String> nameColumn;
    @FXML private TableColumn<RemoteFileInfo, String> sizeColumn;
    @FXML private TableColumn<RemoteFileInfo, String> typeColumn;
    @FXML private TableColumn<RemoteFileInfo, String> permissionsColumn;
    @FXML private TableColumn<RemoteFileInfo, String> ownerColumn;
    @FXML private TableColumn<RemoteFileInfo, String> modifiedColumn;
    
    @FXML private Label statusLabel;
    @FXML private Label fileCountLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label loadingLabel;
    
    @FXML private Button downloadButton;
    @FXML private Button openButton;
    @FXML private Button cancelButton;

    // Internal state
    private SSHServer server;
    private SSHService sshService;
    private String currentPath;
    private String homeDirectory;
    private Stack<String> navigationHistory;
    private Stack<String> forwardHistory;
    private ObservableList<RemoteFileInfo> allFiles;
    private ObservableList<RemoteFileInfo> filteredFiles;
    private RemoteFileInfo selectedFile;

    @FXML
    public void initialize() {
        logger.info("Initializing RemoteFileBrowserDialogController");
        
        navigationHistory = new Stack<>();
        forwardHistory = new Stack<>();
        allFiles = FXCollections.observableArrayList();
        filteredFiles = FXCollections.observableArrayList();
        
        setupTableColumns();
        setupEventHandlers();
        setupFilters();
    }

    /**
     * Set server and initiate connection
     */
    public void setServer(SSHServer server) {
        this.server = server;
        this.sshService = new SSHService();
        
        serverInfoLabel.setText(server.getDisplayString());
        connectToServer();
    }

    /**
     * Setup table columns with optimized cell factories
     */
    private void setupTableColumns() {
        // Icon column with file type icons
        iconColumn.setCellValueFactory(cellData -> new SimpleStringProperty(""));
        iconColumn.setCellFactory(col -> new TableCell<>() {
            private final FontAwesomeIconView icon = new FontAwesomeIconView();
            
            {
                icon.setSize("16");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    RemoteFileInfo file = getTableRow().getItem();
                    updateFileIcon(file);
                    setGraphic(icon);
                }
            }
            
            private void updateFileIcon(RemoteFileInfo file) {
                if (file.isDirectory()) {
                    icon.setIcon(FontAwesomeIcon.FOLDER);
                    icon.setFill(Color.DARKGOLDENROD);
                } else if (file.isLogFile()) {
                    icon.setIcon(FontAwesomeIcon.FILE_TEXT_ALT);
                    icon.setFill(Color.STEELBLUE);
                } else {
                    icon.setIcon(FontAwesomeIcon.FILE_ALT);
                    icon.setFill(Color.DARKGRAY);
                }
            }
        });

        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        sizeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormattedSize()));
        typeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTypeDescription()));
        permissionsColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPermissions()));
        ownerColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getOwner()));
        modifiedColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue().getModified() != null) {
                return new SimpleStringProperty(cellData.getValue().getModified().format(DATE_FORMATTER));
            }
            return new SimpleStringProperty("-");
        });

        fileTable.setItems(filteredFiles);
        
        // Double-click to navigate
        fileTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleFileDoubleClick();
            }
        });
        
        // Selection listener
        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedFile = newVal;
            updateButtonStates();
        });
    }

    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        backButton.setOnAction(e -> navigateBack());
        forwardButton.setOnAction(e -> navigateForward());
        upButton.setOnAction(e -> navigateUp());
        homeButton.setOnAction(e -> navigateHome());
        refreshButton.setOnAction(e -> refreshCurrentDirectory());
        goButton.setOnAction(e -> navigateToPath(pathField.getText()));
        
        pathField.setOnAction(e -> navigateToPath(pathField.getText()));
        
        downloadButton.setOnAction(e -> handleDownload());
        openButton.setOnAction(e -> handleOpenFile());
        cancelButton.setOnAction(e -> handleClose());
        disconnectButton.setOnAction(e -> handleDisconnect());
    }

    /**
     * Setup search and filter functionality
     */
    private void setupFilters() {
        // Populate filter combo box
        filterComboBox.getItems().addAll(
            "All Files",
            "Log Files (.log)",
            "Text Files (.txt)",
            "Directories Only"
        );
        filterComboBox.getSelectionModel().selectFirst();
        
        // Real-time search
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        filterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    /**
     * Apply search and filter to file list
     */
    private void applyFilters() {
        String searchText = searchField.getText();
        String filter = filterComboBox.getValue();
        
        List<RemoteFileInfo> filtered = allFiles.stream()
            .filter(file -> matchesSearch(file, searchText))
            .filter(file -> matchesFilter(file, filter))
            .collect(Collectors.toList());
        
        Collections.sort(filtered);
        
        filteredFiles.clear();
        filteredFiles.addAll(filtered);
        
        fileCountLabel.setText(filtered.size() + " items");
    }

    /**
     * Check if file matches search criteria
     */
    private boolean matchesSearch(RemoteFileInfo file, String search) {
        if (search == null || search.trim().isEmpty()) {
            return true;
        }
        return file.getName().toLowerCase().contains(search.toLowerCase());
    }

    /**
     * Check if file matches filter criteria
     */
    private boolean matchesFilter(RemoteFileInfo file, String filter) {
        if (filter == null || filter.equals("All Files")) {
            return true;
        }
        
        switch (filter) {
            case "Log Files (.log)":
                return file.isDirectory() || file.isLogFile();
            case "Text Files (.txt)":
                return file.isDirectory() || file.getName().toLowerCase().endsWith(".txt");
            case "Directories Only":
                return file.isDirectory();
            default:
                return true;
        }
    }

    /**
     * Connect to SSH server
     */
    private void connectToServer() {
        updateStatus("Connecting to " + server.getHost() + "...");
        showLoading(true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return sshService.connect(
                    server.getHost(),
                    server.getPort(),
                    server.getUsername(),
                    server.getPassword()
                );
            }
        };

        task.setOnSucceeded(e -> {
            boolean connected = task.getValue();
            if (connected) {
                connectionStatusLabel.setText("● Connected");
                connectionStatusLabel.setStyle("-fx-text-fill: green;");
                updateStatus("Connected");
                
                // Get home directory and navigate
                String defaultPath = server.getDefaultPath() != null ? server.getDefaultPath() : null;
                if (defaultPath != null && !defaultPath.trim().isEmpty()) {
                    navigateToPath(defaultPath);
                } else {
                    // Get home directory from SSH
                    getHomeDirectory();
                }
            } else {
                connectionStatusLabel.setText("● Disconnected");
                connectionStatusLabel.setStyle("-fx-text-fill: red;");
                String message = "Failed to establish SSH connection to " + server.getHost() + ":" + server.getPort() + "\n\n";
                message += "Possible causes:\n";
                message += "• Server is not reachable\n";
                message += "• Invalid credentials\n";
                message += "• SSH service not running\n";
                message += "• Network/firewall issues";
                showError("Connection Failed", message);
                showLoading(false);
            }
        });

        task.setOnFailed(e -> {
            Throwable throwable = task.getException();
            logger.error("Connection failed", throwable);
            connectionStatusLabel.setText("● Disconnected");
            connectionStatusLabel.setStyle("-fx-text-fill: red;");
            
            Exception exception = throwable instanceof Exception ? (Exception) throwable : new Exception(throwable);
            String message = buildConnectionErrorMessage(exception);
            showError("Connection Failed", message);
            showLoading(false);
        });

        new Thread(task).start();
    }

    /**
     * Get home directory from server
     */
    private void getHomeDirectory() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                try {
                    String result = sshService.executeCommand("pwd");
                    if (result != null && !result.trim().isEmpty()) {
                        return result.trim();
                    }
                } catch (Exception e) {
                    logger.error("Failed to get home directory", e);
                }
                return "/";
            }
        };

        task.setOnSucceeded(e -> {
            homeDirectory = task.getValue();
            logger.info("Home directory: {}", homeDirectory);
            navigateToPath(homeDirectory);
        });

        new Thread(task).start();
    }

    /**
     * Navigate to specific path
     */
    private void navigateToPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }

        // Add current path to history before navigating
        if (currentPath != null && !currentPath.equals(path)) {
            navigationHistory.push(currentPath);
            forwardHistory.clear();
        }

        currentPath = path;
        pathField.setText(path);
        loadDirectory(path);
        updateNavigationButtons();
    }

    /**
     * Load directory contents with detailed error handling
     */
    private void loadDirectory(String path) {
        updateStatus("Loading " + path + "...");
        showLoading(true);

        Task<DirectoryLoadResult> task = new Task<>() {
            @Override
            protected DirectoryLoadResult call() {
                try {
                    List<RemoteFileInfo> files = sshService.listFiles(path);
                    return new DirectoryLoadResult(files, null, null);
                } catch (java.io.IOException e) {
                    logger.error("Failed to list files: {}", path, e);
                    return new DirectoryLoadResult(null, e, classifyError(e, path));
                } catch (Exception e) {
                    logger.error("Unexpected error listing files: {}", path, e);
                    return new DirectoryLoadResult(null, e, ErrorType.UNKNOWN);
                }
            }
        };

        task.setOnSucceeded(e -> {
            DirectoryLoadResult result = task.getValue();
            if (result.files != null) {
                allFiles.clear();
                allFiles.addAll(result.files);
                applyFilters();
                updateStatus("Loaded " + result.files.size() + " items");
            } else {
                handleLoadError(path, result.error, result.errorType);
            }
            showLoading(false);
        });

        task.setOnFailed(e -> {
            Throwable throwable = task.getException();
            logger.error("Task failed to load directory: {}", path, throwable);
            Exception exception = throwable instanceof Exception ? (Exception) throwable : new Exception(throwable);
            showDetailedError("Directory Load Failed", path, exception, ErrorType.UNKNOWN);
            showLoading(false);
        });

        new Thread(task).start();
    }

    /**
     * Result class for directory loading
     */
    private static class DirectoryLoadResult {
        final List<RemoteFileInfo> files;
        final Exception error;
        final ErrorType errorType;

        DirectoryLoadResult(List<RemoteFileInfo> files, Exception error, ErrorType errorType) {
            this.files = files;
            this.error = error;
            this.errorType = errorType;
        }
    }

    /**
     * Error types for better error messages
     */
    private enum ErrorType {
        PERMISSION_DENIED,
        PATH_NOT_FOUND,
        NOT_A_DIRECTORY,
        CONNECTION_LOST,
        TIMEOUT,
        UNKNOWN
    }

    /**
     * Classify error based on exception message
     */
    private ErrorType classifyError(Exception e, String path) {
        String message = e.getMessage().toLowerCase();
        
        if (message.contains("permission denied") || message.contains("access denied")) {
            return ErrorType.PERMISSION_DENIED;
        } else if (message.contains("no such file") || message.contains("not found") || 
                   message.contains("does not exist")) {
            return ErrorType.PATH_NOT_FOUND;
        } else if (message.contains("not a directory") || message.contains("is a file")) {
            return ErrorType.NOT_A_DIRECTORY;
        } else if (message.contains("connection") || message.contains("session")) {
            return ErrorType.CONNECTION_LOST;
        } else if (message.contains("timeout") || message.contains("timed out")) {
            return ErrorType.TIMEOUT;
        }
        
        return ErrorType.UNKNOWN;
    }

    /**
     * Handle load error with specific messaging
     */
    private void handleLoadError(String path, Exception error, ErrorType errorType) {
        updateStatus("Failed to load directory");
        showDetailedError("Directory Load Failed", path, error, errorType);
    }

    /**
     * Show detailed error with specific message and suggestions
     */
    private void showDetailedError(String title, String path, Exception error, ErrorType errorType) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            
            String message = buildErrorMessage(path, error, errorType);
            alert.setContentText(message);
            
            alert.getDialogPane().setPrefWidth(500);
            alert.showAndWait();
        });
    }

    /**
     * Build connection error message
     */
    private String buildConnectionErrorMessage(Exception error) {
        StringBuilder message = new StringBuilder();
        
        message.append("Failed to connect to SSH server:\n");
        message.append(server.getHost()).append(":").append(server.getPort()).append("\n\n");
        
        String errorMsg = error != null ? error.getMessage() : null;
        
        if (errorMsg != null) {
            if (errorMsg.toLowerCase().contains("auth") || errorMsg.toLowerCase().contains("password")) {
                message.append("❌ Authentication Failed\n\n");
                message.append("The username or password is incorrect.\n\n");
                message.append("Possible solutions:\n");
                message.append("• Verify your username and password\n");
                message.append("• Check if your account is locked\n");
                message.append("• Ensure SSH key is properly configured\n");
            } else if (errorMsg.toLowerCase().contains("timeout")) {
                message.append("❌ Connection Timeout\n\n");
                message.append("The server did not respond in time.\n\n");
                message.append("Possible solutions:\n");
                message.append("• Check your network connection\n");
                message.append("• Verify the server is running\n");
                message.append("• Check firewall settings\n");
            } else if (errorMsg.toLowerCase().contains("refused") || errorMsg.toLowerCase().contains("unreachable")) {
                message.append("❌ Connection Refused\n\n");
                message.append("The server refused the connection.\n\n");
                message.append("Possible solutions:\n");
                message.append("• Verify the server address and port\n");
                message.append("• Check if SSH service is running\n");
                message.append("• Check firewall rules\n");
            } else {
                message.append("❌ Connection Error\n\n");
                message.append("Error details: ").append(errorMsg).append("\n\n");
                message.append("Possible solutions:\n");
                message.append("• Check network connectivity\n");
                message.append("• Verify server configuration\n");
                message.append("• Contact system administrator\n");
            }
        } else {
            message.append("❌ Unknown Connection Error\n\n");
            message.append("Failed to establish SSH connection.\n\n");
            message.append("Possible solutions:\n");
            message.append("• Check your network connection\n");
            message.append("• Verify server details\n");
            message.append("• Try again later\n");
        }
        
        return message.toString();
    }

    /**
     * Build comprehensive error message with suggestions
     */
    private String buildErrorMessage(String path, Exception error, ErrorType errorType) {
        StringBuilder message = new StringBuilder();
        
        message.append("Failed to load directory:\n");
        message.append("Path: ").append(path).append("\n\n");
        
        switch (errorType) {
            case PERMISSION_DENIED:
                message.append("❌ Permission Denied\n\n");
                message.append("You do not have sufficient permissions to access this directory.\n\n");
                message.append("Possible solutions:\n");
                message.append("• Check if your user account has read permissions\n");
                message.append("• Contact your system administrator\n");
                message.append("• Try accessing as a different user\n");
                break;
                
            case PATH_NOT_FOUND:
                message.append("❌ Path Not Found\n\n");
                message.append("The specified directory does not exist on the remote server.\n\n");
                message.append("Possible solutions:\n");
                message.append("• Verify the path is correct\n");
                message.append("• Check for typos in the directory name\n");
                message.append("• The directory may have been moved or deleted\n");
                break;
                
            case NOT_A_DIRECTORY:
                message.append("❌ Not a Directory\n\n");
                message.append("The specified path points to a file, not a directory.\n\n");
                message.append("Possible solutions:\n");
                message.append("• Double-click to open the file instead\n");
                message.append("• Navigate to the parent directory\n");
                break;
                
            case CONNECTION_LOST:
                message.append("❌ Connection Lost\n\n");
                message.append("The SSH connection to the server was lost.\n\n");
                message.append("Possible solutions:\n");
                message.append("• Check your network connection\n");
                message.append("• Reconnect to the server\n");
                message.append("• Verify the server is still running\n");
                break;
                
            case TIMEOUT:
                message.append("❌ Operation Timeout\n\n");
                message.append("The operation took too long to complete.\n\n");
                message.append("Possible solutions:\n");
                message.append("• Check your network speed\n");
                message.append("• The directory might be very large\n");
                message.append("• Try again later\n");
                break;
                
            case UNKNOWN:
            default:
                message.append("❌ Unexpected Error\n\n");
                message.append("An unexpected error occurred while loading the directory.\n\n");
                if (error != null && error.getMessage() != null) {
                    message.append("Error details:\n");
                    message.append(error.getMessage()).append("\n\n");
                }
                message.append("Possible solutions:\n");
                message.append("• Try refreshing the directory\n");
                message.append("• Navigate to a different directory\n");
                message.append("• Check the application logs for details\n");
                break;
        }
        
        return message.toString();
    }

    /**
     * Handle file double-click navigation
     */
    private void handleFileDoubleClick() {
        RemoteFileInfo file = fileTable.getSelectionModel().getSelectedItem();
        if (file == null) {
            return;
        }

        if (file.isDirectory()) {
            navigateToPath(file.getPath());
        } else if (file.isLogFile()) {
            handleOpenFile();
        }
    }

    /**
     * Navigate back in history
     */
    private void navigateBack() {
        if (!navigationHistory.isEmpty()) {
            forwardHistory.push(currentPath);
            String previousPath = navigationHistory.pop();
            currentPath = previousPath;
            pathField.setText(previousPath);
            loadDirectory(previousPath);
            updateNavigationButtons();
        }
    }

    /**
     * Navigate forward in history
     */
    private void navigateForward() {
        if (!forwardHistory.isEmpty()) {
            navigationHistory.push(currentPath);
            String nextPath = forwardHistory.pop();
            currentPath = nextPath;
            pathField.setText(nextPath);
            loadDirectory(nextPath);
            updateNavigationButtons();
        }
    }

    /**
     * Navigate up to parent directory
     */
    private void navigateUp() {
        if (currentPath == null || currentPath.equals("/")) {
            return;
        }

        String parentPath = getParentPath(currentPath);
        navigateToPath(parentPath);
    }

    /**
     * Navigate to home directory
     */
    private void navigateHome() {
        if (homeDirectory != null) {
            navigateToPath(homeDirectory);
        }
    }

    /**
     * Refresh current directory
     */
    private void refreshCurrentDirectory() {
        if (currentPath != null) {
            loadDirectory(currentPath);
        }
    }

    /**
     * Get parent path
     */
    private String getParentPath(String path) {
        if (path.equals("/")) {
            return "/";
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    /**
     * Handle download file
     */
    private void handleDownload() {
        if (selectedFile == null) {
            return;
        }
        
        // TODO: Implement download functionality
        showError("Coming Soon", "Download functionality will be available soon!");
    }

    /**
     * Handle open log file
     */
    private void handleOpenFile() {
        if (selectedFile == null || !selectedFile.isFile()) {
            showError("Invalid Selection", "Please select a log file to open");
            return;
        }

        // TODO: Implement open file in main viewer
        logger.info("Opening file: {}", selectedFile.getPath());
        showError("Coming Soon", "Open file functionality will be integrated with main viewer!");
    }

    /**
     * Handle disconnect
     */
    private void handleDisconnect() {
        if (sshService != null) {
            sshService.disconnect();
        }
        handleClose();
    }

    /**
     * Handle close dialog
     */
    private void handleClose() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Update navigation button states
     */
    private void updateNavigationButtons() {
        backButton.setDisable(navigationHistory.isEmpty());
        forwardButton.setDisable(forwardHistory.isEmpty());
        upButton.setDisable(currentPath == null || currentPath.equals("/"));
    }

    /**
     * Update action button states
     */
    private void updateButtonStates() {
        boolean hasSelection = selectedFile != null;
        boolean isFile = hasSelection && selectedFile.isFile();
        boolean isLogFile = hasSelection && selectedFile.isLogFile();
        
        downloadButton.setDisable(!isFile);
        openButton.setDisable(!isLogFile);
    }

    /**
     * Update status label
     */
    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        Platform.runLater(() -> {
            progressIndicator.setVisible(show);
            loadingLabel.setVisible(show);
            loadingLabel.setText(show ? "Loading..." : "");
        });
    }

    /**
     * Show simple error dialog
     */
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.getDialogPane().setPrefWidth(450);
            alert.showAndWait();
        });
    }
    
    /**
     * Show error with exception details
     */
    private void showErrorWithDetails(String title, String message, Exception exception) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            
            StringBuilder fullMessage = new StringBuilder();
            fullMessage.append(message).append("\n\n");
            
            if (exception != null) {
                fullMessage.append("Error details:\n");
                fullMessage.append(exception.getClass().getSimpleName()).append(": ");
                fullMessage.append(exception.getMessage() != null ? exception.getMessage() : "Unknown error");
            }
            
            alert.setContentText(fullMessage.toString());
            alert.getDialogPane().setPrefWidth(500);
            alert.showAndWait();
        });
    }

    public RemoteFileInfo getSelectedFile() {
        return selectedFile;
    }
}
