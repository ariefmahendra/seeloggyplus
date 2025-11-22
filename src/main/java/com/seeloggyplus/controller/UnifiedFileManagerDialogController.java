package com.seeloggyplus.controller;

import com.seeloggyplus.model.FavoriteFolder;
import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.model.SSHServer;
import com.seeloggyplus.service.FavoriteFolderService;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.service.impl.FavoriteFolderServiceImpl;
import com.seeloggyplus.service.impl.SSHService;
import com.seeloggyplus.service.impl.ServerManagementServiceImpl;
import com.seeloggyplus.util.PasswordPromptDialog;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * Controller for the Unified File Manager Dialog.
 * Handles both local and remote file browsing.
 */
public class UnifiedFileManagerDialogController {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedFileManagerDialogController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // FXML Components
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private Button upButton;
    @FXML
    private Button homeButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Button goButton;
    @FXML
    private TextField pathField;
    @FXML
    private TextField searchField;

    @FXML
    private ListView<LocationItem> locationListView;
    @FXML
    private Button manageServersButton;

    @FXML
    private TableView<FileInfo> fileTable;
    @FXML
    private TableColumn<FileInfo, String> iconColumn;
    @FXML
    private TableColumn<FileInfo, String> nameColumn;
    @FXML
    private TableColumn<FileInfo, String> sizeColumn;
    @FXML
    private TableColumn<FileInfo, String> typeColumn;
    @FXML
    private TableColumn<FileInfo, String> modifiedColumn;
    @FXML
    private TableColumn<FileInfo, String> permissionsColumn;
    @FXML
    private TableColumn<FileInfo, String> ownerColumn;

    @FXML
    private Label statusLabel;
    @FXML
    private Label itemCountLabel;
    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private Button cancelButton;
    @FXML
    private Button previewButton;
    @FXML
    private Button openButton;

    // --- Favorites Components ---
    @FXML
    private ListView<FavoriteFolder> favoritesListView;
    private ContextMenu fileContextMenu;
    private MenuItem addToFavoritesMenuItem;
    private MenuItem removeFromFavoritesMenuItem;


    // Services
    private LocalFileService localFileService;
    private SSHService sshService;
    private ServerManagementService serverManagementService;
    private FavoriteFolderService favoriteFolderService;

    // State
    private ObservableList<LocationItem> locations;
    private ObservableList<FileInfo> allFiles;
    private FilteredList<FileInfo> filteredFiles;
    private final Stack<String> backHistory = new Stack<>();
    private final Stack<String> forwardHistory = new Stack<>();
    private String currentPath;
    private LocationItem currentLocation;

    // Result
    private FileInfo selectedFileResult;
    private SSHService activeSshService;

    @FXML
    public void initialize() {
        logger.info("Initializing UnifiedFileManagerDialogController");

        // --- Service Initialization ---
        localFileService = new LocalFileService();
        serverManagementService = new ServerManagementServiceImpl();
        favoriteFolderService = new FavoriteFolderServiceImpl();

        allFiles = FXCollections.observableArrayList();
        filteredFiles = new FilteredList<>(allFiles, p -> true);

        // --- UI Setup ---
        setupLayout(); // Must be called before other setups that use the lists
        setupLocationList();
        setupFavoritesList();
        setupFileTable();
        setupEventHandlers();

        // Select Local Drive by default
        locationListView.getSelectionModel().select(0);
    }

    private void setupLocationList() {
        locations = FXCollections.observableArrayList();

        // Add Local Drive
        locations.add(new LocationItem("Local Drive", FontAwesomeIcon.DESKTOP, null));

        // Add Saved Servers
        List<SSHServer> servers = serverManagementService.getAllServers();
        for (SSHServer server : servers) {
            locations.add(new LocationItem(server.getName(), FontAwesomeIcon.SERVER, server));
        }

        locationListView.setItems(locations);
        locationListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(LocationItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.name);
                    FontAwesomeIconView icon = new FontAwesomeIconView(item.icon);
                    icon.setSize("16");
                    setGraphic(icon);
                }
            }
        });

        locationListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                handleLocationSelected(newVal);
            }
        });
    }

    private void setupLayout() {
        favoritesListView = new ListView<>();
        // The locationListView is inside a VBox. We need to add our new list to that VBox.
        if (locationListView.getParent() instanceof VBox) {
            VBox leftPanel = (VBox) locationListView.getParent();

            int index = leftPanel.getChildren().indexOf(locationListView);

            Label favoritesLabel = new Label("Favorites");
            favoritesLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            if (index != -1) {
                leftPanel.getChildren().add(index + 1, new Separator());
                leftPanel.getChildren().add(index + 2, favoritesLabel);
                leftPanel.getChildren().add(index + 3, favoritesListView);
            } else {
                leftPanel.getChildren().addAll(new Separator(), favoritesLabel, favoritesListView);
            }
            
            VBox.setVgrow(locationListView, javafx.scene.layout.Priority.SOMETIMES);
            VBox.setVgrow(favoritesListView, javafx.scene.layout.Priority.ALWAYS);
        }
    }

    private void setupFavoritesList() {
        favoritesListView.setCellFactory(param -> new ListCell<>() {
            private final FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.STAR);
            {
                icon.setSize("16");
                icon.setFill(Color.GOLD);
            }
            @Override
            protected void updateItem(FavoriteFolder item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                }
                else {
                    setText(item.getName());
                    setGraphic(icon);
                }
            }
        });

        favoritesListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                FavoriteFolder selectedFavorite = favoritesListView.getSelectionModel().getSelectedItem();
                if (selectedFavorite != null) {
                    navigateTo(selectedFavorite.getPath());
                }
            }
        });
        
        // Context menu for removing a favorite
        ContextMenu favContextMenu = new ContextMenu();
        MenuItem removeFavMenuItem = new MenuItem("Remove Favorite");
        removeFavMenuItem.setOnAction(e -> {
            FavoriteFolder selected = favoritesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                favoriteFolderService.removeFavorite(selected.getId());
                loadFavoritesForCurrentLocation();
            }
        });
        favContextMenu.getItems().add(removeFavMenuItem);
        favoritesListView.setContextMenu(favContextMenu);
    }

    private void setupFileTable() {
        // Enable cell-level selection for copy-paste
        fileTable.getSelectionModel().setCellSelectionEnabled(true);
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // --- Context Menu for Files ---
        fileContextMenu = new ContextMenu();
        addToFavoritesMenuItem = new MenuItem("Add to Favorites");
        removeFromFavoritesMenuItem = new MenuItem("Remove from Favorites");

        addToFavoritesMenuItem.setOnAction(e -> handleAddToFavorites());
        removeFromFavoritesMenuItem.setOnAction(e -> handleRemoveFromFavorites());

        fileContextMenu.getItems().addAll(addToFavoritesMenuItem, removeFromFavoritesMenuItem);

        fileTable.setContextMenu(fileContextMenu);

        fileTable.setOnContextMenuRequested(event -> {
            FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
            if (selected == null || selected.isFile()) {
                // Hide menu if no item or a file is selected
                fileContextMenu.hide();
                return;
            }
            // Check if the folder is already a favorite
            boolean isAlreadyFavorite = favoriteFolderService.isFavorite(selected.getPath(), getLocationIdForCurrent());
            addToFavoritesMenuItem.setVisible(!isAlreadyFavorite);
            removeFromFavoritesMenuItem.setVisible(isAlreadyFavorite);
        });
        
        // Icon Column
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
                    FileInfo file = getTableRow().getItem();
                    if (file.isDirectory()) {
                        icon.setIcon(FontAwesomeIcon.FOLDER);
                        icon.setFill(Color.DARKGOLDENROD);
                        
                        // Add a specific style class if it's a favorite
                        boolean isFavorite = favoriteFolderService.isFavorite(file.getPath(), getLocationIdForCurrent());
                        if(isFavorite) {
                            getTableRow().setStyle("-fx-font-weight: bold;");
                        } else {
                            getTableRow().setStyle("");
                        }
                        
                    } else if (file.isLogFile()) {
                        icon.setIcon(FontAwesomeIcon.FILE_TEXT_ALT);
                        icon.setFill(Color.STEELBLUE);
                        getTableRow().setStyle("");
                    } else {
                        icon.setIcon(FontAwesomeIcon.FILE_ALT);
                        icon.setFill(Color.DARKGRAY);
                        getTableRow().setStyle("");
                    }
                    setGraphic(icon);
                }
            }
        });

        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        sizeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormattedSize()));
        sizeColumn.setComparator((s1, s2) -> {
            long size1 = parseFormattedSize(s1);
            long size2 = parseFormattedSize(s2);
            return Long.compare(size1, size2);
        });
        typeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTypeDescription()));
        permissionsColumn
                .setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPermissions()));
        ownerColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getOwner()));
        modifiedColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue().getModified() != null) {
                return new SimpleStringProperty(cellData.getValue().getModified().format(DATE_FORMATTER));
            }
            return new SimpleStringProperty("-");
        });

        SortedList<FileInfo> sortedData = new SortedList<>(filteredFiles);
        sortedData.comparatorProperty().bind(fileTable.comparatorProperty());
        fileTable.setItems(sortedData);

        fileTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleFileDoubleClick();
            }
        });

        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean isFileSelected = (newVal != null && newVal.isFile());
            openButton.setDisable(!isFileSelected);
            previewButton.setDisable(!isFileSelected);
        });
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> navigateBack());
        forwardButton.setOnAction(e -> navigateForward());
        upButton.setOnAction(e -> navigateUp());
        homeButton.setOnAction(e -> navigateHome());
        refreshButton.setOnAction(e -> refreshCurrentPath());
        goButton.setOnAction(e -> navigateTo(pathField.getText()));

        pathField.setOnAction(e -> navigateTo(pathField.getText()));

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredFiles.setPredicate(file -> {
                if (newVal == null || newVal.isEmpty())
                    return true;
                return file.getName().toLowerCase().contains(newVal.toLowerCase());
            });
            itemCountLabel.setText(filteredFiles.size() + " items");
        });

        manageServersButton.setOnAction(e -> handleManageServers());
        previewButton.setOnAction(e -> handlePreview());
        cancelButton.setOnAction(e -> closeDialog());
        openButton.setOnAction(e -> handleOpen());

        // Add copy support for the table
        fileTable.setOnKeyPressed(event -> {
            if (new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.C, javafx.scene.input.KeyCombination.CONTROL_DOWN).match(event)) {
                copySelectionToClipboard(fileTable);
                event.consume(); // Consume the event to prevent other handlers from acting on it
            }
        });
    }

    private void handleLocationSelected(LocationItem location) {
        if (currentLocation == location)
            return;

        // If switching from remote to local, disconnect old SSH
        if (currentLocation != null && currentLocation.server != null && sshService != null) {
            sshService.disconnect();
            sshService = null;
        }

        currentLocation = location;
        backHistory.clear();
        forwardHistory.clear();
        updateNavigationButtons();
        loadFavoritesForCurrentLocation(); // Load favorites for the new location

        if (location.server == null) {
            // Local
            activeSshService = null;
            navigateTo(localFileService.getHomeDirectory());
        } else {
            // Remote
            connectToRemote(location.server);
        }
    }

    private void connectToRemote(SSHServer server) {
        // Get the password, prompting the user if it's not saved.
        String password = server.getPassword();
        if (password == null || password.isBlank()) {
            logger.info("Password for server {} is not saved, prompting user.", server.getName());
            PasswordPromptDialog prompt = new PasswordPromptDialog(server.getHost(), server.getUsername());
            Optional<String> result = prompt.showAndWait();

            if (result.isPresent()) {
                password = result.get();
            } else {
                logger.info("User cancelled password prompt. Aborting connection.");
                // Set a status and return without connecting
                updateStatus("Connection cancelled.");
                return;
            }
        }

        final String finalPassword = password;
        updateStatus("Connecting to " + server.getHost() + "...");
        progressIndicator.setVisible(true);
        allFiles.clear();

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() {
                sshService = new SSHService();
                return sshService.connect(server.getHost(), server.getPort(), server.getUsername(),
                        finalPassword);
            }
        };

        connectTask.setOnSucceeded(e -> {
            if (connectTask.getValue()) {
                activeSshService = sshService;
                updateStatus("Connected to " + server.getHost());

                // Get home dir
                Task<String> homeTask = new Task<>() {
                    @Override
                    protected String call() throws Exception {
                        String result = sshService.executeCommand("pwd");
                        return result != null ? result.trim() : "/";
                    }
                };
                homeTask.setOnSucceeded(ev -> navigateTo(homeTask.getValue()));
                new Thread(homeTask).start();
            } else {
                updateStatus("Connection failed");
                progressIndicator.setVisible(false);
                showError("Connection Error", "Could not connect to " + server.getHost() + ". Please check credentials.");
            }
        });

        connectTask.setOnFailed(e -> {
            updateStatus("Connection failed");
            progressIndicator.setVisible(false);
            showError("Connection Error", "Could not connect to " + server.getHost());
        });

        new Thread(connectTask).start();
    }

    private String normalizePathString(String path) {
        if (path == null) {
            return null;
        }
        if (currentLocation != null && currentLocation.server == null) {
            // Local path: use NIO for robust normalization.
            try {
                return java.nio.file.Paths.get(path).toAbsolutePath().normalize().toString();
            } catch (Exception e) {
                logger.warn("Path normalization failed for local path: {}", path, e);
                return path; // Fallback to original
            }
        } else {
            // Remote path: simple normalization
            if (path.length() > 1 && path.endsWith("/")) {
                return path.substring(0, path.length() - 1);
            }
            return path;
        }
    }

    private void navigateTo(String path) {
        if (path == null || path.isEmpty())
            return;

        String normalizedNewPath = normalizePathString(path);
        String normalizedCurrentPath = normalizePathString(currentPath);

        if (normalizedCurrentPath != null && !normalizedCurrentPath.equals(normalizedNewPath)) {
            backHistory.push(currentPath); // Push the original, un-normalized path for display
            forwardHistory.clear();
        }

        currentPath = normalizedNewPath;
        pathField.setText(currentPath);
        loadFiles(currentPath);
        updateNavigationButtons();
    }

    private String getParentPath(String path) {
        if (path == null) {
            return null;
        }

        String parent = null;
        if (currentLocation == null || currentLocation.server == null) {
            // Local
            java.io.File f = new java.io.File(path);
            parent = f.getParent();
        } else {
            // Remote
            if (!path.equals("/")) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash > 0) {
                    parent = path.substring(0, lastSlash);
                } else if (lastSlash == 0) {
                    parent = "/";
                }
            }
        }
        return parent;
    }

    private void loadFiles(String path) {
        updateStatus("Loading " + path + "...");
        progressIndicator.setVisible(true);

        Task<List<FileInfo>> loadTask = new Task<>() {
            @Override
            protected List<FileInfo> call() throws Exception {
                List<FileInfo> files;
                if (currentLocation.server == null) {
                    files = new java.util.ArrayList<>(localFileService.listFiles(path));
                } else {
                    if (sshService == null || !sshService.isConnected()) {
                        throw new IOException("Not connected");
                    }
                    // Map RemoteFileInfo to FileInfo
                    files = sshService.listFiles(path).stream().map(r -> {
                        FileInfo f = new FileInfo();
                        f.setName(r.getName());
                        f.setPath(r.getPath());
                        f.setSize(r.getSize());
                        f.setDirectory(r.isDirectory());
                        f.setModifiedTime(r.getModifiedTime());
                        f.setPermissions(r.getPermissions());
                        f.setOwner(r.getOwner());
                        f.setSourceType(FileInfo.SourceType.REMOTE);
                        return f;
                    }).collect(Collectors.toList());
                }

                // Prepend ".." entry if not at root
                String parentPath = getParentPath(path);
                if (parentPath != null) {
                    FileInfo upDir = new FileInfo();
                    upDir.setName("..");
                    upDir.setDirectory(true);
                    upDir.setPath(parentPath);
                    if (currentLocation != null) {
                        upDir.setSourceType(currentLocation.server == null ? FileInfo.SourceType.LOCAL : FileInfo.SourceType.REMOTE);
                    }
                    files.add(0, upDir);
                }

                return files;
            }
        };

        loadTask.setOnSucceeded(e -> {
            allFiles.setAll(loadTask.getValue());
            itemCountLabel.setText(allFiles.size() + " items");
            progressIndicator.setVisible(false);
            updateStatus("Ready");
            updateNavigationButtons();
            loadFavoritesForCurrentLocation(); // Refresh favorites to apply styling
            fileTable.refresh();
        });

        loadTask.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            updateStatus("Error loading files");
            logger.error("Error loading files", loadTask.getException());
            showError("Error", "Failed to load files: " + loadTask.getException().getMessage());
        });

        new Thread(loadTask).start();
    }

    private void handleFileDoubleClick() {
        FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        if (selected.isDirectory()) {
            navigateTo(selected.getPath());
        } else {
            handleOpen();
        }
    }

    private void handleOpen() {
        selectedFileResult = fileTable.getSelectionModel().getSelectedItem();
        if (selectedFileResult != null && selectedFileResult.isFile()) {
            closeDialog();
        }
    }

    private void handleAddToFavorites() {
        FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isDirectory()) {
            return;
        }
        favoriteFolderService.addFavorite(selected.getName(), selected.getPath(), getLocationIdForCurrent());
        loadFavoritesForCurrentLocation();
        fileTable.refresh(); // To update styling
    }

    private void handleRemoveFromFavorites() {
        FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isDirectory()) {
            return;
        }
        favoriteFolderService.isFavorite(selected.getPath(), getLocationIdForCurrent());
        // We need the ID to delete it.
        favoriteFolderService.getFavoritesForLocation(getLocationIdForCurrent()).stream()
                .filter(fav -> fav.getPath().equals(selected.getPath()))
                .findFirst()
                .ifPresent(fav -> favoriteFolderService.removeFavorite(fav.getId()));
        
        loadFavoritesForCurrentLocation();
        fileTable.refresh(); // To update styling
    }

    private void loadFavoritesForCurrentLocation() {
        if (currentLocation == null) return;

        String locationId = getLocationIdForCurrent();
        List<FavoriteFolder> favorites = favoriteFolderService.getFavoritesForLocation(locationId);
        favoritesListView.setItems(FXCollections.observableArrayList(favorites));
    }

    private String getLocationIdForCurrent() {
        if (currentLocation == null) {
            return "";
        }
        // Use a constant for local, and server name for remote.
        return currentLocation.server == null ? "local" : currentLocation.server.getName();
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void navigateBack() {
        if (!backHistory.isEmpty()) {
            forwardHistory.push(currentPath);
            String prev = backHistory.pop();
            currentPath = prev; // Don't push to back history again
            pathField.setText(prev);
            loadFiles(prev);
            updateNavigationButtons();
        }
    }

    private void navigateForward() {
        if (!forwardHistory.isEmpty()) {
            backHistory.push(currentPath);
            String next = forwardHistory.pop();
            currentPath = next;
            pathField.setText(next);
            loadFiles(next);
            updateNavigationButtons();
        }
    }

    private void navigateUp() {
        if (currentPath == null)
            return;

        String parent = null;
        if (currentLocation.server == null) {
            // Local
            java.io.File f = new java.io.File(currentPath);
            parent = f.getParent();
        } else {
            // Remote (Simple string manipulation for now)
            if (!currentPath.equals("/")) {
                int lastSlash = currentPath.lastIndexOf('/');
                if (lastSlash > 0) {
                    parent = currentPath.substring(0, lastSlash);
                } else if (lastSlash == 0) {
                    parent = "/";
                }
            }
        }

        if (parent != null) {
            navigateTo(parent);
        }
    }

    private void navigateHome() {
        if (currentLocation.server == null) {
            navigateTo(localFileService.getHomeDirectory());
        } else {
            // Re-fetch home or store it
            Task<String> homeTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return sshService.executeCommand("pwd").trim();
                }
            };
            homeTask.setOnSucceeded(ev -> navigateTo(homeTask.getValue()));
            new Thread(homeTask).start();
        }
    }

    private void refreshCurrentPath() {
        if (currentPath != null) {
            loadFiles(currentPath);
        }
    }

    private void updateNavigationButtons() {
        backButton.setDisable(backHistory.isEmpty());
        forwardButton.setDisable(forwardHistory.isEmpty());
        upButton.setDisable(currentPath == null || currentPath.equals("/")
                || (currentLocation.server == null && new java.io.File(currentPath).getParent() == null));
    }

    private void copySelectionToClipboard(final javafx.scene.control.TableView<?> table) {
        final javafx.collections.ObservableList<javafx.scene.control.TablePosition> selectedCells = table.getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) {
            return;
        }

        // Group by row index
        final java.util.Map<Integer, java.util.List<javafx.scene.control.TablePosition>> rowMap = new java.util.TreeMap<>();
        for (final javafx.scene.control.TablePosition pos : selectedCells) {
            rowMap.computeIfAbsent(pos.getRow(), k -> new java.util.ArrayList<>()).add(pos);
        }

        final StringBuilder clipboardString = new StringBuilder();
        for (final java.util.List<javafx.scene.control.TablePosition> row : rowMap.values()) {
            // Sort cells by column index
            row.sort(java.util.Comparator.comparingInt(javafx.scene.control.TablePosition::getColumn));

            final String rowString = row.stream()
                    .map(pos -> {
                        final Object cellData = table.getColumns().get(pos.getColumn()).getCellData(pos.getRow());
                        return cellData == null ? "" : cellData.toString();
                    })
                    .collect(java.util.stream.Collectors.joining("\t"));
            clipboardString.append(rowString).append('\n');
        }

        final javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(clipboardString.toString());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    private void handleManageServers() {
        try {
            Stage ownerStage = (Stage) manageServersButton.getScene().getWindow();
            
            // It's possible the owner is another dialog, but we want the main stage.
            // Let's assume the main stage is the ultimate owner.
            Stage mainStage = ownerStage;
            while (mainStage.getOwner() != null) {
                mainStage = (Stage) mainStage.getOwner();
            }

            final Stage finalMainStage = mainStage;
            boolean wasMaximized = finalMainStage.isMaximized();
            double oldX = finalMainStage.getX();
            double oldY = finalMainStage.getY();
            double oldWidth = finalMainStage.getWidth();
            double oldHeight = finalMainStage.getHeight();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Server Management");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(ownerStage); // The dialog's direct owner is the file manager
            stage.setScene(new Scene(root));
            
            stage.showAndWait();

            Platform.runLater(() -> {
                if (wasMaximized) {
                    finalMainStage.setMaximized(true);
                } else {
                    finalMainStage.setX(oldX);
                    finalMainStage.setY(oldY);
                    finalMainStage.setWidth(oldWidth);
                    finalMainStage.setHeight(oldHeight);
                }
            });


            // Refresh server list in case changes were made
            setupLocationList();
        } catch (IOException e) {
            logger.error("Failed to open server management", e);
        }
    }

    private void handlePreview() {
        FileInfo selectedFile = fileTable.getSelectionModel().getSelectedItem();
        if (selectedFile == null || !selectedFile.isFile()) {
            return;
        }

        try {
            Stage ownerStage = (Stage) previewButton.getScene().getWindow();
            Stage mainStage = ownerStage;
            while (mainStage.getOwner() != null) {
                mainStage = (Stage) mainStage.getOwner();
            }

            final Stage finalMainStage = mainStage;
            boolean wasMaximized = finalMainStage.isMaximized();
            double oldX = finalMainStage.getX();
            double oldY = finalMainStage.getY();
            double oldWidth = finalMainStage.getWidth();
            double oldHeight = finalMainStage.getHeight();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LogPreviewDialog.fxml"));
            Parent root = loader.load();

            LogPreviewDialogController controller = loader.getController();
            controller.loadFile(selectedFile, activeSshService);

            Stage stage = new Stage();
            stage.setTitle("Log Preview");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(ownerStage);
            stage.setScene(new Scene(root));
            
            stage.showAndWait();

            Platform.runLater(() -> {
                if (wasMaximized) {
                    finalMainStage.setMaximized(true);
                } else {
                    finalMainStage.setX(oldX);
                    finalMainStage.setY(oldY);
                    finalMainStage.setWidth(oldWidth);
                    finalMainStage.setHeight(oldHeight);
                }
            });

        } catch (IOException e) {
            logger.error("Failed to open log preview dialog", e);
            showError("Preview Error", "Could not open the log preview: " + e.getMessage());
        }
    }

    private void updateStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public FileInfo getSelectedFile() {
        return selectedFileResult;
    }

    public SSHService getSshService() {
        return activeSshService;
    }

    private static class LocationItem {
        String name;
        FontAwesomeIcon icon;
        SSHServer server;

        public LocationItem(String name, FontAwesomeIcon icon, SSHServer server) {
            this.name = name;
            this.icon = icon;
            this.server = server;
        }
    }

    /**
     * Parses a human-readable file size string (e.g., "1.2 KB", "5 MB") into bytes.
     * Returns -1 if the string is not a valid size (e.g., "-").
     * @param formattedSize The formatted size string.
     * @return The size in bytes, or -1 if unparseable.
     */
    private long parseFormattedSize(String formattedSize) {
        if (formattedSize == null || formattedSize.equals("-") || formattedSize.trim().isEmpty()) {
            return -1L; // Representing directories or unknown sizes
        }

        String[] parts = formattedSize.trim().split(" ");
        if (parts.length != 2) {
            return -1L;
        }

        try {
            double value = Double.parseDouble(parts[0]);
            String unit = parts[1].toUpperCase();

            return switch (unit) {
                case "B" -> (long) value;
                case "KB" -> (long) (value * 1024);
                case "MB" -> (long) (value * 1024 * 1024);
                case "GB" -> (long) (value * 1024 * 1024 * 1024);
                default -> -1L;
            };
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}