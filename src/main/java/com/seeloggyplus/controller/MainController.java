package com.seeloggyplus.controller;

import com.seeloggyplus.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.service.impl.*;
import com.seeloggyplus.ui.cell.RecentFileListCell;
import com.seeloggyplus.util.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.util.Duration;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.*;
import com.seeloggyplus.service.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Tooltip;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import java.util.regex.Matcher;
import java.util.Collection;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController {
    private static final String TOGGLE_SELECTED_STYLE = "-fx-background-color: #2196F3; -fx-text-fill: white;";
    private static final String TOGGLE_DESELECTED_STYLE = "";

    // FXML Components - MenuBar
    @FXML
    private MenuBar menuBar;
    @FXML
    private MenuItem exitMenuItem;
    @FXML
    private MenuItem closeTabMenuItem;
    @FXML
    private MenuItem closeAllTabsMenuItem;
    @FXML
    private CheckMenuItem showLeftPanelMenuItem;
    @FXML
    private CheckMenuItem showBottomPanelMenuItem;

    @FXML
    private MenuItem serverManagementMenuItem;
    @FXML
    private MenuItem aboutMenuItem;
    @FXML
    private MenuItem helpContentsMenuItem;
    @FXML
    private MenuItem preferencesMenuItem;

    // FXML Components - Main Layout
    @FXML
    private SplitPane horizontalSplitPane;
    @FXML
    private SplitPane verticalSplitPane;

    // FXML Components - Left Panel (Recent Files)
    @FXML
    private VBox leftPanel;
    @FXML
    private TextField recentFilesFilterField;
    @FXML
    private ListView<RecentFilesDto> recentFilesListView;
    @FXML
    private Button clearRecentButton;
    @FXML
    private Button pinLeftPanelButton;

    private final ObservableList<RecentFilesDto> allRecentFiles = FXCollections.observableArrayList();
    private FilteredList<RecentFilesDto> filteredRecentFiles;
    private Label detailPlaceholderLabel;

    // FXML Components - Center Panel (Log Table)

    @FXML
    private TextField searchField;

    @FXML
    private ToggleButton regexCheckBox;
    @FXML
    private ToggleButton caseSensitiveCheckBox;
    @FXML
    private Button searchButton;
    @FXML
    private Button clearSearchButton;
    @FXML
    private VBox loadingOverlay;
    @FXML
    private Label loadingLabel;
    @FXML
    private ProgressIndicator loadingProgress;
    @FXML
    private StackPane logContainer;
    @FXML
    private TabPane logTabPane;
    @FXML
    private VBox emptyStatePane;

    private final Map<Tab, LogSession> sessionMap = new LinkedHashMap<>();
    private LogSession currentSession = null;

    // Root of the center area (clipped to avoid painting over top/bottom bars)
    @FXML
    private StackPane centerRoot;
    @FXML
    private javafx.scene.shape.Rectangle centerClip;
    @FXML
    private Button refreshButton;
    @FXML
    private ToggleButton tailButton;
    @FXML
    private Button clearLogButton;

    // FXML Components - Bottom Panel (Log Detail)
    @FXML
    private VBox bottomPanel;
    @FXML
    private Button pinBottomPanelButton;

    @FXML
    private ToggleButton toggleLeftPanelButton;
    @FXML
    private ToggleButton toggleBottomPanelButton;
    @FXML
    private com.seeloggyplus.ui.search.SearchNavigator searchNavigator;

    @FXML
    private Label detailLabel;
    @FXML
    private StackPane detailContainer;
    private StyleClassedTextArea detailCodeArea;
    private String currentRawLogContent;

    // Layout State Variables
    private double lastVerticalDividerPos = 0.7;
    private double lastHorizontalDividerPos = 0.2;

    @FXML
    private ToggleButton prettifyJsonButton;
    @FXML
    private ToggleButton prettifyXmlButton;
    @FXML
    private Button copyButton;
    @FXML
    private Button clearDetailButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label memoryStatusLabel;
    @FXML
    private ProgressBar memoryBar;

    // Services and Data
    private ParsingConfigService parsingConfigService;
    private RecentFileService recentFileService;
    private LogParser logParserService;
    private LogFileService logFileService;
    private ServerManagementService serverManagementService;
    private PreferenceService preferenceService;

    // Tail Service
    private TailService tailService;

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private LogFile currentLogFromDb;

    private volatile ParsingConfig currentParsingConfig;
    private File currentFile;
    private boolean isLeftPanelPinned = true;

    /**
     * Snapshot of the last opened session (local or remote) so that
     * "clear → refresh" can reload it.  Replaced atomically whenever a
     * new file/tail is opened; never partially mutated.
     */
    private static class LastSession {
        enum Type { LOCAL, REMOTE }
        final Type type;
        final File localFile;            // non-null for LOCAL
        final String remotePath;         // non-null for REMOTE
        final LogFile logFromDb;         // non-null for REMOTE
        final SSHServiceImpl sshService; // non-null for REMOTE

        static LastSession local(File file) {
            return new LastSession(Type.LOCAL, file, null, null, null);
        }
        static LastSession remote(String path, LogFile logFile, SSHServiceImpl ssh) {
            return new LastSession(Type.REMOTE, null, path, logFile, ssh);
        }
        private LastSession(Type t, File f, String rp, LogFile lf, SSHServiceImpl ssh) {
            this.type = t; this.localFile = f; this.remotePath = rp;
            this.logFromDb = lf; this.sshService = ssh;
        }
    }
    private LastSession lastSession;

    private boolean isBottomPanelPinned = true;
    private Task<?> currentLoadingTask = null;
    private final java.util.concurrent.atomic.AtomicLong downloadGeneration = new java.util.concurrent.atomic.AtomicLong();
    private static final int MAX_TAIL_BUFFER_SIZE = 5000;
    private int sshDownloadThreads = 4;
    private String sshDownloadDirectory = ""; // Custom download dir, empty = system temp
    private int tailWindowSize = 20000;
    private boolean tailModeEnabled = false;
    private SSHServiceImpl activeTailSshService;
    private volatile SSHServiceImpl activeDownloadSshService;
    private volatile File activeDownloadFile;
    private long remoteTailLineCounter = 0;
    private String monitoringRemotePath;
    private final Map<String, File> downloadedRemoteFiles = new java.util.concurrent.ConcurrentHashMap<>();

    // state
    private final java.util.concurrent.atomic.AtomicBoolean tailFlushScheduled = new java.util.concurrent.atomic.AtomicBoolean(
            false);
    private boolean tailColumnsAutoResized = false;
    private boolean isProgrammaticUpdate = false; // Prevent listener loops

    private boolean autoPrettifyJson = false;
    private boolean autoPrettifyXml = false;
    private Predicate<LogEntry> currentTailFilterPredicate = null;
    private Pattern currentTailSearchPattern = null; // Compiled pattern for incremental tail search
    private Predicate<String> currentTailSearchPredicate = null;
    private int tailSearchScannedUpTo = 0; // liveTailList index up to which search has been performed
    private boolean isSkippingFilterTrigger = false;

    private int totalEntries = 0;
    private IntArrayList filteredIndexes = null;

    private static final ForkJoinPool SEARCH_POOL = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    private CanvasLogViewer canvasLogViewer;
    private MappedFileReader mappedFileReader;
    private LineOffsetIndex lineOffsetIndex;
    private int currentMatchIndex = -1;
    private PauseTransition searchDebounce;
    private Task<?> currentSearchTask;
    private volatile long searchGeneration = 0;
    private String lastAppliedSearchQuery = null;
    private boolean lastAppliedRegex = false;
    private boolean lastAppliedCaseSensitive = false;
    private com.seeloggyplus.ui.search.SearchResultPanel searchResultPanel;
    private SplitPane searchSplitPane;

    // Persistent buffer for live tailing (CanvasLogViewer reads this)
    private List<LogEntry> liveTailList = Collections.synchronizedList(new ArrayList<>());

    @FXML
    private ToggleButton followTailButton;

    private final LinkedList<LogEntry> tailBuffer = new LinkedList<>(); // Temp transfer buffer

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");

        parsingConfigService = new ParsingConfigServiceImpl();
        recentFileService = new RecentConfigServiceImpl();
        preferenceService = new PreferenceServiceImpl();
        logParserService = new LogParserServiceImpl();
        logFileService = new LogFileServiceImpl();
        serverManagementService = new ServerManagementServiceImpl();
        tailService = new TailServiceImpl();

        setupMenuBar();
        setupLeftPanel();
        setupCenterPanel();
        setupBottomPanel();
        setupKeyboardShortcuts();
        setupSearchFieldAutoCompletion();

        // Helper to update style based on state
        configureToggleStyle(toggleLeftPanelButton);
        configureToggleStyle(toggleBottomPanelButton);
        configureToggleStyle(tailButton);
        configureToggleStyle(followTailButton);
        configureToggleStyle(regexCheckBox);
        configureToggleStyle(caseSensitiveCheckBox);
        configureToggleStyle(prettifyJsonButton);
        configureToggleStyle(prettifyXmlButton);

        // Bind Toolbar Toggles to Actions
        if (toggleLeftPanelButton != null) {
            toggleLeftPanelButton.setOnAction(e -> toggleLeftPanel());
        }
        if (toggleBottomPanelButton != null) {
            toggleBottomPanelButton.setOnAction(e -> toggleBottomPanel());
        }

        restorePanelVisibility();
        loadPreferences();

        updateTailButtonState();
        startMemoryMonitor();

        if (centerRoot != null && centerClip != null) {
            centerClip.widthProperty().bind(centerRoot.widthProperty());
            centerClip.heightProperty().bind(centerRoot.heightProperty());
        }
    }

    private static double clampDivider(double v) {
        return Math.max(0.05, Math.min(0.95, v));
    }

    private void updateTailButtonState() {
        boolean isRemote = (currentLogFromDb != null && currentLogFromDb.isRemote())
                || (currentSession != null && currentSession.getSessionType() == LogSession.SessionType.REMOTE);
        boolean isLocal = (currentFile != null && currentFile.exists())
                || (currentSession != null && currentSession.getSessionType() == LogSession.SessionType.LOCAL);

        if (isRemote || isLocal) {
            tailButton.setDisable(false);
            if (followTailButton != null) {
                followTailButton.setDisable(false);
            }
        } else {
            tailButton.setDisable(true);
            if (followTailButton != null) {
                followTailButton.setDisable(true);
                followTailButton.setSelected(false);
            }
            if (tailModeEnabled) {
                disableTail();
            }
        }
    }

    private PauseTransition loadingDelay; // Delay before showing loading overlay to avoid flicker

    private void showLoading(String message) {
        if (loadingOverlay == null) return;

        loadingLabel.setText(message);
        if (loadingProgress != null) {
            loadingProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        }

        // If overlay is already visible (long operation with progress updates), just update text
        if (loadingOverlay.isVisible()) return;

        // Delay showing the overlay to avoid flicker on fast operations
        if (loadingDelay == null) {
            loadingDelay = new PauseTransition(Duration.millis(350));
            loadingDelay.setOnFinished(e -> {
                loadingOverlay.setVisible(true);
                loadingOverlay.setManaged(true);
            });
        }
        loadingDelay.playFromStart();
    }

    private void hideLoading() {
        // Cancel pending show — operation finished before delay elapsed
        if (loadingDelay != null) {
            loadingDelay.stop();
        }
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(false);
            loadingOverlay.setManaged(false);
        }
    }

    private void setupMenuBar() {
        exitMenuItem.setOnAction(e -> handleExit());
        if (closeTabMenuItem != null) {
            closeTabMenuItem.setOnAction(e -> handleCloseCurrentTab());
        }
        if (closeAllTabsMenuItem != null) {
            closeAllTabsMenuItem.setOnAction(e -> handleCloseAllTabs());
        }

        showLeftPanelMenuItem.setSelected(true);
        showBottomPanelMenuItem.setSelected(true);
        showLeftPanelMenuItem.setOnAction(e -> toggleLeftPanel());
        showBottomPanelMenuItem.setOnAction(e -> toggleBottomPanel());

        serverManagementMenuItem.setOnAction(e -> handleServerManagement());
        preferencesMenuItem.setOnAction(e -> handlePreferences());

        aboutMenuItem.setOnAction(e -> handleAbout());
        helpContentsMenuItem.setOnAction(e -> handleHelpContents());

        menuBar.setMinHeight(Region.USE_PREF_SIZE);
    }

    private void setupLeftPanel() {
        ContextMenu leftPanelContextMenu = new ContextMenu();

        MenuItem openFileMenuItem = new MenuItem("Open File");
        openFileMenuItem.setOnAction(actionEvent -> {
            RecentFilesDto selected = recentFilesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleRecentFileSelected(selected);
            }
        });

        MenuItem deleteFromRecentMenuItem = new MenuItem("Delete from Recent");
        deleteFromRecentMenuItem.setOnAction(actionEvent -> {
            ObservableList<RecentFilesDto> selected = recentFilesListView.getSelectionModel().getSelectedItems();
            if (selected != null && !selected.isEmpty()) {
                handleClearRecentFiles();
            }
        });

        leftPanelContextMenu.getItems().addAll(openFileMenuItem, new SeparatorMenuItem(), deleteFromRecentMenuItem);
        recentFilesListView.setCellFactory(
                listView -> new RecentFileListCell(serverManagementService, () -> monitoringRemotePath));
        recentFilesListView.getStyleClass().add("recent-files-list");
        allRecentFiles.setAll(recentFileService.findAll());
        filteredRecentFiles = new FilteredList<>(allRecentFiles, p -> true);
        recentFilesListView.setItems(filteredRecentFiles);
        recentFilesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        recentFilesListView.setContextMenu(leftPanelContextMenu);

        if (recentFilesFilterField != null) {
            recentFilesFilterField.textProperty().addListener((obs, oldVal, newVal) -> {
                filteredRecentFiles.setPredicate(dto -> {
                    if (newVal == null || newVal.isBlank()) return true;
                    String filter = newVal.toLowerCase().trim();
                    LogFile file = dto.logFile();
                    if (file == null) return false;
                    boolean matchesName = file.getName() != null && file.getName().toLowerCase().contains(filter);
                    boolean matchesPath = file.getFilePath() != null && file.getFilePath().toLowerCase().contains(filter);
                    boolean matchesServer = false;
                    if (file.isRemote() && file.getSshServerID() != null) {
                        try {
                            SSHServerModel server = serverManagementService.getServerById(file.getSshServerID());
                            if (server != null && server.getName() != null && server.getName().toLowerCase().contains(filter)) {
                                matchesServer = true;
                            }
                        } catch (Exception ignored) {}
                    }
                    return matchesName || matchesPath || matchesServer;
                });
            });
        }

        recentFilesListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                RecentFilesDto selected = recentFilesListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    handleRecentFileSelected(selected);
                }
            }
        });

        clearRecentButton.setOnAction(e -> handleClearRecentFiles());
        pinLeftPanelButton.setOnAction(e -> handleToggleLeftPanelPin());
        updateLeftPanelDisplay();
    }

    private void handleToggleLeftPanelPin() {
        toggleLeftPanel();
    }

    private void updateLeftPanelDisplay() {
        if (horizontalSplitPane == null || leftPanel == null) {
            return;
        }

        if (isLeftPanelPinned) {
            if (!horizontalSplitPane.getItems().contains(leftPanel)) {
                horizontalSplitPane.getItems().add(0, leftPanel);
            }
            leftPanel.setVisible(true);
            leftPanel.setManaged(true);

            double pos = lastHorizontalDividerPos > 0 ? lastHorizontalDividerPos : 0.2;
            horizontalSplitPane.setDividerPositions(clampDivider(pos));
            Platform.runLater(() -> horizontalSplitPane.setDividerPositions(clampDivider(pos)));

            if (toggleLeftPanelButton != null) {
                toggleLeftPanelButton.setSelected(true);
            }
        } else {
            if (horizontalSplitPane.getDividerPositions().length > 0) {
                lastHorizontalDividerPos = horizontalSplitPane.getDividerPositions()[0];
            }

            // Pure native JavaFX: panel is completely closed
            horizontalSplitPane.getItems().remove(leftPanel);
            leftPanel.setVisible(false);
            leftPanel.setManaged(false);

            if (toggleLeftPanelButton != null) {
                toggleLeftPanelButton.setSelected(false);
            }
        }

        if (showLeftPanelMenuItem != null) {
            showLeftPanelMenuItem.setSelected(isLeftPanelPinned);
        }
    }

    /**
     * Selects the given file in the recent files list view.
     * This improves UX by keeping the current file highlighted.
     */
    private void selectRecentFile(File file) {
        if (file == null) return;
        selectRecentFile(file.getAbsolutePath(), null, false);
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        return path.trim().replace('\\', '/');
    }

    /**
     * Selects the recent file entry matching the active session.
     * Ensures reliable selection for both local and remote files.
     */
    void selectRecentFileForSession(LogSession session) {
        if (session == null || recentFilesListView == null) {
            if (recentFilesListView != null) {
                Platform.runLater(() -> recentFilesListView.getSelectionModel().clearSelection());
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

        if (targetPath == null) return;

        final String finalPath = targetPath;
        final String finalServerId = serverId;
        final boolean finalIsRemote = isRemote;
        final String recordId = logRecord != null ? logRecord.getId() : null;

        Platform.runLater(() -> {
            recentFilesListView.getSelectionModel().clearSelection();
            for (RecentFilesDto dto : recentFilesListView.getItems()) {
                if (dto != null && dto.logFile() != null) {
                    LogFile lf = dto.logFile();
                    // 1. Match by DB ID if available
                    if (recordId != null && recordId.equals(lf.getId())) {
                        recentFilesListView.getSelectionModel().select(dto);
                        recentFilesListView.scrollTo(dto);
                        return;
                    }
                    // 2. Match by path & remote status
                    if (lf.isRemote() == finalIsRemote) {
                        if (finalIsRemote) {
                            if (Objects.equals(finalServerId, lf.getSshServerID())
                                    && normalizePath(finalPath).equals(normalizePath(lf.getFilePath()))) {
                                recentFilesListView.getSelectionModel().select(dto);
                                recentFilesListView.scrollTo(dto);
                                return;
                            }
                        } else {
                            if (normalizePath(finalPath).equalsIgnoreCase(normalizePath(lf.getFilePath()))) {
                                recentFilesListView.getSelectionModel().select(dto);
                                recentFilesListView.scrollTo(dto);
                                return;
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Selects the recent file entry matching the given path, server ID, and remote status.
     */
    private void selectRecentFile(String targetPath, String sshServerId, boolean isRemote) {
        if (targetPath == null || recentFilesListView == null) {
            return;
        }
        Platform.runLater(() -> {
            recentFilesListView.getSelectionModel().clearSelection();
            for (RecentFilesDto dto : recentFilesListView.getItems()) {
                if (dto != null && dto.logFile() != null) {
                    LogFile lf = dto.logFile();
                    if (lf.isRemote() == isRemote) {
                        if (isRemote) {
                            if (Objects.equals(sshServerId, lf.getSshServerID())
                                    && normalizePath(targetPath).equals(normalizePath(lf.getFilePath()))) {
                                recentFilesListView.getSelectionModel().select(dto);
                                recentFilesListView.scrollTo(dto);
                                return;
                            }
                        } else {
                            if (normalizePath(targetPath).equalsIgnoreCase(normalizePath(lf.getFilePath()))) {
                                recentFilesListView.getSelectionModel().select(dto);
                                recentFilesListView.scrollTo(dto);
                                return;
                            }
                        }
                    }
                }
            }
        });
    }

    private void selectRecentFileByPath(String targetPath) {
        selectRecentFile(targetPath, null, false);
    }

    /**
     * Updates the status bar line counter with accurate line metrics for the session.
     */
    void updateBottomBarLineCount(LogSession session) {
        if (statusLabel == null) return;
        if (session == null || session.getCanvasLogViewer() == null) {
            statusLabel.setText("Line: 0 / 0");
            return;
        }
        CanvasLogViewer viewer = session.getCanvasLogViewer();
        long total = viewer.getTotalLines();
        if (total <= 0) {
            statusLabel.setText("Line: 0 / 0");
            return;
        }
        long cur = Math.min(viewer.getCurrentTopLine() + 1, total);
        double pct = (total > 0) ? (viewer.getCurrentTopLine() * 100.0 / total) : 0.0;
        statusLabel.setText(String.format("Line: %,d / %,d (%.1f%%)", cur, total, pct));
    }

    private static final String KEYWORDS_REGEX = "(?i)(ERROR|FATAL|EXCEPTION|WARN|INFO|DEBUG|TRACE)";
    private static final Pattern KEYWORDS_PATTERN = Pattern.compile(KEYWORDS_REGEX);

    private void setupCenterPanel() {
        if (followTailButton != null) {
            followTailButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (isProgrammaticUpdate) return;
                if (canvasLogViewer != null) {
                    canvasLogViewer.setFollowTail(newVal);
                }
                if (currentSession != null) {
                    currentSession.setFollowTail(newVal);
                }
            });
        }

        if (logTabPane != null) {
            logTabPane.setStyle("-fx-open-tab-animation: NONE; -fx-close-tab-animation: NONE;");
            logTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                onTabSelected(oldTab, newTab);
            });

            // Intercept UP/DOWN/PAGE_UP/PAGE_DOWN/HOME/END keys so TabPane never changes tabs on arrow keys,
            // and instead routes line scrolling/navigation directly to the log canvas viewer.
            logTabPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                KeyCode code = event.getCode();
                if (code == KeyCode.UP || code == KeyCode.DOWN || code == KeyCode.PAGE_UP || code == KeyCode.PAGE_DOWN
                        || ((code == KeyCode.HOME || code == KeyCode.END) && event.isControlDown())) {
                    event.consume();
                    if (canvasLogViewer != null) {
                        canvasLogViewer.handleKeyNavigation(event);
                        canvasLogViewer.requestCanvasFocus();
                    }
                }
            });

            logTabPane.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.MIDDLE) {
                    javafx.scene.Node target = (javafx.scene.Node) event.getTarget();
                    while (target != null && target != logTabPane) {
                        if (target.getStyleClass().contains("tab")) {
                            for (Tab t : logTabPane.getTabs()) {
                                if (t.getGraphic() != null && isNodeRelated(target, t.getGraphic())) {
                                    LogSession s = sessionMap.get(t);
                                    if (s != null) closeSession(s);
                                    event.consume();
                                    return;
                                }
                            }
                            Tab selected = logTabPane.getSelectionModel().getSelectedItem();
                            if (selected != null) {
                                LogSession s = sessionMap.get(selected);
                                if (s != null) closeSession(s);
                            }
                            event.consume();
                            return;
                        }
                        target = target.getParent();
                    }
                } else if (event.getButton() == MouseButton.PRIMARY) {
                    Platform.runLater(() -> {
                        if (canvasLogViewer != null) {
                            canvasLogViewer.requestCanvasFocus();
                        }
                    });
                }
            });
        }

        // Search Result Panel — shown to the right of the log viewer when search is active
        searchResultPanel = new com.seeloggyplus.ui.search.SearchResultPanel();
        searchResultPanel.setOnLineSelected((listIndex, globalLine, content) -> {
            if (canvasLogViewer != null) {
                canvasLogViewer.jumpToLine(globalLine);
                canvasLogViewer.selectLine(globalLine);
            }
            displayLogDetailFromCanvas(globalLine, content);
            // Sync navigator status with panel click
            if (filteredIndexes != null && searchNavigator != null) {
                currentMatchIndex = listIndex + 1;
                searchNavigator.updateStatus(listIndex + 1, filteredIndexes.size());
            }
        });

        // Wrap logContainer + searchResultPanel in a horizontal SplitPane
        VBox logParent = (VBox) logContainer.getParent();
        int logIdx = logParent.getChildren().indexOf(logContainer);
        SplitPane searchSplit = new SplitPane();
        searchSplit.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        searchSplit.setMinSize(0, 0);
        searchSplit.getItems().add(logContainer);
        // searchResultPanel is added/removed dynamically when search results appear/clear
        VBox.setVgrow(searchSplit, Priority.ALWAYS);
        logParent.getChildren().set(logIdx, searchSplit);
        logParent.setMinHeight(0);
        this.searchSplitPane = searchSplit;

        setupDragAndDrop();
        updateEmptyState();

        setupDetailPanel();

        searchField.setOnAction(e -> {
            boolean querySame = java.util.Objects.equals(searchField.getText(), lastAppliedSearchQuery)
                    && (regexCheckBox != null && regexCheckBox.isSelected() == lastAppliedRegex)
                    && (caseSensitiveCheckBox != null && caseSensitiveCheckBox.isSelected() == lastAppliedCaseSensitive);
            if (querySame && filteredIndexes != null && !filteredIndexes.isEmpty()) {
                // Results already showing for this exact query — Enter navigates to next match
                navigateNextMatch();
            } else {
                performSearch();
            }
        });
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                clearSearch();
            } else if (event.getCode() == KeyCode.ENTER && event.isShiftDown()) {
                navigatePreviousMatch();
                event.consume();
            }
        });

        // Incremental search: debounce 300ms after typing
        searchDebounce = new PauseTransition(Duration.millis(300));
        searchDebounce.setOnFinished(evt -> performSearch());
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (isSkippingFilterTrigger) {
                return;
            }
            if (newText != null && !newText.isEmpty()) {
                searchDebounce.playFromStart();
            } else {
                searchDebounce.stop();
                // Only clear if there was actually a previous search
                if (oldText != null && !oldText.isEmpty()) {
                    clearSearch();
                }
            }
        });
        searchField.setTooltip(new Tooltip("Enter text to search. Press Ctrl+F to focus this field."));
        searchButton.setOnAction(e -> performSearch());
        searchButton.setTooltip(new Tooltip("Perform search (press Enter in text field)"));
        clearSearchButton.setOnAction(e -> clearSearch());
        clearSearchButton.setTooltip(new Tooltip("Clear search and filters (press Escape in text field)"));
        refreshButton.setOnAction(e -> handleReload());
        clearLogButton.setOnAction(e -> handleClearLog());
        refreshButton.setTooltip(new Tooltip("Reload current file or tailing session (Ctrl+R)"));

        if (searchNavigator != null) {
            searchNavigator.setOnNext(this::navigateNextMatch);
            searchNavigator.setOnPrevious(this::navigatePreviousMatch);
        }

        refreshButton.setTooltip(new Tooltip("Reload current file or tailing session (Ctrl+R)"));

        tailButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (isProgrammaticUpdate)
                return;
            if (newVal) {
                enableTail();
            } else {
                disableTail();
            }
        });

        regexCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (!isSkippingFilterTrigger) {
                performSearch();
            }
        });

        caseSensitiveCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (!isSkippingFilterTrigger) {
                performSearch();
            }
        });

        logger.info("Canvas-based log viewer initialized.");
    }

    private void configureToggleStyle(ToggleButton button) {
        if (button == null) {
            return;
        }

        applyToggleStyle(button, button.isSelected());

        button.selectedProperty().addListener((obs, oldVal, newVal) -> {
            applyToggleStyle(button, newVal);
        });
    }

    private void applyToggleStyle(ToggleButton button, boolean selected) {
        if (selected) {
            button.setStyle(TOGGLE_SELECTED_STYLE);
        } else {
            button.setStyle(TOGGLE_DESELECTED_STYLE);
        }
        updateToggleIconColor(button, selected);
    }

    private void updateToggleIconColor(ToggleButton button, boolean selected) {
        if (button == null) return;
        Node graphic = button.getGraphic();
        if (graphic instanceof FontAwesomeIconView icon) {
            icon.setFill(selected ? Color.WHITE : Color.web("#333333"));
        } else if (graphic instanceof javafx.scene.shape.Shape shape) {
            shape.setFill(selected ? Color.WHITE : Color.web("#333333"));
        }
    }

    private void normalizeLayoutState() {
        Platform.runLater(() -> {
            try {
                updateLeftPanelDisplay();
                updateBottomPanelDisplay();
            } catch (Exception ex) {
                logger.warn("normalizeLayoutState failed", ex);
            }
        });
    }

    private void displayLogDetailFromCanvas(long lineNumber, String content) {
        if (detailCodeArea != null && content != null) {
            if (detailPlaceholderLabel != null) {
                detailPlaceholderLabel.setVisible(false);
                detailPlaceholderLabel.setManaged(false);
            }
            this.currentRawLogContent = content;
            detailCodeArea.clear();
            detailCodeArea.replaceText(0, 0, content);
            detailLabel.setText("Line " + (lineNumber + 1));

            boolean prettified = false;
            if (autoPrettifyJson) {
                prettified = prettifyJson();
            }
            if (!prettified && autoPrettifyXml) {
                prettifyXml();
            }
        }
    }

    private void performSearch() {
        final String searchText = searchField.getText();
        final boolean isRegex = regexCheckBox.isSelected();
        final boolean caseSensitive = caseSensitiveCheckBox.isSelected();

        logger.info("Search - Include: '{}', Regex: {}, CaseSensitive: {}",
                searchText, isRegex, caseSensitive);

        lastAppliedSearchQuery = searchText;
        lastAppliedRegex = isRegex;
        lastAppliedCaseSensitive = caseSensitive;

        if ((mappedFileReader == null || lineOffsetIndex == null) && !tailModeEnabled) {
            logger.warn("No file loaded for search");
            return;
        }

        if (searchText == null || searchText.trim().isEmpty()) {
            clearSearch();
            return;
        }

        if (tailModeEnabled) {
            // TAIL MODE: Filtered lines + Highlight
            String highlightPattern = searchText;
            boolean highlightIsRegex = isRegex;

            if (!isRegex) {
                List<String> terms = extractSearchTerms(searchText);
                if (!terms.isEmpty()) {
                    highlightPattern = String.join("|", terms.stream()
                            .map(java.util.regex.Pattern::quote)
                            .toList());
                    highlightIsRegex = true;
                }
            }

            final String finalHighlightPattern = highlightPattern;
            final boolean finalHighlightIsRegex = highlightIsRegex;

            // Compile the search pattern for matching against tail buffer lines
            final Pattern matchPattern;
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                matchPattern = Pattern.compile(
                        finalHighlightIsRegex ? finalHighlightPattern : Pattern.quote(finalHighlightPattern), flags);
            } catch (Exception ex) {
                logger.warn("Invalid search pattern for tail mode", ex);
                return;
            }

            if (canvasLogViewer != null) {
                canvasLogViewer.setSearchHighlight(finalHighlightPattern, finalHighlightIsRegex, caseSensitive);
                // Pause follow-tail so user can navigate search results
                if (canvasLogViewer.isFollowTail()) {
                    canvasLogViewer.setFollowTail(false);
                }
            }

            final long tailOffset = canvasLogViewer != null ? canvasLogViewer.getFileLineCount() : 0;
            final MappedFileReader reader = mappedFileReader;
            final LineOffsetIndex index = lineOffsetIndex;
            final Pattern pattern = matchPattern;
            final CanvasLogViewer viewer = canvasLogViewer;
            final List<LogEntry> snapshot;
            synchronized (liveTailList) {
                snapshot = new ArrayList<>(liveTailList);
            }
            final int snapshotSize = snapshot.size();

            final Predicate<String> searchPredicate;
            if (isRegex) {
                searchPredicate = s -> pattern.matcher(s).find();
            } else {
                searchPredicate = createBooleanSearchPredicate(searchText, caseSensitive);
            }

            // Offload file and tail scanning to background worker to eliminate UI freeze
            SEARCH_POOL.submit(() -> {
                IntArrayList results = new IntArrayList();
                if (reader != null && index != null && tailOffset > 0) {
                    int fileLines = index.getLineCount();
                    for (int i = 0; i < fileLines; i++) {
                        if (Thread.currentThread().isInterrupted()) return;
                        CharSequence line = reader.readLine(index, i);
                        if (line != null && searchPredicate.test(line.toString())) {
                            results.add(i);
                        }
                    }
                    logger.info("Tail search: {} matches in file lines (0..{})", results.size(), fileLines);
                }

                for (int i = 0; i < snapshot.size(); i++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    LogEntry entry = snapshot.get(i);
                    String raw = entry != null ? entry.getRawLog() : "";
                    if (searchPredicate.test(raw)) {
                        results.add((int) (tailOffset + i));
                    }
                }

                Platform.runLater(() -> {
                    this.filteredIndexes = results;
                    this.currentTailSearchPattern = pattern;
                    this.currentTailSearchPredicate = searchPredicate;
                    this.tailSearchScannedUpTo = snapshotSize;

                    if (currentSession != null) {
                        currentSession.setFilteredIndexes(results);
                        currentSession.setSearchQuery(searchText);
                        currentSession.setRegex(isRegex);
                        currentSession.setCaseSensitive(caseSensitive);
                        currentSession.setCurrentTailSearchPattern(pattern);
                        currentSession.setTailSearchScannedUpTo(snapshotSize);
                    }

                    if (viewer != null) {
                        viewer.clearFilter();
                        viewer.setSearchHighlight(finalHighlightPattern, finalHighlightIsRegex, caseSensitive);
                    }

                    if (searchNavigator != null) {
                        searchNavigator.setMatchCount(results.size());
                    }

                    if (results.size() > 0) {
                        showSearchResultPanelForTail(results, pattern);
                        long firstLine = results.get(0);
                        if (viewer != null) {
                            viewer.jumpToLine(firstLine);
                            viewer.selectLine(firstLine);
                        }
                        currentMatchIndex = 1;
                        if (currentSession != null) {
                            currentSession.setCurrentMatchIndex(1);
                        }
                        if (searchNavigator != null) {
                            searchNavigator.updateStatus(1, results.size());
                        }
                        String content = getLineContentForGlobal(firstLine);
                        displayLogDetailFromCanvas(firstLine, content);
                    } else {
                        hideSearchResultPanel();
                        currentMatchIndex = -1;
                        if (currentSession != null) {
                            currentSession.setCurrentMatchIndex(-1);
                        }
                    }
                });
            });

            // Update the predicate for completeness
            try {
                currentTailFilterPredicate = buildSearchPredicate(searchText, isRegex, caseSensitive);
            } catch (Exception ex) {
                logger.warn("Search predicate build failed", ex);
            }

            return;
        }

        showLoading("Searching...");

        // Cancel any in-flight search task to prevent stale results overwriting
        if (currentSearchTask != null && currentSearchTask.isRunning()) {
            currentSearchTask.cancel(true);
        }
        final long thisGeneration = ++searchGeneration;

        Task<IntArrayList> task = new Task<>() {
            @Override
            protected IntArrayList call() {
                int total = lineOffsetIndex.getLineCount();
                final Pattern pattern;
                final Predicate<String> booleanPredicate;

                if (isRegex) {
                    try {
                        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                        pattern = Pattern.compile(searchText, flags);
                    } catch (Exception e) {
                        logger.warn("Invalid regex: {}", searchText);
                        return new IntArrayList();
                    }
                    booleanPredicate = null;
                } else {
                    pattern = null;
                    booleanPredicate = createBooleanSearchPredicate(searchText, caseSensitive);
                }

                int numThreads = Runtime.getRuntime().availableProcessors();
                int chunkSize = Math.max(10000, (total + numThreads - 1) / numThreads);

                logger.info("Parallel search: {} threads, {} lines/chunk, {} total lines", numThreads, chunkSize,
                        total);

                try {
                    List<Future<IntArrayList>> futures = new java.util.ArrayList<>();

                    // Submit chunk tasks
                    for (int start = 0; start < total; start += chunkSize) {
                        final int chunkStart = start;
                        final int chunkEnd = Math.min(start + chunkSize, total);

                        futures.add(SEARCH_POOL.submit(() -> {
                            IntArrayList chunkMatches = new IntArrayList();

                            for (int i = chunkStart; i < chunkEnd; i++) {
                                if (isCancelled())
                                    break;

                                // Zero-allocation search using CharSequence matcher
                                java.util.function.Predicate<CharSequence> matcher;
                                if (pattern != null) {
                                    matcher = cs -> pattern.matcher(cs).find();
                                } else {
                                    matcher = cs -> booleanPredicate.test(cs.toString());
                                }

                                if (mappedFileReader.lineMatches(lineOffsetIndex, i, matcher)) {
                                    chunkMatches.add(i);
                                }
                            }

                            return chunkMatches;
                        }));
                    }

                    // Collect results with progress
                    IntArrayList allMatches = new IntArrayList();
                    int completedChunks = 0;

                    for (var future : futures) {
                        IntArrayList chunkResult = future.get();

                        // Merge sorted (chunks are already in order)
                        for (int i = 0; i < chunkResult.size(); i++) {
                            allMatches.add(chunkResult.get(i));
                        }

                        completedChunks++;
                        final int progress = completedChunks;
                        final int totalChunks = futures.size();
                        Platform.runLater(
                                () -> showLoading(String.format("Searching: %d/%d chunks...", progress, totalChunks)));
                    }

                    return allMatches;

                } catch (Exception e) {
                    logger.error("Parallel search error", e);
                    return new IntArrayList();
                }
                // Note: SEARCH_POOL is shared, do NOT shut down
            }
        };

        task.setOnSucceeded(e -> {
            // Discard stale results — a newer search was already started
            if (thisGeneration != searchGeneration) {
                logger.info("Discarding stale search results (gen {} vs current {})", thisGeneration, searchGeneration);
                return;
            }

            IntArrayList matches = task.getValue();
            logger.info("Search complete: {} matches found", matches.size());

            filteredIndexes = matches;
            if (canvasLogViewer != null) {
                canvasLogViewer.clearFilter();
            }
            if (currentSession != null) {
                currentSession.setFilteredIndexes(matches);
                currentSession.setSearchQuery(searchText);
                currentSession.setRegex(isRegex);
                currentSession.setCaseSensitive(caseSensitive);
            }

            // Enhanced Highlight Logic for Boolean Search
            String highlightPattern = searchText;
            boolean highlightIsRegex = isRegex;

            if (!isRegex) {
                List<String> terms = extractSearchTerms(searchText);
                if (!terms.isEmpty()) {
                    highlightPattern = terms.stream()
                            .map(java.util.regex.Pattern::quote)
                            .collect(java.util.stream.Collectors.joining("|"));
                    highlightIsRegex = true;
                }
            }

            if (canvasLogViewer != null) {
                canvasLogViewer.setSearchHighlight(highlightPattern, highlightIsRegex, caseSensitive);
            }

            if (searchNavigator != null) {
                searchNavigator.setMatchCount(matches.size());
            }

            if (matches.size() > 0) {
                Pattern compiledHighlight = null;
                try {
                    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    compiledHighlight = Pattern.compile(
                            highlightIsRegex ? highlightPattern : Pattern.quote(highlightPattern), flags);
                } catch (Exception ignored) {}
                showSearchResultPanel(matches, compiledHighlight);

                int firstLine = matches.get(0);
                if (canvasLogViewer != null) {
                    canvasLogViewer.jumpToLine(firstLine);
                    canvasLogViewer.selectLine(firstLine);
                }
                currentMatchIndex = 1;
                if (currentSession != null) {
                    currentSession.setCurrentMatchIndex(1);
                }
                if (searchNavigator != null) {
                    searchNavigator.updateStatus(1, matches.size());
                }
                if (mappedFileReader != null && lineOffsetIndex != null) {
                    String content = mappedFileReader.readLine(lineOffsetIndex, firstLine);
                    displayLogDetailFromCanvas(firstLine, content);
                }
            } else {
                hideSearchResultPanel();
                currentMatchIndex = -1;
                if (currentSession != null) {
                    currentSession.setCurrentMatchIndex(-1);
                }
            }

            hideLoading();
        });

        task.setOnFailed(e -> {
            if (thisGeneration != searchGeneration) return;
            hideLoading();
            Throwable ex = task.getException();
            logger.error("Search failed", ex);
            showError("Search Failed", ex.getMessage());
        });

        currentSearchTask = task;
        Thread.ofVirtual().start(task);
    }

    private void navigateNextMatch() {
        // Unified navigation using filteredIndexes for both local and tail mode
        if (filteredIndexes == null || filteredIndexes.size() == 0) return;
        int total = filteredIndexes.size();

        if (currentMatchIndex < 0) currentMatchIndex = 0;
        int nextIdx = currentMatchIndex; // 0-based index into filteredIndexes
        if (nextIdx >= total) nextIdx = 0; // wrap

        int globalLine = filteredIndexes.get(nextIdx);
        if (canvasLogViewer != null) {
            canvasLogViewer.jumpToLine(globalLine);
            canvasLogViewer.selectLine(globalLine);
        }
        currentMatchIndex = nextIdx + 1; // advance for next call
        if (currentSession != null) {
            currentSession.setCurrentMatchIndex(currentMatchIndex);
        }
        if (searchNavigator != null) {
            searchNavigator.updateStatus(nextIdx + 1, total);
        }
        if (searchResultPanel != null) {
            searchResultPanel.selectIndex(nextIdx);
        }
        String content = getLineContentForGlobal(globalLine);
        displayLogDetailFromCanvas(globalLine, content);
    }

    private void navigatePreviousMatch() {
        // Unified navigation using filteredIndexes for both local and tail mode
        if (filteredIndexes == null || filteredIndexes.size() == 0) return;
        int total = filteredIndexes.size();

        int prevIdx = currentMatchIndex - 2; // currentMatchIndex is 1-based "next to visit"
        if (prevIdx < 0) prevIdx = total - 1; // wrap

        int globalLine = filteredIndexes.get(prevIdx);
        if (canvasLogViewer != null) {
            canvasLogViewer.jumpToLine(globalLine);
            canvasLogViewer.selectLine(globalLine);
        }
        currentMatchIndex = prevIdx + 1;
        if (currentSession != null) {
            currentSession.setCurrentMatchIndex(currentMatchIndex);
        }
        if (searchNavigator != null) {
            searchNavigator.updateStatus(prevIdx + 1, total);
        }
        if (searchResultPanel != null) {
            searchResultPanel.selectIndex(prevIdx);
        }
        String content = getLineContentForGlobal(globalLine);
        displayLogDetailFromCanvas(globalLine, content);
    }

    private void clearSearch() {
        searchField.clear();
        filteredIndexes = null;
        currentMatchIndex = -1;
        currentTailSearchPattern = null;
        currentTailSearchPredicate = null;
        tailSearchScannedUpTo = 0;
        lastAppliedSearchQuery = null;
        // Invalidate any in-flight search tasks
        searchGeneration++;
        if (currentSearchTask != null && currentSearchTask.isRunning()) {
            currentSearchTask.cancel(true);
        }
        if (currentSession != null) {
            currentSession.setFilteredIndexes(null);
            currentSession.setSearchQuery("");
            currentSession.setCurrentMatchIndex(-1);
            currentSession.setCurrentTailSearchPattern(null);
            currentSession.setTailSearchScannedUpTo(0);
        }
        if (canvasLogViewer != null) {
            canvasLogViewer.clearFilter();
            canvasLogViewer.clearSearchHighlight();
            // Restore follow-tail when clearing search in tail mode
            if (tailModeEnabled && !canvasLogViewer.isFollowTail()) {
                canvasLogViewer.setFollowTail(true);
            }
        }
        if (searchNavigator != null) {
            searchNavigator.clear();
        }
        hideSearchResultPanel();
    }

    private String getLineContentForGlobal(long globalLine) {
        if (tailModeEnabled) {
            long fileLineCount = canvasLogViewer != null ? canvasLogViewer.getFileLineCount() : 0;
            if (globalLine < fileLineCount) {
                if (mappedFileReader != null && lineOffsetIndex != null && globalLine < lineOffsetIndex.getLineCount()) {
                    return mappedFileReader.readLine(lineOffsetIndex, (int) globalLine);
                }
            } else {
                int tailIdx = (int) (globalLine - fileLineCount);
                synchronized (liveTailList) {
                    if (tailIdx >= 0 && tailIdx < liveTailList.size()) {
                        LogEntry entry = liveTailList.get(tailIdx);
                        return entry != null ? entry.getRawLog() : "";
                    }
                }
            }
            return "";
        } else {
            if (mappedFileReader != null && lineOffsetIndex != null && globalLine < lineOffsetIndex.getLineCount()) {
                return mappedFileReader.readLine(lineOffsetIndex, (int) globalLine);
            }
            return "";
        }
    }

    private void showSearchResultPanel(IntArrayList matches, Pattern highlightPattern) {
        if (searchResultPanel == null || searchSplitPane == null) return;
        // Set resolver so panel can lazily fetch line content
        searchResultPanel.setLineContentResolver(idx -> {
            if (mappedFileReader != null && lineOffsetIndex != null && idx < lineOffsetIndex.getLineCount()) {
                return mappedFileReader.readLine(lineOffsetIndex, idx);
            }
            return "";
        });
        searchResultPanel.setSearchPattern(highlightPattern);
        searchResultPanel.showResults(matches, lineOffsetIndex != null ? lineOffsetIndex.getLineCount() : 0);
        if (!searchSplitPane.getItems().contains(searchResultPanel)) {
            searchSplitPane.getItems().add(searchResultPanel);
            Platform.runLater(() -> searchSplitPane.setDividerPositions(0.75));
        }
    }

    private void hideSearchResultPanel() {
        if (searchResultPanel == null || searchSplitPane == null) return;
        searchResultPanel.clear();
        searchSplitPane.getItems().remove(searchResultPanel);
    }

    /**
     * Show search result panel for tail mode. Uses liveTailList as the content source.
     */
    private void showSearchResultPanelForTail(IntArrayList matches, Pattern highlightPattern) {
        if (searchResultPanel == null || searchSplitPane == null) return;
        // Resolver: idx can be a file line (< tailOffset) or tail buffer line (>= tailOffset).
        long tailOffset = canvasLogViewer != null ? canvasLogViewer.getFileLineCount() : 0;
        searchResultPanel.setLineContentResolver(idx -> {
            if (idx < tailOffset) {
                // File line
                if (mappedFileReader != null && lineOffsetIndex != null && idx < lineOffsetIndex.getLineCount()) {
                    return mappedFileReader.readLine(lineOffsetIndex, idx);
                }
            } else {
                // Tail buffer line
                int bufferIdx = (int) (idx - tailOffset);
                if (bufferIdx >= 0 && bufferIdx < liveTailList.size()) {
                    LogEntry entry = liveTailList.get(bufferIdx);
                    return entry != null ? entry.getRawLog() : "";
                }
            }
            return "";
        });
        searchResultPanel.setSearchPattern(highlightPattern);
        long totalLines = tailOffset + liveTailList.size();
        searchResultPanel.showResults(matches, (int) totalLines);
        if (!searchSplitPane.getItems().contains(searchResultPanel)) {
            searchSplitPane.getItems().add(searchResultPanel);
            Platform.runLater(() -> searchSplitPane.setDividerPositions(0.75));
        }
    }

    /**
     * Release mmap, index and filter resources.
     * Call before loading new file, on clear, or on cancel.
     */
    private void deleteCancelledDownloadFiles(File file) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                java.nio.file.Files.deleteIfExists(file.toPath());
                java.nio.file.Files.deleteIfExists(file.toPath().resolveSibling(file.getName() + ".partial"));
                return;
            } catch (IOException e) {
                if (attempt == 4) {
                    logger.warn("Failed to delete cancelled download file after worker shutdown: {}", file, e);
                    return;
                }
                try {
                    Thread.sleep(100L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void cleanupFileResources() {
        if (mappedFileReader != null) {
            mappedFileReader.close();
            mappedFileReader = null;
        }
        lineOffsetIndex = null;
        filteredIndexes = null;
        totalEntries = 0;
        if (canvasLogViewer != null) {
            canvasLogViewer.clearFilter();
            canvasLogViewer.clearSearchHighlight();
        }
        logger.info("File resources cleaned up");
    }

    private void loadFileWithParallelParsing(File file, LogFile logFile, boolean updateRecentFilesList) {
        loadFileWithParallelParsing(file, logFile, updateRecentFilesList, false, true);
    }

    private void loadFileWithParallelParsing(File file, LogFile logFile, boolean updateRecentFilesList,
            boolean jumpToEnd) {
        loadFileWithParallelParsing(file, logFile, updateRecentFilesList, jumpToEnd, true);
    }

    private void loadFileWithParallelParsing(File file, LogFile logFile, boolean updateRecentFilesList,
            boolean jumpToEnd, boolean selectInRecent) {
        if (currentSession != null) {
            loadFileWithParallelParsing(currentSession, file, logFile, updateRecentFilesList, jumpToEnd, selectInRecent);
        }
    }

    private void loadFileWithParallelParsing(LogSession session, File file, LogFile logFile, boolean updateRecentFilesList,
            boolean jumpToEnd, boolean selectInRecent) {
        loadFileWithParallelParsing(session, file, logFile, updateRecentFilesList, jumpToEnd, selectInRecent, false);
    }

    private void loadFileWithParallelParsing(LogSession session, File file, LogFile logFile, boolean updateRecentFilesList,
            boolean jumpToEnd, boolean selectInRecent, boolean startTail) {
        loadFileWithParallelParsing(session, file, logFile, updateRecentFilesList, jumpToEnd, selectInRecent, startTail, 0);
    }

    private void loadFileWithParallelParsing(LogSession session, File file, LogFile logFile, boolean updateRecentFilesList,
            boolean jumpToEnd, boolean selectInRecent, boolean startTail, int targetLine) {
        final int jumpLine = targetLine;
        showLoading("Indexing file: " + file.getName() + " (scanning for lines...)");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                RandomAccessFile raf = new RandomAccessFile(file, "r");
                com.seeloggyplus.util.LineOffsetIndex index = new com.seeloggyplus.util.LineOffsetIndex();

                long fileSize = raf.length();
                index.buildIndex(raf, bytesProcessed -> {
                    double percent = (double) bytesProcessed / fileSize * 100;
                    Platform.runLater(() -> showLoading(
                            String.format("Indexing: %.1f%% (%d lines found)", percent, index.getLineCount())));
                });

                raf.close();
                logger.info("Indexing complete! {} lines indexed in {}", index.getLineCount(), file.getName());
                MappedFileReader oldReader = session.getReader();
                MappedFileReader reader = new com.seeloggyplus.util.MappedFileReader(file);
                session.setIndex(index);
                session.setReader(reader);
                session.setTotalEntries(index.getLineCount());
                if (oldReader != null) {
                    oldReader.close();
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            logger.info("Index complete! {} entries available", session.getTotalEntries());

            Platform.runLater(() -> {
                if (session.isClosed()) {
                    if (session.getReader() != null) {
                        session.getReader().close();
                    }
                    return;
                }
                session.getLiveTailList().clear();
                if (session.getCanvasLogViewer() != null) {
                    session.getCanvasLogViewer().loadFile(session.getReader(), session.getIndex());
                    long total = session.getTotalEntries();
                    boolean jumpRequested = jumpLine > 0 && total > 0;
                    long jumpTarget = jumpRequested
                            ? Math.min(jumpLine - 1L, total - 1)
                            : (jumpToEnd && total > 0 ? total - 1 : 0);
                    session.getCanvasLogViewer().jumpToLine(jumpTarget);
                    if (jumpRequested) {
                        session.getCanvasLogViewer().selectLine(jumpTarget);
                    }
                }

                if (session == currentSession) {
                    mappedFileReader = session.getReader();
                    lineOffsetIndex = session.getIndex();
                    totalEntries = session.getTotalEntries();
                    if (session.getCanvasLogViewer() != null) {
                        session.getCanvasLogViewer().requestCanvasFocus();
                    }
                    updateTailButtonState();
                    updateBottomBarLineCount(session);
                }
                logger.info("Canvas viewer initialized for session: {}", session.getTitle());
            });

            if (updateRecentFilesList) {
                RecentFile recentFile = new RecentFile();
                recentFile.setFileId(logFile.getId());
                recentFile.setLastOpened(LocalDateTime.now());
                recentFileService.save(logFile, recentFile);
                refreshRecentFilesList();
                logger.info("Added file to recent files: {}", file.getName());
            }

            if (selectInRecent) {
                selectRecentFileForSession(session);
            }

            hideLoading();
            updateTailButtonState();
            currentLoadingTask = null;
            logger.info("File loaded with Canvas-based viewer. RAM usage minimal.");

            if (startTail) {
                Platform.runLater(() -> {
                    if (currentSession == session && !session.isTailModeEnabled()) {
                        enableTail();
                    }
                });
            }
        });

        task.setOnFailed(e -> {
            hideLoading();
            Throwable ex = task.getException();
            logger.error("Failed to index file", ex);
            showError("Failed to load file", ex.getMessage());
            currentLoadingTask = null;
        });

        task.setOnCancelled(e -> {
            hideLoading();
            logger.info("Indexing cancelled by user");
            currentLoadingTask = null;
        });

        currentLoadingTask = task;
        Thread.ofVirtual().start(task);
    }

    private void handleReload() {
        logger.info("Reload triggered by user.");

        if (currentSession != null) {
            if (currentSession.getSessionType() == LogSession.SessionType.LOCAL && currentSession.getLocalFile() != null) {
                logger.info("Reloading local file in current tab '{}'.", currentSession.getLocalFile().getName());
                showLoading("Reloading file...");
                loadFileWithParallelParsing(currentSession, currentSession.getLocalFile(), currentSession.getLogFileRecord(), false, false, false);
                return;
            } else if (currentSession.getSessionType() == LogSession.SessionType.REMOTE) {
                if (currentSession.getSshService() != null && currentSession.getRemotePath() != null) {
                    final LogSession targetSession = currentSession;
                    logger.info("Reloading remote tail for session '{}'", targetSession.getTitle());
                    disableTail(targetSession, true);
                    targetSession.getLiveTailList().clear();
                    if (targetSession.getCanvasLogViewer() != null) {
                        targetSession.getCanvasLogViewer().refreshTail();
                    }
                    targetSession.setTailModeEnabled(true);
                    targetSession.getSshService().tailFile(targetSession.getRemotePath(), tailWindowSize,
                            line -> handleTailLineBackground(targetSession, line),
                            error -> Platform.runLater(() -> {
                                showError("Remote Tail Error", error);
                                disableTail(targetSession, false);
                            }));
                    updateTabBadge(targetSession);
                    return;
                }
            }
        }

        // 1. Active remote tail — just restart it
        if (tailModeEnabled && monitoringRemotePath != null && activeTailSshService != null) {
            reloadActiveRemoteTail();
            return;
        }

        // 2. Active local file — reload it
        if (currentFile != null) {
            logger.info("Reloading local file '{}'.", currentFile.getName());
            showLoading("Reloading file...");
            openLocalLogFile(currentFile, false);
            return;
        }

        // 3. Nothing active — try to restore from lastSession
        if (lastSession == null) {
            logger.warn("Reload triggered but no active file or tail session.");
            return;
        }

        if (lastSession.type == LastSession.Type.LOCAL) {
            if (lastSession.localFile != null && lastSession.localFile.exists()) {
                logger.info("Reloading last local file '{}'.", lastSession.localFile.getName());
                showLoading("Reloading file...");
                openLocalLogFile(lastSession.localFile, false);
            }
        } else {
            reloadLastRemoteTail();
        }
    }

    private void reloadActiveRemoteTail() {
        logger.info("Reloading active remote tail for '{}'.", monitoringRemotePath);
        if (currentLogFromDb == null || currentLogFromDb.getSshServerID() == null) {
            showError("Reload Error", "Missing log file database information.");
            return;
        }
        SSHServerModel server = serverManagementService.getServerById(currentLogFromDb.getSshServerID());
        if (server == null) {
            showError("Reload Error", "Missing server configuration.");
            return;
        }
        if (currentParsingConfig == null && currentSession != null) {
            currentParsingConfig = currentSession.getParsingConfig();
        }
        if (currentParsingConfig == null) {
            currentParsingConfig = parsingConfigService.findDefault().orElseGet(ParsingConfig::createRawConfig);
        }
        showLoading("Reloading remote tail...");
        try {
            String password = server.getPassword();
            if (password == null || password.isBlank()) {
                logger.warn("Cannot get password for reload, relying on existing session.");
            }
            activeTailSshService.connect(server.getHost(), server.getPort(), server.getUsername(), password);
            startRemoteTail(monitoringRemotePath, activeTailSshService, server);
        } catch (Exception e) {
            logger.error("Failed to re-connect for tail reload", e);
            showError("Reload Error", "Failed to re-connect to server: " + e.getMessage());
        } finally {
            hideLoading();
        }
    }

    private void reloadLastRemoteTail() {
        if (lastSession == null || lastSession.logFromDb == null || lastSession.logFromDb.getSshServerID() == null) {
            showError("Reload Error", "No remote session to reload.");
            return;
        }
        String remotePath = lastSession.remotePath;
        LogFile logFromDb = lastSession.logFromDb;
        SSHServiceImpl savedSsh = lastSession.sshService;

        logger.info("Reloading last remote tail for '{}'.", remotePath);
        SSHServerModel server = serverManagementService.getServerById(logFromDb.getSshServerID());
        if (server == null) {
            showError("Reload Error", "Server configuration not found.");
            return;
        }

        currentLogFromDb = logFromDb;
        showLoading("Reconnecting to " + server.getHost() + "...");

        SSHServiceImpl sshService = (savedSsh != null && savedSsh.isConnected()) ? savedSsh : new SSHServiceImpl();

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() {
                if (sshService.isConnected()) return true;
                String password = server.getPassword();
                if (password == null || password.isBlank()) return false;
                return sshService.connect(server.getHost(), server.getPort(), server.getUsername(), password);
            }
        };
        connectTask.setOnSucceeded(e -> {
            hideLoading();
            if (connectTask.getValue()) {
                tailModeEnabled = true;
                startRemoteTail(remotePath, sshService, server);
            } else {
                showError("Reload Error", "Could not reconnect to " + server.getHost());
            }
        });
        connectTask.setOnFailed(e -> {
            hideLoading();
            showError("Reload Error", "Reconnection failed: " + connectTask.getException().getMessage());
        });
        new Thread(connectTask).start();
    }


    @FXML
    public void handleClearLog() {
        logger.info("User requested to clear log view");
        if (tailModeEnabled) {
            disableTail();
        }
        cleanupFileResources();
        currentFile = null;
        currentLogFromDb = null;
        currentParsingConfig = null;
        clearSearch();
        clearDetail();
        updateTailButtonState();
        recentFilesListView.getSelectionModel().clearSelection();
        if (followTailButton != null) {
            Platform.runLater(() -> followTailButton.setSelected(false));
        }
        logger.info("Log view cleared");
    }

    private void setupBottomPanel() {
        prettifyJsonButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
            autoPrettifyJson = newVal;
            preferenceService
                    .saveOrUpdatePreferences(new Preference("main_auto_prettify_json", String.valueOf(newVal)));
            if (newVal) {
                prettifyJsonButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
                applyAutoPrettify();
            } else {
                prettifyJsonButton.setStyle("");
                restoreRawDetailIfNotPrettified();
            }
        });

        prettifyXmlButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
            autoPrettifyXml = newVal;
            preferenceService.saveOrUpdatePreferences(new Preference("main_auto_prettify_xml", String.valueOf(newVal)));
            if (newVal) {
                prettifyXmlButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
                applyAutoPrettify();
            } else {
                prettifyXmlButton.setStyle("");
                restoreRawDetailIfNotPrettified();
            }
        });
        copyButton.setOnAction(e -> copyDetailToClipboard());
        clearDetailButton.setOnAction(e -> clearDetail());
        pinBottomPanelButton.setOnAction(e -> handleToggleBottomPanelPin());

        if (statusLabel != null) {
            statusLabel.setCursor(Cursor.HAND);
            statusLabel.setOnMouseClicked(e -> handleGoToLine());
            statusLabel.setTooltip(new Tooltip("Click to Go To Line (Ctrl+G)"));
            statusLabel.setOnMouseEntered(e -> statusLabel.setStyle(
                    "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 11px; -fx-text-fill: #0066cc; -fx-underline: true;"));
            statusLabel.setOnMouseExited(e -> statusLabel.setStyle(
                    "-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 11px; -fx-text-fill: #333333; -fx-underline: false;"));
        }

        updateBottomPanelDisplay();
    }

    private void applyAutoPrettify() {
        if (currentRawLogContent == null && detailCodeArea != null) {
            currentRawLogContent = detailCodeArea.getText();
        }
        if (currentRawLogContent == null || currentRawLogContent.isEmpty()) {
            return;
        }

        boolean prettified = false;
        if (autoPrettifyJson) {
            prettified = prettifyJson();
        }
        if (!prettified && autoPrettifyXml) {
            prettifyXml();
        }
    }

    private void restoreRawDetailIfNotPrettified() {
        if (currentRawLogContent != null && detailCodeArea != null) {
            boolean prettified = false;
            if (autoPrettifyJson) {
                prettified = prettifyJson();
            }
            if (!prettified && autoPrettifyXml) {
                prettifyXml();
            }
            if (!prettified) {
                detailCodeArea.replaceText(currentRawLogContent);
                try {
                    detailCodeArea.setStyleSpans(0, computeHighlightingSpans(currentRawLogContent));
                } catch (Exception e) {
                    logger.warn("CSS load fail in restore raw detail");
                }
            }
        }
    }

    private void handleToggleBottomPanelPin() {
        toggleBottomPanel();
    }

    private void updateBottomPanelDisplay() {
        if (verticalSplitPane == null || bottomPanel == null) {
            return;
        }

        if (isBottomPanelPinned) {
            if (!verticalSplitPane.getItems().contains(bottomPanel)) {
                verticalSplitPane.getItems().add(bottomPanel);
            }
            bottomPanel.setVisible(true);
            bottomPanel.setManaged(true);

            double pos = lastVerticalDividerPos > 0 ? lastVerticalDividerPos : 0.7;
            verticalSplitPane.setDividerPositions(clampDivider(pos));
            Platform.runLater(() -> verticalSplitPane.setDividerPositions(clampDivider(pos)));

            if (toggleBottomPanelButton != null) {
                toggleBottomPanelButton.setSelected(true);
            }
        } else {
            if (verticalSplitPane.getDividerPositions().length > 0) {
                lastVerticalDividerPos = verticalSplitPane.getDividerPositions()[0];
            }

            // Pure native JavaFX: panel is completely closed
            verticalSplitPane.getItems().remove(bottomPanel);
            bottomPanel.setVisible(false);
            bottomPanel.setManaged(false);

            if (toggleBottomPanelButton != null) {
                toggleBottomPanelButton.setSelected(false);
            }
        }

        if (showBottomPanelMenuItem != null) {
            showBottomPanelMenuItem.setSelected(isBottomPanelPinned);
        }
    }

    private void setupKeyboardShortcuts() {
        if (menuBar.getScene() == null) {
            Platform.runLater(this::setupKeyboardShortcuts);
            return;
        }

        Scene scene = menuBar.getScene();
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> searchField.requestFocus());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN), this::handleReload);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN),
                this::toggleLeftPanel);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.J, KeyCombination.CONTROL_DOWN),
                this::toggleBottomPanel);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN),
                this::handleGoToLine);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F3),
                this::navigateNextMatch);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN),
                this::navigatePreviousMatch);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),
                this::handleCloseCurrentTab);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.TAB, KeyCombination.CONTROL_DOWN),
                this::selectNextTab);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.TAB, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::selectPreviousTab);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN), () -> {
            Node focusOwner = scene.getFocusOwner();
            if (focusOwner instanceof TextInputControl) {
                return;
            }
            if (focusOwner != null && focusOwner.getClass().getName().toLowerCase().contains("richtext")) {
                return;
            }
            if (canvasLogViewer != null && canvasLogViewer.hasSelection()) {
                canvasLogViewer.copySelectedLines();
            }
        });
    }

    @FXML
    public void handleOpen() {
        try {
            Stage mainStage = (Stage) menuBar.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
            Parent root = loader.load();
            UnifiedFileManagerDialogController controller = loader.getController();

            Stage dialog = new Stage();
            addAppIcon(dialog);
            dialog.setTitle("Open File");
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(mainStage);
            dialog.setScene(new Scene(root));
            dialog.setMaximized(true);
            dialog.setOnShown(e -> dialog.setMaximized(true));

            dialog.showAndWait();

            FileInfo selectedFile = controller.getSelectedFile();
            SSHServiceImpl sshService = controller.getSshService();
            UnifiedFileManagerDialogController.OpenAction action = controller.getOpenAction();
            SSHServerModel sshServer = controller.getActiveServer();
            int targetLine = controller.getPendingJumpLine();

            if (selectedFile == null) {
                logger.info("No file selected from UnifiedFileManagerDialog, operation cancelled.");
                if (sshService != null) {
                    sshService.disconnect();
                }
                return;
            }

            if (selectedFile.getSourceType() == FileInfo.SourceType.LOCAL) {
                boolean startTail = (action == UnifiedFileManagerDialogController.OpenAction.TAIL);
                openLocalLogFile(new File(selectedFile.getPath()), true, startTail, targetLine);
                if (sshService != null) {
                    sshService.disconnect();
                }
            } else {
                if (action == UnifiedFileManagerDialogController.OpenAction.OPEN) {
                    openRemoteLogFile(selectedFile.getPath(), selectedFile.getName(), sshService, sshServer, targetLine);
                } else if (action == UnifiedFileManagerDialogController.OpenAction.TAIL) {
                    startRemoteTail(selectedFile.getPath(), sshService, sshServer,
                            controller.getPendingTailWindowLines(), controller.getPendingTailJumpIndex());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to open Unified File Manager", e);
            showError("Error Opening File Browser", "Could not open the file browser: " + e.getMessage());
        }
    }

    private String remoteDownloadKey(String remotePath, SSHServerModel server) {
        return remoteDownloadKey(remotePath, server != null ? server.getId() : null);
    }

    private String remoteDownloadKey(String remotePath, String serverId) {
        return (serverId != null ? serverId : "") + "\u0000" + remotePath;
    }

    private void openRemoteLogFile(String remotePath, String remoteFileName, SSHServiceImpl sshService, SSHServerModel sshServer) {
        openRemoteLogFile(remotePath, remoteFileName, sshService, sshServer, 0);
    }

    private void openRemoteLogFile(String remotePath, String remoteFileName, SSHServiceImpl sshService,
            SSHServerModel sshServer, int targetLine) {
        File cachedFile = downloadedRemoteFiles.get(remoteDownloadKey(remotePath, sshServer));
        if (cachedFile != null && cachedFile.isFile()) {
            logger.info("Reusing cached remote download {} for {}", cachedFile.getAbsolutePath(), remotePath);
            openLocalLogFile(cachedFile, remoteFileName, sshServer, remotePath, true, false, targetLine);
            if (sshService != null) sshService.disconnect();
            return;
        }
        if (sshService == null || !sshService.isConnected()) {
            showError("Connection Error", "SSH connection is not active. Please re-select the file.");
            return;
        }
        long generation = downloadGeneration.incrementAndGet();
        activeDownloadSshService = sshService;
        showLoading("Downloading remote file: " + remoteFileName);

        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                String downloadDir = (sshDownloadDirectory != null && !sshDownloadDirectory.isBlank())
                        ? sshDownloadDirectory : System.getProperty("java.io.tmpdir");
                File dir = new File(downloadDir);
                if (!dir.exists()) dir.mkdirs();
                String sanitizedName = new File(remoteFileName).getName();
                File localTmpFile = new File(dir,
                        "seeloggyplus-" + System.currentTimeMillis() + "-" + sanitizedName);
                activeDownloadFile = localTmpFile;
                logger.info("Downloading remote file {} to temporary path {}", remotePath,
                        localTmpFile.getAbsolutePath());
                boolean success = sshService.downloadFileConcurrent(remotePath, localTmpFile.getAbsolutePath(),
                        sshDownloadThreads,
                        new LogParser.ProgressCallback() {
                            @Override
                            public void onProgress(double progress, long bytesProcessed, long totalBytes) {
                                if (generation != downloadGeneration.get() || isCancelled()) return;
                                Platform.runLater(() -> {
                                    if (generation != downloadGeneration.get() || isCancelled()) return;
                                    if (loadingOverlay != null && loadingOverlay.isVisible()) {
                                        if (loadingProgress != null) {
                                            loadingProgress.setProgress(progress);
                                        }
                                        loadingLabel.setText(String.format("Downloading... %.0f%% (%s / %s)",
                                                progress * 100, formatBytes(bytesProcessed), formatBytes(totalBytes)));
                                    }
                                });
                            }

                            @Override
                            public void onComplete(long totalEntries) {
                                logger.info("Download complete.");
                            }
                        });

                if (!success) {
                    throw new IOException("Failed to download file from server.");
                }

                logger.info("Remote file downloaded successfully.");
                return localTmpFile;
            }
        };

        downloadTask.setOnSucceeded(e -> {
            if (generation != downloadGeneration.get() || downloadTask.isCancelled()) return;
            File localFile = downloadTask.getValue();
            hideLoading();
            activeDownloadSshService = null;
            activeDownloadFile = null;
            downloadedRemoteFiles.put(remoteDownloadKey(remotePath, sshServer), localFile);
            openLocalLogFile(localFile, remoteFileName, sshServer, remotePath, true, false, targetLine);
            sshService.disconnect();
        });

        downloadTask.setOnFailed(e -> {
            if (generation != downloadGeneration.get() || downloadTask.isCancelled()) return;
            hideLoading();
            activeDownloadSshService = null;
            activeDownloadFile = null;
            Throwable ex = downloadTask.getException();
            logger.error("Failed to download remote file", ex);
            showError("Remote File Error", "Failed to download file: " + ex.getMessage());
            sshService.disconnect();
        });
        currentLoadingTask = downloadTask;
        Thread.ofVirtual().start(downloadTask);
    }

    @FXML
    public void handleCancelProcessing() {
        logger.info("User requested processing cancellation.");
        cancelCurrentLoadingTask();
        hideLoading();
    }

    private void cancelCurrentLoadingTask() {
        downloadGeneration.incrementAndGet();
        if (activeDownloadSshService != null) {
            activeDownloadSshService.disconnect();
            activeDownloadSshService = null;
        }
        File cancelledDownload = activeDownloadFile;
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            logger.info("Cancelling previous loading task...");
            currentLoadingTask.cancel(true);
            logger.info("Previous task cancellation requested.");
        }
        activeDownloadFile = null;
        if (cancelledDownload != null) {
            Thread.ofVirtual().start(() -> deleteCancelledDownloadFiles(cancelledDownload));
        }

        cleanupFileResources();
        Thread.ofVirtual().start(() -> {
            System.gc();
            logger.info("Memory cleanup (GC) triggered on background thread");
        });

        logger.info("Memory cleanup requested");
    }

    private void openLocalLogFile(File file, boolean updateRecentFilesList) {
        openLocalLogFile(file, null, null, null, updateRecentFilesList, false);
    }

    private void openLocalLogFile(File file, boolean updateRecentFilesList, boolean startTail) {
        openLocalLogFile(file, null, null, null, updateRecentFilesList, startTail, 0);
    }

    private void openLocalLogFile(File file, boolean updateRecentFilesList, boolean startTail, int targetLine) {
        openLocalLogFile(file, null, null, null, updateRecentFilesList, startTail, targetLine);
    }

    private void openLocalLogFile(File file, String customDisplayName, SSHServerModel remoteServer, boolean updateRecentFilesList) {
        openLocalLogFile(file, customDisplayName, remoteServer, null, updateRecentFilesList, false, 0);
    }

    private void openLocalLogFile(File file, String customDisplayName, SSHServerModel remoteServer, String remotePath, boolean updateRecentFilesList) {
        openLocalLogFile(file, customDisplayName, remoteServer, remotePath, updateRecentFilesList, false, 0);
    }

    private void openLocalLogFile(File file, String customDisplayName, SSHServerModel remoteServer, String remotePath, boolean updateRecentFilesList, boolean startTail) {
        openLocalLogFile(file, customDisplayName, remoteServer, remotePath, updateRecentFilesList, startTail, 0);
    }

    private void openLocalLogFile(File file, String customDisplayName, SSHServerModel remoteServer, String remotePath, boolean updateRecentFilesList, boolean startTail, int targetLine) {
        if (file == null || !file.exists()) {
            logger.error("File does not exist: {}", file);
            showError("File Error", "The selected file does not exist or cannot be accessed.");
            return;
        }

        // Check if file is already open in an existing tab
        for (Map.Entry<Tab, LogSession> entry : sessionMap.entrySet()) {
            LogSession s = entry.getValue();
            if (s.getSessionType() == LogSession.SessionType.LOCAL && s.getLocalFile() != null) {
                try {
                    if (s.getLocalFile().getCanonicalPath().equalsIgnoreCase(file.getCanonicalPath())) {
                        if (logTabPane != null) {
                            logTabPane.getSelectionModel().select(entry.getKey());
                        }
                        if (startTail && !s.isTailModeEnabled()) {
                            enableTail();
                        }
                        return;
                    }
                } catch (IOException ignored) {
                    if (s.getLocalFile().getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
                        if (logTabPane != null) {
                            logTabPane.getSelectionModel().select(entry.getKey());
                        }
                        if (startTail && !s.isTailModeEnabled()) {
                            enableTail();
                        }
                        return;
                    }
                }
            }
        }

        ParsingConfig parsingConfig = ParsingConfig.createRawConfig();
        logger.info("Using default RAW configuration for local file: {}", file.getAbsolutePath());

        String cleanName = customDisplayName;
        if (cleanName == null) {
            cleanName = file.getName();
            if (cleanName.matches("^seeloggyplus-\\d+-(.+)$")) {
                cleanName = cleanName.replaceFirst("^seeloggyplus-\\d+-", "");
            }
        }

        LogFile logFile = getOrCreateLogFile(file, cleanName, remoteServer, remotePath);
        if (logFile == null) {
            logger.error("Failed to get or create log file record for: {}", file.getAbsolutePath());
            showError("Database Error", "Failed to save log file information to database.");
            return;
        }

        String tabTitle = cleanName;
        if (remoteServer != null && remoteServer.getName() != null && !remoteServer.getName().isBlank()) {
            tabTitle = cleanName + " (" + remoteServer.getName() + ")";
        }

        LogSession session = new LogSession(tabTitle, LogSession.SessionType.LOCAL);
        session.setLocalFile(file);
        if (remoteServer != null) {
            session.setSshServer(remoteServer);
        }
        if (remotePath != null && !remotePath.isBlank()) {
            session.setRemotePath(remotePath);
        }
        session.setLogFileRecord(logFile);
        session.setParsingConfig(parsingConfig);
        session.setTailService(new TailServiceImpl());

        Tab tab = createTabForSession(session);
        sessionMap.put(tab, session);
        if (logTabPane != null) {
            logTabPane.getTabs().add(tab);
            logTabPane.getSelectionModel().select(tab);
        }

        long fileSizeInBytes = file.length();
        logger.info("Starting to load file: {} ({}) into new tab", tabTitle,
                FileUtils.formatFileSize(fileSizeInBytes));
        loadFileWithParallelParsing(session, file, logFile, updateRecentFilesList, false, true, startTail, targetLine);
    }

    private LogFile getOrCreateLogFile(File file) {
        String cleanName = file.getName();
        if (cleanName.matches("^seeloggyplus-\\d+-(.+)$")) {
            cleanName = cleanName.replaceFirst("^seeloggyplus-\\d+-", "");
        }
        return getOrCreateLogFile(file, cleanName, null, null);
    }

    private LogFile getOrCreateLogFile(File file, String displayName, SSHServerModel remoteServer) {
        return getOrCreateLogFile(file, displayName, remoteServer, null);
    }

    private LogFile getOrCreateLogFile(File file, String displayName, SSHServerModel remoteServer, String remotePath) {
        try {
            ParsingConfig parsingConfig = ParsingConfig.createRawConfig(); // Internal default
            String serverId = remoteServer != null ? remoteServer.getId() : null;
            boolean isRemote = remoteServer != null;

            String storedPath = (isRemote && remotePath != null && !remotePath.isBlank()) ? remotePath : file.getAbsolutePath();

            LogFile existingLogFile = logFileService.getLogFileByPathNameAndServer(displayName, storedPath, serverId, isRemote);
            if (existingLogFile == null) {
                existingLogFile = logFileService.getLogFileByPathNameAndServer(file.getName(), storedPath, serverId, isRemote);
            }
            if (existingLogFile == null && isRemote) {
                existingLogFile = logFileService.getLogFileByPathNameAndServer(displayName, file.getAbsolutePath(), serverId, isRemote);
            }

            if (existingLogFile != null) {
                logger.info("LogFile found in database, updating metadata for: {}", storedPath);
                existingLogFile.setName(displayName);
                existingLogFile.setFilePath(storedPath);
                existingLogFile.setSize(com.seeloggyplus.util.FileUtils.formatFileSize(file.length()));
                existingLogFile.setModified(String.valueOf(file.lastModified()));
                existingLogFile.setParsingConfigurationID(parsingConfig.getId());

                try {
                    logFileService.updateLogFile(existingLogFile);
                    logger.info("Successfully updated LogFile: {}", existingLogFile.getId());
                    return existingLogFile;
                } catch (Exception ex) {
                    logger.error("Failed to update LogFile, will use existing data", ex);
                    return existingLogFile;
                }
            } else {
                logger.info("LogFile not found in database, creating new entry for: {}", storedPath);
                LogFile newLogFile = new LogFile();
                newLogFile.setName(displayName);
                newLogFile.setFilePath(storedPath);
                newLogFile.setRemote(isRemote);
                newLogFile.setSshServerID(serverId);
                newLogFile.setParsingConfigurationID(parsingConfig.getId());
                newLogFile.setModified(String.valueOf(file.lastModified()));
                newLogFile.setSize(com.seeloggyplus.util.FileUtils.formatFileSize(file.length()));

                logFileService.insertLogFile(newLogFile);
                logger.info("Successfully created LogFile with ID: {}", newLogFile.getId());
                return newLogFile;
            }
        } catch (Exception ex) {
            logger.error("Error in getOrCreateLogFile for: {}", file.getAbsolutePath(), ex);
            return null;
        }
    }

    private void handleServerManagement() {
        try {
            Stage mainStage = (Stage) menuBar.getScene().getWindow();
            boolean wasMaximized = mainStage.isMaximized();
            double oldX = mainStage.getX();
            double oldY = mainStage.getY();
            double oldWidth = mainStage.getWidth();
            double oldHeight = mainStage.getHeight();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
            Parent root = loader.load();

            Stage dialog = new Stage();
            dialog.setTitle("SSH Server Management");
            dialog.initOwner(mainStage);
            dialog.initModality(Modality.APPLICATION_MODAL);
            addAppIcon(dialog);
            restoreWindow(mainStage, wasMaximized, oldX, oldY, oldWidth, oldHeight, root, dialog);

            logger.info("Server management dialog closed");
        } catch (IOException e) {
            logger.error("Failed to open server management dialog", e);
            showError("Failed to open server management", e.getMessage());
        }
    }

    private void handlePreferences() {
        try {
            Stage mainStage = (Stage) menuBar.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PreferencesDialog.fxml"));
            Parent root = loader.load();

            PreferencesDialogController controller = loader.getController();
            controller.setOnSaveCallback(this::loadPreferences);

            Stage dialog = new Stage();
            dialog.setTitle("Preferences");
            dialog.initOwner(mainStage);
            dialog.initModality(Modality.WINDOW_MODAL);
            addAppIcon(dialog);
            dialog.setScene(new Scene(root));
            dialog.setResizable(false);
            dialog.setWidth(550);
            dialog.setHeight(480);

            dialog.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open preferences dialog", e);
            showError("Preferences Error", "Could not open preferences: " + e.getMessage());
        }
    }

    private void loadPreferences() {
        logger.info("Loading preferences...");

        String fontFamily = preferenceService.getPreferencesByCode("app_font_family").orElse("Consolas");
        String fontSizeStr = preferenceService.getPreferencesByCode("app_font_size").orElse("12");
        int fontSize = 12;
        try {
            fontSize = Integer.parseInt(fontSizeStr);
        } catch (NumberFormatException e) {
            logger.warn("Invalid font size preference: {}", fontSizeStr);
        }

        String fontStyle = String.format("-fx-font-family: '%s'; -fx-font-size: %dpx;", fontFamily, fontSize);
        if (detailCodeArea != null) {
            detailCodeArea.setStyle(fontStyle);
        }

        String threadsStr = preferenceService.getPreferencesByCode("ssh_download_threads").orElse("4");
        try {
            this.sshDownloadThreads = Integer.parseInt(threadsStr);
        } catch (NumberFormatException e) {
            logger.warn("Invalid ssh threads preference: {}", threadsStr);
        }

        this.sshDownloadDirectory = preferenceService.getPreferencesByCode("ssh_download_directory").orElse("");

        this.autoPrettifyJson = Boolean
                .parseBoolean(preferenceService.getPreferencesByCode("main_auto_prettify_json").orElse("false"));
        this.autoPrettifyXml = Boolean
                .parseBoolean(preferenceService.getPreferencesByCode("main_auto_prettify_xml").orElse("false"));

        String tailWindowStr = preferenceService.getPreferencesByCode("main_tail_window_size").orElse("20000");
        try {
            this.tailWindowSize = Integer.parseInt(tailWindowStr);
        } catch (NumberFormatException e) {
            logger.warn("Invalid tail window size preference: {}", tailWindowStr);
        }

        logger.info("Preferences loaded: font={} {}, threads={}, tailWindowSize={}", fontFamily, fontSize,
                sshDownloadThreads, tailWindowSize);

        if (autoPrettifyJson || autoPrettifyXml) {
            applyAutoPrettify();
        }

        prettifyJsonButton.setSelected(autoPrettifyJson);
        prettifyXmlButton.setSelected(autoPrettifyXml);
    }

    static void restoreWindow(Stage mainStage, boolean wasMaximized, double oldX, double oldY, double oldWidth,
            double oldHeight, Parent root, Stage dialog) {
        dialog.setScene(new Scene(root));
        dialog.showAndWait();

        Platform.runLater(() -> {
            if (wasMaximized) {
                mainStage.setMaximized(true);
            } else {
                mainStage.setX(oldX);
                mainStage.setY(oldY);
                mainStage.setWidth(oldWidth);
                mainStage.setHeight(oldHeight);
            }
        });
    }

    private void handleRecentFileSelected(RecentFilesDto recentFile) {
        if (recentFile == null || recentFile.logFile() == null) return;
        LogFile logFile = recentFile.logFile();

        if (logFile.isRemote()) {
            String filePath = logFile.getFilePath();
            if (filePath != null && (filePath.matches("^[A-Za-z]:[\\\\/].*") || filePath.contains("\\") || filePath.contains("seeloggyplus-"))) {
                logger.warn("Recent remote file has local temp path stored: {}", filePath);
                File localCopy = new File(filePath);
                if (localCopy.exists()) {
                    openLocalLogFile(localCopy, false);
                    return;
                } else {
                    showError("File Not Found", "The temporary file no longer exists: " + filePath);
                    return;
                }
            }

            // Check if already open in a tab
            for (Map.Entry<Tab, LogSession> entry : sessionMap.entrySet()) {
                LogSession s = entry.getValue();
                boolean sameRemoteFile = logFile.getFilePath().equals(s.getRemotePath())
                        && s.getSshServer() != null
                        && s.getSshServer().getId().equals(logFile.getSshServerID());
                boolean sameDownloadedRemoteFile = s.getSessionType() == LogSession.SessionType.LOCAL
                        && s.getLogFileRecord() != null
                        && s.getLogFileRecord().isRemote()
                        && logFile.getFilePath().equals(s.getLogFileRecord().getFilePath())
                        && logFile.getSshServerID() != null
                        && logFile.getSshServerID().equals(s.getLogFileRecord().getSshServerID());
                if (sameRemoteFile || sameDownloadedRemoteFile) {
                    if (logTabPane != null) {
                        logTabPane.getSelectionModel().select(entry.getKey());
                    }
                    return;
                }
            }

            SSHServerModel cachedServer = logFile.getSshServerID() == null ? null
                    : serverManagementService.getServerById(logFile.getSshServerID());
            File cachedDownload = downloadedRemoteFiles.get(remoteDownloadKey(logFile.getFilePath(), logFile.getSshServerID()));
            if (cachedDownload != null && cachedDownload.isFile()) {
                logger.info("Opening cached remote download from Recent: {}", cachedDownload.getAbsolutePath());
                openLocalLogFile(cachedDownload, logFile.getName(), cachedServer, logFile.getFilePath(), false);
                return;
            }

            hideLoading();
            cancelCurrentLoadingTask();

            Task<SSHServiceImpl> connectTask = new Task<>() {
                @Override
                protected SSHServiceImpl call() throws Exception {
                    String sshServerId = logFile.getSshServerID();
                    if (sshServerId == null || sshServerId.isBlank()) {
                        throw new IOException("This remote file does not have SSH server information.");
                    }

                    final SSHServerModel server = serverManagementService.getServerById(sshServerId);
                    if (server == null) {
                        throw new IOException("SSH server configuration with ID " + sshServerId + " was not found.");
                    }

                    final CompletableFuture<String> passwordFuture = new CompletableFuture<>();
                    Platform.runLater(() -> {
                        String password = server.getPassword();
                        if (password == null || password.isBlank()) {
                            logger.info("Password for server {} is not saved, prompting user.", server.getName());
                            PasswordPromptDialog prompt = new PasswordPromptDialog(server.getHost(),
                                    server.getUsername());
                            prompt.showAndWait().ifPresentOrElse(passwordFuture::complete,
                                    () -> passwordFuture.complete(null));
                        } else {
                            passwordFuture.complete(password);
                        }
                    });
                    String password = passwordFuture.get();
                    if (password == null) {
                        logger.info("User cancelled password prompt for remote recent file.");
                        updateMessage("SSH connection cancelled.");
                        cancel();
                        return null;
                    }

                    updateMessage("Connecting to " + server.getHost() + "...");
                    SSHServiceImpl sshService = new SSHServiceImpl();
                    boolean connected = sshService.connect(server.getHost(), server.getPort(), server.getUsername(),
                            password);

                    if (!connected) {
                        throw new IOException("Could not connect to " + server.getHost());
                    }

                    serverManagementService.updateServerLastUsed(server.getId());
                    return sshService;
                }
            };

            showLoading("Connecting to remote server...");
            connectTask.runningProperty().addListener((obs, wasRunning, isRunning) -> {
                if (!isRunning) {
                    hideLoading();
                }
            });

            connectTask.setOnSucceeded(e -> {
                SSHServiceImpl sshService = connectTask.getValue();
                if (sshService != null) {
                    logger.info("SSH connected for remote recent file, opening normally.");
                    resetFilters();
                    openRemoteLogFile(logFile.getFilePath(), logFile.getName(), sshService,
                            serverManagementService.getServerById(logFile.getSshServerID()));
                }

            });

            connectTask.setOnFailed(e -> {
                Throwable ex = connectTask.getException();
                logger.error("Failed to connect to SSH server for recent file", ex);
                showError("Connection Error", ex.getMessage());

            });

            currentLoadingTask = connectTask;
            Thread.ofVirtual().start(connectTask);

        } else {
            File file = new File(logFile.getFilePath());
            if (!file.exists()) {
                showError("File Not Found", "The file no longer exists: " + logFile.getFilePath());
                return;
            }

            // Check if already open in a tab
            for (Map.Entry<Tab, LogSession> entry : sessionMap.entrySet()) {
                LogSession s = entry.getValue();
                if (s.getSessionType() == LogSession.SessionType.LOCAL && s.getLocalFile() != null) {
                    try {
                        if (s.getLocalFile().getCanonicalPath().equalsIgnoreCase(file.getCanonicalPath())) {
                            if (logTabPane != null) {
                                logTabPane.getSelectionModel().select(entry.getKey());
                            }
                            return;
                        }
                    } catch (IOException ignored) {
                        if (s.getLocalFile().getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
                            if (logTabPane != null) {
                                logTabPane.getSelectionModel().select(entry.getKey());
                            }
                            return;
                        }
                    }
                }
            }

            logger.info("Opening recent file: {} with RAW config", file.getName());
            resetFilters();
            openLocalLogFile(file, false);
        }
    }

    private void resetFilters() {
        if (searchField != null)
            searchField.clear();
        if (regexCheckBox != null)
            regexCheckBox.setSelected(false);
        if (caseSensitiveCheckBox != null)
            caseSensitiveCheckBox.setSelected(false);
        if (searchField != null) {
            searchField.setStyle("");
        }
        logger.info("Filters reset to default");
    }

    private void setupDetailPanel() {
        detailCodeArea = new StyleClassedTextArea();
        detailCodeArea.setEditable(false);
        detailCodeArea.setWrapText(true);
        detailCodeArea.getStyleClass().add("detail-area");
        try {
            detailCodeArea.getStylesheets()
                    .add(Objects.requireNonNull(getClass().getResource("/style/richtext.css")).toExternalForm());
        } catch (Exception e) {
            logger.warn("CSS load fail");
        }
        VirtualizedScrollPane<StyleClassedTextArea> vsp = new VirtualizedScrollPane<>(detailCodeArea);

        detailPlaceholderLabel = new Label("Select a log line from the table above to view formatted details, JSON/XML, or stack trace");
        detailPlaceholderLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px; -fx-font-style: italic;");
        detailPlaceholderLabel.setWrapText(true);
        detailPlaceholderLabel.setAlignment(Pos.CENTER);

        detailContainer.getChildren().addAll(vsp, detailPlaceholderLabel);
    }

    private StyleSpans<Collection<String>> computeHighlightingSpans(String text) {
        return SyntaxHighlighter.computeLogHighlighting(text);
    }

    private boolean prettifyJson() {
        String sourceText = currentRawLogContent != null ? currentRawLogContent : detailCodeArea.getText();
        if (sourceText == null || sourceText.isEmpty()) {
            return false;
        }

        if (sourceText.length() > 4096) {
            CompletableFuture.supplyAsync(() -> {
                String prettified = JsonPrettify.prettifyFromLog(sourceText);
                if (prettified != null && !prettified.equals(sourceText)) {
                    StyleSpans<Collection<String>> spans = null;
                    try {
                        spans = SyntaxHighlighter.computeJsonHighlighting(prettified);
                    } catch (Exception ignored) {}
                    return new Object[] { prettified, spans };
                }
                return null;
            }).thenAccept(result -> {
                if (result != null && sourceText.equals(currentRawLogContent)) {
                    Platform.runLater(() -> {
                        String prettified = (String) result[0];
                        @SuppressWarnings("unchecked")
                        StyleSpans<Collection<String>> spans = (StyleSpans<Collection<String>>) result[1];
                        detailCodeArea.replaceText(prettified);
                        if (spans != null) {
                            try {
                                detailCodeArea.setStyleSpans(0, spans);
                            } catch (Exception ignored) {}
                        }
                    });
                }
            });
            return true;
        }

        String prettified = JsonPrettify.prettifyFromLog(sourceText);
        if (prettified != null && !prettified.equals(sourceText)) {
            detailCodeArea.replaceText(prettified);
            try {
                detailCodeArea.setStyleSpans(0, SyntaxHighlighter.computeJsonHighlighting(prettified));
            } catch (Exception e) {
                logger.warn("CSS load fail in pretty json", e);
            }
            return true;
        }
        return false;
    }

    private boolean prettifyXml() {
        String sourceText = currentRawLogContent != null ? currentRawLogContent : detailCodeArea.getText();
        if (sourceText == null || sourceText.isEmpty()) {
            return false;
        }

        if (sourceText.length() > 4096) {
            CompletableFuture.supplyAsync(() -> {
                String prettified = XmlPrettify.prettifyFromLog(sourceText);
                if (prettified != null && !prettified.equals(sourceText)) {
                    StyleSpans<Collection<String>> spans = null;
                    try {
                        spans = SyntaxHighlighter.computeXmlHighlighting(prettified);
                    } catch (Exception ignored) {}
                    return new Object[] { prettified, spans };
                }
                return null;
            }).thenAccept(result -> {
                if (result != null && sourceText.equals(currentRawLogContent)) {
                    Platform.runLater(() -> {
                        String prettified = (String) result[0];
                        @SuppressWarnings("unchecked")
                        StyleSpans<Collection<String>> spans = (StyleSpans<Collection<String>>) result[1];
                        detailCodeArea.replaceText(prettified);
                        if (spans != null) {
                            try {
                                detailCodeArea.setStyleSpans(0, spans);
                            } catch (Exception ignored) {}
                        }
                    });
                }
            });
            return true;
        }

        String prettified = XmlPrettify.prettifyFromLog(sourceText);
        if (prettified != null && !prettified.equals(sourceText)) {
            detailCodeArea.replaceText(prettified);
            try {
                detailCodeArea.setStyleSpans(0, SyntaxHighlighter.computeXmlHighlighting(prettified));
            } catch (Exception e) {
                logger.warn("CSS load fail in pretty xml", e);
            }
            return true;
        }
        return false;
    }

    private void copyDetailToClipboard() {
        String text = detailCodeArea.getText();
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private void clearDetail() {
        Runnable r = () -> {
            if (detailCodeArea != null) {
                detailCodeArea.clear();
            }
            currentRawLogContent = null;
            if (detailLabel != null) {
                detailLabel.setText("Log Detail");
            }
            if (detailPlaceholderLabel != null) {
                detailPlaceholderLabel.setVisible(true);
                detailPlaceholderLabel.setManaged(true);
            }
        };
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    private void toggleLeftPanel() {
        isLeftPanelPinned = !isLeftPanelPinned;
        updateLeftPanelDisplay();
        preferenceService
                .saveOrUpdatePreferences(new Preference("left_panel_pinned", String.valueOf(isLeftPanelPinned)));
    }

    private void toggleBottomPanel() {
        isBottomPanelPinned = !isBottomPanelPinned;
        updateBottomPanelDisplay();
        preferenceService
                .saveOrUpdatePreferences(new Preference("bottom_panel_pinned", String.valueOf(isBottomPanelPinned)));
    }

    private void restorePanelVisibility() {
        isLeftPanelPinned = preferenceService.getPreferencesByCode("left_panel_pinned")
                .filter(Predicate.not(String::isBlank))
                .map(Boolean::parseBoolean)
                .orElse(true);
        updateLeftPanelDisplay();

        isBottomPanelPinned = preferenceService.getPreferencesByCode("bottom_panel_pinned")
                .filter(Predicate.not(String::isBlank))
                .map(Boolean::parseBoolean)
                .orElse(true);
        updateBottomPanelDisplay();
    }

    private void handleClearRecentFiles() {
        ObservableList<RecentFilesDto> selected = recentFilesListView.getSelectionModel().getSelectedItems();
        if (selected != null && !selected.isEmpty()) {
            int count = selected.size();

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Remove Selected Recent Files");
            alert.setHeaderText("Remove " + count + " selected recent file(s)?");
            alert.setContentText("This action cannot be undone.");
            Optional<ButtonType> result = showAndWaitAndRestore(alert);
            if (result.isPresent() && result.get() == ButtonType.OK) {
                List<RecentFilesDto> listToDeleteRecentFiles = List.copyOf(selected);

                for (RecentFilesDto recentFilesDto : listToDeleteRecentFiles) {
                    LogFile logFile = recentFilesDto.logFile();
                    if (logFile != null) {
                        closeTabsForLogFile(logFile);

                        if (logFile.isRemote() && monitoringRemotePath != null
                                && monitoringRemotePath.equals(logFile.getFilePath())) {
                            stopRemoteTail();
                        }

                        recentFileService.deleteByFileId(logFile.getId());
                        logFileService.deleteLogFileById(logFile.getId());
                    }
                }

                refreshRecentFilesList();
                cleanupTempFiles();
            }
            return;
        }

        if (recentFilesListView.getItems().isEmpty()) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear Recent Files");
        alert.setHeaderText("Clear ALL recent files?");
        alert.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = showAndWaitAndRestore(alert);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            handleCloseAllTabs();
            recentFileService.deleteAll();
            logFileService.deleteAllLogFiles();
            stopRemoteTail();
            clearSearch();
            refreshRecentFilesList();
            cleanupTempFiles();
        }
    }

    void closeTabsForLogFile(LogFile logFile) {
        if (logFile == null || sessionMap == null || sessionMap.isEmpty()) return;

        List<LogSession> sessionsToClose = new ArrayList<>();
        for (LogSession session : sessionMap.values()) {
            if (isSessionMatchingLogFile(session, logFile)) {
                sessionsToClose.add(session);
            }
        }
        for (LogSession s : sessionsToClose) {
            closeSession(s);
        }
    }

    private boolean isSessionMatchingLogFile(LogSession session, LogFile logFile) {
        if (session == null || logFile == null) return false;

        // 1. Match by database ID
        if (session.getLogFileRecord() != null && session.getLogFileRecord().getId() != null) {
            if (session.getLogFileRecord().getId().equals(logFile.getId())) {
                return true;
            }
        }

        // 2. Match by remote path & server ID
        if (logFile.isRemote()) {
            String sessRemotePath = session.getRemotePath();
            if (sessRemotePath != null) {
                String sessServerId = session.getSshServer() != null ? session.getSshServer().getId() : null;
                if (Objects.equals(sessServerId, logFile.getSshServerID())
                        && normalizePath(sessRemotePath).equals(normalizePath(logFile.getFilePath()))) {
                    return true;
                }
            }
        } else {
            // 3. Match by local file path
            if (session.getLocalFile() != null && logFile.getFilePath() != null) {
                if (normalizePath(session.getLocalFile().getAbsolutePath()).equalsIgnoreCase(normalizePath(logFile.getFilePath()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void refreshRecentFilesList() {
        allRecentFiles.setAll(recentFileService.findAll());
    }

    private void handleAbout() {
        try {
            Stage mainStage = (Stage) menuBar.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AboutDialog.fxml"));
            Parent root = loader.load();

            Stage dialog = new Stage();
            dialog.setTitle("About SeeLoggyPlus");
            dialog.initOwner(mainStage);
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setResizable(false);
            addAppIcon(dialog);
            dialog.setScene(new Scene(root));

            dialog.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open About dialog", e);
            showError("Error", "Could not open About dialog: " + e.getMessage());
        }
    }

    private void handleHelpContents() {
        try {
            // Look for help files relative to the application directory
            File appDir = new File(System.getProperty("user.dir"));
            File helpFile = new File(appDir, "help/index.html");

            if (!helpFile.exists()) {
                // Fallback: try extracting from resources to temp
                java.net.URL helpUrl = getClass().getResource("/help/index.html");
                if (helpUrl != null) {
                    java.awt.Desktop.getDesktop().browse(helpUrl.toURI());
                    return;
                }
                showError("Help Not Found", "Help file not found at: " + helpFile.getAbsolutePath());
                return;
            }

            java.awt.Desktop.getDesktop().browse(helpFile.toURI());
        } catch (Exception e) {
            logger.error("Failed to open help", e);
            showError("Help Error", "Could not open help: " + e.getMessage());
        }
    }

    private void handleExit() {
        for (LogSession session : new ArrayList<>(sessionMap.values())) {
            try {
                session.close();
            } catch (Exception e) {
                logger.warn("Error closing session {}: {}", session.getTitle(), e.getMessage());
            }
        }
        sessionMap.clear();
        stopLocalTail();
        if (activeTailSshService != null) {
            stopRemoteTail();
        }
        logger.info("Application exiting, stopped tailers and closed all sessions.");
        Platform.exit();
    }

    @FXML
    private void enableTail() {
        boolean isRemote = currentSession != null
                ? currentSession.getSessionType() == LogSession.SessionType.REMOTE
                : currentLogFromDb != null && currentLogFromDb.isRemote();
        boolean isLocal = currentSession != null
                ? currentSession.getSessionType() == LogSession.SessionType.LOCAL
                : currentFile != null && currentFile.exists();

        if (!isRemote && !isLocal) {
            showInfo("Tail Mode", "No active file to tail.");
            if (tailButton.isSelected())
                tailButton.setSelected(false);
            return;
        }

        if (currentParsingConfig == null && currentSession != null) {
            currentParsingConfig = currentSession.getParsingConfig();
        }

        if (currentParsingConfig == null) {
            String cfgId = (currentLogFromDb != null) ? currentLogFromDb.getParsingConfigurationID() : null;
            if (cfgId != null && !cfgId.isBlank()) {
                currentParsingConfig = parsingConfigService.findById(cfgId).orElse(null);
            }
        }

        if (currentParsingConfig == null) {
            currentParsingConfig = parsingConfigService.findDefault()
                    .orElseGet(ParsingConfig::createRawConfig);
        }

        if (currentSession != null && currentSession.getParsingConfig() == null) {
            currentSession.setParsingConfig(currentParsingConfig);
        }

        tailModeEnabled = true;
        if (currentSession != null) {
            currentSession.setTailModeEnabled(true);
        }
        if (!tailButton.isSelected()) {
            isProgrammaticUpdate = true;
            try {
                tailButton.setSelected(true);
            } finally {
                isProgrammaticUpdate = false;
            }
        }
        // Style handled by listener

        if (currentSession != null) {
            boolean isStreamMode = (currentSession.getSessionType() == LogSession.SessionType.REMOTE && currentSession.getReader() == null);
            currentSession.getLiveTailList().clear();
            synchronized (currentSession.getTailBuffer()) {
                currentSession.getTailBuffer().clear();
            }
            currentSession.setTailSearchScannedUpTo(0);
            if (isStreamMode) {
                // Requirement 1: Clear previous stream entries when switching to tail mode
                if (currentSession.getCanvasLogViewer() != null) {
                    currentSession.getCanvasLogViewer().resetView();
                    currentSession.getCanvasLogViewer().setTailBuffer(currentSession.getLiveTailList());
                    currentSession.getCanvasLogViewer().refreshTail();
                }
            } else {
                // Local or indexed file: preserve static file reader, attach live tail buffer
                if (currentSession.getCanvasLogViewer() != null) {
                    currentSession.getCanvasLogViewer().setTailBuffer(currentSession.getLiveTailList());
                    currentSession.getCanvasLogViewer().refreshTail();
                }
            }
            updateTabBadge(currentSession);
            updateBottomBarLineCount(currentSession);
        } else {
            liveTailList.clear();
            synchronized (tailBuffer) {
                tailBuffer.clear();
            }
            tailSearchScannedUpTo = 0;
            if (canvasLogViewer != null) {
                canvasLogViewer.resetView();
                canvasLogViewer.setTailBuffer(liveTailList);
                canvasLogViewer.refreshTail();
            }
            updateBottomBarLineCount(null);
        }

        try {
            currentTailFilterPredicate = buildSearchPredicate(searchField.getText(), regexCheckBox.isSelected(),
                    caseSensitiveCheckBox.isSelected());
            if (currentSession != null) {
                currentSession.setCurrentTailFilterPredicate(currentTailFilterPredicate);
            }
        } catch (Exception e) {
            logger.warn("Tail filter error", e);
        }

        if (isRemote) {
            startRemoteTailInternal();
        } else {
            if (currentSession != null) {
                File fileToTail = currentSession.getLocalFile() != null ? currentSession.getLocalFile() : currentFile;
                startLocalTail(currentSession, fileToTail);
            } else {
                startLocalTail(currentFile);
            }
        }
    }

    private void startRemoteTailInternal() {
        if (currentSession != null && currentSession.getSessionType() == LogSession.SessionType.REMOTE) {
            final LogSession targetSession = currentSession;
            targetSession.setTailModeEnabled(true);
            targetSession.getLiveTailList().clear();
            synchronized (targetSession.getTailBuffer()) {
                targetSession.getTailBuffer().clear();
            }
            targetSession.setTailSearchScannedUpTo(0);
            if (targetSession.getCanvasLogViewer() != null) {
                targetSession.getCanvasLogViewer().resetView();
                targetSession.getCanvasLogViewer().setTailBuffer(targetSession.getLiveTailList());
                targetSession.getCanvasLogViewer().refreshTail();
            }
            updateBottomBarLineCount(targetSession);

            SSHServiceImpl existingSsh = targetSession.getSshService();
            if (existingSsh != null && existingSsh.isConnected()) {
                logger.info("Resuming remote tail on existing session: {}", targetSession.getTitle());
                existingSsh.tailFile(targetSession.getRemotePath(), tailWindowSize,
                        line -> handleTailLineBackground(targetSession, line),
                        error -> Platform.runLater(() -> {
                            logger.error("Remote tail error for {}: {}", targetSession.getTitle(), error);
                            showError("Remote Tail Error", error);
                            disableTail(targetSession, false);
                        }));
                updateTabBadge(targetSession);
                return;
            }

            SSHServerModel server = targetSession.getSshServer();
            if (server == null && currentLogFromDb != null && currentLogFromDb.getSshServerID() != null) {
                server = serverManagementService.getServerById(currentLogFromDb.getSshServerID());
                targetSession.setSshServer(server);
            }
            if (server == null) {
                showError("Error", "SSH server configuration not found.");
                disableTail(targetSession, false);
                return;
            }

            final SSHServerModel finalServer = server;
            String password = server.getPassword();
            if (password == null || password.isBlank()) {
                com.seeloggyplus.util.PasswordPromptDialog prompt = new com.seeloggyplus.util.PasswordPromptDialog(
                        server.getHost(), server.getUsername());
                Optional<String> result = prompt.showAndWait();
                if (result.isPresent() && !result.get().isBlank()) {
                    password = result.get();
                } else {
                    disableTail(targetSession, false);
                    return;
                }
            }
            final String finalPassword = password;
            showLoading("Connecting to " + server.getHost() + "...");

            Task<Boolean> connectTask = new Task<>() {
                private final SSHServiceImpl newSsh = new SSHServiceImpl();

                @Override
                protected Boolean call() throws Exception {
                    return newSsh.connect(finalServer.getHost(), finalServer.getPort(), finalServer.getUsername(), finalPassword);
                }

                @Override
                protected void succeeded() {
                    hideLoading();
                    if (getValue()) {
                        targetSession.setSshService(newSsh);
                        newSsh.tailFile(targetSession.getRemotePath(), tailWindowSize,
                                line -> handleTailLineBackground(targetSession, line),
                                error -> Platform.runLater(() -> {
                                    logger.error("Remote tail error for {}: {}", targetSession.getTitle(), error);
                                    showError("Remote Tail Error", error);
                                    disableTail(targetSession, false);
                                }));
                        updateTabBadge(targetSession);
                    } else {
                        showError("Connection Failed", "Could not connect to " + finalServer.getHost());
                        disableTail(targetSession, false);
                    }
                }

                @Override
                protected void failed() {
                    hideLoading();
                    Throwable ex = getException();
                    logger.error("SSH connection failed", ex);
                    showError("Connection Failed", ex != null ? ex.getMessage() : "Unknown error");
                    disableTail(targetSession, false);
                }
            };
            Thread.ofVirtual().start(connectTask);
            return;
        }

        String sshServerId = (currentLogFromDb != null) ? currentLogFromDb.getSshServerID() : null;
        if (sshServerId == null) {
            showError("Error", "Server not found");
            disableTail();
            return;
        }
        SSHServerModel server = serverManagementService.getServerById(sshServerId);
        if (server == null) {
            showError("Error", "Server not found");
            disableTail();
            return;
        }

        String remotePath = (currentSession != null && currentSession.getRemotePath() != null)
                ? currentSession.getRemotePath()
                : (currentLogFromDb != null ? currentLogFromDb.getFilePath() : null);
        if (remotePath == null || remotePath.isEmpty()) {
            showError("Error", "Remote path is missing");
            disableTail();
            return;
        }

        if (remotePath.matches("^[A-Za-z]:[\\\\/].*") || remotePath.contains("\\") || remotePath.contains("seeloggyplus-")) {
            logger.error("Attempted to start remote tail with local Windows/temp path: {}", remotePath);
            showError("Invalid Remote Path", "This file is a local or temporary copy and cannot be streamed from remote SSH server: " + remotePath);
            disableTail();
            return;
        }

        // Logic to get password (saved or prompt)
        String password = server.getPassword();
        if (password == null || password.isBlank()) {
            com.seeloggyplus.util.PasswordPromptDialog prompt = new com.seeloggyplus.util.PasswordPromptDialog(
                    server.getHost(), server.getUsername());
            Optional<String> result = prompt.showAndWait();
            if (result.isPresent() && !result.get().isBlank()) {
                password = result.get();
            } else {
                disableTail();
                return;
            }
        }
        final String finalPassword = password;

        showLoading("Connecting to " + server.getHost() + "...");

        Task<Boolean> connectTask = new Task<>() {
            private final SSHServiceImpl sshService = new SSHServiceImpl();

            @Override
            protected Boolean call() throws Exception {
                return sshService.connect(server.getHost(), server.getPort(), server.getUsername(), finalPassword);
            }

            @Override
            protected void succeeded() {
                hideLoading();
                if (getValue()) {
                    // Assuming we have parsing config setup
                    startRemoteTail(remotePath, sshService, server); // Removed currentParsingConfig
                } else {
                    hideLoading();
                    showError("Connection Failed", "Could not connect to " + server.getHost());
                    disableTail();
                }
            }

            @Override
            protected void failed() {
                hideLoading();
                showError("Connection Error", getException() != null ? getException().getMessage() : "Failed to connect");
                disableTail();
            }
        };
        Thread.ofVirtual().start(connectTask);
    }

    private void disableTail() {
        disableTail(false);
    }

    /**
     * Disable tail mode. If {@code skipReindex} is true, the current file will NOT
     * be re-indexed (useful when a different file is about to be loaded immediately
     * after, avoiding a race between two async loadFileWithParallelParsing tasks).
     */
    private void disableTail(boolean skipReindex) {
        if (currentSession != null) {
            disableTail(currentSession, skipReindex);
        } else {
            tailModeEnabled = false;
            if (tailButton != null && tailButton.isSelected()) {
                isProgrammaticUpdate = true;
                tailButton.setSelected(false);
                isProgrammaticUpdate = false;
            }
        }
    }

    private void disableTail(LogSession session, boolean skipReindex) {
        if (session == null) return;
        session.setTailModeEnabled(false);

        if (session.getTailService() != null) {
            session.getTailService().stopTail();
        }
        if (session.getSshService() != null) {
            session.getSshService().stopTailing();
        }

        synchronized (session.getTailBuffer()) {
            session.getTailBuffer().clear();
        }
        // Do NOT clear liveTailList here so that logs remain visible and navigable!
        session.getTailFlushScheduled().set(false);
        session.setCurrentTailSearchPattern(null);
        session.setTailSearchScannedUpTo(0);

        if (session.getCanvasLogViewer() != null) {
            session.getCanvasLogViewer().exitTailMode();
        }
        updateTabBadge(session);

        if (session == currentSession) {
            tailModeEnabled = false;
            if (tailButton != null && tailButton.isSelected()) {
                isProgrammaticUpdate = true;
                tailButton.setSelected(false);
                isProgrammaticUpdate = false;
            }
            if (tailButton != null) {
                applyToggleStyle(tailButton, false);
            }
        }

        // For local files: re-index so new lines appended during tail are visible
        if (!skipReindex && session.getSessionType() == LogSession.SessionType.LOCAL
                && session.getLocalFile() != null && session.getLocalFile().exists()) {
            logger.info("Re-indexing local file after tail: {}", session.getLocalFile().getName());
            LogFile logFile = session.getLogFileRecord() != null ? session.getLogFileRecord() : getOrCreateLogFile(session.getLocalFile());
            if (logFile != null) {
                loadFileWithParallelParsing(session, session.getLocalFile(), logFile, false, true, false);
            }
        }

        logger.info("Tail mode DISABLED for session: {}", session.getTitle());
    }

    private void startRemoteTail(String remotePath, SSHServiceImpl sshService, SSHServerModel server) {
        startRemoteTail(remotePath, sshService, server, 0, -1);
    }

    private void startRemoteTail(String remotePath, SSHServiceImpl sshService, SSHServerModel server,
            int tailLines, int tailJumpIndex) {
        if (remotePath == null || remotePath.isBlank()) {
            showError("Invalid Remote Path", "Remote path cannot be empty.");
            return;
        }
        if (remotePath.matches("^[A-Za-z]:[\\\\/].*") || remotePath.contains("\\") || remotePath.contains("seeloggyplus-")) {
            logger.error("Attempted to start remote tail with Windows/local temp path: {}", remotePath);
            showError("Invalid Remote Path", "Windows local path cannot be streamed via remote SSH: " + remotePath);
            return;
        }

        // Check if this remote tail session is already open in an existing tab
        for (Map.Entry<Tab, LogSession> entry : sessionMap.entrySet()) {
            LogSession s = entry.getValue();
            if (s.getSessionType() == LogSession.SessionType.REMOTE &&
                    remotePath.equals(s.getRemotePath()) &&
                    s.getSshServer() != null && server != null &&
                    s.getSshServer().getId().equals(server.getId())) {
                if (logTabPane != null) {
                    logTabPane.getSelectionModel().select(entry.getKey());
                }
                return;
            }
        }

        LogFile tailLogFile = saveRemoteTailToRecent(remotePath, server);

        ParsingConfig rawConfig = ParsingConfig.createRawConfig();
        if (tailLogFile != null && tailLogFile.getParsingConfigurationID() != null) {
            rawConfig = parsingConfigService.findById(tailLogFile.getParsingConfigurationID())
                    .orElse(ParsingConfig.createRawConfig());
        } else if (currentLogFromDb != null && currentLogFromDb.getParsingConfigurationID() != null) {
            rawConfig = parsingConfigService.findById(currentLogFromDb.getParsingConfigurationID())
                    .orElse(ParsingConfig.createRawConfig());
        }

        String fileName = new File(remotePath).getName();
        String title = fileName + " (" + (server != null ? server.getName() : "Remote") + ")";
        LogSession session = new LogSession(title, LogSession.SessionType.REMOTE);
        session.setRemotePath(remotePath);
        session.setSshServer(server);
        session.setSshService(sshService);
        session.setLogFileRecord(tailLogFile != null ? tailLogFile : this.currentLogFromDb);
        session.setParsingConfig(rawConfig);
        session.setTailModeEnabled(true);
        session.setPendingTailJumpIndex(tailJumpIndex);

        Tab tab = createTabForSession(session);
        sessionMap.put(tab, session);
        if (logTabPane != null) {
            logTabPane.getTabs().add(tab);
            logTabPane.getSelectionModel().select(tab);
        }

        onActiveSessionChanged(session);
        if (session.getCanvasLogViewer() != null) {
            session.getCanvasLogViewer().resetView();
            session.getCanvasLogViewer().setTailBuffer(session.getLiveTailList());
        }
        updateTabBadge(session);
        updateTailButtonState();

        logger.info("ListView ready for config: {}", rawConfig.getName());

        int window = tailLines > 0 ? tailLines : tailWindowSize;
        sshService.tailFile(remotePath, window, line -> handleTailLineBackground(session, line),
                error -> Platform.runLater(() -> {
                    logger.error("Remote tail error for {}: {}", session.getTitle(), error);
                    showError("Remote Tail Error", error);
                    disableTail(session, false);
                }));
    }

    private LogFile saveRemoteTailToRecent(String remotePath, SSHServerModel server) {
        try {
            ParsingConfig parsingConfig = ParsingConfig.createRawConfig(); // Internal default
            String fileName = new File(remotePath).getName();
            String serverId = server != null ? server.getId() : null;
            LogFile logFile = logFileService.getLogFileByPathNameAndServer(fileName, remotePath, serverId, true);

            if (logFile == null) {
                logFile = new LogFile();
                logFile.setName(fileName);
                logFile.setFilePath(remotePath);
                logFile.setRemote(true);
                logFile.setSshServerID(serverId);
                logFile.setParsingConfigurationID(parsingConfig.getId());
                logFile.setModified(String.valueOf(System.currentTimeMillis()));
                logFile.setSize("-");

                logFileService.insertLogFile(logFile);
                logger.info("Created remote LogFile for tail: id={}, serverId={}, path={}", logFile.getId(), serverId, remotePath);
            } else {
                logFile.setRemote(true);
                logFile.setSshServerID(serverId);
                logFile.setParsingConfigurationID(parsingConfig.getId());
                logFile.setModified(String.valueOf(System.currentTimeMillis()));
                logFile.setSize("-"); // Reset size for tail
                logFileService.updateLogFile(logFile);
                logger.info("Updated existing LogFile for remote tail: id={}, serverId={}, path={}", logFile.getId(), serverId, remotePath);
            }

            Optional<RecentFile> existingRecentOpt = recentFileService.findByFileId(logFile.getId());
            boolean createdNewRecent;

            if (existingRecentOpt.isEmpty()) {
                RecentFile recent = new RecentFile();
                recent.setFileId(logFile.getId());
                recent.setLastOpened(LocalDateTime.now());

                recentFileService.save(logFile, recent);
                createdNewRecent = true;

                logger.info("Created new RecentFile for remote tail: fileId={}, serverId={}", logFile.getId(), serverId);
            } else {
                createdNewRecent = false;
                logger.info("Remote tail for existing recent fileId={}, NOT updating lastOpened (no re-sort)",
                        logFile.getId());
            }

            this.currentLogFromDb = logFile;
            this.monitoringRemotePath = remotePath;

            final String fServerId = serverId;
            Platform.runLater(() -> {
                if (createdNewRecent) {
                    refreshRecentFilesList();
                } else {
                    recentFilesListView.refresh();
                }
                selectRecentFile(remotePath, fServerId, true);
            });
            return logFile;
        } catch (Exception e) {
            logger.error("Failed to save remote tail to recent for path {}", remotePath, e);
            return null;
        }
    }

    private synchronized void handleTailLineBackground(String line) {
        if (currentSession != null) {
            handleTailLineBackground(currentSession, line);
        }
    }

    private void handleTailLineBackground(LogSession session, String line) {
        if (session == null || session.isClosed()) return;
        long lineNumber = session.incrementRemoteTailLineCounter();
        LogEntry entry = logParserService.parseLine(line, lineNumber);
        synchronized (session.getTailBuffer()) {
            if (session.getTailBuffer().size() >= MAX_TAIL_BUFFER_SIZE) {
                session.getTailBuffer().remove(0);
            }
            session.getTailBuffer().add(entry);
        }

        scheduleTailFlush(session);
    }

    private Predicate<LogEntry> buildSearchPredicate(String searchText, boolean isRegex, boolean caseSensitive) {
        final boolean hasTextSearch = searchText != null && !searchText.trim().isEmpty();
        final Pattern compiledIncludePattern;
        if (isRegex && hasTextSearch) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            try {
                compiledIncludePattern = Pattern.compile(searchText, flags);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid include regex: " + e.getMessage(), e);
            }
        } else {
            compiledIncludePattern = null;
        }

        return entry -> {
            if (hasTextSearch) {
                String raw = entry.getRawLog();
                if (raw == null) {
                    return false;
                }

                if (isRegex) {
                    return compiledIncludePattern.matcher(raw).find();
                } else {
                    return createBooleanSearchPredicate(searchText, caseSensitive).test(raw);
                }
            }
            return true;
        };
    }

    private Predicate<String> createBooleanSearchPredicate(String query, boolean caseSensitive) {
        if (query == null || query.trim().isEmpty()) {
            return s -> true;
        }

        String[] orParts = query.split("\\s+OR\\s+");
        Predicate<String> orPredicate = null;

        for (String orPart : orParts) {
            String[] andParts = orPart.split("\\s+AND\\s+");
            Predicate<String> andPredicate = null;

            for (String andPart : andParts) {
                String term = andPart.trim();
                boolean isNot = false;

                if (term.startsWith("NOT ")) {
                    isNot = true;
                    term = term.substring(4).trim();
                }

                final String finalTerm = caseSensitive ? term : term.toLowerCase();
                Predicate<String> termPredicate = raw -> {
                    if (raw == null)
                        return false;
                    String content = caseSensitive ? raw : raw.toLowerCase();
                    return content.contains(finalTerm);
                };

                if (isNot) {
                    termPredicate = termPredicate.negate();
                }

                if (andPredicate == null) {
                    andPredicate = termPredicate;
                } else {
                    andPredicate = andPredicate.and(termPredicate);
                }
            }

            if (andPredicate != null) {
                if (orPredicate == null) {
                    orPredicate = andPredicate;
                } else {
                    orPredicate = orPredicate.or(andPredicate);
                }
            }
        }

        return orPredicate != null ? orPredicate : s -> false;
    }

    private List<String> extractSearchTerms(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> terms = new LinkedHashSet<>();
        String[] orParts = query.split("\\s+OR\\s+");

        for (String orPart : orParts) {
            String[] andParts = orPart.split("\\s+AND\\s+");
            for (String andPart : andParts) {
                String term = andPart.trim();
                boolean isNot = false;

                if (term.startsWith("NOT ")) {
                    isNot = true;
                    term = term.substring(4).trim();
                }

                if (!isNot && !term.isEmpty()) {
                    terms.add(term);
                }
            }
        }

        return new ArrayList<>(terms);
    }

    private void setupSearchFieldAutoCompletion() {
        ContextMenu suggestionsMenu = new ContextMenu();
        MenuItem itemAnd = new MenuItem("AND");
        MenuItem itemOr = new MenuItem("OR");
        MenuItem itemNot = new MenuItem("NOT");
        itemAnd.setOnAction(e -> insertSearchTerm("AND"));
        itemOr.setOnAction(e -> insertSearchTerm("OR"));
        itemNot.setOnAction(e -> insertSearchTerm("NOT"));

        suggestionsMenu.getItems().addAll(itemAnd, itemOr, itemNot);

        searchField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE) {
                if (!suggestionsMenu.isShowing()) {
                    suggestionsMenu.show(searchField, Side.BOTTOM, 0, 0);
                }
            } else {
                suggestionsMenu.hide();
            }
        });
    }

    private void insertSearchTerm(String term) {
        String currentText = searchField.getText();
        int caretPosition = searchField.getCaretPosition();

        String before = currentText.substring(0, caretPosition);
        String after = currentText.substring(caretPosition);

        String toInsert = term;
        if (!before.isEmpty() && !before.endsWith(" ")) {
            toInsert = " " + toInsert;
        }
        if (!after.isEmpty() && !after.startsWith(" ")) {
            toInsert = toInsert + " ";
        } else if (after.isEmpty()) {
            toInsert = toInsert + " ";
        }

        searchField.insertText(caretPosition, toInsert);
        searchField.positionCaret(caretPosition + toInsert.length());
    }

    private void scheduleTailFlush() {
        if (currentSession != null) {
            scheduleTailFlush(currentSession);
        }
    }

    private void scheduleTailFlush(LogSession session) {
        if (session == null || !session.getTailFlushScheduled().compareAndSet(false, true)) {
            return;
        }

        Platform.runLater(() -> {
            session.getTailFlushScheduled().set(false);

            // Guard: don't flush if tail mode was disabled while this was queued
            if (!session.isTailModeEnabled() || session.isClosed()) {
                synchronized (session.getTailBuffer()) {
                    session.getTailBuffer().clear();
                }
                return;
            }

            try {
                List<LogEntry> toAdd;
                synchronized (session.getTailBuffer()) {
                    if (session.getTailBuffer().isEmpty()) {
                        return;
                    }
                    toAdd = new ArrayList<>(session.getTailBuffer());
                    session.getTailBuffer().clear();
                }

                int oldSize = session.getLiveTailList().size();
                session.getLiveTailList().addAll(toAdd);

                // Limit Logic here
                int trimmed = 0;
                if (session.getLiveTailList().size() > 50000) {
                    trimmed = session.getLiveTailList().size() - 50000;
                    session.getLiveTailList().subList(0, trimmed).clear();
                }

                if (session.isActive()) {
                    session.getCanvasLogViewer().refreshTail();

                    // Apply a pending tail jump once enough lines have arrived (Find in Files -> Tail).
                    int pendingJump = session.getPendingTailJumpIndex();
                    if (pendingJump >= 0) {
                        if (trimmed > 0) {
                            pendingJump -= trimmed;
                        }
                        if (pendingJump < 0) {
                            session.setPendingTailJumpIndex(-1);
                        } else if (pendingJump < session.getLiveTailList().size()) {
                            session.setPendingTailJumpIndex(-1);
                            session.getCanvasLogViewer().jumpToLine(pendingJump);
                            session.getCanvasLogViewer().selectLine(pendingJump);
                            session.getCanvasLogViewer().requestCanvasFocus();
                        } else {
                            session.setPendingTailJumpIndex(pendingJump);
                        }
                    }

                    // Incremental search: scan new lines and append results to the result panel
                    if (session == currentSession && currentTailSearchPattern != null && filteredIndexes != null) {
                        long tailOffset = canvasLogViewer != null ? canvasLogViewer.getFileLineCount() : 0;

                        // If buffer was trimmed, adjust existing filteredIndexes in-place
                        if (trimmed > 0) {
                            int removedMatchCount = 0;
                            int writeIdx = 0;
                            for (int i = 0; i < filteredIndexes.size(); i++) {
                                int shifted = filteredIndexes.get(i) - trimmed;
                                if (shifted >= (int) tailOffset) {
                                    filteredIndexes.set(writeIdx++, shifted);
                                } else {
                                    removedMatchCount++;
                                }
                            }
                            // Remove trailing entries that were compacted
                            while (filteredIndexes.size() > writeIdx) {
                                filteredIndexes.removeLast();
                            }
                            tailSearchScannedUpTo = Math.max(0, tailSearchScannedUpTo - trimmed);
                            if (removedMatchCount > 0 && searchResultPanel != null) {
                                searchResultPanel.trimFromStart(removedMatchCount);
                            }
                        }

                        // Scan ONLY lines that haven't been scanned yet
                        IntArrayList newMatches = new IntArrayList();
                        Predicate<String> pred = currentTailSearchPredicate != null ? currentTailSearchPredicate
                                : (currentTailSearchPattern != null ? (s -> currentTailSearchPattern.matcher(s).find()) : null);
                        if (pred != null) {
                            for (int i = tailSearchScannedUpTo; i < session.getLiveTailList().size(); i++) {
                                LogEntry entry = session.getLiveTailList().get(i);
                                String raw = entry != null ? entry.getRawLog() : "";
                                if (pred.test(raw)) {
                                    int globalIdx = (int) (tailOffset + i);
                                    filteredIndexes.add(globalIdx);
                                    newMatches.add(globalIdx);
                                }
                            }
                        }
                        tailSearchScannedUpTo = session.getLiveTailList().size();
                        session.setTailSearchScannedUpTo(tailSearchScannedUpTo);

                        if (newMatches.size() > 0 && searchResultPanel != null) {
                            searchResultPanel.syncItemCount();
                        }
                        if (searchNavigator != null) {
                            searchNavigator.setMatchCount(filteredIndexes.size());
                        }
                    } else if (session == currentSession && canvasLogViewer != null && canvasLogViewer.hasSearchHighlight()) {
                        if (searchNavigator != null) {
                            searchNavigator.setMatchCount(canvasLogViewer.countMatches());
                        }
                    }

                    if (session == currentSession && statusLabel != null) {
                        updateBottomBarLineCount(session);
                    }
                } else {
                    // DORMANT BACKGROUND TAB: Do not render canvas, just update unread badge!
                    session.addUnreadTailLines(toAdd.size());
                    updateTabBadge(session);
                }

                if (!session.isTailColumnsAutoResized()) {
                    session.setTailColumnsAutoResized(true);
                    logger.debug("Tail batch displayed for session {}: {} lines", session.getTitle(), toAdd.size());
                }
            } catch (Throwable t) {
                logger.error("CRITICAL ERROR in tail flush for session {}", session.getTitle(), t);
            }
        });
    }

    private void stopRemoteTail() {
        if (activeTailSshService != null) {
            activeTailSshService.stopTailing();
            activeTailSshService = null;
        }

        monitoringRemotePath = null;
        Platform.runLater(() -> recentFilesListView.refresh());
    }

    private void cleanupTempFiles() {
        // Scan both system temp dir and custom download dir
        Set<String> dirsToClean = new java.util.LinkedHashSet<>();
        dirsToClean.add(System.getProperty("java.io.tmpdir"));
        if (sshDownloadDirectory != null && !sshDownloadDirectory.isBlank()) {
            dirsToClean.add(sshDownloadDirectory);
        }

        Set<String> activeDownloadPaths = sessionMap.values().stream()
                .filter(session -> !session.isClosed() && session.getLocalFile() != null)
                .map(session -> {
                    try {
                        return session.getLocalFile().getCanonicalPath();
                    } catch (IOException e) {
                        return session.getLocalFile().getAbsolutePath();
                    }
                })
                .collect(java.util.stream.Collectors.toSet());
        int totalSuccess = 0;
        int totalFail = 0;

        for (String dirPath : dirsToClean) {
            try {
                File dir = new File(dirPath);
                if (!dir.exists() || !dir.isDirectory()) continue;

                File[] files = dir.listFiles((d, name) -> name.startsWith("seeloggyplus-"));
                if (files == null || files.length == 0) continue;

                for (File f : files) {
                    try {
                        String canonicalPath = f.getCanonicalPath();
                        if (activeDownloadPaths.contains(canonicalPath)) {
                            logger.debug("Preserving active download temp file: {}", f.getAbsolutePath());
                            continue;
                        }
                        if (f.delete()) {
                            totalSuccess++;
                            logger.info("Deleted temp file: {}", f.getAbsolutePath());
                        } else {
                            totalFail++;
                        }
                    } catch (Exception ex) {
                        totalFail++;
                        logger.error("Error deleting temp file: {}", f.getAbsolutePath(), ex);
                    }
                }
            } catch (Exception e) {
                logger.error("Error cleaning directory: {}", dirPath, e);
            }
        }

        logger.info("Temp cleanup completed. Deleted: {}, Failed: {}", totalSuccess, totalFail);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(message);
            showAndWaitAndRestore(alert);
        });
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(title);
            alert.setContentText(message);
            showAndWaitAndRestore(alert);
        });
    }

    private <T> Optional<T> showAndWaitAndRestore(Dialog<T> dialog) {
        if (menuBar == null || menuBar.getScene() == null || menuBar.getScene().getWindow() == null) {
            return dialog.showAndWait();
        }
        Stage mainStage = (Stage) menuBar.getScene().getWindow();
        boolean wasMaximized = mainStage.isMaximized();
        double oldX = mainStage.getX();
        double oldY = mainStage.getY();
        double oldWidth = mainStage.getWidth();
        double oldHeight = mainStage.getHeight();

        if (dialog.getOwner() == null) {
            dialog.initOwner(mainStage);
        }

        Optional<T> result = dialog.showAndWait();

        Platform.runLater(() -> {
            if (wasMaximized) {
                mainStage.setMaximized(true);
            } else {
                if (mainStage.getX() != oldX || mainStage.getY() != oldY || mainStage.getWidth() != oldWidth
                        || mainStage.getHeight() != oldHeight) {
                    mainStage.setX(oldX);
                    mainStage.setY(oldY);
                    mainStage.setWidth(oldWidth);
                    mainStage.setHeight(oldHeight);
                }
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

    @FXML
    private void handleGC() {
        System.gc();
        updateMemoryStatus();
    }

    private void startMemoryMonitor() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> updateMemoryStatus()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        updateMemoryStatus();
    }

    private void updateMemoryStatus() {
        if (memoryStatusLabel == null || memoryBar == null) {
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        long max = runtime.maxMemory();

        double usedMb = used / (1024.0 * 1024.0);
        double totalMb = total / (1024.0 * 1024.0);
        double maxMb = max / (1024.0 * 1024.0);

        double progress = (max > 0) ? (double) used / max : (double) used / total;

        String text = String.format("Used: %.0f MB / Total: %.0f MB (Max: %.0f MB)", usedMb, totalMb, maxMb);
        memoryStatusLabel.setText(text);

        if (progress > 0.85) {
            memoryBar.setStyle("-fx-accent: #f44336; -fx-control-inner-background: #e0e0e0;"); // Red
        } else if (progress > 0.60) {
            memoryBar.setStyle("-fx-accent: #ff9800; -fx-control-inner-background: #e0e0e0;"); // Orange
        } else {
            memoryBar.setStyle("-fx-accent: #2196f3; -fx-control-inner-background: #e0e0e0;"); // Blue
        }

        memoryBar.setProgress(progress);
    }

    private void startLocalTail(File file) {
        if (currentSession != null) {
            startLocalTail(currentSession, file);
        } else {
            stopLocalTail();
            liveTailList.clear();
            if (canvasLogViewer != null) {
                canvasLogViewer.setTailBuffer(liveTailList);
                canvasLogViewer.refreshTail();
            }

            // If the file is already loaded in the viewer, skip initial context load
            // to avoid duplicating lines that are already displayed from the file index.
            boolean needsContext = (mappedFileReader == null || lineOffsetIndex == null);

            logger.info("Starting local tail (TailService) for: {} (loadContext={})", file.getAbsolutePath(),
                    needsContext);
            Thread starterThread = new Thread(() -> tailService.startLocalTail(
                    file,
                    this::handleTailLineBackground,
                    ex -> {
                        if (ex instanceof InterruptedException) {
                            // Normal shutdown, ignore
                            return;
                        }
                        if (ex instanceof FileNotFoundException) {
                            Platform.runLater(() -> showInfo("Tail Info", "File not found or renamed."));
                        } else {
                            logger.error("Tailer error", ex);
                        }
                    },
                    needsContext, tailWindowSize), "TailService-Starter");
            starterThread.setDaemon(true);
            starterThread.start();
        }
    }

    private void startLocalTail(LogSession session, File file) {
        if (session == null || file == null) return;
        if (session.getTailService() != null) {
            session.getTailService().stopTail();
        } else {
            session.setTailService(new TailServiceImpl());
        }
        boolean needsContext = (session.getReader() == null || session.getIndex() == null);
        if (needsContext) {
            session.getLiveTailList().clear();
        }
        if (session.getCanvasLogViewer() != null) {
            session.getCanvasLogViewer().setTailBuffer(session.getLiveTailList());
            if (session.isActive()) {
                session.getCanvasLogViewer().refreshTail();
            }
        }
        logger.info("Starting local tail for session '{}': {} (loadContext={})", session.getTitle(),
                file.getAbsolutePath(), needsContext);

        Thread starterThread = new Thread(() -> session.getTailService().startLocalTail(
                file,
                line -> handleTailLineBackground(session, line),
                ex -> {
                    if (ex instanceof InterruptedException) return;
                    if (ex instanceof FileNotFoundException) {
                        Platform.runLater(() -> showInfo("Tail Info", "File not found or renamed."));
                    } else {
                        logger.error("Tailer error for session " + session.getTitle(), ex);
                    }
                },
                needsContext, tailWindowSize), "TailService-Starter-" + session.getId());
        starterThread.setDaemon(true);
        starterThread.start();
        updateTabBadge(session);
    }

    @FXML
    public void handleGoToLine() {
        if (canvasLogViewer == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Go to Line");
        dialog.setHeaderText("Enter line number:");
        dialog.setContentText("Line number:");

        try {
            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            addAppIcon(stage);
        } catch (Exception e) {
            logger.warn("failed to load icon");
        }

        showAndWaitAndRestore(dialog).ifPresent(result -> {
            try {
                String clean = result.replaceAll("[,.]", "").trim();
                long line = Long.parseLong(clean);
                if (line > 0 && canvasLogViewer != null) {
                    canvasLogViewer.jumpToLine(line - 1);
                }
                followTailButton.setSelected(false);
            } catch (NumberFormatException e) {
                logger.warn("Invalid line number");
            }
        });
    }

    private void stopLocalTail() {
        if (currentSession != null && currentSession.getTailService() != null) {
            currentSession.getTailService().stopTail();
        }
        if (tailService != null) {
            tailService.stopTail();
        }
    }

    // ==================== Tab Management & Lifecycle ====================

    private Tab createTabForSession(LogSession session) {
        Tab tab = new Tab();
        String rawTitle = session.getTitle();
        if (rawTitle != null && rawTitle.matches("^seeloggyplus-\\d+-(.+)$")) {
            rawTitle = rawTitle.replaceFirst("^seeloggyplus-\\d+-", "");
            session.setTitle(rawTitle);
        }
        tab.setText(session.getTitle());
        session.setTab(tab);

        CanvasLogViewer viewer = new CanvasLogViewer();
        viewer.setTailBuffer(session.getLiveTailList());
        session.setCanvasLogViewer(viewer);
        setupViewerCallbacks(session, viewer);

        tab.setContent(viewer);

        HBox graphicBox = new HBox(4);
        graphicBox.setAlignment(Pos.CENTER_LEFT);

        Circle liveDot = new Circle(3.5);
        liveDot.getStyleClass().add("tab-live-indicator");
        liveDot.setFill(Color.web("#4CAF50"));
        liveDot.setVisible(session.isTailModeEnabled());
        liveDot.setManaged(session.isTailModeEnabled());
        session.setLiveIndicatorDot(liveDot);

        Label badge = new Label();
        badge.getStyleClass().add("tab-badge-unread");
        badge.setVisible(false);
        badge.setManaged(false);
        session.setUnreadBadgeLabel(badge);

        graphicBox.getChildren().addAll(liveDot, badge);
        graphicBox.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.MIDDLE) {
                event.consume();
                closeSession(session);
            } else {
                Platform.runLater(() -> {
                    if (session.getCanvasLogViewer() != null) {
                        session.getCanvasLogViewer().requestCanvasFocus();
                    }
                });
            }
        });
        tab.setGraphic(graphicBox);

        tab.setOnSelectionChanged(event -> {
            if (tab.isSelected()) {
                Platform.runLater(() -> {
                    if (session.getCanvasLogViewer() != null) {
                        session.getCanvasLogViewer().requestCanvasFocus();
                    }
                });
            }
        });

        String tooltipText = session.getSessionType() == LogSession.SessionType.LOCAL
                && session.getLocalFile() != null ? session.getLocalFile().getAbsolutePath()
                : (session.getRemotePath() != null ? session.getRemotePath() : session.getTitle());
        tab.setTooltip(new Tooltip(tooltipText));

        tab.setContextMenu(createTabContextMenu(session));

        tab.setOnCloseRequest(event -> {
            event.consume();
            closeSession(session);
        });

        return tab;
    }

    private ContextMenu createTabContextMenu(LogSession session) {
        ContextMenu menu = new ContextMenu();

        MenuItem closeItem = new MenuItem("Close Tab");
        closeItem.setOnAction(e -> closeSession(session));

        MenuItem closeRightItem = new MenuItem("Close Tabs to the Right");
        closeRightItem.setOnAction(e -> handleCloseTabsToTheRight(session));

        MenuItem closeOthersItem = new MenuItem("Close Other Tabs");
        closeOthersItem.setOnAction(e -> handleCloseOtherTabs(session));

        MenuItem closeAllItem = new MenuItem("Close All Tabs");
        closeAllItem.setOnAction(e -> handleCloseAllTabs());

        MenuItem reloadItem = new MenuItem("Reload");
        reloadItem.setOnAction(e -> {
            if (session.getSessionType() == LogSession.SessionType.LOCAL && session.getLocalFile() != null) {
                loadFileWithParallelParsing(session, session.getLocalFile(), session.getLogFileRecord(), false, false, false);
            }
        });

        MenuItem copyPathItem = new MenuItem("Copy File Path");
        copyPathItem.setOnAction(e -> {
            String path = session.getLocalFile() != null ? session.getLocalFile().getAbsolutePath()
                    : (session.getRemotePath() != null ? session.getRemotePath() : "");
            if (!path.isEmpty()) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent cc = new ClipboardContent();
                cc.putString(path);
                clipboard.setContent(cc);
            }
        });

        MenuItem showInExplorerItem = new MenuItem("Show in File Explorer");
        showInExplorerItem.setOnAction(e -> {
            if (session.getLocalFile() != null && session.getLocalFile().exists()) {
                try {
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        new ProcessBuilder("explorer.exe", "/select,", session.getLocalFile().getAbsolutePath()).start();
                    } else {
                        java.awt.Desktop.getDesktop().open(session.getLocalFile().getParentFile());
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to open file explorer for {}", session.getLocalFile(), ex);
                }
            }
        });

        menu.getItems().addAll(closeItem, closeRightItem, closeOthersItem, closeAllItem, new SeparatorMenuItem(), reloadItem, new SeparatorMenuItem(), copyPathItem, showInExplorerItem);
        return menu;
    }

    private void setupViewerCallbacks(LogSession session, CanvasLogViewer viewer) {
        if (session == null || viewer == null) return;

        viewer.setOnLineClick((lineNumber, lineContent) -> {
            session.setSelectedLine(lineNumber);
            session.setSelectedLineContent(lineContent);
            if (session == currentSession) {
                displayLogDetailFromCanvas(lineNumber, lineContent);
            }
        });

        viewer.setOnStatusUpdate((currentLine, totalLines, percentage) -> {
            if (session == currentSession && statusLabel != null) {
                if (totalLines <= 0) {
                    statusLabel.setText("Line: 0 / 0");
                } else {
                    statusLabel.setText(String.format("Line: %,d / %,d (%.1f%%)", currentLine, totalLines, percentage));
                }
            }
        });

        viewer.setOnFollowTailChanged(following -> {
            session.setFollowTail(following);
            if (session == currentSession && followTailButton != null && followTailButton.isSelected() != following) {
                isProgrammaticUpdate = true;
                followTailButton.setSelected(following);
                isProgrammaticUpdate = false;
            }
        });
    }

    private void onTabSelected(Tab oldTab, Tab newTab) {
        if (oldTab != null) {
            LogSession oldSession = sessionMap.get(oldTab);
            if (oldSession != null) {
                oldSession.setActive(false);
                oldSession.setSearchQuery(searchField != null ? searchField.getText() : "");
                oldSession.setRegex(regexCheckBox != null && regexCheckBox.isSelected());
                oldSession.setCaseSensitive(caseSensitiveCheckBox != null && caseSensitiveCheckBox.isSelected());
                oldSession.setFilteredIndexes(filteredIndexes);
                oldSession.setCurrentMatchIndex(currentMatchIndex);
                oldSession.setCurrentTailSearchPattern(currentTailSearchPattern);
                oldSession.setTailSearchScannedUpTo(tailSearchScannedUpTo);
            }
        }

        if (newTab != null) {
            LogSession newSession = sessionMap.get(newTab);
            onActiveSessionChanged(newSession);
        } else {
            onActiveSessionChanged(null);
        }

        updateEmptyState();
    }

    private void onActiveSessionChanged(LogSession session) {
        if (currentSession != null && currentSession != session) {
            currentSession.setActive(false);
        }

        currentSession = session;

        if (session != null) {
            session.setActive(true);
            session.resetUnreadTailLines();
            updateTabBadge(session);

            canvasLogViewer = session.getCanvasLogViewer();
            if (canvasLogViewer != null) {
                canvasLogViewer.setTailBuffer(session.getLiveTailList());
            }
            mappedFileReader = session.getReader();
            lineOffsetIndex = session.getIndex();
            totalEntries = session.getTotalEntries();
            currentFile = session.getLocalFile();
            currentLogFromDb = session.getLogFileRecord();
            currentParsingConfig = session.getParsingConfig() != null ? session.getParsingConfig() : ParsingConfig.createRawConfig();
            tailModeEnabled = session.isTailModeEnabled();
            filteredIndexes = session.getFilteredIndexes();
            currentMatchIndex = session.getCurrentMatchIndex();
            liveTailList = session.getLiveTailList();
            currentTailSearchPattern = session.getCurrentTailSearchPattern();
            tailSearchScannedUpTo = session.getTailSearchScannedUpTo();
            lastAppliedSearchQuery = session.getSearchQuery();
            lastAppliedRegex = session.isRegex();
            lastAppliedCaseSensitive = session.isCaseSensitive();

            isSkippingFilterTrigger = true;
            if (searchField != null) {
                searchField.setText(session.getSearchQuery() != null ? session.getSearchQuery() : "");
            }
            if (regexCheckBox != null) {
                regexCheckBox.setSelected(session.isRegex());
            }
            if (caseSensitiveCheckBox != null) {
                caseSensitiveCheckBox.setSelected(session.isCaseSensitive());
            }
            isSkippingFilterTrigger = false;

            isProgrammaticUpdate = true;
            if (tailButton != null) {
                tailButton.setSelected(session.isTailModeEnabled());
                configureToggleStyle(tailButton);
            }
            if (followTailButton != null) {
                followTailButton.setSelected(session.isFollowTail());
                configureToggleStyle(followTailButton);
            }
            isProgrammaticUpdate = false;

            if (canvasLogViewer != null) {
                canvasLogViewer.clearFilter();
                if (filteredIndexes != null) {
                    String searchText = session.getSearchQuery();
                    if (searchText != null && !searchText.isEmpty()) {
                        String highlightPattern = searchText;
                        boolean highlightIsRegex = session.isRegex();
                        if (!session.isRegex()) {
                            List<String> terms = extractSearchTerms(searchText);
                            if (!terms.isEmpty()) {
                                highlightPattern = terms.stream()
                                        .map(java.util.regex.Pattern::quote)
                                        .collect(java.util.stream.Collectors.joining("|"));
                                highlightIsRegex = true;
                            }
                        }
                        canvasLogViewer.setSearchHighlight(highlightPattern, highlightIsRegex, session.isCaseSensitive());
                    }
                } else {
                    canvasLogViewer.clearSearchHighlight();
                }
            }

            if (filteredIndexes != null && filteredIndexes.size() > 0) {
                if (searchNavigator != null) {
                    searchNavigator.setMatchCount(filteredIndexes.size());
                    if (currentMatchIndex > 0) {
                        searchNavigator.updateStatus(currentMatchIndex, filteredIndexes.size());
                    }
                }
                String searchText = session.getSearchQuery();
                if (searchText != null && !searchText.isEmpty()) {
                    Pattern compiledHighlight = null;
                    try {
                        int flags = session.isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
                        compiledHighlight = Pattern.compile(
                                session.isRegex() ? searchText : Pattern.quote(searchText), flags);
                    } catch (Exception ignored) {}
                    if (session.isTailModeEnabled()) {
                        showSearchResultPanelForTail(filteredIndexes, compiledHighlight);
                    } else {
                        showSearchResultPanel(filteredIndexes, compiledHighlight);
                    }
                }
            } else {
                hideSearchResultPanel();
                if (searchNavigator != null) {
                    searchNavigator.clear();
                }
            }

            if (session.getSelectedLineContent() != null) {
                displayLogDetailFromCanvas(session.getSelectedLine(), session.getSelectedLineContent());
            } else {
                clearDetail();
            }

            if (session.getCanvasLogViewer() != null) {
                if (session.isTailModeEnabled()) {
                    session.getCanvasLogViewer().refreshTail();
                } else {
                    session.getCanvasLogViewer().redraw();
                }
                Platform.runLater(() -> {
                    if (session.getCanvasLogViewer() != null) {
                        session.getCanvasLogViewer().requestCanvasFocus();
                    }
                });
            }

            selectRecentFileForSession(session);
            updateTailButtonState();
            updateBottomBarLineCount(session);
        } else {
            canvasLogViewer = null;
            mappedFileReader = null;
            lineOffsetIndex = null;
            totalEntries = 0;
            currentFile = null;
            currentLogFromDb = null;
            tailModeEnabled = false;
            filteredIndexes = null;
            currentMatchIndex = -1;

            isSkippingFilterTrigger = true;
            if (searchField != null) {
                searchField.clear();
            }
            isSkippingFilterTrigger = false;

            hideSearchResultPanel();
            if (searchNavigator != null) {
                searchNavigator.clear();
            }
            clearDetail();
            updateTailButtonState();
            if (recentFilesListView != null) {
                Platform.runLater(() -> recentFilesListView.getSelectionModel().clearSelection());
            }
            updateBottomBarLineCount(null);
        }

        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean hasTabs = logTabPane != null && !logTabPane.getTabs().isEmpty();
        if (emptyStatePane != null) {
            emptyStatePane.setVisible(!hasTabs);
            emptyStatePane.setManaged(!hasTabs);
        }
        if (logTabPane != null) {
            logTabPane.setVisible(hasTabs);
            logTabPane.setManaged(hasTabs);
        }

        if (closeTabMenuItem != null) {
            closeTabMenuItem.setDisable(!hasTabs);
        }
        if (closeAllTabsMenuItem != null) {
            closeAllTabsMenuItem.setDisable(!hasTabs);
        }
        if (searchField != null) {
            searchField.setDisable(!hasTabs);
        }
        if (searchButton != null) {
            searchButton.setDisable(!hasTabs);
        }
        if (tailButton != null) {
            tailButton.setDisable(!hasTabs);
        }
        if (followTailButton != null) {
            followTailButton.setDisable(!hasTabs);
        }
    }

    private void updateTabBadge(LogSession session) {
        if (session == null) return;
        Platform.runLater(() -> {
            if (session.getLiveIndicatorDot() != null) {
                boolean tailing = session.isTailModeEnabled();
                session.getLiveIndicatorDot().setVisible(tailing);
                session.getLiveIndicatorDot().setManaged(tailing);
            }
            Tab tab = session.getTab();
            if (tab != null) {
                int unread = session.getUnreadTailLines();
                if (unread > 0 && !session.isActive()) {
                    tab.setText(session.getTitle() + " (+" + (unread > 999 ? "999+" : unread) + ")");
                } else {
                    tab.setText(session.getTitle());
                }
            }
            if (session.getUnreadBadgeLabel() != null) {
                session.getUnreadBadgeLabel().setVisible(false);
                session.getUnreadBadgeLabel().setManaged(false);
            }
        });
    }

    private void closeSession(LogSession session) {
        if (session == null) return;

        if (session.isTailModeEnabled()) {
            disableTail(session, true);
        }

        session.close();

        Tab tab = session.getTab();
        if (tab != null && logTabPane != null) {
            sessionMap.remove(tab);
            logTabPane.getTabs().remove(tab);
        }

        if (currentSession == session) {
            Tab nextSelected = logTabPane != null ? logTabPane.getSelectionModel().getSelectedItem() : null;
            if (nextSelected != null) {
                onActiveSessionChanged(sessionMap.get(nextSelected));
            } else {
                onActiveSessionChanged(null);
            }
        }

        updateEmptyState();
        logger.info("Closed session: {}", session.getTitle());
    }

    @FXML
    public void handleCloseCurrentTab() {
        if (currentSession != null) {
            closeSession(currentSession);
        }
    }

    @FXML
    public void handleCloseAllTabs() {
        List<LogSession> sessions = new ArrayList<>(sessionMap.values());
        for (LogSession s : sessions) {
            closeSession(s);
        }
    }

    private void handleCloseOtherTabs(LogSession keepSession) {
        List<LogSession> sessions = new ArrayList<>(sessionMap.values());
        for (LogSession s : sessions) {
            if (s != keepSession) {
                closeSession(s);
            }
        }
    }

    private void handleCloseTabsToTheRight(LogSession session) {
        if (logTabPane == null || session == null || session.getTab() == null) return;
        int targetIdx = logTabPane.getTabs().indexOf(session.getTab());
        if (targetIdx < 0) return;

        List<Tab> tabsToClose = new ArrayList<>();
        for (int i = targetIdx + 1; i < logTabPane.getTabs().size(); i++) {
            tabsToClose.add(logTabPane.getTabs().get(i));
        }

        for (Tab t : tabsToClose) {
            LogSession s = sessionMap.get(t);
            if (s != null) {
                closeSession(s);
            }
        }
    }

    private boolean isNodeRelated(javafx.scene.Node a, javafx.scene.Node b) {
        if (a == b) return true;
        javafx.scene.Node p = b;
        while (p != null) {
            if (p == a) return true;
            p = p.getParent();
        }
        p = a;
        while (p != null) {
            if (p == b) return true;
            p = p.getParent();
        }
        return false;
    }

    private void selectNextTab() {
        if (logTabPane != null && logTabPane.getTabs().size() > 1) {
            int current = logTabPane.getSelectionModel().getSelectedIndex();
            int next = (current + 1) % logTabPane.getTabs().size();
            logTabPane.getSelectionModel().select(next);
        }
    }

    private void selectPreviousTab() {
        if (logTabPane != null && logTabPane.getTabs().size() > 1) {
            int current = logTabPane.getSelectionModel().getSelectedIndex();
            int total = logTabPane.getTabs().size();
            int prev = (current - 1 + total) % total;
            logTabPane.getSelectionModel().select(prev);
        }
    }

    private void setupDragAndDrop() {
        if (logContainer == null) return;

        logContainer.setOnDragOver(event -> {
            if (event.getGestureSource() != logContainer && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        logContainer.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                for (File file : files) {
                    if (file.isFile()) {
                        openLocalLogFile(file, true);
                        success = true;
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }
}
