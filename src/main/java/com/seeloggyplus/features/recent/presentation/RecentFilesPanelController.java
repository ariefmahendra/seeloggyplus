package com.seeloggyplus.features.recent.presentation;

import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.features.recent.domain.RecentFile;
import com.seeloggyplus.shared.logs.LogFileService;
import com.seeloggyplus.features.recent.application.RecentFileService;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RecentFilesPanelController {

    public interface Host {
        void onOpenRecent(RecentFilesDto recent);

        void closeSession(LogSession session);

        void closeAllTabs();

        void clearSearch();

        void stopRemoteTailIfMonitoring(String remotePath);

        void stopRemoteTail();

        void cleanupTempFiles();

        Optional<ButtonType> showConfirmation(Alert alert);

        String monitoringRemotePath();
    }

    @FXML
    private VBox leftPanel;
    @FXML
    private TextField recentFilesFilterField;
    @FXML
    private TreeView<RecentFileTreeCell.RecentNode> recentFilesTreeView;
    @FXML
    private Button clearRecentButton;
    @FXML
    private Button pinLeftPanelButton;

    private final ObservableList<RecentFilesDto> allRecentFiles = FXCollections.observableArrayList();

    private RecentFileService recentFileService;
    private LogFileService logFileService;
    private TabSessionManager tabSessionManager;
    private Host host;
    private Runnable onCloseRequest = () -> { };

    public VBox getRoot() {
        return leftPanel;
    }

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest != null ? onCloseRequest : () -> { };
    }

    public void start(RecentFileService recentFileService, LogFileService logFileService,
            TabSessionManager tabSessionManager, Host host) {
        this.recentFileService = recentFileService;
        this.logFileService = logFileService;
        this.tabSessionManager = tabSessionManager;
        this.host = host;
        setupPanel();
    }

    public void rebuildTree() {
        if (recentFilesTreeView == null) {
            return;
        }
        TreeItem<RecentFileTreeCell.RecentNode> root =
                new TreeItem<>(RecentFileTreeCell.RecentNode.root());
        root.setExpanded(true);
        String query = recentFilesFilterField != null ? recentFilesFilterField.getText() : null;
        Map<String, TreeItem<RecentFileTreeCell.RecentNode>> servers = new LinkedHashMap<>();
        for (RecentFilesDto dto : allRecentFiles) {
            if (!RecentFileTreeCell.matches(dto, query)) {
                continue;
            }
            String source = RecentFileTreeCell.sourceLabel(dto);
            TreeItem<RecentFileTreeCell.RecentNode> serverNode = servers.computeIfAbsent(source, name -> {
                TreeItem<RecentFileTreeCell.RecentNode> item =
                        new TreeItem<>(RecentFileTreeCell.RecentNode.server(name));
                item.setExpanded(true);
                root.getChildren().add(item);
                return item;
            });
            serverNode.getChildren().add(new TreeItem<>(RecentFileTreeCell.RecentNode.file(dto)));
        }
        recentFilesTreeView.setRoot(root);
    }

    public void refresh() {
        allRecentFiles.setAll(recentFileService.findAll());
        rebuildTree();
    }

    public void refreshTree() {
        if (recentFilesTreeView != null) {
            Platform.runLater(recentFilesTreeView::refresh);
        }
    }

    public void clearSelection() {
        if (recentFilesTreeView != null) {
            recentFilesTreeView.getSelectionModel().clearSelection();
        }
    }

    public void saveOpened(LogFile logFile, boolean tail) {
        RecentFile recentFile = new RecentFile();
        recentFile.setFileId(logFile.getId());
        recentFile.setLastOpened(LocalDateTime.now());
        recentFile.setMode(tail ? RecentFile.MODE_TAIL : RecentFile.MODE_OPEN);
        recentFileService.save(logFile, recentFile);
        refresh();
    }

    public void markOpened(LogSession session) {
        if (session == null || session.getLogFileRecord() == null || session.getLogFileRecord().getId() == null) {
            return;
        }
        LogFile file = session.getLogFileRecord();
        RecentFile recent = recentFileService.findByFileId(file.getId()).orElseGet(
                () -> new RecentFile(UUID.randomUUID().toString(), file.getId(), null, null));
        recent.setLastOpened(LocalDateTime.now());
        recent.setMode(session.isTailModeEnabled() ? RecentFile.MODE_TAIL : RecentFile.MODE_OPEN);
        recentFileService.save(file, recent);
        refresh();
        selectForSession(session);
    }

    public void markTailOpened(LogFile logFile, String remotePath) {
        RecentFile recent = recentFileService.findByFileId(logFile.getId()).orElseGet(RecentFile::new);
        recent.setFileId(logFile.getId());
        recent.setLastOpened(LocalDateTime.now());
        recent.setMode(RecentFile.MODE_TAIL);
        recentFileService.save(logFile, recent);
        final String serverId = logFile.getSshServerID();
        Platform.runLater(() -> {
            refresh();
            selectRecentFile(remotePath, serverId, true);
        });
    }

    public void closeTabsForLogFile(LogFile logFile) {
        if (logFile == null || tabSessionManager.isEmpty()) {
            return;
        }
        List<LogSession> sessionsToClose = new ArrayList<>();
        for (LogSession session : tabSessionManager.all()) {
            if (isSessionMatchingLogFile(session, logFile)) {
                sessionsToClose.add(session);
            }
        }
        for (LogSession session : sessionsToClose) {
            host.closeSession(session);
        }
    }

    public void selectForSession(LogSession session) {
        if (session == null || recentFilesTreeView == null) {
            if (recentFilesTreeView != null) {
                Platform.runLater(() -> recentFilesTreeView.getSelectionModel().clearSelection());
            }
            return;
        }

        LogFile logRecord = session.getLogFileRecord();
        String targetPath = null;
        String serverId = null;
        boolean isRemote = false;

        if (logRecord != null) {
            targetPath = logRecord.getFilePath();
            serverId = logRecord.getSshServerID();
            isRemote = logRecord.isRemote();
        } else if (session.getRemotePath() != null) {
            targetPath = session.getRemotePath();
            serverId = session.getSshServer() != null ? session.getSshServer().getId() : null;
            isRemote = true;
        } else if (session.getLocalFile() != null) {
            targetPath = session.getLocalFile().getAbsolutePath();
            isRemote = false;
        }

        if (targetPath == null) {
            return;
        }

        final String finalPath = targetPath;
        final String finalServerId = serverId;
        final boolean finalIsRemote = isRemote;
        final String recordId = logRecord != null ? logRecord.getId() : null;

        Platform.runLater(() -> {
            recentFilesTreeView.getSelectionModel().clearSelection();
            for (TreeItem<RecentFileTreeCell.RecentNode> node : recentFileNodes()) {
                RecentFilesDto dto = node.getValue().getFile();
                if (dto != null && dto.logFile() != null) {
                    LogFile logFile = dto.logFile();
                    if (recordId != null && recordId.equals(logFile.getId())) {
                        selectRecentNode(node);
                        return;
                    }
                    if (logFile.isRemote() == finalIsRemote) {
                        if (finalIsRemote) {
                            if (Objects.equals(finalServerId, logFile.getSshServerID())
                                    && normalizePath(finalPath).equals(normalizePath(logFile.getFilePath()))) {
                                selectRecentNode(node);
                                return;
                            }
                        } else {
                            if (normalizePath(finalPath).equalsIgnoreCase(normalizePath(logFile.getFilePath()))) {
                                selectRecentNode(node);
                                return;
                            }
                        }
                    }
                }
            }
        });
    }

    public static boolean isTailModeRecent(RecentFilesDto recentFile) {
        return recentFile != null && RecentFile.MODE_TAIL.equalsIgnoreCase(recentFile.openMode());
    }

    private void setupPanel() {
        ContextMenu leftPanelContextMenu = new ContextMenu();

        MenuItem openFileMenuItem = new MenuItem("Open File");
        openFileMenuItem.setOnAction(actionEvent -> {
            RecentFileTreeCell.RecentNode selected = selectedRecentNode();
            if (selected != null && selected.isFile()) {
                host.onOpenRecent(selected.getFile());
            }
        });

        MenuItem deleteFromRecentMenuItem = new MenuItem("Delete from Recent");
        deleteFromRecentMenuItem.setOnAction(actionEvent -> {
            if (!selectedRecentFiles().isEmpty()) {
                handleClearRecentFiles();
            }
        });

        leftPanelContextMenu.getItems().addAll(openFileMenuItem, new SeparatorMenuItem(), deleteFromRecentMenuItem);
        recentFilesTreeView.setShowRoot(false);
        recentFilesTreeView.setCellFactory(tree -> new RecentFileTreeCell(host::monitoringRemotePath));
        recentFilesTreeView.getStyleClass().add("recent-files-list");
        allRecentFiles.setAll(recentFileService.findAll());
        rebuildTree();
        recentFilesTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        recentFilesTreeView.setContextMenu(leftPanelContextMenu);

        if (recentFilesFilterField != null) {
            recentFilesFilterField.textProperty().addListener((obs, oldVal, newVal) -> rebuildTree());
        }

        recentFilesTreeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                RecentFileTreeCell.RecentNode selected = selectedRecentNode();
                if (selected != null && selected.isFile()) {
                    host.onOpenRecent(selected.getFile());
                }
            }
        });

        clearRecentButton.setOnAction(e -> handleClearRecentFiles());
        pinLeftPanelButton.setOnAction(e -> onCloseRequest.run());
    }

    private void handleClearRecentFiles() {
        List<RecentFilesDto> selected = selectedRecentFiles();
        if (!selected.isEmpty()) {
            int count = selected.size();

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Remove Selected Recent Files");
            alert.setHeaderText("Remove " + count + " selected recent file(s)?");
            alert.setContentText("This action cannot be undone.");
            Optional<ButtonType> result = host.showConfirmation(alert);
            if (result.isPresent() && result.get() == ButtonType.OK) {
                List<RecentFilesDto> listToDeleteRecentFiles = List.copyOf(selected);

                for (RecentFilesDto recentFilesDto : listToDeleteRecentFiles) {
                    LogFile logFile = recentFilesDto.logFile();
                    if (logFile != null) {
                        closeTabsForLogFile(logFile);
                        host.stopRemoteTailIfMonitoring(logFile.isRemote() ? logFile.getFilePath() : null);

                        recentFileService.deleteByFileId(logFile.getId());
                        logFileService.deleteLogFileById(logFile.getId());
                    }
                }

                refresh();
                host.cleanupTempFiles();
            }
            return;
        }

        if (!hasRecentFiles()) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear Recent Files");
        alert.setHeaderText("Clear ALL recent files?");
        alert.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = host.showConfirmation(alert);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            host.closeAllTabs();
            recentFileService.deleteAll();
            logFileService.deleteAllLogFiles();
            host.stopRemoteTail();
            host.clearSearch();
            refresh();
            host.cleanupTempFiles();
        }
    }

    private RecentFileTreeCell.RecentNode selectedRecentNode() {
        TreeItem<RecentFileTreeCell.RecentNode> item = recentFilesTreeView.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private List<RecentFilesDto> selectedRecentFiles() {
        List<RecentFilesDto> files = new ArrayList<>();
        for (TreeItem<RecentFileTreeCell.RecentNode> item
                : recentFilesTreeView.getSelectionModel().getSelectedItems()) {
            if (item != null && item.getValue() != null && item.getValue().isFile()) {
                files.add(item.getValue().getFile());
            }
        }
        return files;
    }

    private List<TreeItem<RecentFileTreeCell.RecentNode>> recentFileNodes() {
        List<TreeItem<RecentFileTreeCell.RecentNode>> nodes = new ArrayList<>();
        TreeItem<RecentFileTreeCell.RecentNode> root = recentFilesTreeView.getRoot();
        if (root != null) {
            for (TreeItem<RecentFileTreeCell.RecentNode> server : root.getChildren()) {
                nodes.addAll(server.getChildren());
            }
        }
        return nodes;
    }

    private boolean hasRecentFiles() {
        return !recentFileNodes().isEmpty();
    }

    private void selectRecentNode(TreeItem<RecentFileTreeCell.RecentNode> node) {
        recentFilesTreeView.getSelectionModel().select(node);
        recentFilesTreeView.scrollTo(recentFilesTreeView.getRow(node));
    }

    private void selectRecentFile(String targetPath, String sshServerId, boolean isRemote) {
        if (targetPath == null || recentFilesTreeView == null) {
            return;
        }
        Platform.runLater(() -> {
            recentFilesTreeView.getSelectionModel().clearSelection();
            for (TreeItem<RecentFileTreeCell.RecentNode> node : recentFileNodes()) {
                RecentFilesDto dto = node.getValue().getFile();
                if (dto != null && dto.logFile() != null) {
                    LogFile logFile = dto.logFile();
                    if (logFile.isRemote() == isRemote) {
                        if (isRemote) {
                            if (Objects.equals(sshServerId, logFile.getSshServerID())
                                    && normalizePath(targetPath).equals(normalizePath(logFile.getFilePath()))) {
                                selectRecentNode(node);
                                return;
                            }
                        } else {
                            if (normalizePath(targetPath).equalsIgnoreCase(normalizePath(logFile.getFilePath()))) {
                                selectRecentNode(node);
                                return;
                            }
                        }
                    }
                }
            }
        });
    }

    private boolean isSessionMatchingLogFile(LogSession session, LogFile logFile) {
        if (session == null || logFile == null) return false;

        if (session.getLogFileRecord() != null && session.getLogFileRecord().getId() != null) {
            if (session.getLogFileRecord().getId().equals(logFile.getId())) {
                return true;
            }
        }

        if (logFile.isRemote()) {
            String sessionRemotePath = session.getRemotePath();
            if (sessionRemotePath != null) {
                String sessionServerId = session.getSshServer() != null ? session.getSshServer().getId() : null;
                if (Objects.equals(sessionServerId, logFile.getSshServerID())
                        && normalizePath(sessionRemotePath).equals(normalizePath(logFile.getFilePath()))) {
                    return true;
                }
            }
        } else {
            if (session.getLocalFile() != null && logFile.getFilePath() != null) {
                if (normalizePath(session.getLocalFile().getAbsolutePath())
                        .equalsIgnoreCase(normalizePath(logFile.getFilePath()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        return path.trim().replace('\\', '/');
    }
}
