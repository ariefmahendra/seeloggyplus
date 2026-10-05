package com.seeloggyplus.features.files.presentation;

import com.seeloggyplus.shared.dto.RemoteFileInfo;
import com.seeloggyplus.features.files.domain.FavoriteFolder;
import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.ui.AppIcons;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.features.files.application.FavoriteFolderService;
import com.seeloggyplus.features.files.application.LocalFileService;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.servers.ServerCatalog;
import com.seeloggyplus.shared.ui.SshConnectFlow;
import com.seeloggyplus.features.files.infrastructure.FavoriteFolderServiceImpl;
import com.seeloggyplus.features.files.infrastructure.LocalFileServiceImpl;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import com.seeloggyplus.shared.util.PasswordPromptDialog;
import com.seeloggyplus.shared.util.SshConnectionFeedback;
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
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.scene.Cursor;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

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
    private VBox locationsContainer;
    @FXML
    private Label locationDropHint;
    @FXML
    private Button newGroupButton;
    @FXML
    private Button manageServersButton;
    @FXML
    private Button favoriteCurrentButton;
    @FXML
    private TitledPane favoritesPane;
    @FXML
    private VBox favoritesBox;

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
    private ServerCatalog serverManagementService;
    private PreferenceService preferenceService;
    private SSHServiceFactory sshServiceFactory;


    private FavoriteFolderService favoriteFolderService;

    private FileManagerHost host = new FileManagerHost() {
        @Override
        public FindInFilesResult openFindInFiles(SSHService sshService, SSHServerModel server, String path,
                int maxTailWindow) {
            return null;
        }

        @Override
        public void openPreview(FileInfo file, SSHService sshService) {
            // no-op default
        }

        @Override
        public void openServerManagement() {
            // no-op default
        }
    };

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
    private SSHService activeSshService;
    private Task<Boolean> currentConnectTask;
    private Task<List<FileInfo>> currentLoadTask;
    @Getter
    private OpenAction openAction = OpenAction.OPEN;

    // --- Performance Enhancements ---
    private final Map<String, CacheEntry> directoryCache = new ConcurrentHashMap<>();
    private final Set<String> favoritePathsCache = new HashSet<>();
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    private boolean suppressAutoRefresh = false;
    private boolean suppressSortSave = false;
    private boolean locationTreeReady;
    private String draggedLocationServerId;
    private String draggedLocationGroup;
    private OpenAction doubleClickAction = OpenAction.OPEN;
    private String cachedFavoritesLocationId = null; // Track which location favorites are cached for

    private final ExecutorService fileIoExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FileManager-IO");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FileManager-Prefetch");
        t.setDaemon(true);
        return t;
    });

    private Future<?> currentPrefetchFuture = null;
    private final ExecutorService locationMutationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FileManager-Locations");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger prefetchGeneration = new AtomicInteger(0);
    private PauseTransition prefetchDebounce = null;

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
        favoriteFolderService = new FavoriteFolderServiceImpl();

        allFiles = FXCollections.observableArrayList();
        filteredFiles = new FilteredList<>(allFiles, p -> true);

        setupLayout();
        setupLocationTree();
        setupFavoritesList();
        applyDoubleClickPreference();
        setupFileTable();
        setupEventHandlers();

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

    private void applyDoubleClickPreference() {
        String value = preferenceService != null
                ? preferenceService.getPreferencesByCode("file_double_click_action").orElse("OPEN")
                : "OPEN";
        doubleClickAction = preferredFileAction(value);
        if (fileTable != null) {
            fileTable.setTooltip(new Tooltip("Double-click / Enter: "
                    + (doubleClickAction == OpenAction.TAIL ? "Tail" : "Open/Download") + ". Folders always open."));
        }
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
        locationTree.setContextMenu(buildLocationContextMenu(LocationItem.root()));
        if (locationsContainer != null) {
            ContextMenu rootMenu = buildLocationContextMenu(LocationItem.root());
            locationsContainer.setOnContextMenuRequested(event -> {
                rootMenu.show(locationsContainer, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            locationsContainer.setOnDragOver(event -> {
                if (draggedLocationServerId == null) return;
                event.acceptTransferModes(TransferMode.MOVE);
                locationsContainer.pseudoClassStateChanged(PseudoClass.getPseudoClass("drop-ungroup"), true);
                showLocationDropHint(draggedLocationGroup == null ? "Move to end of ungrouped servers" : "Move out of group");
                event.consume();
            });
            locationsContainer.setOnDragExited(event -> {
                locationsContainer.pseudoClassStateChanged(PseudoClass.getPseudoClass("drop-ungroup"), false);
                if (locationDropHint != null) locationDropHint.setVisible(false);
            });
            locationsContainer.setOnDragDropped(event -> {
                boolean accepted = draggedLocationServerId != null;
                if (accepted) queueLocationPlacement(draggedLocationServerId, null, null, false);
                clearLocationDropFeedback();
                event.setDropCompleted(accepted);
                event.consume();
            });
        }
        // Selecting a node must never open it: starting a drag selects the row before
        // the drag is detected. Connections happen on click or Enter instead.
        locationTree.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                handleLocationClick(selectedLocationItem());
                event.consume();
            }
        });
        locationTreeReady = true;
        locationTree.setRoot(buildLocationRoot());
    }

    /** Builds the WinSCP-style location tree: Local, nested group folders, ungrouped servers. */
    private TreeItem<LocationItem> buildLocationRoot() {
        TreeItem<LocationItem> root = new TreeItem<>(LocationItem.root());
        root.setExpanded(true);
        root.getChildren().add(new TreeItem<>(LocationItem.local()));

        Map<String, TreeItem<LocationItem>> groupFolders = new LinkedHashMap<>();
        if (serverManagementService != null) {
            for (String group : serverManagementService.getGroupNames()) {
                ensureFolderNode(root, groupFolders, group);
            }
            for (SSHServerModel server : serverManagementService.getAllServers()) {
                TreeItem<LocationItem> node = new TreeItem<>(LocationItem.of(server));
                String group = server.getGroupName();
                if (group == null || group.isBlank()) {
                    root.getChildren().add(node);
                } else {
                    ensureFolderNode(root, groupFolders, group).getChildren().add(node);
                }
            }
        }
        return root;
    }

    /** Creates (if needed) every segment of a "Parent/Child" path and returns the deepest folder. */
    private TreeItem<LocationItem> ensureFolderNode(TreeItem<LocationItem> root,
            Map<String, TreeItem<LocationItem>> folders, String path) {
        TreeItem<LocationItem> parent = root;
        StringBuilder current = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            if (current.length() > 0) {
                current.append('/');
            }
            current.append(segment);
            String full = current.toString();
            TreeItem<LocationItem> node = folders.get(full);
            if (node == null) {
                node = newFolderItem(full);
                folders.put(full, node);
                parent.getChildren().add(node);
            }
            parent = node;
        }
        return parent;
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
                clearCellDropFeedback(this);
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

        cell.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1 && e.isStillSincePress()) {
                handleLocationClick(cell.isEmpty() ? null : cell.getItem());
            }
        });

        cell.setOnDragDetected(e -> {
            if (cell.isEmpty() || cell.getItem() == null || !cell.getItem().isServer()) {
                return;
            }
            draggedLocationServerId = cell.getItem().getServer().getId();
            draggedLocationGroup = normalizedPath(cell.getItem().getServer().getGroupName());
            var board = cell.startDragAndDrop(TransferMode.MOVE);
            var content = new ClipboardContent();
            content.putString(draggedLocationServerId);
            board.setContent(content);
            e.consume();
        });
        cell.setOnDragDone(e -> {
            draggedLocationServerId = null;
            draggedLocationGroup = null;
            clearLocationDropFeedback();
        });
        cell.setOnDragOver(e -> {
            clearCellDropFeedback(cell);
            if (draggedLocationServerId == null || cell.isEmpty() || cell.getItem() == null) return;
            LocationItem target = cell.getItem();
            if (target.isServer() && Objects.equals(draggedLocationServerId, target.getServer().getId())) {
                e.consume();
                return;
            }
            DropPlacement placement = dropPlacement(target, draggedLocationGroup, e.getY(), cell.getHeight());
            e.acceptTransferModes(TransferMode.MOVE);
            cell.pseudoClassStateChanged(PseudoClass.getPseudoClass(pseudoClassFor(placement)), true);
            String action = switch (placement) {
                case BEFORE -> "Move before " + target.getLabel();
                case AFTER -> "Move after " + target.getLabel();
                case INTO_GROUP -> "Move into " + groupForDropTarget(target);
                case UNGROUP -> "Move out of group";
            };
            showLocationDropHint(action);
            if (locationsContainer != null) locationsContainer.pseudoClassStateChanged(
                    PseudoClass.getPseudoClass("drop-ungroup"), false);
            e.consume();
        });
        cell.setOnDragExited(e -> clearCellDropFeedback(cell));
        cell.setOnDragDropped(e -> {
            if (cell.isEmpty() || cell.getItem() == null) return;
            LocationItem target = cell.getItem();
            boolean accepted = draggedLocationServerId != null && !(target.isServer()
                    && Objects.equals(draggedLocationServerId, target.getServer().getId()));
            if (accepted) {
                DropPlacement placement = dropPlacement(target, draggedLocationGroup, e.getY(), cell.getHeight());
                if (placement == DropPlacement.BEFORE || placement == DropPlacement.AFTER) {
                    reorderServer(draggedLocationServerId, target.getServer().getId(), placement == DropPlacement.AFTER);
                } else {
                    queueLocationPlacement(draggedLocationServerId, null, groupForDropTarget(target), false);
                }
            }
            clearLocationDropFeedback();
            e.setDropCompleted(accepted);
            e.consume();
        });
        return cell;
    }

    public enum DropPlacement { BEFORE, AFTER, INTO_GROUP, UNGROUP }

    public static DropPlacement dropPlacement(LocationItem target, String sourceGroup, double y, double height) {
        if (target == null || target.isRoot() || target.isLocal()) return DropPlacement.UNGROUP;
        if (target.isGroup()) return DropPlacement.INTO_GROUP;
        double fraction = height > 0 ? y / height : 0.5;
        if (fraction < 0.25) return DropPlacement.BEFORE;
        if (fraction > 0.75) return DropPlacement.AFTER;
        if (Objects.equals(normalizedPath(sourceGroup), normalizedPath(target.getServer().getGroupName()))) {
            return fraction < 0.5 ? DropPlacement.BEFORE : DropPlacement.AFTER;
        }
        return normalizedPath(target.getServer().getGroupName()) == null ? DropPlacement.UNGROUP : DropPlacement.INTO_GROUP;
    }

    private static String pseudoClassFor(DropPlacement placement) {
        return switch (placement) {
            case BEFORE -> "drop-before";
            case AFTER -> "drop-after";
            case INTO_GROUP -> "drop-target";
            case UNGROUP -> "drop-ungroup";
        };
    }

    private static void clearCellDropFeedback(TreeCell<?> cell) {
        for (String name : new String[]{"drop-before", "drop-after", "drop-target", "drop-ungroup"}) {
            cell.pseudoClassStateChanged(PseudoClass.getPseudoClass(name), false);
        }
    }

    private void showLocationDropHint(String message) {
        if (locationDropHint != null) {
            locationDropHint.setText(message);
            locationDropHint.setVisible(true);
        }
    }

    private void clearLocationDropFeedback() {
        if (locationDropHint != null) locationDropHint.setVisible(false);
        if (locationsContainer != null) locationsContainer.pseudoClassStateChanged(
                PseudoClass.getPseudoClass("drop-ungroup"), false);
        for (Node node : locationTree.lookupAll(".tree-cell")) {
            if (node instanceof TreeCell<?> cell) clearCellDropFeedback(cell);
        }
    }

    public void reorderServer(String serverId, String targetId, boolean after) {
        if (Objects.equals(serverId, targetId)) return;
        queueLocationPlacement(serverId, targetId, null, after);
    }

    private void queueLocationPlacement(String serverId, String targetId, String group, boolean after) {
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                return persistLocationPlacement(serverId, targetId, group, after);
            }
        };
        task.setOnSucceeded(event -> {
            if (task.getValue()) {
                rebuildLocationTree();
                selectNode(findServerNodeById(serverId));
            }
        });
        task.setOnFailed(event -> {
            logger.error("Could not move/reorder location server {}", serverId, task.getException());
            rebuildLocationTree();
            showError("Unable to move server", "The server position could not be saved. Please try again.");
        });
        locationMutationExecutor.execute(task);
    }

    private boolean persistLocationPlacement(String serverId, String targetId, String requestedGroup, boolean after) {
        List<SSHServerModel> servers = serverManagementService.getAllServers();
        SSHServerModel source = servers.stream().filter(server -> Objects.equals(serverId, server.getId())).findFirst().orElse(null);
        SSHServerModel target = targetId == null ? null : servers.stream()
                .filter(server -> targetId.equals(server.getId())).findFirst().orElse(null);
        if (source == null || (targetId != null && target == null)) return false;
        String group = normalizedPath(target == null ? requestedGroup : target.getGroupName());
        if (!Objects.equals(normalizedPath(source.getGroupName()), group)) {
            source.setGroupName(group);
            serverManagementService.saveServer(source);
        }
        List<String> siblings = servers.stream().filter(server -> Objects.equals(normalizedPath(server.getGroupName()), group))
                .map(SSHServerModel::getId).collect(Collectors.toCollection(ArrayList::new));
        siblings.remove(serverId);
        int position = targetId == null ? siblings.size() : siblings.indexOf(targetId) + (after ? 1 : 0);
        siblings.add(position, serverId);
        var ordered = siblings.iterator();
        List<String> allIds = servers.stream().map(server -> Objects.equals(normalizedPath(server.getGroupName()), group)
                ? ordered.next() : server.getId()).toList();
        serverManagementService.reorderServers(allIds);
        return true;
    }

    private static FontAwesomeIcon iconFor(LocationItem item) {
        if (item.isLocal()) return FontAwesomeIcon.DESKTOP;
        if (item.isGroup()) return FontAwesomeIcon.FOLDER;
        return FontAwesomeIcon.SERVER;
    }

    private ContextMenu buildLocationContextMenu(LocationItem item) {
        ContextMenu menu = new ContextMenu();
        MenuItem newGroup = new MenuItem("New group...");
        newGroup.setOnAction(e -> promptNewGroup(parentPathForNewGroup(item)));
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
    public static String groupForDropTarget(LocationItem target) {
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

    public void createGroup(String name) {
        serverManagementService.createGroup(name);
        rebuildLocationTree();
        selectNode(findGroupNode(normalizedPath(name)));
    }

    public void renameGroup(String oldName, String newName) {
        serverManagementService.renameGroup(oldName, newName);
        rebuildLocationTree();
        selectNode(findGroupNode(normalizedPath(newName)));
    }

    public void deleteGroup(String name) {
        serverManagementService.deleteGroup(name);
        rebuildLocationTree();
        selectNode(selectedLocationNode());
    }

    public void moveServerToGroup(String serverId, String groupName) {
        TreeItem<LocationItem> node = findServerNodeById(serverId);
        SSHServerModel server = node != null && node.getValue() != null
            ? node.getValue().getServer()
            : serverManagementService.getServerById(serverId);
        if (server == null) {
            return;
        }
        // Dragging/moving must never open the server; the tree is only rebuilt.
        server.setGroupName(groupName);
        serverManagementService.saveServer(server);
        rebuildLocationTree();
        selectNode(findServerNodeById(serverId));
    }

    /** Opens a location on an explicit click/Enter. Selection alone (drag start) does nothing. */
    public void handleLocationClick(LocationItem location) {
        if (location == null || !(location.isLocal() || location.isServer())
                || isSameLocation(location, currentLocation)) {
            return;
        }
        handleLocationSelected(location);
    }

    private static boolean isSameLocation(LocationItem a, LocationItem b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.isLocal() || b.isLocal()) {
            return a.isLocal() && b.isLocal();
        }
        if (a.isServer() && b.isServer()) {
            SSHServerModel sa = a.getServer();
            SSHServerModel sb = b.getServer();
            return sa != null && sb != null && Objects.equals(sa.getId(), sb.getId());
        }
        return false;
    }

    private LocationItem selectedLocationItem() {
        TreeItem<LocationItem> item = locationTree.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    /** New groups are created inside the selected group (or the selected server's group). */
    private static String parentPathForNewGroup(LocationItem item) {
        if (item == null) {
            return null;
        }
        if (item.isGroup()) {
            return item.getGroupName();
        }
        if (item.isServer()) {
            return item.getServer().getGroupName();
        }
        return null;
    }

    private static String parentPath(String path) {
        int slash = path == null ? -1 : path.lastIndexOf('/');
        return slash <= 0 ? null : path.substring(0, slash);
    }

    private static String leafName(String path) {
        int slash = path == null ? -1 : path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String normalizedPath(String path) {
        if (path == null) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/", -1)) {
            String clean = segment.trim();
            if (!clean.isEmpty()) {
                segments.add(clean);
            }
        }
        return segments.isEmpty() ? null : String.join("/", segments);
    }

    private void promptNewGroup() {
        promptNewGroup(parentPathForNewGroup(selectedLocationItem()));
    }

    private void promptNewGroup(String parentPath) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New group");
        dialog.setHeaderText(parentPath == null || parentPath.isBlank()
                ? "Create a server group"
                : "Create a group inside '" + parentPath + "'");
        dialog.setContentText("Group name:");
        addAppIcon(dialog);
        dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty()).ifPresent(name -> {
            String fullPath = parentPath == null || parentPath.isBlank() ? name : parentPath + "/" + name;
            try {
                createGroup(fullPath);
            } catch (RuntimeException ex) {
                logger.error("Failed to create server group {}", fullPath, ex);
                showError("Group Error", "Could not create group: " + ex.getMessage());
            }
        });
    }

    private void promptRenameGroup(String group) {
        String leaf = leafName(group);
        TextInputDialog dialog = new TextInputDialog(leaf);
        dialog.setTitle("Rename group");
        dialog.setHeaderText("Rename group '" + group + "'");
        dialog.setContentText("Group name:");
        addAppIcon(dialog);
        dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty() && !name.equals(leaf)).ifPresent(name -> {
            String parent = parentPath(group);
            String fullPath = parent == null ? name : parent + "/" + name;
            try {
                renameGroup(group, fullPath);
            } catch (RuntimeException ex) {
                logger.error("Failed to rename group {} to {}", group, fullPath, ex);
                showError("Group Error", ex.getMessage());
            }
        });
    }

    private void promptDeleteGroup(String group) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete group");
        alert.setHeaderText("Delete group '" + group + "'?");
        alert.setContentText("Nested groups are deleted as well. Servers become ungrouped. No server is deleted.");
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
        List<String> choices = new ArrayList<>();
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
            if (Objects.equals(target, server.getGroupName())) {
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

    public void rebuildLocationTree() {
        Map<String, Boolean> expansion = new HashMap<>();
        rememberGroupExpansion(locationTree.getRoot(), expansion);
        LocationItem selected = selectedLocationItem();
        String selectedServerId = selected != null && selected.isServer() ? selected.getServer().getId() : null;
        String selectedGroup = selected != null && selected.isGroup() ? selected.getGroupName() : null;
        boolean selectedLocal = selected != null && selected.isLocal();

        locationTree.setRoot(buildLocationRoot());
        restoreGroupExpansion(locationTree.getRoot(), expansion);
        if (selectedServerId != null) {
            selectNode(findServerNodeById(selectedServerId));
        } else if (selectedGroup != null) {
            selectNode(findGroupNode(selectedGroup));
        } else if (selectedLocal) {
            selectNode(findLocalNode());
        }
    }

    private void selectNode(TreeItem<LocationItem> node) {
        if (node == null) {
            return;
        }
        for (TreeItem<LocationItem> parent = node.getParent(); parent != null; parent = parent.getParent()) {
            parent.setExpanded(true);
        }
        locationTree.getSelectionModel().select(node);
        locationTree.scrollTo(locationTree.getRow(node));
    }

    private static void rememberGroupExpansion(TreeItem<LocationItem> node, Map<String, Boolean> states) {
        if (node == null) return;
        if (node.getValue() != null && node.getValue().isGroup()) states.put(node.getValue().getGroupName(), node.isExpanded());
        node.getChildren().forEach(child -> rememberGroupExpansion(child, states));
    }

    private static void restoreGroupExpansion(TreeItem<LocationItem> node, Map<String, Boolean> states) {
        if (node.getValue() != null && node.getValue().isGroup() && states.containsKey(node.getValue().getGroupName())) {
            node.setExpanded(states.get(node.getValue().getGroupName()));
        }
        node.getChildren().forEach(child -> restoreGroupExpansion(child, states));
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
        if (local == null) {
            return;
        }
        locationTree.getSelectionModel().select(local);
        handleLocationClick(local.getValue());
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
        return root == null || group == null ? null : findGroupNode(root, group);
    }

    private static TreeItem<LocationItem> findGroupNode(TreeItem<LocationItem> node, String group) {
        LocationItem value = node.getValue();
        if (value != null && value.isGroup() && group.equals(value.getGroupName())) {
            return node;
        }
        for (TreeItem<LocationItem> child : node.getChildren()) {
            TreeItem<LocationItem> found = findGroupNode(child, group);
            if (found != null) {
                return found;
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

    private TreeItem<LocationItem> findServerNode(Predicate<SSHServerModel> match) {
        TreeItem<LocationItem> root = locationTree.getRoot();
        return root == null ? null : findServerNode(root, match);
    }

    private static TreeItem<LocationItem> findServerNode(TreeItem<LocationItem> node,
            Predicate<SSHServerModel> match) {
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
        favoritesListView.setPlaceholder(new Label("No saved folders yet."));
        if (favoritesBox != null) {
            favoritesBox.getChildren().add(favoritesListView);
            VBox.setVgrow(favoritesListView, Priority.ALWAYS);
        }
        favoriteCurrentButton.setOnAction(e -> toggleCurrentFavorite());
        // The server tree is the primary content of the left panel; favorites
        // collapse into a section so an empty list never eats vertical space.
        VBox.setVgrow(locationTree, Priority.ALWAYS);
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
        MenuItem newGroup = new MenuItem("New group...");
        newGroup.setOnAction(event -> promptNewGroup(null));
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
        favContextMenu.getItems().addAll(newGroup, new SeparatorMenuItem(), removeFavMenuItem);
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
                    if (!Objects.equals(row.getStyle(), targetStyle)) {
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
            if (new KeyCodeCombination(KeyCode.C,
                    KeyCombination.CONTROL_DOWN).match(event)) {
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
            activeSshService = createSshService();
            connectToRemote(location.getServer());
        }
    }

    private void connectToRemote(SSHServerModel server) {
        String password = server.usesKeyAuth() ? server.getKeyPassphrase() : server.getPassword();
        if (!server.usesKeyAuth() && (password == null || password.isBlank())) {
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
        fileTable.setCursor(Cursor.WAIT);
        allFiles.clear();

        if (currentConnectTask != null && currentConnectTask.isRunning()) {
            currentConnectTask.cancel(true);
        }

        final var connectingService = activeSshService;
        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() {
                boolean connected = SshConnectFlow.connect(connectingService, server, finalPassword);
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
                fileTable.setCursor(Cursor.DEFAULT);
                String detail = connectingService.getLastConnectError();
                showConnectionError(server, detail);
                selectLocalLocation(); // Go back to local on failure
            }
        });

        connectTask.setOnFailed(e -> {
            if (connectTask != currentConnectTask) return;
            updateStatus("Connection failed");
            progressIndicator.setVisible(false);
            fileTable.setCursor(Cursor.DEFAULT);
            Throwable ex = connectTask.getException();
            logger.error("SSH Connection task failed", ex);
            showConnectionError(server, ex != null ? ex.getMessage() : connectingService.getLastConnectError());
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
            activeSshService = createSshService();
        }
        SSHServerModel server = currentLocation.getServer();
        String password = server.usesKeyAuth() ? server.getKeyPassphrase() : server.getPassword();
        if (!server.usesKeyAuth() && (password == null || password.isBlank())) {
            throw new IOException("SSH session is not active and no password is saved for server: " + server.getName());
        }
        boolean ok = SshConnectFlow.connect(activeSshService, server, password);
        if (!ok || !activeSshService.isConnected()) {
            throw new IOException("Failed to establish SSH connection to " + server.getHost());
        }
        logger.info("Successfully established SSH connection to {}", server.getHost());
    }

    private SSHService createSshService() {
        if (sshServiceFactory == null) {
            throw new IllegalStateException("SSHServiceFactory not injected");
        }
        return sshServiceFactory.create();
    }

    private String normalizePathString(String path) {
        if (path == null) {
            return null;
        }
        if (currentLocation != null && currentLocation.getServer() == null) {
            try {
                return Paths.get(path).toAbsolutePath().normalize().toString();
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
        selectNode(target);
        LocationItem item = target.getValue();
        // Bare controllers (unit tests) have no live tree; navigate only when it is ready,
        // so restoring never opens a connection in isolation tests.
        if (locationTreeReady && (item.isLocal() || item.isServer())) {
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
            File f = new File(path);
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
                updateStatus("Ready");
                progressIndicator.setVisible(false);
                fileTable.setCursor(Cursor.DEFAULT);
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
            fileTable.setCursor(Cursor.WAIT);
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
                    files = new ArrayList<>(localFileService.listFiles(path));
                } else {
                    if (activeSshService == null || !activeSshService.isConnected()) {
                        ensureSshConnected();
                    }
                    List<RemoteFileInfo> remoteFiles = activeSshService.listFiles(path);
                    files = new ArrayList<>(remoteFiles.size() + 1);
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
            fileTable.setCursor(Cursor.DEFAULT);
            updateStatus("Ready");
            updateNavigationButtons();
            loadFavoritesForCurrentLocation();

            scheduleSpeculativePrefetch(path, loadedFiles);
        });

        loadTask.setOnFailed(e -> {
            if (loadTask != currentLoadTask) return;
            progressIndicator.setVisible(false);
            fileTable.setCursor(Cursor.DEFAULT);
            Throwable ex = loadTask.getException();
            logger.error("Error loading files for path: {}", path, ex);

            if (wasShowingStale) {
                updateStatus("Ready");
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
            if (!Objects.equals(fa.getName(), fb.getName())
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
        prefetchDebounce = new PauseTransition(Duration.millis(800));
        final List<FileInfo> filesCopy = new ArrayList<>(files);
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

        List<String> subDirPaths = new ArrayList<>();
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

        final SSHService ssh = activeSshService;
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
                        List<FileInfo> subFiles = new ArrayList<>(remoteFiles.size() + 1);
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

    public static OpenAction preferredFileAction(String value) {
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

        FileManagerHost.FindInFilesResult r = host.openFindInFiles(activeSshService, currentLocation.getServer(),
                currentPath, readTailWindowSize());
        if (r != null && r.file() != null) {
            selectedFileResult = r.file();
            pendingJumpLine = r.targetLine();
            pendingTailWindowLines = r.tailWindowLines();
            pendingTailJumpIndex = r.tailJumpIndex();
            boolean tail = r.tail() && !r.openInstead();
            openAction = tail ? OpenAction.TAIL : OpenAction.OPEN;
            saveLastOpenedFile(selectedFileResult);
            closeDialog();
        }
    }

    private void updateFavoriteButton() {
        if (favoriteCurrentButton == null) return;
        favoriteCurrentButton.setDisable(currentPath == null);
        favoriteCurrentButton.setTooltip(new Tooltip(favoritePathsCache.contains(currentPath)
                ? "Remove this folder from favorites"
                : "Favorite this folder"));
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
        if (favoritesPane != null) {
            favoritesPane.setExpanded(!favorites.isEmpty());
        }
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
        locationMutationExecutor.shutdown();

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
            File f = new File(currentPath);
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
                || (currentLocation.getServer() == null && new File(currentPath).getParent() == null));
    }

    private void copySelectionToClipboard(final TableView<?> table) {
        final ObservableList<TablePosition> selectedCells = table
                .getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) {
            return;
        }

        final Map<Integer, List<TablePosition>> rowMap = new TreeMap<>();
        for (final TablePosition pos : selectedCells) {
            rowMap.computeIfAbsent(pos.getRow(), k -> new ArrayList<>()).add(pos);
        }

        final StringBuilder clipboardString = new StringBuilder();
        for (final List<TablePosition> row : rowMap.values()) {
            row.sort(Comparator.comparingInt(TablePosition::getColumn));

            final String rowString = row.stream()
                    .map(pos -> {
                        final Object cellData = table.getColumns().get(pos.getColumn()).getCellData(pos.getRow());
                        return cellData == null ? "" : cellData.toString();
                    })
                    .collect(Collectors.joining("\t"));
            clipboardString.append(rowString).append('\n');
        }

        final ClipboardContent content = new ClipboardContent();
        content.putString(clipboardString.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void handleManageServers() {
        host.openServerManagement();
        rebuildLocationTree();
    }

    private void handlePreview() {
        FileInfo selectedFile = fileTable.getSelectionModel().getSelectedItem();
        if (selectedFile == null || !selectedFile.isFile()) {
            return;
        }

        host.openPreview(selectedFile, activeSshService);
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

    private void showConnectionError(SSHServerModel server, String detail) {
        Platform.runLater(() -> {
            suppressAutoRefresh = true;
            Alert alert = SshConnectionFeedback.createAlert(detail, server);
            addAppIcon(alert);
            if (cancelButton != null && cancelButton.getScene() != null) {
                alert.initOwner(cancelButton.getScene().getWindow());
            }
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

    public SSHService getSshService() {
        return activeSshService;
    }

    public SSHServerModel getActiveServer() {
        return currentLocation != null ? currentLocation.getServer() : null;
    }

    public void setHost(FileManagerHost host) {
        this.host = host;
    }

    public void setSshServiceFactory(SSHServiceFactory factory) {
        this.sshServiceFactory = factory;
    }

    public void setServerCatalog(ServerCatalog catalog) {
        this.serverManagementService = catalog;
        if (locationTree != null) {
            rebuildLocationTree();
        }
    }

    public void setPreferenceService(PreferenceService service) {
        this.preferenceService = service;
        applyDoubleClickPreference();
        restoreLastLocation();
    }

    /** One node of the location tree: the hidden root, Local Drive, a group folder, or a server. */
    public static class LocationItem {
        enum Kind { ROOT, LOCAL, GROUP, SERVER }

        private final Kind kind;
        private final String groupName;
        private final SSHServerModel server;

        private LocationItem(Kind kind, String groupName, SSHServerModel server) {
            this.kind = kind;
            this.groupName = groupName;
            this.server = server;
        }

        public static LocationItem root() {
            return new LocationItem(Kind.ROOT, null, null);
        }

        public static LocationItem local() {
            return new LocationItem(Kind.LOCAL, null, null);
        }

        public static LocationItem group(String groupName) {
            return new LocationItem(Kind.GROUP, groupName, null);
        }

        public static LocationItem of(SSHServerModel server) {
            return new LocationItem(Kind.SERVER, server == null ? null : server.getGroupName(), server);
        }

        public boolean isRoot() {
            return kind == Kind.ROOT;
        }

        public boolean isLocal() {
            return kind == Kind.LOCAL;
        }

        public boolean isGroup() {
            return kind == Kind.GROUP;
        }

        public boolean isServer() {
            return kind == Kind.SERVER;
        }

        public String getGroupName() {
            return groupName;
        }

        public SSHServerModel getServer() {
            return server;
        }

        public String getLabel() {
            return switch (kind) {
                case LOCAL -> "Local Drive";
                case GROUP -> leafName(groupName);
                case SERVER -> (server.isFavorite() ? "★ " : "") + server.getName();
                default -> "Locations";
            };
        }

        public String getTooltip() {
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
            if (dialog.getOwner() == null && cancelButton != null && cancelButton.getScene() != null) {
                dialog.initOwner(cancelButton.getScene().getWindow());
            }

            if (dialog.getDialogPane().getScene() != null
                    && dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
                AppIcons.apply(stage);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
