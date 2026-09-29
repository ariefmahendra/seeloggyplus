package com.seeloggyplus.controller;

import com.seeloggyplus.dto.RemoteFileInfo;
import com.seeloggyplus.model.FavoriteFolder;
import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.util.AppTheme;
import com.seeloggyplus.model.Preference;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.FavoriteFolderService;
import com.seeloggyplus.service.LocalFileService;
import com.seeloggyplus.service.PreferenceService;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.service.impl.FavoriteFolderServiceImpl;
import com.seeloggyplus.service.impl.LocalFileServiceImpl;
import com.seeloggyplus.service.impl.PreferenceServiceImpl;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.service.impl.ServerManagementServiceImpl;
import com.seeloggyplus.util.PasswordPromptDialog;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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
    private Button tailButton;
    @FXML
    private Button findInFilesButton;

    @FXML
    private TreeView<LocationItem> locationTree;
    @FXML
    private Button newGroupButton;
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
    private ListView<FavoriteFolder> favoritesListView;
    private ContextMenu fileContextMenu;
    private MenuItem addToFavoritesMenuItem;
    private MenuItem removeFromFavoritesMenuItem;

    public enum OpenAction {
        OPEN, TAIL
    }

    // Services
    private LocalFileService localFileService;
    private ServerManagementService serverManagementService;
    private PreferenceService preferenceService;
    private java.util.function.Supplier<com.seeloggyplus.service.impl.SSHServiceImpl> sshServiceFactory = com.seeloggyplus.service.impl.SSHServiceImpl::new;


    private FavoriteFolderService favoriteFolderService;

    private ObservableList<FileInfo> allFiles;
    private FilteredList<FileInfo> filteredFiles;
    private final Stack<String> backHistory = new Stack<>();
    private final Stack<String> forwardHistory = new Stack<>();
    private String currentPath;
    private LocationItem currentLocation;
    private FileInfo selectedFileResult;
    private int pendingJumpLine;
    private int pendingTailWindowLines;
    private int pendingTailJumpIndex = -1;
    private com.seeloggyplus.service.impl.SSHServiceImpl activeSshService;
    private Task<Boolean> currentConnectTask;
    private Task<List<FileInfo>> currentLoadTask;
    @Getter
    private OpenAction openAction = OpenAction.OPEN;

    // --- Performance Enhancements ---
    private final java.util.Map<String, CacheEntry> directoryCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> favoritePathsCache = new java.util.HashSet<>();
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    private boolean suppressAutoRefresh = false;
    private boolean suppressSortSave = false;
    private boolean locationListenerInstalled;
    private boolean rebuildingLocationTree;
    private String draggedLocationServerId;
    private OpenAction doubleClickAction = OpenAction.OPEN;
    private Button favoriteCurrentButton;
    private String cachedFavoritesLocationId = null; // Track which location favorites are cached for

    private final java.util.concurrent.ExecutorService fileIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FileManager-IO");
        t.setDaemon(true);
        return t;
    });

    private final java.util.concurrent.ExecutorService prefetchExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FileManager-Prefetch");
        t.setDaemon(true);
        return t;
    });

    private java.util.concurrent.Future<?> currentPrefetchFuture = null;
    private final java.util.concurrent.atomic.AtomicInteger prefetchGeneration = new java.util.concurrent.atomic.AtomicInteger(0);
    private javafx.animation.PauseTransition prefetchDebounce = null;

    private static final Comparator<FileInfo> BACKGROUND_PRE_SORT = Comparator
        .<FileInfo>comparingInt(f -> f.getName().equals("..") ? 0 : (f.isDirectory() ? 1 : 2))
        .thenComparing((f1, f2) -> Long.compare(f2.getModifiedTime(), f1.getModifiedTime()))
        .thenComparing(FileInfo::getName, String.CASE_INSENSITIVE_ORDER);

    private String getCacheKey(String path) {
        String norm = normalizePathString(path);
        return getLocationIdForCurrent() + ":" + (norm != null ? norm : path);
    }

    private String getLocationIdFor(LocationItem loc) {
        if (loc == null) return "";
        return loc.getServer() == null ? "local" : loc.getServer().getName();
    }

    @FXML
    public void initialize() {
        logger.info("Initializing UnifiedFileManagerDialogController");

        localFileService = new LocalFileServiceImpl();
        serverManagementService = new ServerManagementServiceImpl();
        favoriteFolderService = new FavoriteFolderServiceImpl();
        preferenceService = new PreferenceServiceImpl();

        allFiles = FXCollections.observableArrayList();
        filteredFiles = new FilteredList<>(allFiles, p -> true);

        setupLayout();
        setupLocationTree();
        setupFavoritesList();
        doubleClickAction = preferredFileAction(preferenceService.getPreferencesByCode("file_double_click_action").orElse("OPEN"));
        fileTable.setTooltip(new Tooltip("Double-click / Enter: " + (doubleClickAction == OpenAction.TAIL ? "Tail" : "Open/Download") + ". Folders always open."));
        setupFileTable();
        setupEventHandlers();

        restoreLastLocation();

        // WinSCP-style keyboard shortcuts
        Platform.runLater(() -> {
            if (pathField != null && pathField.getScene() != null) {
                pathField.getScene().getAccelerators().put(
                        new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),
                        this::refreshCurrentPath);
                pathField.getScene().getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F5),
                        this::refreshCurrentPath);
                pathField.getScene().getAccelerators().put(
                        new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                        () -> {
                            pathField.requestFocus();
                            pathField.selectAll();
                        });
            }
        });
    }

    private void handleWindowGainedFocus() {
        if (suppressAutoRefresh) {
            suppressAutoRefresh = false;
            return;
        }
        // WinSCP-style: do not automatically clear cache or re-download on focus.
        // Manual refresh is available via the refresh button or Ctrl+R.
    }

    private void setupLocationTree() {
        locationTree.setShowRoot(false);
        locationTree.setCellFactory(tree -> createLocationCell());

        if (!locationListenerInstalled) {
            locationListenerInstalled = true;
            locationTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (rebuildingLocationTree || newVal == null) {
                    return;
                }
                LocationItem item = newVal.getValue();
                if (item != null && (item.isLocal() || item.isServer())) {
                    handleLocationSelected(item);
                }
            });
        }

        locationTree.setRoot(buildLocationRoot());
    }

    /** Builds the WinSCP-style location tree: Local, group folders, then ungrouped servers. */
    private TreeItem<LocationItem> buildLocationRoot() {
        TreeItem<LocationItem> root = new TreeItem<>(LocationItem.root());
        root.setExpanded(true);
        root.getChildren().add(new TreeItem<>(LocationItem.local()));

        java.util.Map<String, TreeItem<LocationItem>> groupFolders = new java.util.LinkedHashMap<>();
        for (String group : serverManagementService.getGroupNames()) {
            TreeItem<LocationItem> folder = newFolderItem(group);
            groupFolders.put(group, folder);
            root.getChildren().add(folder);
        }
        for (SSHServerModel server : serverManagementService.getAllServers()) {
            TreeItem<LocationItem> node = new TreeItem<>(LocationItem.of(server));
            String group = server.getGroupName();
            if (group == null || group.isBlank()) {
                root.getChildren().add(node);
            } else {
                TreeItem<LocationItem> folder = groupFolders.computeIfAbsent(group, name -> {
                    TreeItem<LocationItem> created = newFolderItem(name);
                    root.getChildren().add(created);
                    return created;
                });
                folder.getChildren().add(node);
            }
        }
        return root;
    }

    private static TreeItem<LocationItem> newFolderItem(String group) {
        TreeItem<LocationItem> folder = new TreeItem<>(LocationItem.group(group));
        folder.setExpanded(true);
        return folder;
    }

    private TreeCell<LocationItem> createLocationCell() {
        TreeCell<LocationItem> cell = new TreeCell<>() {
            @Override
            protected void updateItem(LocationItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    setContextMenu(null);
                    return;
                }
                setText(item.getLabel());
                FontAwesomeIconView icon = new FontAwesomeIconView(iconFor(item));
                icon.setSize("16");
                setGraphic(icon);
                setTooltip(new Tooltip(item.getTooltip()));
                setContextMenu(buildLocationContextMenu(item));
            }
        };

        cell.setOnDragDetected(e -> {
            if (cell.isEmpty() || cell.getItem() == null || !cell.getItem().isServer()) {
                return;
            }
            draggedLocationServerId = cell.getItem().getServer().getId();
            var board = cell.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(draggedLocationServerId);
            board.setContent(content);
            e.consume();
        });
        cell.setOnDragDone(e -> draggedLocationServerId = null);
        cell.setOnDragOver(e -> {
            if (draggedLocationServerId != null && !cell.isEmpty() && cell.getItem() != null
                    && !cell.getItem().isRoot()) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            e.consume();
        });
        cell.setOnDragEntered(e -> {
            if (draggedLocationServerId != null && !cell.isEmpty()) {
                cell.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("drop-target"), true);
            }
        });
        cell.setOnDragExited(e -> cell.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("drop-target"), false));
        cell.setOnDragDropped(e -> {
            boolean ok = draggedLocationServerId != null && !cell.isEmpty() && cell.getItem() != null
                    && !cell.getItem().isRoot();
            if (ok) {
                moveServerToGroup(draggedLocationServerId, groupForDropTarget(cell.getItem()));
            }
            e.setDropCompleted(ok);
            e.consume();
        });
        return cell;
    }

    private static FontAwesomeIcon iconFor(LocationItem item) {
        if (item.isLocal()) return FontAwesomeIcon.DESKTOP;
        if (item.isGroup()) return FontAwesomeIcon.FOLDER;
        return FontAwesomeIcon.SERVER;
    }

    private ContextMenu buildLocationContextMenu(LocationItem item) {
        ContextMenu menu = new ContextMenu();
        MenuItem newGroup = new MenuItem("New group...");
        newGroup.setOnAction(e -> promptNewGroup());
        if (item.isGroup()) {
            MenuItem renameGroup = new MenuItem("Rename group...");
            renameGroup.setOnAction(e -> promptRenameGroup(item.getGroupName()));
            MenuItem deleteGroup = new MenuItem("Delete group");
            deleteGroup.setOnAction(e -> promptDeleteGroup(item.getGroupName()));
            menu.getItems().addAll(newGroup, renameGroup, deleteGroup);
        } else if (item.isServer()) {
            MenuItem moveToGroup = new MenuItem("Move to group...");
            moveToGroup.setOnAction(e -> promptMoveServerToGroup(item.getServer()));
            menu.getItems().addAll(moveToGroup, newGroup);
        } else {
            menu.getItems().add(newGroup);
        }
        return menu;
    }

    /** Dropping onto a folder joins it, onto a server joins its group, onto Local/root ungroups. */
    static String groupForDropTarget(LocationItem target) {
        if (target == null) {
            return null;
        }
        if (target.isGroup()) {
            return target.getGroupName();
        }
        if (target.isServer()) {
            return target.getServer().getGroupName();
        }
        return null;
    }

    void createGroup(String name) {
        serverManagementService.createGroup(name);
        rebuildLocationTree();
        selectNodeQuietly(findGroupNode(name == null ? null : name.trim()));
    }

    void renameGroup(String oldName, String newName) {
        serverManagementService.renameGroup(oldName, newName);
        rebuildLocationTree();
        selectNodeQuietly(findGroupNode(newName == null ? null : newName.trim()));
    }

    void deleteGroup(String name) {
        serverManagementService.deleteGroup(name);
        rebuildLocationTree();
        selectNodeQuietly(selectedLocationNode());
    }

    void moveServerToGroup(String serverId, String groupName) {
        TreeItem<LocationItem> node = findServerNodeById(serverId);
        SSHServerModel server = node != null && node.getValue() != null
            ? node.getValue().getServer()
            : serverManagementService.getServerById(serverId);
        if (server == null) {
            return;
        }
        server.setGroupName(groupName);
        serverManagementService.saveServer(server);
        rebuildLocationTree();
        selectNodeQuietly(findServerNodeById(serverId));
    }

    private void promptNewGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New group");
        dialog.setHeaderText("Create a server group");
        dialog.setContentText("Group name:");
        addAppIcon(dialog);
        dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty()).ifPresent(name -> {
            try {
                createGroup(name);
            } catch (RuntimeException ex) {
                logger.error("Failed to create server group {}", name, ex);
                showError("Group Error", "Could not create group: " + ex.getMessage());
            }
        });
    }

    private void promptRenameGroup(String group) {
        TextInputDialog dialog = new TextInputDialog(group);
        dialog.setTitle("Rename group");
        dialog.setHeaderText("Rename group '" + group + "'");
        dialog.setContentText("Group name:");
        addAppIcon(dialog);
        dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty() && !name.equals(group)).ifPresent(name -> {
            try {
                renameGroup(group, name);
            } catch (RuntimeException ex) {
                logger.error("Failed to rename group {} to {}", group, name, ex);
                showError("Group Error", ex.getMessage());
            }
        });
    }

    private void promptDeleteGroup(String group) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete group");
        alert.setHeaderText("Delete group '" + group + "'?");
        alert.setContentText("Servers in this group become ungrouped. No server is deleted.");
        addAppIcon(alert);
        alert.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> {
            try {
                deleteGroup(group);
            } catch (RuntimeException ex) {
                logger.error("Failed to delete group {}", group, ex);
                showError("Group Error", "Could not delete group: " + ex.getMessage());
            }
        });
    }

    private void promptMoveServerToGroup(SSHServerModel server) {
        String ungrouped = "(Ungrouped)";
        java.util.List<String> choices = new java.util.ArrayList<>();
        choices.add(ungrouped);
        choices.addAll(serverManagementService.getGroupNames());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(
            server.getGroupName() != null ? server.getGroupName() : ungrouped, choices);
        dialog.setTitle("Move to group");
        dialog.setHeaderText("Move '" + server.getName() + "' to a group");
        dialog.setContentText("Group:");
        addAppIcon(dialog);
        dialog.showAndWait().ifPresent(choice -> {
            String target = ungrouped.equals(choice) ? null : choice;
            if (java.util.Objects.equals(target, server.getGroupName())) {
                return;
            }
            try {
                moveServerToGroup(server.getId(), target);
            } catch (RuntimeException ex) {
                logger.error("Failed to move server {} to group {}", server.getId(), target, ex);
                showError("Group Error", "Could not move server: " + ex.getMessage());
            }
        });
    }

    private void rebuildLocationTree() {
        LocationItem selected = locationTree.getSelectionModel().getSelectedItem() == null
            ? null : locationTree.getSelectionModel().getSelectedItem().getValue();
        String selectedServerId = selected != null && selected.isServer() ? selected.getServer().getId() : null;
        String selectedGroup = selected != null && selected.isGroup() ? selected.getGroupName() : null;
        boolean selectedLocal = selected != null && selected.isLocal();

        rebuildingLocationTree = true;
        try {
            locationTree.setRoot(buildLocationRoot());
            if (selectedServerId != null) {
                locationTree.getSelectionModel().select(findServerNodeById(selectedServerId));
            } else if (selectedGroup != null) {
                locationTree.getSelectionModel().select(findGroupNode(selectedGroup));
            } else if (selectedLocal) {
                locationTree.getSelectionModel().select(findLocalNode());
            }
        } finally {
            rebuildingLocationTree = false;
        }
    }

    private void selectNodeQuietly(TreeItem<LocationItem> node) {
        if (node == null) {
            return;
        }
        rebuildingLocationTree = true;
        try {
            locationTree.getSelectionModel().select(node);
            locationTree.scrollTo(locationTree.getRow(node));
        } finally {
            rebuildingLocationTree = false;
        }
    }

    private TreeItem<LocationItem> selectedLocationNode() {
        if (currentLocation == null) {
            return findLocalNode();
        }
        if (currentLocation.isServer() && currentLocation.getServer() != null) {
            return findServerNodeById(currentLocation.getServer().getId());
        }
        return findLocalNode();
    }

    private void selectLocalLocation() {
        TreeItem<LocationItem> local = findLocalNode();
        if (local != null) {
            locationTree.getSelectionModel().select(local);
        }
    }

    private TreeItem<LocationItem> findLocalNode() {
        TreeItem<LocationItem> root = locationTree.getRoot();
        if (root == null) {
            return null;
        }
        for (TreeItem<LocationItem> child : root.getChildren()) {
            if (child.getValue() != null && child.getValue().isLocal()) {
                return child;
            }
        }
        return null;
    }

    private TreeItem<LocationItem> findGroupNode(String group) {
        TreeItem<LocationItem> root = locationTree.getRoot();
        if (root == null || group == null) {
            return null;
        }
        for (TreeItem<LocationItem> child : root.getChildren()) {
            if (child.getValue() != null && child.getValue().isGroup()
                    && group.equals(child.getValue().getGroupName())) {
                return child;
            }
        }
        return null;
    }

    private TreeItem<LocationItem> findServerNodeById(String id) {
        return findServerNode(server -> id != null && id.equals(server.getId()));
    }

    private TreeItem<LocationItem> findServerNodeByName(String name) {
        return findServerNode(server -> name != null && server.getName() != null
                && server.getName().equalsIgnoreCase(name));
    }

    private TreeItem<LocationItem> findServerNode(java.util.function.Predicate<SSHServerModel> match) {
        TreeItem<LocationItem> root = locationTree.getRoot();
        return root == null ? null : findServerNode(root, match);
    }

    private static TreeItem<LocationItem> findServerNode(TreeItem<LocationItem> node,
            java.util.function.Predicate<SSHServerModel> match) {
        LocationItem value = node.getValue();
        if (value != null && value.isServer() && match.test(value.getServer())) {
            return node;
        }
        for (TreeItem<LocationItem> child : node.getChildren()) {
            TreeItem<LocationItem> found = findServerNode(child, match);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void setupLayout() {
        favoritesListView = new ListView<>();
        favoritesListView.setPlaceholder(new Label("No saved folders here.\nUse ‘Favorite this folder’ below."));
        if (locationTree.getParent() instanceof VBox leftPanel) {
            Label favoritesLabel = new Label("Favorite folders");
            favoritesLabel.getStyleClass().add("section-title");
            favoritesLabel.setTooltip(new Tooltip("Saved folders for the selected local drive or server"));
            leftPanel.getChildren().addAll(new Separator(), favoritesLabel, favoritesListView);
            favoriteCurrentButton = new Button("Favorite this folder");
            favoriteCurrentButton.setMaxWidth(Double.MAX_VALUE);
            favoriteCurrentButton.setOnAction(e -> toggleCurrentFavorite());
            leftPanel.getChildren().add(favoriteCurrentButton);
            VBox.setVgrow(locationTree, javafx.scene.layout.Priority.SOMETIMES);
            VBox.setVgrow(favoritesListView, javafx.scene.layout.Priority.ALWAYS);
        }
    }

    private void setupFavoritesList() {
        favoritesListView.setCellFactory(param -> new ListCell<>() {
            private final FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.STAR);

            {
                icon.setSize("16");
                icon.getStyleClass().add("favorite-star");
            }

            @Override
            protected void updateItem(FavoriteFolder item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName());
                    setTooltip(new Tooltip(item.getPath() + "\nDouble-click to open folder"));
                    setGraphic(icon);
                }
            }
        });

        favoritesListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() % 2 == 0) {
                FavoriteFolder selectedFavorite = favoritesListView.getSelectionModel().getSelectedItem();
                if (selectedFavorite != null) {
                    navigateTo(selectedFavorite.getPath());
                }
            }
        });
        ContextMenu favContextMenu = new ContextMenu();
        MenuItem removeFavMenuItem = new MenuItem("Remove Favorite");
        removeFavMenuItem.setOnAction(e -> {
            FavoriteFolder selected = favoritesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                favoriteFolderService.removeFavorite(selected.getId());
                cachedFavoritesLocationId = null;
                loadFavoritesForCurrentLocation();
                fileTable.refresh();
            }
        });
        favContextMenu.getItems().add(removeFavMenuItem);
        favoritesListView.setContextMenu(favContextMenu);
    }

    private void setupFileTable() {
        fileTable.getSelectionModel().setCellSelectionEnabled(false);
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

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
                fileContextMenu.hide();
                return;
            }
            boolean isAlreadyFavorite = favoritePathsCache.contains(selected.getPath());
            addToFavoritesMenuItem.setVisible(!isAlreadyFavorite);
            removeFromFavoritesMenuItem.setVisible(isAlreadyFavorite);
        });

        iconColumn.setCellValueFactory(cellData -> new SimpleStringProperty(""));
        iconColumn.setCellFactory(col -> new TableCell<>() {
            private final FontAwesomeIconView icon = new FontAwesomeIconView();

            {
                icon.setSize("16");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                TableRow<?> row = getTableRow();
                if (empty || row == null || row.getItem() == null) {
                    setGraphic(null);
                    if (row != null && row.getStyle() != null && !row.getStyle().isEmpty()) {
                        row.setStyle("");
                    }
                } else {
                    FileInfo file = (FileInfo) row.getItem();
                    String targetStyle = "";
                    icon.getStyleClass().removeAll("file-icon-folder", "file-icon-log", "file-icon-file");
                    if (file.isDirectory()) {
                        icon.setIcon(FontAwesomeIcon.FOLDER);
                        icon.getStyleClass().add("file-icon-folder");
                        boolean isFavorite = favoritePathsCache.contains(file.getPath());
                        if (isFavorite) {
                            targetStyle = "-fx-font-weight: bold;";
                        }
                    } else if (file.isLogFile()) {
                        icon.setIcon(FontAwesomeIcon.FILE_TEXT_ALT);
                        icon.getStyleClass().add("file-icon-log");
                    } else {
                        icon.setIcon(FontAwesomeIcon.FILE_ALT);
                        icon.getStyleClass().add("file-icon-file");
                    }
                    if (!java.util.Objects.equals(row.getStyle(), targetStyle)) {
                        row.setStyle(targetStyle);
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
        modifiedColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFormattedModified()));
        modifiedColumn.setComparator((m1, m2) -> {
            if (m1 == null || "-".equals(m1)) return (m2 == null || "-".equals(m2)) ? 0 : -1;
            if (m2 == null || "-".equals(m2)) return 1;
            return m1.compareTo(m2);
        });

        SortedList<FileInfo> sortedData = new SortedList<>(filteredFiles);
        // ponytail: fallback comparator — directories first, then modified desc, tie-break name asc
        // Optimized to use primitive long comparison and String.CASE_INSENSITIVE_ORDER to avoid object churn
        Comparator<FileInfo> defaultSort = Comparator
            .<FileInfo>comparingInt(f -> f.getName().equals("..") ? 0 : (f.isDirectory() ? 1 : 2))
            .thenComparing((f1, f2) -> Long.compare(f2.getModifiedTime(), f1.getModifiedTime()))
            .thenComparing(FileInfo::getName, String.CASE_INSENSITIVE_ORDER);
        sortedData.comparatorProperty().bind(
            fileTable.comparatorProperty().map(c -> c != null ? c : defaultSort)
        );
        fileTable.setItems(sortedData);

        fileTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() % 2 == 0) {
                if (event.getTarget() instanceof Node target) {
                    TableRow<?> row = findParentTableRow(target);
                    if (row != null && !row.isEmpty()) {
                        handleFileDoubleClick();
                    }
                } else {
                    handleFileDoubleClick();
                }
            }
        });

        fileTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleFileDoubleClick();
                event.consume();
            } else if (event.getCode() == KeyCode.BACK_SPACE) {
                navigateBack();
                event.consume();
            }
        });

        // ponytail: single unified save trigger for all sort state changes.
        // JavaFX column header click cycles: ASC → DESC → clear.
        // We listen to BOTH list changes (new column / clear) and sortType toggles (ASC↔DESC),
        // but coalesce into one deferred save so we always read final state.
        Runnable deferredSave = new Runnable() {
            private boolean scheduled = false;
            @Override public void run() {
                if (suppressSortSave) return;
                if (!scheduled) {
                    scheduled = true;
                    Platform.runLater(() -> {
                        scheduled = false;
                        if (!suppressSortSave) saveSortOrdering();
                    });
                }
            }
        };

        fileTable.getSortOrder().addListener((ListChangeListener<? super TableColumn<FileInfo, ?>>) c -> deferredSave.run());

        for (TableColumn<FileInfo, ?> col : fileTable.getColumns()) {
            if (col.isSortable()) {
                col.sortTypeProperty().addListener((obs, oldDir, newDir) -> deferredSave.run());
            }
        }

        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean isFileSelected = (newVal != null && newVal.isFile());
            openButton.setDisable(!isFileSelected);
            previewButton.setDisable(!isFileSelected);

            if (tailButton != null) {
                boolean canTail = isFileSelected;
                tailButton.setDisable(!canTail);
            }
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
            String query = (newVal == null) ? "" : newVal.trim().toLowerCase();
            filteredFiles.setPredicate(file -> {
                if (query.isEmpty())
                    return true;
                return file.getName().toLowerCase().contains(query);
            });
            itemCountLabel.setText(filteredFiles.size() + " items");
        });

        newGroupButton.setOnAction(e -> promptNewGroup());
        manageServersButton.setOnAction(e -> handleManageServers());
        if (findInFilesButton != null) {
            findInFilesButton.setOnAction(e -> handleFindInFiles());
        }
        previewButton.setOnAction(e -> handlePreview());
        cancelButton.setOnAction(e -> closeDialog());
        openButton.setOnAction(e -> handleOpen());

        fileTable.setOnKeyPressed(event -> {
            if (new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.C,
                    javafx.scene.input.KeyCombination.CONTROL_DOWN).match(event)) {
                copySelectionToClipboard(fileTable);
                event.consume();
            }
        });
    }

    private void handleLocationSelected(LocationItem location) {
        if (currentConnectTask != null) currentConnectTask.cancel(true);
        currentConnectTask = null;
        if (currentLoadTask != null) currentLoadTask.cancel(false);
        currentLoadTask = null;
        if (currentPrefetchFuture != null && !currentPrefetchFuture.isDone()) {
            currentPrefetchFuture.cancel(true);
        }

        // Disconnect from the previous session if there was one
        if (activeSshService != null) {
            activeSshService.disconnect();
            activeSshService = null;
        }

        currentPath = null; // Never carry a path into another server's navigation history.
        currentLocation = location;
        updateFindInFilesState();
        if (preferenceService != null) {
            String locId = location.getServer() == null ? "local" : location.getServer().getName();
            preferenceService.saveOrUpdatePreferences(new Preference("file_manager_last_location", locId));
        }
        backHistory.clear();
        forwardHistory.clear();
        cachedFavoritesLocationId = null; // Force favorites reload for new location
        updateNavigationButtons();
        loadFavoritesForCurrentLocation();

        if (location.getServer() == null) {
            // This is a local drive, no connection needed
            String lastPath = preferenceService.getPreferencesByCode(lastPathKey())
                .filter(p -> !p.isBlank())
                .orElse(localFileService.getHomeDirectory());
            navigateTo(lastPath);
        } else {
            // This is a remote server, create a new service and connect
            activeSshService = sshServiceFactory.get();
            connectToRemote(location.getServer());
        }
    }

    private void connectToRemote(SSHServerModel server) {
        String password = server.getPassword();
        if (password == null || password.isBlank()) {
            logger.info("Password for server {} is not saved, prompting user.", server.getName());
            suppressAutoRefresh = true; // Prevent focus-triggered refresh while dialog is open
            PasswordPromptDialog prompt = new PasswordPromptDialog(server.getHost(), server.getUsername());
            Optional<String> result = prompt.showAndWait();
            suppressAutoRefresh = true; // Re-set: closing dialog triggers focus gain before connect finishes

            if (result.isPresent() && !result.get().isBlank()) {
                password = result.get();
                server.setPassword(password);
            } else {
                logger.info("User cancelled password prompt. Aborting connection.");
                updateStatus("Connection cancelled.");
                selectLocalLocation(); // Go back to local
                return;
            }
        }

        final String finalPassword = password;
        updateStatus("Connecting to " + server.getHost() + "...");
        progressIndicator.setVisible(true);
        fileTable.setCursor(javafx.scene.Cursor.WAIT);
        allFiles.clear();

        if (currentConnectTask != null && currentConnectTask.isRunning()) {
            currentConnectTask.cancel(true);
        }

        final var connectingService = activeSshService;
        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() {
                boolean connected = connectingService.connect(server.getHost(), server.getPort(), server.getUsername(), finalPassword);
                if (isCancelled()) { connectingService.disconnect(); return false; }
                return connected;
            }
        };
        currentConnectTask = connectTask;

        connectTask.setOnSucceeded(e -> {
            if (connectTask != currentConnectTask) return;
            if (connectTask.getValue()) {
                serverManagementService.updateServerLastUsed(server.getId());
                updateStatus("Connected to " + server.getHost());
                String lastPath = preferenceService.getPreferencesByCode(lastPathKey())
                    .filter(p -> !p.isBlank())
                    .orElse(server.getDefaultPath() != null ? server.getDefaultPath() : "/");
                navigateTo(lastPath);
            } else {
                updateStatus("Connection failed");
                progressIndicator.setVisible(false);
                fileTable.setCursor(javafx.scene.Cursor.DEFAULT);
                showError("Connection Error",
                        "Could not connect to " + server.getHost() + ". Please check credentials.");
                selectLocalLocation(); // Go back to local on failure
            }
        });

        connectTask.setOnFailed(e -> {
            if (connectTask != currentConnectTask) return;
            updateStatus("Connection failed");
            progressIndicator.setVisible(false);
            fileTable.setCursor(javafx.scene.Cursor.DEFAULT);
            Throwable ex = connectTask.getException();
            logger.error("SSH Connection task failed", ex);
            showError("Connection Error", "Could not connect to " + server.getHost() + ": " + ex.getMessage());
            selectLocalLocation(); // Go back to local on failure
        });

        new Thread(connectTask).start();
    }

    private synchronized void ensureSshConnected() throws IOException {
        if (currentLocation == null || currentLocation.getServer() == null) {
            return;
        }
        if (activeSshService != null && activeSshService.isConnected()) {
            return;
        }
        logger.info("SSH session not active. Attempting transparent reconnection to {}...",
                currentLocation.getServer().getHost());
        if (activeSshService == null) {
            activeSshService = sshServiceFactory.get();
        }
        SSHServerModel server = currentLocation.getServer();
        String password = server.getPassword();
        if (password == null || password.isBlank()) {
            throw new IOException("SSH session is not active and no password is saved for server: " + server.getName());
        }
        boolean ok = activeSshService.connect(server.getHost(), server.getPort(), server.getUsername(), password);
        if (!ok || !activeSshService.isConnected()) {
            throw new IOException("Failed to establish SSH connection to " + server.getHost());
        }
        logger.info("Successfully established SSH connection to {}", server.getHost());
    }

    private String normalizePathString(String path) {
        if (path == null) {
            return null;
        }
        if (currentLocation != null && currentLocation.getServer() == null) {
            try {
                return java.nio.file.Paths.get(path).toAbsolutePath().normalize().toString();
            } catch (Exception e) {
                logger.warn("Path normalization failed for local path: {}", path, e);
                return path;
            }
        } else {
            if (path.length() > 1 && path.endsWith("/")) {
                return path.substring(0, path.length() - 1);
            }
            return path;
        }
    }

    private String lastPathKey() {
        return currentLocation == null || currentLocation.getServer() == null
            ? "file_manager_last_path_local"
            : "file_manager_last_path_" + currentLocation.getServer().getName();
    }

    private boolean isInitialFileSelectionPending = true;

    private String lastFileKey() {
        return currentLocation == null || currentLocation.getServer() == null
            ? "file_manager_last_file_local"
            : "file_manager_last_file_" + currentLocation.getServer().getName();
    }

    private String lastFileFolderKey() {
        return currentLocation == null || currentLocation.getServer() == null
            ? "file_manager_last_file_dir_local"
            : "file_manager_last_file_dir_" + currentLocation.getServer().getName();
    }

    private void saveLastOpenedFile(FileInfo file) {
        if (preferenceService != null && file != null) {
            preferenceService.saveOrUpdatePreferences(new Preference(lastFileKey(), file.getName()));
            if (currentPath != null) {
                preferenceService.saveOrUpdatePreferences(new Preference(lastFileFolderKey(), currentPath));
            }
            if (currentLocation != null) {
                String locId = currentLocation.getServer() == null ? "local" : currentLocation.getServer().getName();
                preferenceService.saveOrUpdatePreferences(new Preference("file_manager_last_location", locId));
            }
        }
    }

    private void restoreFileSelection() {
        try {
            if (!isInitialFileSelectionPending) return;
            isInitialFileSelectionPending = false;

            if (preferenceService == null) return;
            String savedDir = preferenceService.getPreferencesByCode(lastFileFolderKey()).orElse(null);
            if (savedDir != null && currentPath != null) {
                String normSaved = normalizePathString(savedDir);
                String normCurrent = normalizePathString(currentPath);
                if (!normSaved.equals(normCurrent)) {
                    return;
                }
            }

            preferenceService.getPreferencesByCode(lastFileKey())
                .filter(f -> !f.isBlank())
                .ifPresent(lastFileName -> {
                    for (FileInfo file : fileTable.getItems()) {
                        if (file.isFile() && file.getName().equals(lastFileName)) {
                            fileTable.getSelectionModel().select(file);
                            fileTable.scrollTo(file);
                            break;
                        }
                    }
                });
        } catch (Exception e) {
            logger.warn("Failed to restore file selection", e);
        }
    }

    private void restoreLastLocation() {
        TreeItem<LocationItem> target = findLocalNode();
        if (preferenceService != null) {
            String lastLoc = preferenceService.getPreferencesByCode("file_manager_last_location").orElse("local");
            if (!"local".equalsIgnoreCase(lastLoc) && !lastLoc.isBlank()) {
                TreeItem<LocationItem> serverNode = findServerNodeByName(lastLoc);
                if (serverNode != null) {
                    target = serverNode;
                }
            }
        }
        if (target == null || target.getValue() == null) {
            return;
        }
        selectNodeQuietly(target);
        LocationItem item = target.getValue();
        // Bare controllers (unit tests) have no selection listener; navigate explicitly only
        // when the tree is live, so restoring never opens a connection in isolation tests.
        if (locationListenerInstalled && (item.isLocal() || item.isServer())) {
            handleLocationSelected(item);
        }
    }

    private String sortKey() {
        return currentLocation == null || currentLocation.getServer() == null
            ? "file_manager_sort_local"
            : "file_manager_sort_" + currentLocation.getServer().getName();
    }

    private void saveSortOrdering() {
        if (suppressSortSave) return;
        try {
            var sortOrder = fileTable.getSortOrder();
            if (sortOrder.isEmpty()) {
                // 3rd click (clear) — remove preference so default sort is used on next open
                logger.info("saveSortOrdering: key={}, value=(cleared)", sortKey());
                preferenceService.saveOrUpdatePreferences(new Preference(sortKey(), ""));
                return;
            }
            TableColumn<FileInfo, ?> col = sortOrder.get(0);
            String encoded = col.getId() + ":" + col.getSortType().name();
            logger.info("saveSortOrdering: key={}, value={}", sortKey(), encoded);
            preferenceService.saveOrUpdatePreferences(new Preference(sortKey(), encoded));
        } catch (Exception e) {
            logger.warn("Failed to save sort ordering", e);
        }
    }

    private void restoreSortOrdering() {
        try {
            Optional<String> pref = preferenceService.getPreferencesByCode(sortKey());
            logger.info("restoreSortOrdering: key={}, pref={}", sortKey(), pref.orElse("(empty)"));
            if (pref.isEmpty() || pref.get().isBlank()) {
                fileTable.getSortOrder().clear();
                return;
            }
            String[] parts = pref.get().split(":");
            if (parts.length != 2) {
                fileTable.getSortOrder().clear();
                return;
            }
            Optional<TableColumn<FileInfo, ?>> col = fileTable.getColumns().stream()
                .filter(c -> parts[0].equals(c.getId()))
                .findFirst();
            if (col.isEmpty()) {
                fileTable.getSortOrder().clear();
                return;
            }
            TableColumn.SortType direction = TableColumn.SortType.valueOf(parts[1]);
            var currentOrder = fileTable.getSortOrder();
            if (currentOrder.size() == 1 && currentOrder.get(0) == col.get() && col.get().getSortType() == direction) {
                return;
            }
            suppressSortSave = true;
            col.get().setSortType(direction);
            fileTable.getSortOrder().setAll(col.get());
            col.get().setSortType(direction);
            suppressSortSave = false;
            logger.info("restoreSortOrdering: applied col={}, dir={}", col.get().getId(), col.get().getSortType());
        } catch (Exception e) {
            logger.warn("Failed to restore sort ordering", e);
            suppressSortSave = false;
            fileTable.getSortOrder().clear();
        }
    }

    private void navigateTo(String path) {
        if (path == null || path.isEmpty())
            return;

        if (prefetchDebounce != null) {
            prefetchDebounce.stop();
        }
        prefetchGeneration.incrementAndGet();

        String normalizedNewPath = normalizePathString(path);
        String normalizedCurrentPath = normalizePathString(currentPath);

        // If user repeatedly double-clicks while actively loading this exact path, ignore duplicate trigger
        if (normalizedNewPath != null && normalizedNewPath.equals(normalizedCurrentPath)
                && currentLoadTask != null && currentLoadTask.isRunning()) {
            return;
        }

        if (normalizedCurrentPath != null && !normalizedCurrentPath.equals(normalizedNewPath)) {
            backHistory.push(currentPath); // Push the original, un-normalized path for display
            forwardHistory.clear();
        }

        currentPath = normalizedNewPath;
        pathField.setText(currentPath);
        if (preferenceService != null) {
            preferenceService.saveOrUpdatePreferences(new Preference(lastPathKey(), currentPath));
        }
        loadFiles(currentPath);
        updateNavigationButtons();
    }

    private String getParentPath(String path) {
        if (path == null) {
            return null;
        }

        String parent = null;
        if (currentLocation == null || currentLocation.getServer() == null) {
            java.io.File f = new java.io.File(path);
            parent = f.getParent();
        } else {
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
        // Invalidate even before a cache hit; an old network response must not overwrite this folder.
        if (currentLoadTask != null) currentLoadTask.cancel(false);
        currentLoadTask = null;
        if (currentPrefetchFuture != null && !currentPrefetchFuture.isDone()) {
            currentPrefetchFuture.cancel(false);
        }

        // --- Caching Layer (Stale-While-Revalidate) ---
        String cacheKey = getCacheKey(path);
        CacheEntry cachedEntry = directoryCache.get(cacheKey);

        boolean isShowingStale = false;
        if (cachedEntry != null) {
            allFiles.setAll(cachedEntry.getFiles());
            restoreSortOrdering();
            itemCountLabel.setText(allFiles.size() + " items");
            updateNavigationButtons();
            restoreFileSelection();

            if (!cachedEntry.isExpired()) {
                logger.info("Cache HIT for path: {}", path);
                updateStatus("Ready (from cache)");
                progressIndicator.setVisible(false);
                fileTable.setCursor(javafx.scene.Cursor.DEFAULT);
                scheduleSpeculativePrefetch(path, cachedEntry.getFiles());
                return;
            }

            logger.info("Cache STALE for path: {}, displaying cached while revalidating in background", path);
            updateStatus("Updating " + path + "...");
            isShowingStale = true;
        } else {
            logger.info("Cache MISS for path: {}", path);
            allFiles.clear();
            itemCountLabel.setText("0 items");
            updateStatus("Reading " + path + "...");
            progressIndicator.setVisible(true);
            fileTable.setCursor(javafx.scene.Cursor.WAIT);
        }

        if (currentLoadTask != null && currentLoadTask.isRunning()) {
            currentLoadTask.cancel(false);
        }

        final boolean wasShowingStale = isShowingStale;
        Task<List<FileInfo>> loadTask = new Task<>() {
            @Override
            protected List<FileInfo> call() throws Exception {
                List<FileInfo> files;
                if (currentLocation.getServer() == null) {
                    files = new java.util.ArrayList<>(localFileService.listFiles(path));
                } else {
                    if (activeSshService == null || !activeSshService.isConnected()) {
                        ensureSshConnected();
                    }
                    List<RemoteFileInfo> remoteFiles = activeSshService.listFiles(path);
                    files = new java.util.ArrayList<>(remoteFiles.size() + 1);
                    for (RemoteFileInfo r : remoteFiles) {
                        FileInfo f = new FileInfo();
                        f.setName(r.getName());
                        f.setPath(r.getPath());
                        f.setSize(r.getSize());
                        f.setDirectory(r.isDirectory());
                        f.setModifiedTime(r.getModifiedTime());
                        f.setPermissions(r.getPermissions());
                        f.setOwner(r.getOwner() != null && !r.getOwner().isBlank() ? r.getOwner() : "-");
                        f.setSourceType(FileInfo.SourceType.REMOTE);
                        files.add(f);
                    }
                }

                // Add ".." entry for parent directory navigation
                String parentPath = getParentPath(path);
                if (parentPath != null) {
                    FileInfo upDir = new FileInfo();
                    upDir.setName("..");
                    upDir.setDirectory(true);
                    upDir.setPath(parentPath);
                    if (currentLocation != null) {
                        upDir.setSourceType(currentLocation.getServer() == null ? FileInfo.SourceType.LOCAL
                                : FileInfo.SourceType.REMOTE);
                    }
                    files.add(0, upDir);
                }

                files.sort(BACKGROUND_PRE_SORT);
                return files;
            }
        };
        currentLoadTask = loadTask;

        loadTask.setOnSucceeded(e -> {
            if (loadTask != currentLoadTask) return;
            List<FileInfo> loadedFiles = loadTask.getValue();
            directoryCache.put(cacheKey, new CacheEntry(loadedFiles)); // Update cache

            if (!wasShowingStale || !isSameFileList(allFiles, loadedFiles)) {
                allFiles.setAll(loadedFiles);
                restoreSortOrdering(); // restores saved sort or falls back to defaultSort
                itemCountLabel.setText(allFiles.size() + " items");
                restoreFileSelection();
            }

            progressIndicator.setVisible(false);
            fileTable.setCursor(javafx.scene.Cursor.DEFAULT);
            updateStatus("Ready");
            updateNavigationButtons();
            loadFavoritesForCurrentLocation();

            scheduleSpeculativePrefetch(path, loadedFiles);
        });

        loadTask.setOnFailed(e -> {
            if (loadTask != currentLoadTask) return;
            progressIndicator.setVisible(false);
            fileTable.setCursor(javafx.scene.Cursor.DEFAULT);
            Throwable ex = loadTask.getException();
            logger.error("Error loading files for path: {}", path, ex);

            if (wasShowingStale) {
                updateStatus("Ready (showing cached)");
            } else {
                updateStatus("Error loading files");
                showError("Error", "Failed to load files: " + ex.getMessage());
            }
        });

        fileIoExecutor.submit(loadTask);
    }

    private boolean isSameFileList(List<FileInfo> a, List<FileInfo> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            FileInfo fa = a.get(i);
            FileInfo fb = b.get(i);
            if (!java.util.Objects.equals(fa.getName(), fb.getName())
                    || fa.getSize() != fb.getSize()
                    || fa.getModifiedTime() != fb.getModifiedTime()
                    || fa.isDirectory() != fb.isDirectory()) {
                return false;
            }
        }
        return true;
    }

    private void scheduleSpeculativePrefetch(String parentPath, List<FileInfo> files) {
        if (prefetchDebounce != null) {
            prefetchDebounce.stop();
        }
        prefetchDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(800));
        final List<FileInfo> filesCopy = new java.util.ArrayList<>(files);
        prefetchDebounce.setOnFinished(e -> triggerSpeculativePrefetch(parentPath, filesCopy));
        prefetchDebounce.play();
    }

    private void triggerSpeculativePrefetch(String parentPath, List<FileInfo> files) {
        if (currentLocation == null || currentLocation.getServer() == null) {
            return;
        }
        if (activeSshService == null || !activeSshService.isConnected()) {
            return;
        }

        final int generation = prefetchGeneration.incrementAndGet();
        if (currentPrefetchFuture != null && !currentPrefetchFuture.isDone()) {
            currentPrefetchFuture.cancel(false);
        }

        List<String> subDirPaths = new java.util.ArrayList<>();
        for (FileInfo f : files) {
            if (f != null && f.isDirectory() && !"..".equals(f.getName())) {
                String subPath = f.getPath();
                String key = getCacheKey(subPath);
                CacheEntry entry = directoryCache.get(key);
                if (entry == null || entry.isExpired()) {
                    subDirPaths.add(subPath);
                    if (subDirPaths.size() >= 5) {
                        break;
                    }
                }
            }
        }

        if (subDirPaths.isEmpty()) {
            return;
        }

        final com.seeloggyplus.service.impl.SSHServiceImpl ssh = activeSshService;
        final LocationItem loc = currentLocation;

        currentPrefetchFuture = prefetchExecutor.submit(() -> {
            for (String subPath : subDirPaths) {
                if (generation != prefetchGeneration.get() || ssh != activeSshService || !ssh.isConnected()) {
                    break;
                }
                try {
                    String key = getLocationIdFor(loc) + ":" + normalizePathString(subPath);
                    if (directoryCache.containsKey(key) && !directoryCache.get(key).isExpired()) {
                        continue;
                    }
                    List<RemoteFileInfo> remoteFiles = ssh.listFiles(subPath);
                    if (remoteFiles != null) {
                        List<FileInfo> subFiles = new java.util.ArrayList<>(remoteFiles.size() + 1);
                        for (RemoteFileInfo r : remoteFiles) {
                            FileInfo f = new FileInfo();
                            f.setName(r.getName());
                            f.setPath(r.getPath());
                            f.setSize(r.getSize());
                            f.setDirectory(r.isDirectory());
                            f.setModifiedTime(r.getModifiedTime());
                            f.setPermissions(r.getPermissions());
                            f.setOwner(r.getOwner() != null && !r.getOwner().isBlank() ? r.getOwner() : "-");
                            f.setSourceType(FileInfo.SourceType.REMOTE);
                            subFiles.add(f);
                        }

                        // Add ".." entry
                        FileInfo upDir = new FileInfo();
                        upDir.setName("..");
                        upDir.setDirectory(true);
                        upDir.setPath(parentPath);
                        upDir.setSourceType(FileInfo.SourceType.REMOTE);
                        subFiles.add(0, upDir);

                        subFiles.sort(BACKGROUND_PRE_SORT);
                        directoryCache.put(key, new CacheEntry(subFiles));
                        logger.debug("Speculative prefetch cached: {}", subPath);
                    }
                } catch (Exception ex) {
                    logger.debug("Prefetch skipped for {}: {}", subPath, ex.getMessage());
                }
            }
        });
    }

    private void handleFileDoubleClick() {
        FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        if (selected.isDirectory()) {
            navigateTo(selected.getPath());
        } else if (doubleClickAction == OpenAction.TAIL) {
            handleTail();
        } else {
            handleOpen();
        }
    }

    static OpenAction preferredFileAction(String value) {
        return "TAIL".equalsIgnoreCase(value) ? OpenAction.TAIL : OpenAction.OPEN;
    }

    private TableRow<?> findParentTableRow(Node node) {
        while (node != null && !(node instanceof TableRow)) {
            if (node instanceof TableView) return null;
            node = node.getParent();
        }
        return (TableRow<?>) node;
    }

    private void handleOpen() {
        selectedFileResult = fileTable.getSelectionModel().getSelectedItem();
        if (selectedFileResult != null && selectedFileResult.isFile()) {
            openAction = OpenAction.OPEN;
            saveLastOpenedFile(selectedFileResult);
            closeDialog();
        }
    }

    @FXML
    private void handleTail() {
        if (fileTable != null && fileTable.getSelectionModel() != null) {
            selectedFileResult = fileTable.getSelectionModel().getSelectedItem();
        }
        if (selectedFileResult == null || !selectedFileResult.isFile()) {
            return;
        }

        openAction = OpenAction.TAIL;
        saveLastOpenedFile(selectedFileResult);
        closeDialog();
    }

    private int readTailWindowSize() {
        try {
            if (preferenceService != null) {
                return Integer.parseInt(
                        preferenceService.getPreferencesByCode("main_tail_window_size").orElse("20000"));
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return 20000;
    }

    void updateFindInFilesState() {
        if (findInFilesButton != null) {
            findInFilesButton.setDisable(currentLocation == null || currentLocation.getServer() == null);
        }
    }

    private void handleFindInFiles() {
        if (currentLocation == null || currentLocation.getServer() == null) {
            showError("Find in Files", "Select a remote server location first.");
            return;
        }
        try {
            ensureSshConnected();
        } catch (IOException e) {
            showError("Find in Files", "SSH connection is not active: " + e.getMessage());
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RemoteLogSearchDialog.fxml"));
            Parent root = loader.load();
            RemoteLogSearchDialogController searchController = loader.getController();
            searchController.setContext(activeSshService, currentLocation.getServer(), currentPath);
            searchController.setMaxTailWindow(readTailWindowSize());

            Stage dialog = new Stage();
            dialog.setTitle("Find in Files");
            addAppIcon(dialog);
            dialog.initModality(Modality.WINDOW_MODAL);
            if (cancelButton != null && cancelButton.getScene() != null) {
                dialog.initOwner(cancelButton.getScene().getWindow());
            }
            dialog.setScene(AppTheme.scene(root));
            dialog.showAndWait();

            FileInfo chosen = searchController.getChosenFile();
            if (chosen != null) {
                selectedFileResult = chosen;
                pendingJumpLine = searchController.getTargetLine();
                pendingTailWindowLines = searchController.getTailWindowLines();
                pendingTailJumpIndex = searchController.getTailJumpIndex();
                boolean tail = searchController.isTailAction() && !searchController.isOpenInstead();
                openAction = tail ? OpenAction.TAIL : OpenAction.OPEN;
                saveLastOpenedFile(selectedFileResult);
                closeDialog();
            }
        } catch (IOException e) {
            logger.error("Failed to open Find in Files dialog", e);
            showError("Find in Files", "Could not open dialog: " + e.getMessage());
        }
    }

    private void updateFavoriteButton() {
        if (favoriteCurrentButton == null) return;
        favoriteCurrentButton.setDisable(currentPath == null);
        favoriteCurrentButton.setText(favoritePathsCache.contains(currentPath) ? "Remove folder favorite" : "Favorite this folder");
    }

    private void toggleCurrentFavorite() {
        if (currentPath == null) return;
        if (favoritePathsCache.contains(currentPath)) {
            favoritesListView.getItems().stream().filter(f -> currentPath.equals(f.getPath())).findFirst()
                    .ifPresent(f -> favoriteFolderService.removeFavorite(f.getId()));
        } else {
            String name = currentPath.replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1);
            favoriteFolderService.addFavorite(name.isBlank() ? currentPath : name, currentPath, getLocationIdForCurrent());
        }
        cachedFavoritesLocationId = null;
        loadFavoritesForCurrentLocation(); fileTable.refresh();
    }

    private void handleAddToFavorites() {
        FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isDirectory()) {
            return;
        }
        favoriteFolderService.addFavorite(selected.getName(), selected.getPath(), getLocationIdForCurrent());
        cachedFavoritesLocationId = null; // Invalidate favorites cache
        loadFavoritesForCurrentLocation();
        fileTable.refresh(); // To update styling
    }

    private void handleRemoveFromFavorites() {
        FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isDirectory()) {
            return;
        }

        // We need the ID to delete it.
        favoriteFolderService.getFavoritesForLocation(getLocationIdForCurrent()).stream()
                .filter(fav -> fav.getPath().equals(selected.getPath()))
                .findFirst()
                .ifPresent(fav -> favoriteFolderService.removeFavorite(fav.getId()));

        cachedFavoritesLocationId = null; // Invalidate favorites cache
        loadFavoritesForCurrentLocation();
        fileTable.refresh(); // To update styling
    }

    private void loadFavoritesForCurrentLocation() {
        if (currentLocation == null)
            return;

        String locationId = getLocationIdForCurrent();

        // Skip DB query if favorites for this location are already cached
        if (locationId.equals(cachedFavoritesLocationId)) {
            return;
        }

        List<FavoriteFolder> favorites = favoriteFolderService.getFavoritesForLocation(locationId);

        favoritePathsCache.clear();
        favorites.forEach(f -> favoritePathsCache.add(f.getPath()));
        cachedFavoritesLocationId = locationId;

        favoritesListView.setItems(FXCollections.observableArrayList(favorites));
        updateFavoriteButton();
    }

    private String getLocationIdForCurrent() {
        if (currentLocation == null) {
            return "";
        }
        // Use a constant for local, and server name for remote.
        return currentLocation.getServer() == null ? "local" : currentLocation.getServer().getName();
    }

    private void closeDialog() {
        if (prefetchDebounce != null) {
            prefetchDebounce.stop();
        }
        if (currentPrefetchFuture != null && !currentPrefetchFuture.isDone()) {
            currentPrefetchFuture.cancel(false);
        }
        fileIoExecutor.shutdown();
        prefetchExecutor.shutdown();

        // Ensure any active connection is terminated when the dialog closes, unless we
        // selected a file to open
        if (activeSshService != null && selectedFileResult == null) {
            activeSshService.disconnect();
        } else if (activeSshService != null && selectedFileResult != null) {
            logger.info("Keeping SSH connection alive for caller to use with file: {}", selectedFileResult.getName());
        }

        if (cancelButton != null && cancelButton.getScene() != null && cancelButton.getScene().getWindow() != null) {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            stage.close();
        }
    }

    private void navigateBack() {
        if (!backHistory.isEmpty()) {
            forwardHistory.push(currentPath);
            String prev = backHistory.pop();
            currentPath = prev; // Don't push to back history again
            pathField.setText(prev);
            preferenceService.saveOrUpdatePreferences(new Preference(lastPathKey(), prev));
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
            preferenceService.saveOrUpdatePreferences(new Preference(lastPathKey(), next));
            loadFiles(next);
            updateNavigationButtons();
        }
    }

    private void navigateUp() {
        if (currentPath == null)
            return;

        String parent = null;
        if (currentLocation.getServer() == null) {
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
        if (currentLocation.getServer() == null) {
            navigateTo(localFileService.getHomeDirectory());
        } else {
            navigateTo(currentLocation.getServer().getDefaultPath() != null ? currentLocation.getServer().getDefaultPath() : "/");
        }
    }

    private void refreshCurrentPath() {
        if (currentPath != null) {
            directoryCache.remove(getCacheKey(currentPath)); // Invalidate cache for this path
            logger.info("Cache invalidated for path: {}", currentPath);
            loadFiles(currentPath);
        }
    }

    private void updateNavigationButtons() {
        backButton.setText("Back"); forwardButton.setText("Forward"); upButton.setText("Up");
        backButton.setTooltip(new Tooltip(backHistory.isEmpty() ? "Back: no previous folder in this location" : "Back to " + backHistory.peek()));
        forwardButton.setTooltip(new Tooltip(forwardHistory.isEmpty() ? "Forward: no next folder" : "Forward to " + forwardHistory.peek()));
        upButton.setTooltip(new Tooltip("Up: parent folder (independent of history)"));
        updateFavoriteButton();
        backButton.setDisable(backHistory.isEmpty());
        forwardButton.setDisable(forwardHistory.isEmpty());
        upButton.setDisable(currentLocation == null || currentPath == null || currentPath.equals("/")
                || (currentLocation.getServer() == null && new java.io.File(currentPath).getParent() == null));
    }

    private void copySelectionToClipboard(final javafx.scene.control.TableView<?> table) {
        final javafx.collections.ObservableList<javafx.scene.control.TablePosition> selectedCells = table
                .getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) {
            return;
        }

        final java.util.Map<Integer, java.util.List<javafx.scene.control.TablePosition>> rowMap = new java.util.TreeMap<>();
        for (final javafx.scene.control.TablePosition pos : selectedCells) {
            rowMap.computeIfAbsent(pos.getRow(), k -> new java.util.ArrayList<>()).add(pos);
        }

        final StringBuilder clipboardString = new StringBuilder();
        for (final java.util.List<javafx.scene.control.TablePosition> row : rowMap.values()) {
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
            addAppIcon(stage);
            stage.setTitle("Server Management");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(ownerStage); // The dialog's direct owner is the file manager
            MainController.restoreWindow(finalMainStage, wasMaximized, oldX, oldY, oldWidth, oldHeight, root, stage);

            rebuildLocationTree();
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
            addAppIcon(stage);
            stage.setTitle("Log Preview");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(ownerStage);
            MainController.restoreWindow(finalMainStage, wasMaximized, oldX, oldY, oldWidth, oldHeight, root, stage);

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
            suppressAutoRefresh = true;
            Alert alert = new Alert(Alert.AlertType.ERROR);
            addAppIcon(alert);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public FileInfo getSelectedFile() {
        return selectedFileResult;
    }

    /** 1-based line to jump to when the file was chosen from Find in Files, or 0. */
    public int getPendingJumpLine() {
        return pendingJumpLine;
    }

    /** Tail window (last N lines) requested by Find in Files, or 0 for the default. */
    public int getPendingTailWindowLines() {
        return pendingTailWindowLines;
    }

    /** 0-based index within the tail buffer to jump to, or -1. */
    public int getPendingTailJumpIndex() {
        return pendingTailJumpIndex;
    }

    public com.seeloggyplus.service.impl.SSHServiceImpl getSshService() {
        return activeSshService;
    }

    public SSHServerModel getActiveServer() {
        return currentLocation != null ? currentLocation.getServer() : null;
    }

    /** One node of the location tree: the hidden root, Local Drive, a group folder, or a server. */
    static class LocationItem {
        enum Kind { ROOT, LOCAL, GROUP, SERVER }

        private final Kind kind;
        private final String groupName;
        private final SSHServerModel server;

        private LocationItem(Kind kind, String groupName, SSHServerModel server) {
            this.kind = kind;
            this.groupName = groupName;
            this.server = server;
        }

        static LocationItem root() {
            return new LocationItem(Kind.ROOT, null, null);
        }

        static LocationItem local() {
            return new LocationItem(Kind.LOCAL, null, null);
        }

        static LocationItem group(String groupName) {
            return new LocationItem(Kind.GROUP, groupName, null);
        }

        static LocationItem of(SSHServerModel server) {
            return new LocationItem(Kind.SERVER, server == null ? null : server.getGroupName(), server);
        }

        boolean isRoot() {
            return kind == Kind.ROOT;
        }

        boolean isLocal() {
            return kind == Kind.LOCAL;
        }

        boolean isGroup() {
            return kind == Kind.GROUP;
        }

        boolean isServer() {
            return kind == Kind.SERVER;
        }

        String getGroupName() {
            return groupName;
        }

        SSHServerModel getServer() {
            return server;
        }

        String getLabel() {
            return switch (kind) {
                case LOCAL -> "Local Drive";
                case GROUP -> groupName;
                case SERVER -> (server.isFavorite() ? "★ " : "") + server.getName();
                default -> "Locations";
            };
        }

        String getTooltip() {
            return switch (kind) {
                case LOCAL -> "This computer";
                case GROUP -> "Group: " + groupName;
                case SERVER -> (groupName == null || groupName.isBlank() ? "Ungrouped" : groupName)
                        + " / " + server.getName();
                default -> null;
            };
        }
    }

    /**
     * Parses a human-readable file size string (e.g., "1.2 KB", "5 MB") into bytes.
     * Returns -1 if the string is not a valid size (e.g., "-").
     *
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

    private static class CacheEntry {
        private final List<FileInfo> files;
        private final long timestamp;

        public CacheEntry(List<FileInfo> files) {
            this.files = files;
            this.timestamp = System.currentTimeMillis();
        }

        public List<FileInfo> getFiles() {
            return files;
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_DURATION_MS;
        }
    }

    private void addAppIcon(Dialog<?> dialog) {
        try {
            if (dialog.getOwner() == null && cancelButton.getScene() != null) {
                dialog.initOwner(cancelButton.getScene().getWindow());
            }

            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
                addAppIcon(stage);
            }
        } catch (Exception e) {
            // Ignore
        }
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