package com.seeloggyplus.features.servers.presentation;

import com.seeloggyplus.shared.ui.DialogWindowSupport;

import com.seeloggyplus.shared.ui.ThemedAlerts;

import com.seeloggyplus.features.servers.application.ServerManagementService;
import com.seeloggyplus.features.servers.infrastructure.ServerManagementServiceImpl;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.DialogPresenter;
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
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.Objects;
import javafx.scene.image.Image;

public class ServerManagementDialogController {

    private static final Logger logger = LoggerFactory.getLogger(ServerManagementDialogController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private TableView<SSHServerModel> serverTable;
    @FXML private TreeView<GroupNode> groupTree;
    @FXML private Button newGroupButton;
    @FXML private Button renameGroupButton;
    @FXML private Button deleteGroupButton;
    @FXML private Button moveToGroupButton;
    @FXML private TableColumn<SSHServerModel, String> groupColumn;
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

    @FXML private javafx.scene.layout.VBox groupSidebar;
    @FXML private javafx.scene.layout.VBox detailsPanel;
    @FXML private SplitPane serverSplitPane;
    @FXML private ToggleButton detailsButton;
    @FXML private Label serverCountLabel;
    @FXML private Label selectionLabel;
    @FXML private Label detailConnectionLabel;
    private com.seeloggyplus.shared.settings.PreferenceService preferenceService;
    private boolean restoringColumns;
    private static final String VISIBLE_COLUMNS = "server_management_visible_columns";

    private ServerManagementService serverService;
    private SSHServiceFactory sshServiceFactory;
    private ObservableList<SSHServerModel> allServers;
    private ObservableList<SSHServerModel> filteredServers;
    private SSHServerModel selectedForConnection;
    private List<String> groupNames = List.of();
    private String selectedGroupPath;
    private boolean rebuildingGroups;
    private boolean groupMutationRunning;
    private int loadGeneration;

    private record ServerSnapshot(List<SSHServerModel> servers, List<String> groups) { }

    private record GroupNode(String path) {
        @Override public String toString() {
            return path == null ? "All servers" : path.isEmpty() ? "Ungrouped" : path;
        }
    }
    private DialogPresenter dialogPresenter = defaultDialogPresenter();

    @FXML
    public void initialize() {
        logger.info("Initializing ServerManagementDialogController");

        serverService = new ServerManagementServiceImpl();
        allServers = FXCollections.observableArrayList();
        filteredServers = FXCollections.observableArrayList();

        SplitPane.setResizableWithParent(groupSidebar, false);
        serverSplitPane.widthProperty().addListener((obs, old, width) -> {
            if (old.doubleValue() == 0 && width.doubleValue() > 0) {
                serverSplitPane.setDividerPositions(201 / width.doubleValue());
            }
        });
        detailsPanel.visibleProperty().bind(detailsButton.selectedProperty());
        detailsPanel.managedProperty().bind(detailsPanel.visibleProperty());
        clearSearchButton.disableProperty().bind(searchField.textProperty().isEmpty());
        for (Label label : List.of(detailNameLabel, detailConnectionLabel, detailPathLabel, selectionLabel)) {
            Tooltip tooltip = new Tooltip();
            tooltip.textProperty().bind(label.textProperty());
            label.setTooltip(tooltip);
        }
        setupGroups();
        setupTableColumns();
        setupEventHandlers();
        loadServers();
        updateButtonStates();
    }

    private void setupGroups() {
        groupTree.setCellFactory(tree -> new TreeCell<>() {
            private final FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.FOLDER);
            { icon.setSize("14"); icon.getStyleClass().add("file-icon-folder"); }
            @Override protected void updateItem(GroupNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null); setTooltip(null);
                } else {
                    String path = item.path();
                    setText(path == null || path.isEmpty() ? item.toString() : leafName(path));
                    setGraphic(icon);
                    setTooltip(new Tooltip(item.toString()));
                }
            }
        });
        groupTree.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (rebuildingGroups) return;
            selectedGroupPath = selected == null ? null : selected.getValue().path();
            filterServers(searchField.getText());
            updateButtonStates();
        });
        newGroupButton.setDisable(true);
        newGroupButton.setOnAction(event -> promptNewGroup());
        renameGroupButton.setOnAction(event -> promptRenameGroup());
        deleteGroupButton.setOnAction(event -> promptDeleteGroup());
        moveToGroupButton.setOnAction(event -> promptMoveToGroup());
        MenuItem create = new MenuItem("New group...");
        MenuItem rename = new MenuItem("Rename group...");
        MenuItem delete = new MenuItem("Delete group");
        create.setOnAction(event -> promptNewGroup());
        rename.setOnAction(event -> promptRenameGroup());
        delete.setOnAction(event -> promptDeleteGroup());
        ContextMenu menu = new ContextMenu(create, rename, delete);
        menu.setOnShowing(event -> {
            create.setDisable(newGroupButton.isDisabled());
            rename.setDisable(renameGroupButton.isDisabled());
            delete.setDisable(deleteGroupButton.isDisabled());
        });
        groupTree.setContextMenu(menu);
    }

    private void rebuildGroups() {
        Map<String, Boolean> expansion = new LinkedHashMap<>();
        rememberExpansion(groupTree.getRoot(), expansion);
        TreeItem<GroupNode> root = new TreeItem<>(new GroupNode(null));
        root.setExpanded(true);
        Map<String, TreeItem<GroupNode>> folders = new LinkedHashMap<>();
        TreeItem<GroupNode> ungrouped = new TreeItem<>(new GroupNode(""));
        root.getChildren().add(ungrouped);
        for (String path : groupNames) ensureGroup(root, folders, path, expansion);
        for (SSHServerModel server : allServers) {
            if (server.getGroupName() != null && !server.getGroupName().isBlank()) {
                ensureGroup(root, folders, server.getGroupName(), expansion);
            }
        }
        TreeItem<GroupNode> selected = selectedGroupPath == null ? root
                : selectedGroupPath.isEmpty() ? ungrouped : folders.get(selectedGroupPath);
        if (selected == null) { selected = root; selectedGroupPath = null; }
        rebuildingGroups = true;
        try {
            groupTree.setRoot(root);
            for (TreeItem<GroupNode> parent = selected.getParent(); parent != null; parent = parent.getParent()) {
                parent.setExpanded(true);
            }
            groupTree.getSelectionModel().select(selected);
        } finally { rebuildingGroups = false; }
    }

    private void rememberExpansion(TreeItem<GroupNode> item, Map<String, Boolean> expansion) {
        if (item == null) return;
        expansion.put(item.getValue().path(), item.isExpanded());
        item.getChildren().forEach(child -> rememberExpansion(child, expansion));
    }

    private void ensureGroup(TreeItem<GroupNode> root, Map<String, TreeItem<GroupNode>> folders,
            String path, Map<String, Boolean> expansion) {
        TreeItem<GroupNode> parent = root;
        String current = "";
        for (String segment : path.split("/")) {
            if (segment.isBlank()) continue;
            current = current.isEmpty() ? segment : current + "/" + segment;
            TreeItem<GroupNode> folder = folders.get(current);
            if (folder == null) {
                folder = new TreeItem<>(new GroupNode(current));
                folder.setExpanded(expansion.getOrDefault(current, true));
                folders.put(current, folder);
                parent.getChildren().add(folder);
            }
            parent = folder;
        }
    }

    private boolean belongsToSelectedGroup(String group) {
        if (selectedGroupPath == null) return true;
        if (selectedGroupPath.isEmpty()) return group == null || group.isBlank();
        return selectedGroupPath.equals(group) || group != null && group.startsWith(selectedGroupPath + "/");
    }

    private void promptNewGroup() {
        if (groupMutationRunning) return;
        String parent = selectedGroupPath == null || selectedGroupPath.isEmpty() ? null : selectedGroupPath;
        TextInputDialog dialog = new TextInputDialog();
        DialogWindowSupport.styleDialog(dialog, groupTree == null || groupTree.getScene() == null
                ? null : groupTree.getScene().getWindow());
        dialog.setTitle("New group");
        dialog.setHeaderText(parent == null ? "Create a server group" : "Create a group inside '" + parent + "'");
        dialog.setContentText("Group name:");
        dialogPresenter.present(dialog).map(String::trim).filter(name -> !name.isEmpty()).ifPresent(name -> {
            String path = parent == null ? name : parent + "/" + name;
            mutateGroups(() -> serverService.createGroup(path), path, null);
        });
    }

    private void promptRenameGroup() {
        String path = selectedGroupPath;
        if (groupMutationRunning || path == null || path.isEmpty()) return;
        TextInputDialog dialog = new TextInputDialog(leafName(path));
        DialogWindowSupport.styleDialog(dialog, groupTree == null || groupTree.getScene() == null
                ? null : groupTree.getScene().getWindow());
        dialog.setTitle("Rename group");
        dialog.setHeaderText("Rename group '" + path + "'");
        dialog.setContentText("Group name:");
        dialogPresenter.present(dialog).map(String::trim).filter(name -> !name.isEmpty() && !name.equals(leafName(path)))
                .ifPresent(name -> {
                    String parent = parentPath(path);
                    String renamed = parent == null ? name : parent + "/" + name;
                    mutateGroups(() -> serverService.renameGroup(path, renamed), renamed, null);
                });
    }

    private void promptDeleteGroup() {
        String path = selectedGroupPath;
        if (groupMutationRunning || path == null || path.isEmpty()) return;
        Alert dialog = ThemedAlerts.create(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Delete group");
        dialog.setHeaderText("Delete group '" + path + "'?");
        dialog.setContentText("Nested groups are deleted as well. Servers become ungrouped. No server is deleted.");
        dialogPresenter.present(dialog).filter(answer -> answer == ButtonType.OK).ifPresent(answer ->
                mutateGroups(() -> serverService.deleteGroup(path), parentPath(path), null));
    }

    private void promptMoveToGroup() {
        SSHServerModel selected = serverTable.getSelectionModel().getSelectedItem();
        if (groupMutationRunning || selected == null) return;
        String ungrouped = "(Ungrouped)";
        List<String> choices = new ArrayList<>();
        choices.add(ungrouped);
        choices.addAll(groupNames);
        ChoiceDialog<String> dialog = new ChoiceDialog<>(selected.getGroupName() == null ? ungrouped : selected.getGroupName(), choices);
        DialogWindowSupport.styleDialog(dialog, groupTree == null || groupTree.getScene() == null
                ? null : groupTree.getScene().getWindow());
        dialog.setTitle("Move to group");
        dialog.setHeaderText("Move '" + selected.getName() + "' to a group");
        dialog.setContentText("Group:");
        dialogPresenter.present(dialog).ifPresent(choice -> {
            String target = ungrouped.equals(choice) ? null : choice;
            if (Objects.equals(target, selected.getGroupName())) return;
            String id = selected.getId();
            mutateGroups(() -> {
                SSHServerModel server = serverService.getServerById(id);
                if (server == null) throw new IllegalStateException("The selected server no longer exists");
                server.setGroupName(target);
                serverService.saveServer(server);
            }, target == null ? "" : target, id);
        });
    }

    private void mutateGroups(Runnable mutation, String groupToSelect, String serverToSelect) {
        groupMutationRunning = true;
        loadGeneration++;
        updateButtonStates();
        Task<Void> task = new Task<>() {
            @Override protected Void call() { mutation.run(); return null; }
        };
        task.setOnSucceeded(event -> {
            selectedGroupPath = groupToSelect == null ? null : java.util.Arrays.stream(groupToSelect.split("/", -1))
                    .map(String::trim).collect(java.util.stream.Collectors.joining("/"));
            loadServers(serverToSelect);
        });
        task.setOnFailed(event -> {
            groupMutationRunning = false;
            updateButtonStates();
            logger.error("Failed to update server groups", task.getException());
            showError("Group Error", task.getException().getMessage());
        });
        startBackground(task);
    }

    private static void startBackground(Task<?> task) {
        Thread thread = new Thread(task, "ServerManagement-IO");
        thread.setDaemon(true);
        thread.start();
    }

    private static String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? null : path.substring(0, slash);
    }

    private static String leafName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private void setupTableColumns() {
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getName() != null ? cellData.getValue().getName() : "-"));

        groupColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getGroupName() == null || cellData.getValue().getGroupName().isBlank()
                        ? "Ungrouped" : cellData.getValue().getGroupName()));

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

        serverTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        for (TableColumn<SSHServerModel, ?> column : serverTable.getColumns()) {
            column.setId(column == nameColumn ? "nameColumn" : column == hostColumn ? "hostColumn"
                    : column == portColumn ? "portColumn" : column == usernameColumn ? "usernameColumn"
                    : column == groupColumn ? "groupColumn" : column == defaultPathColumn ? "defaultPathColumn" : "lastUsedColumn");
            column.visibleProperty().addListener((obs, old, visible) -> saveColumnVisibility());
        }
        for (TableColumn<SSHServerModel, String> column : List.of(nameColumn, hostColumn, portColumn, usernameColumn, groupColumn, defaultPathColumn, lastUsedColumn)) {
            column.setCellFactory(table -> new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setTooltip(empty || item == null ? null : new Tooltip(item));
                }
            });
        }
        Runnable resizeName = () -> nameColumn.setPrefWidth(Math.max(160, serverTable.getWidth() - 22
                - serverTable.getVisibleLeafColumns().stream().filter(column -> column != nameColumn)
                .mapToDouble(TableColumn::getWidth).sum()));
        serverTable.widthProperty().addListener((obs, old, width) -> resizeName.run());
        for (var column : serverTable.getColumns()) {
            if (column != nameColumn) column.widthProperty().addListener((obs, old, width) -> resizeName.run());
            column.visibleProperty().addListener((obs, old, visible) -> resizeName.run());
        }
        serverTable.setItems(filteredServers);

        serverTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateDetailsPanel(newVal);
            updateButtonStates();
        });

        serverTable.setRowFactory(tv -> {
            TableRow<SSHServerModel> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleEditServer();
                }
            });
            return row;
        });

        ContextMenu contextMenu = new ContextMenu();

        MenuItem addMenuItem = new MenuItem("Add Server...");
        addMenuItem.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.PLUS));
        addMenuItem.setOnAction(e -> handleAddServer());

        MenuItem editMenuItem = new MenuItem("Edit...");
        editMenuItem.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.EDIT));
        editMenuItem.setOnAction(e -> handleEditServer());

        MenuItem cloneMenuItem = new MenuItem("Clone...");
        cloneMenuItem.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.CLONE));
        cloneMenuItem.setOnAction(e -> handleCloneServer());

        MenuItem deleteMenuItem = new MenuItem("Delete");
        deleteMenuItem.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.TRASH));
        deleteMenuItem.setOnAction(e -> handleDeleteServer());

        MenuItem moveMenuItem = new MenuItem("Move to group...");
        moveMenuItem.setOnAction(event -> promptMoveToGroup());
        contextMenu.getItems().addAll(
                addMenuItem,
                new SeparatorMenuItem(),
                editMenuItem,
                cloneMenuItem,
                moveMenuItem,
                deleteMenuItem
        );

        contextMenu.setOnShowing(e -> {
            boolean hasRow = !groupMutationRunning && serverTable.getSelectionModel().getSelectedItem() != null;
            addMenuItem.setDisable(groupMutationRunning);
            editMenuItem.setDisable(!hasRow);
            cloneMenuItem.setDisable(!hasRow);
            deleteMenuItem.setDisable(!hasRow);
            moveMenuItem.setDisable(!hasRow || groupMutationRunning);
        });

        serverTable.setContextMenu(contextMenu);
    }

    private void setupEventHandlers() {
        addServerButton.setOnAction(e -> handleAddServer());
        editServerButton.setOnAction(e -> handleEditServer());
        if (cloneServerButton != null) {
            cloneServerButton.setOnAction(e -> handleCloneServer());
        }
        deleteServerButton.setOnAction(e -> handleDeleteServer());
        refreshButton.setOnAction(e -> loadServers());
        closeButton.setOnAction(e -> handleClose());

        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterServers(newVal));
        clearSearchButton.setOnAction(e -> searchField.clear());
    }

    private void loadServers() {
        loadServers(null);
    }

    private void loadServers(String selectServerId) {
        int generation = ++loadGeneration;
        ServerManagementService service = serverService;
        Task<ServerSnapshot> task = new Task<>() {
            @Override
            protected ServerSnapshot call() {
                return new ServerSnapshot(service.getAllServers(), service.getGroupNames());
            }
        };

        task.setOnSucceeded(e -> {
            if (generation != loadGeneration) return;
            ServerSnapshot snapshot = task.getValue();
            allServers.setAll(snapshot.servers());
            groupNames = List.copyOf(snapshot.groups());
            rebuildGroups();
            filterServers(searchField.getText());
            serverTable.refresh();
            groupMutationRunning = false;
            updateButtonStates();
            logger.info("Loaded {} servers and {} groups", allServers.size(), groupNames.size());
            if (selectServerId != null) {
                for (SSHServerModel server : filteredServers) {
                    if (selectServerId.equals(server.getId())) {
                        serverTable.getSelectionModel().select(server);
                        serverTable.scrollTo(server);
                        break;
                    }
                }
            }
        });
        task.setOnFailed(e -> {
            if (generation != loadGeneration) return;
            groupMutationRunning = false;
            updateButtonStates();
            logger.error("Failed to load servers", task.getException());
            showError("Load Error", "Failed to load servers: " + task.getException().getMessage());
        });
        startBackground(task);
    }

    private void filterServers(String searchText) {
        String search = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        filteredServers.setAll(allServers.stream()
                .filter(server -> belongsToSelectedGroup(server.getGroupName()))
                .filter(server -> search.isEmpty() || matchesSearch(server, search))
                .toList());
        serverTable.sort();
        boolean active = !search.isEmpty() || selectedGroupPath != null;
        serverCountLabel.setText(active ? filteredServers.size() + " of " + allServers.size() + " servers" : allServers.size() + " servers");
    }

    private boolean matchesSearch(SSHServerModel server, String search) {
        return (server.getGroupName() != null && server.getGroupName().toLowerCase(Locale.ROOT).contains(search)) || (server.getName() != null && server.getName().toLowerCase().contains(search)) ||
                server.getHost().toLowerCase().contains(search) ||
                server.getUsername().toLowerCase().contains(search) ||
                (server.getDefaultPath() != null && server.getDefaultPath().toLowerCase().contains(search));
    }

    private void handleAddServer() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerEditDialog.fxml"));
            Parent root = loader.load();

            ServerEditDialogController controller = loader.getController();
            controller.setServerService(serverService);
            controller.setSshServiceFactory(sshServiceFactory);
            controller.setInitialGroup(selectedGroupPath == null || selectedGroupPath.isEmpty() ? null : selectedGroupPath);

            Stage dialog = new Stage();
            dialog.setTitle("Add SSH Server");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(addServerButton.getScene().getWindow());
            dialog.setScene(AppTheme.scene(root));

            dialogPresenter.present(dialog);

            if (controller.isSaved()) {
                SSHServerModel saved = controller.getSavedServer();
                loadServers(saved != null ? saved.getId() : null);
            }
        } catch (IOException e) {
            logger.error("Failed to open add server dialog", e);
            showError("Dialog Error", "Failed to open server editor: " + e.getMessage());
        }
    }

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
            controller.setSshServiceFactory(sshServiceFactory);
            controller.setServer(selected);

            Stage dialog = new Stage();
            dialog.setTitle("Edit SSH Server");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(editServerButton.getScene().getWindow());
            dialog.setScene(AppTheme.scene(root));

            dialogPresenter.present(dialog);

            if (controller.isSaved()) {
                SSHServerModel saved = controller.getSavedServer();
                loadServers(saved != null ? saved.getId() : (selected != null ? selected.getId() : null));
            }
        } catch (IOException e) {
            logger.error("Failed to open edit server dialog", e);
            showError("Dialog Error", "Failed to open server editor: " + e.getMessage());
        }
    }

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
            controller.setSshServiceFactory(sshServiceFactory);
            controller.setCloneServer(selected);

            Stage dialog = new Stage();
            dialog.setTitle("Clone SSH Server");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(cloneServerButton.getScene().getWindow());
            dialog.setScene(AppTheme.scene(root));

            dialogPresenter.present(dialog);

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

    private void handleDeleteServer() {
        SSHServerModel selected = serverTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Alert alert = ThemedAlerts.create(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Server");
        alert.setHeaderText("Delete SSH Server Configuration?");
        alert.setContentText(String.format("Are you sure you want to delete '%s'?\nThis action cannot be undone.",
                selected.getDisplayString()));

        Optional<ButtonType> result = dialogPresenter.present(alert);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            serverService.deleteServer(selected.getId());
            loadServers();
            logger.info("Deleted server: {}", selected.getDisplayString());
        }
    }

    private void updateDetailsPanel(SSHServerModel server) {
        detailsButton.setDisable(server == null);
        selectionLabel.setText(server == null ? "No selection" : server.getName());
        detailConnectionLabel.setText(server == null ? "-" : server.getUsername() + "@" + server.getHost() + ":" + server.getPort());
        if (server == null) detailsButton.setSelected(false);
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

    private void updateButtonStates() {
        boolean hasSelection = serverTable.getSelectionModel().getSelectedItem() != null;
        addServerButton.setDisable(groupMutationRunning);
        editServerButton.setDisable(groupMutationRunning || !hasSelection);
        if (cloneServerButton != null) {
            cloneServerButton.setDisable(groupMutationRunning || !hasSelection);
        }
        deleteServerButton.setDisable(groupMutationRunning || !hasSelection);
        boolean selectedGroup = selectedGroupPath != null && !selectedGroupPath.isEmpty();
        newGroupButton.setDisable(groupMutationRunning);
        renameGroupButton.setDisable(groupMutationRunning || !selectedGroup);
        deleteGroupButton.setDisable(groupMutationRunning || !selectedGroup);
        moveToGroupButton.setDisable(groupMutationRunning || !hasSelection);
        refreshButton.setDisable(groupMutationRunning);
    }

    private void handleClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = ThemedAlerts.create(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(message);
            dialogPresenter.present(alert);
        });
    }

    public void setPreferenceService(com.seeloggyplus.shared.settings.PreferenceService service) {
        preferenceService = service;
        if (service == null) return;
        service.getPreferencesByCode(VISIBLE_COLUMNS).filter(value -> !value.isBlank()).ifPresent(value -> {
            var ids = java.util.Set.of(value.split(","));
            if (serverTable.getColumns().stream().noneMatch(column -> ids.contains(column.getId()))) return;
            restoringColumns = true;
            try {
                serverTable.getColumns().forEach(column -> column.setVisible(ids.contains(column.getId())));
            } finally { restoringColumns = false; }
        });
    }

    private void saveColumnVisibility() {
        if (preferenceService == null || restoringColumns) return;
        String value = serverTable.getColumns().stream().filter(TableColumn::isVisible)
                .map(TableColumn::getId).collect(java.util.stream.Collectors.joining(","));
        preferenceService.saveOrUpdatePreferences(new com.seeloggyplus.shared.model.Preference(VISIBLE_COLUMNS, value));
    }

    public void configureWindow(Stage stage) {
        var owner = stage.getOwner();
        var screens = owner == null ? List.<javafx.stage.Screen>of() : javafx.stage.Screen.getScreensForRectangle(owner.getX(), owner.getY(), 1, 1);
        var bounds = (screens.isEmpty() ? javafx.stage.Screen.getPrimary() : screens.getFirst()).getVisualBounds();
        stage.setResizable(true);
        stage.setMinWidth(Math.min(800, bounds.getWidth()));
        stage.setMinHeight(Math.min(520, bounds.getHeight()));
        stage.setWidth(Math.min(1000, bounds.getWidth()));
        stage.setHeight(Math.min(640, bounds.getHeight()));
        stage.setX(bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2);
    }

    public void setSshServiceFactory(SSHServiceFactory factory) {
        this.sshServiceFactory = factory;
    }

    public void setDialogPresenter(DialogPresenter dialogPresenter) {
        this.dialogPresenter = dialogPresenter == null ? defaultDialogPresenter() : dialogPresenter;
    }

    private DialogPresenter defaultDialogPresenter() {
        return new DialogPresenter() {
            @Override
            public void present(Stage dialog) {
                showAndWaitAndRestore(dialog);
            }

            @Override
            public <T> Optional<T> present(Dialog<T> dialog) {
                return showAndWaitAndRestore(dialog);
            }
        };
    }

    public SSHServerModel getSelectedServer() {
        return selectedForConnection;
    }

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
            logger.debug("Could not apply the dialog icon", e);
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
            Image icon = new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/images/app-icon.png")));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            logger.warn("Failed to load app icon for dialog", e);
        }
    }
}
