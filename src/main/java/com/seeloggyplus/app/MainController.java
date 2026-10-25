package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.ThemedAlerts;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;

import com.seeloggyplus.features.recent.infrastructure.RecentConfigServiceImpl;
import com.seeloggyplus.features.recent.application.RecentFileService;
import com.seeloggyplus.shared.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.app.composition.LogServices;
import com.seeloggyplus.shared.progress.ProgressCallback;
import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.features.servers.infrastructure.ServerManagementServiceImpl;
import com.seeloggyplus.shared.tail.TailServiceImpl;
import com.seeloggyplus.shared.ui.SshConnectFlow;
import com.seeloggyplus.features.recent.presentation.RecentFilesPanelController;
import com.seeloggyplus.shared.util.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.scene.Cursor;
import javafx.util.Duration;

import com.seeloggyplus.features.recent.domain.RecentFile;
import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.features.detail.presentation.LogDetailPaneController;
import com.seeloggyplus.features.update.presentation.UpdateFeature;
import com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController;
import com.seeloggyplus.shared.model.*;
import com.seeloggyplus.shared.logs.port.LogFileService;
import com.seeloggyplus.shared.logs.port.LogParser;
import com.seeloggyplus.shared.logs.port.ParsingConfigService;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import com.seeloggyplus.features.servers.presentation.ServerManagementDialogController;
import com.seeloggyplus.shared.servers.ServerCatalog;
import com.seeloggyplus.shared.tail.TailService;
import com.seeloggyplus.shared.ui.DialogPresenter;
import com.seeloggyplus.shared.ui.DialogWindowSupport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.application.Platform;
import javafx.scene.shape.Circle;
import javafx.concurrent.Task;
import com.seeloggyplus.shared.ui.RoundedPanelSupport;
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
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.seeloggyplus.features.search.presentation.SearchNavigator;
import com.seeloggyplus.features.search.presentation.SearchResultPanel;
import java.awt.Desktop;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javafx.geometry.Orientation;
import javafx.scene.image.Image;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;

public class MainController {

    private ObservableValue<Boolean> memoryMonitorShowing;

    private final LogServices suppliedLogServices;

    public MainController() {
        suppliedLogServices = null;
    }

    public MainController(LogServices logServices) {
        suppliedLogServices = java.util.Objects.requireNonNull(logServices);
    }

    private boolean sceneKeyNavInstalled = false;

    private static final double TAB_HEADER_SCROLL_THRESHOLD = 30;
    private static final long TAB_HEADER_SCROLL_COOLDOWN_MS = 260;
    private static final double MIN_LOG_TAB_CONTENT_WIDTH = 60;
    private static final double MAX_LOG_TAB_CONTENT_WIDTH = 220;

    private static final double LOG_TAB_HORIZONTAL_PADDING = 29;
    private static final double LOG_TAB_OVERFLOW_RESERVE = 40;
    private double tabHeaderScrollAccumulator = 0;
    private long lastTabHeaderSwitchAt = 0;

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
    private MenuItem storeMenuItem;

    @FXML
    private MenuItem serverManagementMenuItem;
    @FXML
    private MenuItem aboutMenuItem;
    @FXML
    private MenuItem helpContentsMenuItem;
    @FXML
    private MenuItem preferencesMenuItem;
    @FXML
    private MenuItem refreshMenuItem;
    @FXML private MenuItem clearLogMenuItem;

    @FXML
    private SplitPane horizontalSplitPane;
    @FXML
    private SplitPane verticalSplitPane;

    @FXML private ToolBar workbenchToolbar;
    @FXML private Button openFileButton;
    @FXML private Button serversButton;
    @FXML private Label tailStatusLabel;
    @FXML private Label resourceDetailLabel;
    @FXML private RadioMenuItem resultsAutoMenuItem;
    @FXML private RadioMenuItem resultsRightMenuItem;
    @FXML private RadioMenuItem resultsBottomMenuItem;
    private WorkbenchDock workbenchDock;
    private final java.util.concurrent.ExecutorService layoutPreferenceWriter = new java.util.concurrent.ThreadPoolExecutor(0, 1, 5,
            java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1), r -> {
        Thread thread = new Thread(r, "workbench-preferences"); thread.setDaemon(true); return thread;
    }, new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private VBox leftPanel;
    @FXML
    private VBox recentPanel;
    @FXML
    private RecentFilesPanelController recentPanelController;

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
    private VBox logIsland;
    @FXML
    private TabPane logTabPane;
    @FXML
    private VBox emptyStatePane;

    private final TabSessionManager tabSessionManager = new TabSessionManager();

    @FXML
    private StackPane centerRoot;
    @FXML
    private Rectangle centerClip;
    @FXML
    private ToggleButton tailButton;

    @FXML
    private VBox detailPane;
    @FXML
    private LogDetailPaneController detailPaneController;

    @FXML
    private ToggleButton toggleLeftPanelButton;
    @FXML
    private ToggleButton toggleBottomPanelButton;
    @FXML
    private SearchNavigator searchNavigator;

    private double lastVerticalDividerPos = 0.7;
    private double lastHorizontalDividerPos = 0.2;

    @FXML
    private Label statusLabel;
    @FXML
    private Label selectionStatusLabel;
    @FXML
    private Label memoryStatusLabel;
    @FXML
    private ProgressBar memoryBar;

    private ParsingConfigService parsingConfigService;
    private RecentFileService recentFileService;
    private LogParser logParserService;
    private LogFileService logFileService;
    private ServerCatalog serverManagementService;
    private DialogPresenter dialogPresenter = DialogPresenter.showAndWait();

    public void setDialogPresenter(DialogPresenter dialogPresenter) {
        this.dialogPresenter = dialogPresenter == null ? DialogPresenter.showAndWait() : dialogPresenter;
    }
    private PreferenceService preferenceService;
    private final SSHServiceFactory sshServiceFactory = SSHServiceImpl::new;

    private TailService tailService;

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private LogFile currentLogFromDb;


    private volatile ParsingConfig currentParsingConfig;
    private File currentFile;
    private boolean isLeftPanelPinned = true;

    private static class LastSession {
        enum Type { LOCAL, REMOTE }
        final Type type;
        final File localFile;
        final String remotePath;
        final LogFile logFromDb;
        final SSHService sshService;

        static LastSession local(File file) {
            return new LastSession(Type.LOCAL, file, null, null, null);
        }
        static LastSession remote(String path, LogFile logFile, SSHService ssh) {
            return new LastSession(Type.REMOTE, null, path, logFile, ssh);
        }
        private LastSession(Type t, File f, String rp, LogFile lf, SSHService ssh) {
            this.type = t; this.localFile = f; this.remotePath = rp;
            this.logFromDb = lf; this.sshService = ssh;
        }
    }
    private LastSession lastSession;

    private boolean isBottomPanelPinned = true;
    private Task<?> currentLoadingTask = null;
    private final AtomicLong downloadGeneration = new AtomicLong();
    private static final int MAX_TAIL_BUFFER_SIZE = 5000;
    private int sshDownloadThreads = 4;
    private String sshDownloadDirectory = "";
    private int tailWindowSize = 20000;
    private boolean tailModeEnabled = false;
    private SSHService activeTailSshService;
    private volatile SSHService activeDownloadSshService;
    private volatile File activeDownloadFile;
    private long remoteTailLineCounter = 0;
    private String monitoringRemotePath;
    private final Map<String, File> downloadedRemoteFiles = new ConcurrentHashMap<>();

    private final AtomicBoolean tailFlushScheduled = new AtomicBoolean(
            false);
    private boolean tailColumnsAutoResized = false;
    private boolean isProgrammaticUpdate = false;

    private Predicate<LogEntry> currentTailFilterPredicate = null;
    private Pattern currentTailSearchPattern = null;
    private Predicate<String> currentTailSearchPredicate = null;
    private int tailSearchScannedUpTo = 0;
    private boolean isSkippingFilterTrigger = false;

    private int totalEntries = 0;
    private IntArrayList filteredIndexes = null;

    private static final int SEARCH_WORKERS = Math.max(1, Math.min(Integer.getInteger("seeloggyplus.searchWorkers", 6),
            Runtime.getRuntime().availableProcessors()));
    private static final ForkJoinPool SEARCH_POOL = new ForkJoinPool(SEARCH_WORKERS);
    private CanvasLogViewer canvasLogViewer;
    private MappedFileReader mappedFileReader;
    private LineOffsetIndex lineOffsetIndex;
    private int currentMatchIndex = -1;
    private PauseTransition searchDebounce;
    private Task<?> currentSearchTask;
    private Future<?> currentTailSearchTask;
    private Object incrementalTailSearchToken;
    private final AtomicLong searchGeneration = new AtomicLong();
    private String lastAppliedSearchQuery = null;
    private boolean lastAppliedRegex = false;
    private boolean lastAppliedCaseSensitive = false;
    private SearchResultPanel searchResultPanel;
    private SplitPane searchSplitPane;

    private List<LogEntry> liveTailList = Collections.synchronizedList(new ArrayList<>());

    @FXML
    private ToggleButton followTailButton;

    private final LinkedList<LogEntry> tailBuffer = new LinkedList<>();

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");

        LogServices logServices = suppliedLogServices != null ? suppliedLogServices : LogServices.createDefault();
        parsingConfigService = logServices.parsingConfigs();
        recentFileService = new RecentConfigServiceImpl();
        preferenceService = new PreferenceServiceImpl();
        logParserService = logServices.parser();
        logFileService = logServices.files();
        serverManagementService = new ServerManagementServiceImpl();
        tailService = new TailServiceImpl();

        setupMenuBar();
        setupRecentPanel();
        setupCenterPanel();
        setupBottomPanel();
        setupKeyboardShortcuts();
        setupSearchFieldAutoCompletion();

        if (toggleLeftPanelButton != null) {
            toggleLeftPanelButton.setOnAction(e -> toggleLeftPanel());
        }
        if (toggleBottomPanelButton != null) {
            toggleBottomPanelButton.setOnAction(e -> toggleBottomPanel());
        }

        restorePanelVisibility();
        loadPreferences();
        setupWorkbench();

        updateTailButtonState();
        startMemoryMonitor();
        var updateFeature = new UpdateFeature(preferenceService);
        storeMenuItem.setVisible(com.seeloggyplus.shared.util.AppDistribution.isStore());
        updateFeature.reconcileUpdateStartup();
        updateFeature.showWhatsNewIfNeeded();

        if (logIsland != null && emptyStatePane != null && logContainer != null) {
            logIsland.minHeightProperty().bind(Bindings.createDoubleBinding(
                    () -> emptyStatePane.isManaged()
                            ? Math.max(120, Math.ceil(emptyStatePane.prefHeight(logContainer.getWidth()))) : 120,
                    emptyStatePane.managedProperty(), logContainer.widthProperty(),
                    emptyStatePane.getChildren().getFirst().layoutBoundsProperty()));
        }

        RoundedPanelSupport.apply(recentPanel, 4);
        RoundedPanelSupport.apply(logContainer, 4);
        RoundedPanelSupport.apply(detailPane, 4);

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
                || (tabSessionManager.getActive() != null && tabSessionManager.getActive().getSessionType() == LogSession.SessionType.REMOTE);
        boolean isLocal = (currentFile != null && currentFile.exists())
                || (tabSessionManager.getActive() != null && tabSessionManager.getActive().getSessionType() == LogSession.SessionType.LOCAL);

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

    private PauseTransition loadingDelay;

    private void showLoading(String message) {
        if (loadingOverlay == null) return;

        loadingLabel.setText(message);
        if (loadingProgress != null) {
            loadingProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        }

        if (loadingOverlay.isVisible()) return;

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

    private void setupRecentPanel() {
        leftPanel = recentPanel;
        recentPanelController.setOnCloseRequest(this::toggleLeftPanel);
        recentPanelController.start(recentFileService, logFileService, tabSessionManager,
                new RecentFilesPanelController.Host() {
                    @Override
                    public void onOpenRecent(RecentFilesDto recent) {
                        handleRecentFileSelected(recent);
                    }

                    @Override
                    public void closeSession(LogSession session) {
                        MainController.this.closeSession(session);
                    }

                    @Override
                    public void closeAllTabs() {
                        handleCloseAllTabs();
                    }

                    @Override
                    public void clearSearch() {
                        MainController.this.clearSearch();
                    }

                    @Override
                    public void stopRemoteTailIfMonitoring(String remotePath) {
                        if (remotePath != null && remotePath.equals(monitoringRemotePath)) {
                            stopRemoteTail();
                        }
                    }

                    @Override
                    public void stopRemoteTail() {
                        MainController.this.stopRemoteTail();
                    }

                    @Override
                    public void cleanupTempFiles() {
                        MainController.this.cleanupTempFiles();
                    }

                    @Override
                    public Optional<ButtonType> showConfirmation(Alert alert) {
                        return showAndWaitAndRestore(alert);
                    }

                    @Override
                    public String monitoringRemotePath() {
                        return monitoringRemotePath;
                    }
                });
        updateLeftPanelDisplay();
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

    private void setupCenterPanel() {
        if (followTailButton != null) {
            followTailButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (isProgrammaticUpdate) return;
                if (canvasLogViewer != null) {
                    canvasLogViewer.setFollowTail(newVal);
                }
                if (tabSessionManager.getActive() != null) {
                    tabSessionManager.getActive().setFollowTail(newVal);
                }
            });
        }

        if (logTabPane != null) {
            setupAdaptiveLogTabs();
            logTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                onTabSelected(oldTab, newTab);
            });

            if (menuBar != null) {
                menuBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null && !sceneKeyNavInstalled) {
                        sceneKeyNavInstalled = true;
                        newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalLogNavigation);
                    }
                });
            }

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

            logTabPane.addEventFilter(ScrollEvent.SCROLL, event -> {
                if (!isInTabHeaderArea(event.getTarget())) {
                    return;
                }
                double delta = event.getDeltaX();
                if (Math.abs(delta) < 0.01 && event.isShiftDown()) {
                    delta = event.getDeltaY();
                }
                if (Math.abs(delta) < 0.01) {
                    return;
                }
                event.consume();

                long now = System.currentTimeMillis();
                if (now - lastTabHeaderSwitchAt < TAB_HEADER_SCROLL_COOLDOWN_MS) {
                    return;
                }
                if (Math.signum(delta) != Math.signum(tabHeaderScrollAccumulator)) {
                    tabHeaderScrollAccumulator = 0;
                }
                tabHeaderScrollAccumulator += delta;
                if (Math.abs(tabHeaderScrollAccumulator) < TAB_HEADER_SCROLL_THRESHOLD) {
                    return;
                }
                int direction = tabHeaderScrollAccumulator > 0 ? 1 : -1;
                tabHeaderScrollAccumulator = 0;
                int index = logTabPane.getSelectionModel().getSelectedIndex();
                int next = index + direction;
                if (next >= 0 && next < logTabPane.getTabs().size()) {
                    logTabPane.getSelectionModel().select(next);
                    lastTabHeaderSwitchAt = now;
                }
            });

            logTabPane.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.MIDDLE) {
                    Node target = (Node) event.getTarget();
                    while (target != null && target != logTabPane) {
                        if (target.getStyleClass().contains("tab")) {
                            for (Tab t : logTabPane.getTabs()) {
                                if (t.getGraphic() != null && isNodeRelated(target, t.getGraphic())) {
                                    LogSession s = tabSessionManager.get(t);
                                    if (s != null) closeSession(s);
                                    event.consume();
                                    return;
                                }
                            }
                            Tab selected = logTabPane.getSelectionModel().getSelectedItem();
                            if (selected != null) {
                                LogSession s = tabSessionManager.get(selected);
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

        searchResultPanel = new SearchResultPanel();
        RoundedPanelSupport.apply(searchResultPanel, 4);
        searchResultPanel.setOnLineSelected((listIndex, globalLine, content) -> displayLogDetailFromCanvas(globalLine, content));
        searchResultPanel.setOnLineActivated((listIndex, globalLine) -> {
            revealWorkbenchDetail();
            if (canvasLogViewer != null) {
                canvasLogViewer.jumpToLine(globalLine);
                canvasLogViewer.selectLine(globalLine);
            }
            if (filteredIndexes != null && searchNavigator != null) {
                currentMatchIndex = listIndex + 1;
                if (tabSessionManager.getActive() != null) tabSessionManager.getActive().setCurrentMatchIndex(currentMatchIndex);
                searchNavigator.updateStatus(listIndex + 1, filteredIndexes.size());
            }
        });

        VBox logParent = (VBox) logContainer.getParent();
        int logIdx = logParent.getChildren().indexOf(logContainer);
        SplitPane searchSplit = new SplitPane();
        searchSplit.setOrientation(Orientation.HORIZONTAL);
        searchSplit.setMinSize(0, 0);
        searchSplit.getItems().add(logContainer);

        VBox.setVgrow(searchSplit, Priority.ALWAYS);
        logParent.getChildren().set(logIdx, searchSplit);
        this.searchSplitPane = searchSplit;
        workbenchDock = new WorkbenchDock(verticalSplitPane, searchSplit, logIsland, detailPane, searchResultPanel);

        setupDragAndDrop();
        updateEmptyState();

        searchField.setOnAction(e -> {
            boolean querySame = Objects.equals(searchField.getText(), lastAppliedSearchQuery)
                    && (regexCheckBox != null && regexCheckBox.isSelected() == lastAppliedRegex)
                    && (caseSensitiveCheckBox != null && caseSensitiveCheckBox.isSelected() == lastAppliedCaseSensitive);
            if (querySame && filteredIndexes != null && !filteredIndexes.isEmpty()) {
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

        searchDebounce = new PauseTransition(Duration.millis(300));
        searchDebounce.setOnFinished(evt -> performSearch());
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (isSkippingFilterTrigger) {
                return;
            }
            cancelSearchWork();
            if (newText != null && !newText.isEmpty()) {
                searchDebounce.playFromStart();
            } else {
                searchDebounce.stop();

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

        if (searchNavigator != null) {
            searchNavigator.setOnNext(this::navigateNextMatch);
            searchNavigator.setOnPrevious(this::navigatePreviousMatch);
        }


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

    private void handleGlobalLogNavigation(KeyEvent event) {
        if (event.isConsumed()) {
            return;
        }
        KeyCode code = event.getCode();
        boolean navigationKey = code == KeyCode.UP || code == KeyCode.DOWN
                || code == KeyCode.PAGE_UP || code == KeyCode.PAGE_DOWN
                || ((code == KeyCode.HOME || code == KeyCode.END) && event.isControlDown());
        if (!navigationKey || canvasLogViewer == null) {
            return;
        }
        if (usesArrowKeysForItself(event.getTarget())) {
            return;
        }
        canvasLogViewer.handleKeyNavigation(event);
        if (event.isConsumed()) {
            canvasLogViewer.requestCanvasFocus();
        }
    }

    private static boolean usesArrowKeysForItself(Object target) {
        if (!(target instanceof Node node)) {
            return true;
        }
        return node instanceof TextInputControl
                || node instanceof ListView
                || node instanceof TableView
                || node instanceof TreeView
                || node instanceof ScrollBar
                || node instanceof Slider
                || node instanceof MenuBar
                || node.getStyleClass().contains("styled-text-area");
    }

    private boolean isInTabHeaderArea(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        while (node != null) {
            if (node.getStyleClass().contains("tab-header-area")) {
                return true;
            }
            if (node == logTabPane) {
                return false;
            }
            node = node.getParent();
        }
        return false;
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
        detailPaneController.show(lineNumber, content);
    }

    private void cancelSearchWork() {
        searchGeneration.incrementAndGet();
        incrementalTailSearchToken = null;
        if (searchDebounce != null) searchDebounce.stop();
        if (currentSearchTask != null) currentSearchTask.cancel(true);
        if (currentTailSearchTask != null) currentTailSearchTask.cancel(true);
        hideLoading();
    }

    private boolean searchContextIsCurrent(long generation, LogSession session, MappedFileReader reader,
            LineOffsetIndex index, boolean tail) {
        return generation == searchGeneration.get() && tabSessionManager.getActive() == session
                && (session == null || (!session.isClosed() && session.getReader() == reader && session.getIndex() == index))
                && mappedFileReader == reader
                && lineOffsetIndex == index && tailModeEnabled == tail;
    }

    private void performSearch() {
        cancelSearchWork();
        final long thisGeneration = searchGeneration.get();
        final LogSession searchSession = tabSessionManager.getActive();
        final MappedFileReader searchReader = mappedFileReader;
        final LineOffsetIndex searchIndex = lineOffsetIndex;
        final boolean searchingTail = tailModeEnabled;
        final String searchText = searchField.getText();
        final boolean isRegex = regexCheckBox.isSelected();
        final boolean caseSensitive = caseSensitiveCheckBox.isSelected();

        logger.info("Search - Include: '{}', Regex: {}, CaseSensitive: {}",
                searchText, isRegex, caseSensitive);

        lastAppliedSearchQuery = null;

        if ((mappedFileReader == null || lineOffsetIndex == null) && !tailModeEnabled) {
            logger.warn("No file loaded for search");
            return;
        }

        if (searchText == null || searchText.trim().isEmpty()) {
            clearSearch();
            return;
        }

        if (isRegex) {
            try {
                Pattern.compile(searchText, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (java.util.regex.PatternSyntaxException error) {
                filteredIndexes = null;
                currentTailSearchPattern = null;
                currentTailSearchPredicate = null;
                if (searchSession != null) {
                    searchSession.setFilteredIndexes(null);
                    searchSession.setCurrentTailSearchPattern(null);
                }
                if (canvasLogViewer != null) canvasLogViewer.clearSearchHighlight();
                hideSearchResultPanel();
                if (searchNavigator != null) searchNavigator.showMessage("Invalid regular expression", error.getDescription());
                return;
            }
        }

        filteredIndexes = null;
        currentMatchIndex = -1;
        currentTailSearchPattern = null;
        currentTailSearchPredicate = null;
        if (searchSession != null) {
            searchSession.setFilteredIndexes(null);
            searchSession.setSearchQuery(searchText);
            searchSession.setRegex(isRegex);
            searchSession.setCaseSensitive(caseSensitive);
            searchSession.setCurrentTailSearchPattern(null);
        }
        hideSearchResultPanel();
        if (searchNavigator != null) searchNavigator.setMatchCount(-1);

        if (tailModeEnabled) {
            String highlightPattern = searchText;
            boolean highlightIsRegex = isRegex;

            if (!isRegex) {
                List<String> terms = extractSearchTerms(searchText);
                if (!terms.isEmpty()) {
                    highlightPattern = String.join("|", terms.stream()
                            .map(Pattern::quote)
                            .toList());
                    highlightIsRegex = true;
                }
            }

            final String finalHighlightPattern = highlightPattern;
            final boolean finalHighlightIsRegex = highlightIsRegex;

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
                snapshot = liveTailList instanceof com.seeloggyplus.shared.tail.TailHistory history
                        ? history.snapshot() : new ArrayList<>(liveTailList);
            }
            final int snapshotSize = snapshot.size();
            final long snapshotBase = snapshot instanceof com.seeloggyplus.shared.tail.TailHistory.Snapshot historySnapshot
                    ? historySnapshot.baseIndex() : 0;

            final Predicate<String> searchPredicate;
            if (isRegex) {
                searchPredicate = s -> pattern.matcher(s).find();
            } else {
                searchPredicate = createBooleanSearchPredicate(searchText, caseSensitive);
            }

            currentTailSearchTask = new java.util.concurrent.FutureTask<Void>(() -> {
                try {
                    IntArrayList results = new IntArrayList();
                    if (reader != null && index != null && tailOffset > 0) {
                        int fileLines = index.getLineCount();
                        for (int i = 0; i < fileLines; i++) {
                            if (Thread.currentThread().isInterrupted() || thisGeneration != searchGeneration.get()) return;
                            CharSequence line = reader.readLine(index, i);
                            if (line != null && searchPredicate.test(line.toString())) {
                                results.add(i);
                            }
                        }
                        logger.info("Tail search: {} matches in file lines (0..{})", results.size(), fileLines);
                    }

                    for (int i = 0; i < snapshot.size(); i++) {
                        if (Thread.currentThread().isInterrupted() || thisGeneration != searchGeneration.get()) return;
                        LogEntry entry = snapshot.get(i);
                        String raw = entry != null ? entry.getRawLog() : "";
                        if (searchPredicate.test(raw)) {
                            results.add((int) (tailOffset + i));
                        }
                    }

                    Platform.runLater(() -> {
                        if (!searchContextIsCurrent(thisGeneration, searchSession, searchReader, searchIndex, searchingTail)) return;
                        long trimmedSinceSnapshot = tailBase(searchSession.getLiveTailList()) - snapshotBase;
                        if (trimmedSinceSnapshot > 0) rebaseTailResults(results, tailOffset, trimmedSinceSnapshot);
                        int scanned = (int) Math.max(0, snapshotSize - trimmedSinceSnapshot);
                        lastAppliedSearchQuery = searchText;
                        lastAppliedRegex = isRegex;
                        lastAppliedCaseSensitive = caseSensitive;
                        this.filteredIndexes = results;
                        this.currentTailSearchPattern = pattern;
                        this.currentTailSearchPredicate = searchPredicate;
                        this.tailSearchScannedUpTo = scanned;

                        if (tabSessionManager.getActive() != null) {
                            tabSessionManager.getActive().setFilteredIndexes(results);
                            tabSessionManager.getActive().setSearchQuery(searchText);
                            tabSessionManager.getActive().setRegex(isRegex);
                            tabSessionManager.getActive().setCaseSensitive(caseSensitive);
                            tabSessionManager.getActive().setCurrentTailSearchPattern(pattern);
                            tabSessionManager.getActive().setTailSearchScannedUpTo(scanned);
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
                            if (tabSessionManager.getActive() != null) {
                                tabSessionManager.getActive().setCurrentMatchIndex(1);
                            }
                            if (searchNavigator != null) {
                                searchNavigator.updateStatus(1, results.size());
                            }
                            searchResultPanel.selectAndTriggerLine(0);
                        } else {
                            showSearchResultPanelForTail(results, pattern);
                            currentMatchIndex = -1;
                            if (tabSessionManager.getActive() != null) {
                                tabSessionManager.getActive().setCurrentMatchIndex(-1);
                            }
                        }
                        scheduleIncrementalTailSearch(searchSession);
                    });
                } catch (Exception error) {
                    if (Thread.currentThread().isInterrupted()) return;
                    logger.error("Tail search failed", error);
                    Platform.runLater(() -> {
                        if (!searchContextIsCurrent(thisGeneration, searchSession, searchReader, searchIndex, searchingTail)) return;
                        if (searchNavigator != null) searchNavigator.showMessage("Search failed", error.getMessage());
                    });
                }
            }, null) {
                @Override protected void done() {
                    if (snapshot instanceof com.seeloggyplus.shared.tail.TailHistory.Snapshot historySnapshot) historySnapshot.close();
                }
            };
            SEARCH_POOL.execute((Runnable) currentTailSearchTask);

            try {
                currentTailFilterPredicate = buildSearchPredicate(searchText, isRegex, caseSensitive);
            } catch (Exception ex) {
                logger.warn("Search predicate build failed", ex);
            }

            return;
        }

        showLoading("Searching...");

        Task<IntArrayList> task = new Task<>() {
            @Override
            protected IntArrayList call() {
                int total = searchIndex.getLineCount();
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

                final boolean simpleLiteral = pattern == null && !searchText.matches("(?s).*\\s+(AND|OR)\\s+.*")
                        && !searchText.trim().startsWith("NOT ");
                final String literalTerm = searchText.trim();
                int numThreads = SEARCH_WORKERS;
                int chunkSize = Math.max(10000, (total + numThreads - 1) / numThreads);

                logger.info("Parallel search: {} threads, {} lines/chunk, {} total lines", numThreads, chunkSize,
                        total);

                try {
                    List<Future<IntArrayList>> futures = new ArrayList<>();

                    for (int start = 0; start < total; start += chunkSize) {
                        final int chunkStart = start;
                        final int chunkEnd = Math.min(start + chunkSize, total);

                        futures.add(SEARCH_POOL.submit(() -> {
                            IntArrayList chunkMatches = new IntArrayList();
                            Predicate<CharSequence> matcher = pattern != null
                                    ? cs -> pattern.matcher(cs).find() : cs -> booleanPredicate.test(cs.toString());

                            for (int i = chunkStart; i < chunkEnd; i++) {
                                if (isCancelled() || thisGeneration != searchGeneration.get())
                                    break;

                                boolean matches = simpleLiteral
                                        ? searchReader.lineContainsLiteral(searchIndex, i, literalTerm, caseSensitive)
                                        : searchReader.lineMatches(searchIndex, i, matcher);
                                if (matches) {
                                    chunkMatches.add(i);
                                }
                            }

                            return chunkMatches;
                        }));
                    }

                    IntArrayList allMatches = new IntArrayList();
                    int completedChunks = 0;

                    for (var future : futures) {
                        IntArrayList chunkResult = future.get();

                        allMatches.moveAllFrom(chunkResult);

                        completedChunks++;
                        final int progress = completedChunks;
                        final int totalChunks = futures.size();
                        Platform.runLater(() -> {
                            if (searchContextIsCurrent(thisGeneration, searchSession, searchReader, searchIndex, searchingTail))
                                showLoading(String.format("Searching: %d/%d chunks...", progress, totalChunks));
                        });
                    }

                    return allMatches;
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw new IllegalStateException("Could not complete the log search", e);
                }
            }
        };

        task.setOnSucceeded(e -> {
            if (!searchContextIsCurrent(thisGeneration, searchSession, searchReader, searchIndex, searchingTail)) {
                logger.info("Discarding stale search results (gen {} vs current {})", thisGeneration, searchGeneration.get());
                return;
            }

            lastAppliedSearchQuery = searchText;
            lastAppliedRegex = isRegex;
            lastAppliedCaseSensitive = caseSensitive;
            IntArrayList matches = task.getValue();
            logger.info("Search complete: {} matches found", matches.size());

            filteredIndexes = matches;
            if (canvasLogViewer != null) {
                canvasLogViewer.clearFilter();
            }
            if (tabSessionManager.getActive() != null) {
                tabSessionManager.getActive().setFilteredIndexes(matches);
                tabSessionManager.getActive().setSearchQuery(searchText);
                tabSessionManager.getActive().setRegex(isRegex);
                tabSessionManager.getActive().setCaseSensitive(caseSensitive);
            }

            String highlightPattern = searchText;
            boolean highlightIsRegex = isRegex;

            if (!isRegex) {
                List<String> terms = extractSearchTerms(searchText);
                if (!terms.isEmpty()) {
                    highlightPattern = terms.stream()
                            .map(Pattern::quote)
                            .collect(Collectors.joining("|"));
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
                } catch (Exception ex) {
                    logger.debug("Ignoring an invalid highlight pattern", ex);
                }
                showSearchResultPanel(matches, compiledHighlight);

                int firstLine = matches.get(0);
                if (canvasLogViewer != null) {
                    canvasLogViewer.jumpToLine(firstLine);
                    canvasLogViewer.selectLine(firstLine);
                }
                currentMatchIndex = 1;
                if (tabSessionManager.getActive() != null) {
                    tabSessionManager.getActive().setCurrentMatchIndex(1);
                }
                if (searchNavigator != null) {
                    searchNavigator.updateStatus(1, matches.size());
                }
                searchResultPanel.selectAndTriggerLine(0);
            } else {
                showSearchResultPanel(matches, null);
                currentMatchIndex = -1;
                if (tabSessionManager.getActive() != null) {
                    tabSessionManager.getActive().setCurrentMatchIndex(-1);
                }
            }

            hideLoading();
        });

        task.setOnFailed(e -> {
            if (!searchContextIsCurrent(thisGeneration, searchSession, searchReader, searchIndex, searchingTail)) return;
            hideLoading();
            Throwable ex = task.getException();
            logger.error("Search failed", ex);
            if (searchNavigator != null) searchNavigator.showMessage("Search failed", ex.getMessage());
        });

        currentSearchTask = task;
        Thread.ofVirtual().start(task);
    }

    private void navigateNextMatch() {
        if (filteredIndexes == null || filteredIndexes.size() == 0) return;
        int total = filteredIndexes.size();

        if (currentMatchIndex < 0) currentMatchIndex = 0;
        int nextIdx = currentMatchIndex;
        if (nextIdx >= total) nextIdx = 0;

        int globalLine = filteredIndexes.get(nextIdx);
        if (canvasLogViewer != null) {
            canvasLogViewer.jumpToLine(globalLine);
            canvasLogViewer.selectLine(globalLine);
        }
        currentMatchIndex = nextIdx + 1;
        if (tabSessionManager.getActive() != null) {
            tabSessionManager.getActive().setCurrentMatchIndex(currentMatchIndex);
        }
        if (searchNavigator != null) {
            searchNavigator.updateStatus(nextIdx + 1, total);
        }
        if (searchResultPanel != null) {
            searchResultPanel.selectAndTriggerLine(nextIdx);
        }
    }

    private void navigatePreviousMatch() {
        if (filteredIndexes == null || filteredIndexes.size() == 0) return;
        int total = filteredIndexes.size();

        int prevIdx = currentMatchIndex - 2;
        if (prevIdx < 0) prevIdx = total - 1;

        int globalLine = filteredIndexes.get(prevIdx);
        if (canvasLogViewer != null) {
            canvasLogViewer.jumpToLine(globalLine);
            canvasLogViewer.selectLine(globalLine);
        }
        currentMatchIndex = prevIdx + 1;
        if (tabSessionManager.getActive() != null) {
            tabSessionManager.getActive().setCurrentMatchIndex(currentMatchIndex);
        }
        if (searchNavigator != null) {
            searchNavigator.updateStatus(prevIdx + 1, total);
        }
        if (searchResultPanel != null) {
            searchResultPanel.selectAndTriggerLine(prevIdx);
        }
    }

    private void clearSearch() {
        cancelSearchWork();
        boolean skipping = isSkippingFilterTrigger;
        isSkippingFilterTrigger = true;
        try { searchField.clear(); } finally { isSkippingFilterTrigger = skipping; }
        filteredIndexes = null;
        currentMatchIndex = -1;
        currentTailSearchPattern = null;
        currentTailSearchPredicate = null;
        tailSearchScannedUpTo = 0;
        lastAppliedSearchQuery = null;

        if (tabSessionManager.getActive() != null) {
            tabSessionManager.getActive().setFilteredIndexes(null);
            tabSessionManager.getActive().setSearchQuery("");
            tabSessionManager.getActive().setCurrentMatchIndex(-1);
            tabSessionManager.getActive().setCurrentTailSearchPattern(null);
            tabSessionManager.getActive().setTailSearchScannedUpTo(0);
        }
        if (canvasLogViewer != null) {
            canvasLogViewer.clearFilter();
            canvasLogViewer.clearSearchHighlight();

            if (tailModeEnabled && !canvasLogViewer.isFollowTail()) {
                canvasLogViewer.setFollowTail(true);
            }
        }
        if (searchNavigator != null) {
            searchNavigator.clear();
        }
        hideSearchResultPanel();
    }

    private void showSearchResultPanel(IntArrayList matches, Pattern highlightPattern) {
        if (searchResultPanel == null || searchSplitPane == null) return;

        final MappedFileReader reader = mappedFileReader;
        final LineOffsetIndex index = lineOffsetIndex;
        searchResultPanel.setLineContentResolver(idx -> {
            if (reader != null && index != null && idx >= 0 && idx < index.getLineCount()) {
                return reader.readLine(index, idx);
            }
            return "";
        }, command -> Thread.ofVirtual().start(command));
        searchResultPanel.setSearchPattern(highlightPattern);
        searchResultPanel.showResults(matches, lineOffsetIndex != null ? lineOffsetIndex.getLineCount() : 0);
        workbenchDock.resultsVisible(true);
    }

    private void hideSearchResultPanel() {
        if (searchResultPanel == null || searchSplitPane == null) return;
        searchResultPanel.clear();
        workbenchDock.resultsVisible(false);
    }

    private void showSearchResultPanelForTail(IntArrayList matches, Pattern highlightPattern) {
        if (searchResultPanel == null || searchSplitPane == null) return;

        long tailOffset = canvasLogViewer != null ? canvasLogViewer.getFileLineCount() : 0;
        final List<LogEntry> tailLines = liveTailList;
        final MappedFileReader reader = mappedFileReader;
        final LineOffsetIndex index = lineOffsetIndex;
        searchResultPanel.setLineContentResolver(idx -> {
            if (idx < tailOffset) {
                if (reader != null && index != null && idx >= 0 && idx < index.getLineCount()) {
                    return reader.readLine(index, idx);
                }
            } else {
                int bufferIdx = (int) (idx - tailOffset);
                synchronized (tailLines) {
                    if (bufferIdx >= 0 && bufferIdx < tailLines.size()) {
                        LogEntry entry = tailLines.get(bufferIdx);
                        return entry != null ? entry.getRawLog() : "";
                    }
                }
            }
            return "";
        }, command -> Thread.ofVirtual().start(command));
        searchResultPanel.setSearchPattern(highlightPattern);
        long totalLines = tailOffset + liveTailList.size();
        searchResultPanel.showResults(matches, (int) totalLines);
        workbenchDock.resultsVisible(true);
    }

    private void deleteCancelledDownloadFiles(File file) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.deleteIfExists(file.toPath());
                Files.deleteIfExists(file.toPath().resolveSibling(file.getName() + ".partial"));
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
        if (tabSessionManager.getActive() != null) {
            loadFileWithParallelParsing(tabSessionManager.getActive(), file, logFile, updateRecentFilesList, jumpToEnd, selectInRecent);
        }
    }

    static LineOffsetIndex buildFileIndex(RandomAccessFile file, java.util.function.BiConsumer<Long, Integer> progress)
            throws IOException {
        try (file) {
            var index = new LineOffsetIndex();
            index.buildIndex(file, progress == null ? null : bytes -> progress.accept(bytes, index.getLineCount()));
            return index;
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
        if (session == tabSessionManager.getActive()) cancelSearchWork();
        showLoading("Indexing file: " + file.getName() + " (scanning for lines...)");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long fileSize = file.length();
                LineOffsetIndex index = buildFileIndex(new RandomAccessFile(file, "r"), (bytesProcessed, lines) -> {
                    double percent = (double) bytesProcessed / fileSize * 100;
                    Platform.runLater(() -> showLoading(
                            String.format("Indexing: %.1f%% (%d lines found)", percent, lines)));
                });

                logger.info("Indexing complete! {} lines indexed in {}", index.getLineCount(), file.getName());
                MappedFileReader oldReader = session.getReader();
                MappedFileReader reader = new MappedFileReader(file);
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

                if (session == tabSessionManager.getActive()) {
                    mappedFileReader = session.getReader();
                    lineOffsetIndex = session.getIndex();
                    totalEntries = session.getTotalEntries();
                    if (session.getCanvasLogViewer() != null) {
                        session.getCanvasLogViewer().requestCanvasFocus();
                    }
                    updateTailButtonState();
                    updateBottomBarLineCount(session);
                    if (!searchField.getText().isBlank()) performSearch();
                }
                logger.info("Canvas viewer initialized for session: {}", session.getTitle());
            });

            if (updateRecentFilesList) {
                recentPanelController.saveOpened(logFile, startTail);
                logger.info("Added file to recent files: {} (mode={})", file.getName(),
                        startTail ? RecentFile.MODE_TAIL : RecentFile.MODE_OPEN);
            }

            if (selectInRecent) {
                recentPanelController.selectForSession(session);
            }

            hideLoading();
            updateTailButtonState();
            currentLoadingTask = null;
            logger.info("File loaded with Canvas-based viewer. RAM usage minimal.");

            if (startTail) {
                Platform.runLater(() -> {
                    if (tabSessionManager.getActive() == session && !session.isTailModeEnabled()) {
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

    @FXML
    private void handleReload() {
        logger.info("Reload triggered by user.");

        if (tabSessionManager.getActive() != null) {
            if (tabSessionManager.getActive().getSessionType() == LogSession.SessionType.LOCAL && tabSessionManager.getActive().getLocalFile() != null) {
                logger.info("Reloading local file in current tab '{}'.", tabSessionManager.getActive().getLocalFile().getName());
                showLoading("Reloading file...");
                loadFileWithParallelParsing(tabSessionManager.getActive(), tabSessionManager.getActive().getLocalFile(), tabSessionManager.getActive().getLogFileRecord(), false, false, false);
                return;
            } else if (tabSessionManager.getActive().getSessionType() == LogSession.SessionType.REMOTE) {
                if (tabSessionManager.getActive().getSshService() != null && tabSessionManager.getActive().getRemotePath() != null) {
                    final LogSession targetSession = tabSessionManager.getActive();
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

        if (tailModeEnabled && monitoringRemotePath != null && activeTailSshService != null) {
            reloadActiveRemoteTail();
            return;
        }

        if (currentFile != null) {
            logger.info("Reloading local file '{}'.", currentFile.getName());
            showLoading("Reloading file...");
            openLocalLogFile(currentFile, false);
            return;
        }

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
        if (currentParsingConfig == null && tabSessionManager.getActive() != null) {
            currentParsingConfig = tabSessionManager.getActive().getParsingConfig();
        }
        if (currentParsingConfig == null) {
            currentParsingConfig = parsingConfigService.findDefault().orElseGet(ParsingConfig::createRawConfig);
        }
        showLoading("Reloading remote tail...");

        final SSHService service = activeTailSshService;
        final String remotePath = monitoringRemotePath;
        Thread.ofPlatform().name("tail-reload").start(() -> {
            boolean connected = false;
            String failure = null;
            try {
                String secret = server.usesKeyAuth() ? server.getKeyPassphrase() : server.getPassword();
                connected = SshConnectFlow.connect(service, server, secret);
                if (!connected) {
                    failure = service.getLastConnectError();
                }
            } catch (Exception e) {
                logger.error("Failed to re-connect for tail reload", e);
                failure = e.getMessage();
            }
            final boolean ok = connected;
            final String detail = failure;
            Platform.runLater(() -> {
                hideLoading();
                if (ok) {
                    startRemoteTail(remotePath, service, server);
                } else {
                    showError("Reload Error", "Failed to re-connect to server: "
                            + (detail == null || detail.isBlank() ? server.getHost() : detail));
                }
            });
        });
    }

    private void reloadLastRemoteTail() {
        if (lastSession == null || lastSession.logFromDb == null || lastSession.logFromDb.getSshServerID() == null) {
            showError("Reload Error", "No remote session to reload.");
            return;
        }
        String remotePath = lastSession.remotePath;
        LogFile logFromDb = lastSession.logFromDb;
        SSHService savedSsh = lastSession.sshService;

        logger.info("Reloading last remote tail for '{}'.", remotePath);
        SSHServerModel server = serverManagementService.getServerById(logFromDb.getSshServerID());
        if (server == null) {
            showError("Reload Error", "Server configuration not found.");
            return;
        }

        currentLogFromDb = logFromDb;
        showLoading("Reconnecting to " + server.getHost() + "...");

        SSHService sshService = (savedSsh != null && savedSsh.isConnected()) ? savedSsh : sshServiceFactory.create();

        Task<Boolean> connectTask = new ServerConnectTask(sshService, server, ServerCredentials.storedSecret(server));
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
        if (tabSessionManager.getActive() != null) {
            clearSessionLogView(tabSessionManager.getActive());
        } else {
            if (tailModeEnabled) {
                disableTail();
            }
            cleanupFileResources();
        }
        currentFile = null;
        currentLogFromDb = null;
        currentParsingConfig = null;
        clearSearch();
        clearDetail();
        updateTailButtonState();
        recentPanelController.clearSelection();
        if (followTailButton != null) {
            Platform.runLater(() -> followTailButton.setSelected(false));
        }
        logger.info("Log view cleared");
    }

    private void clearSessionLogView(LogSession session) {
        if (session.isTailModeEnabled()) {
            disableTail(session, true);
        }
        MappedFileReader reader = session.getReader();
        if (reader != null) {
            reader.close();
        }
        session.setReader(null);
        session.setIndex(null);
        session.setTotalEntries(0);

        session.getLiveTailList().clear();
        synchronized (session.getTailBuffer()) {
            session.getTailBuffer().clear();
        }
        session.setRemoteTailLineCounter(0);
        session.setTailSearchScannedUpTo(0);
        session.setCurrentTailSearchPattern(null);
        session.setCurrentTailFilterPredicate(null);
        session.setFilteredIndexes(null);
        session.setCurrentMatchIndex(-1);
        session.setSelectedLine(-1);
        session.setSelectedLineContent(null);

        CanvasLogViewer viewer = session.getCanvasLogViewer();
        if (viewer != null) {
            viewer.resetView();
            viewer.clearSearchHighlight();
            viewer.setTailBuffer(session.getLiveTailList());
        }

        if (session == tabSessionManager.getActive()) {
            mappedFileReader = null;
            lineOffsetIndex = null;
            filteredIndexes = null;
            totalEntries = 0;
            updateBottomBarLineCount(session);
        }
    }

    private void setupBottomPanel() {
        detailPaneController.setOnCloseRequest(this::handleToggleBottomPanelPin);

        if (statusLabel != null) {
            statusLabel.setCursor(Cursor.HAND);
            statusLabel.setOnMouseClicked(e -> handleGoToLine());
            statusLabel.setTooltip(new Tooltip("Click to Go To Line (Ctrl+G)"));
            if (!statusLabel.getStyleClass().contains("status-link")) {
                statusLabel.getStyleClass().add("status-link");
            }
        }

        updateBottomPanelDisplay();
    }

    private void setupWorkbench() {
        ToggleGroup positions = new ToggleGroup();
        resultsAutoMenuItem.setToggleGroup(positions);
        resultsRightMenuItem.setToggleGroup(positions);
        resultsBottomMenuItem.setToggleGroup(positions);
        resultsAutoMenuItem.setOnAction(e -> setResultsPosition("auto"));
        resultsRightMenuItem.setOnAction(e -> setResultsPosition("right"));
        resultsBottomMenuItem.setOnAction(e -> setResultsPosition("bottom"));
        String position=preferenceService.getPreferencesByCode("workbench_results_position").orElse("auto");
        workbenchDock.position(position);
        selectResultsPositionMenu();
        lastHorizontalDividerPos=readLayoutNumber("workbench_sidebar_divider", .24);
        double bottom=readLayoutNumber("workbench_bottom_divider",lastVerticalDividerPos);
        workbenchDock.restoreDividers(readLayoutNumber("workbench_right_divider",.66),bottom);
        SplitPane.setResizableWithParent(leftPanel,false);
        Platform.runLater(() -> {
            if (preferenceService.getPreferencesByCode("workbench_sidebar_divider").isEmpty()) {
                lastHorizontalDividerPos=horizontalSplitPane.getWidth()>0 ? 240/horizontalSplitPane.getWidth() : .24;
            }
            if (isLeftPanelPinned) horizontalSplitPane.setDividerPositions(clampDivider(lastHorizontalDividerPos));
        });
        workbenchToolbar.widthProperty().addListener((o,a,b) -> resizeWorkbenchSearch());
        searchNavigator.managedProperty().addListener((o,a,b) -> resizeWorkbenchSearch());
        resizeWorkbenchSearch();
        tailButton.selectedProperty().addListener((o,a,b) -> updateWorkbenchTailStatus());
        followTailButton.selectedProperty().addListener((o,a,b) -> updateWorkbenchTailStatus());
        centerRoot.addEventFilter(MouseEvent.MOUSE_RELEASED,e -> {
            Node node=e.getTarget() instanceof Node n ? n : null;
            while (node!=null && !node.getStyleClass().contains("split-pane-divider")) node=node.getParent();
            if (node!=null) saveWorkbenchSizes();
        });
    }

    private void resizeWorkbenchSearch() {
        boolean compact=workbenchToolbar.getWidth()<1000;
        workbenchToolbar.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("compact"),compact);
        var display=compact ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT;
        openFileButton.setContentDisplay(display); serversButton.setContentDisplay(display);
        tailStatusLabel.setMinWidth(Region.USE_PREF_SIZE);
        searchField.setPrefWidth(Math.max(140,workbenchToolbar.getWidth()-(searchNavigator.isManaged()?760:620)));
    }

    private double readLayoutNumber(String code,double fallback) {
        try {
            double value=Double.parseDouble(preferenceService.getPreferencesByCode(code).orElse(""));
            return Double.isFinite(value) && value>=.1 && value<=.9 ? value : fallback;
        } catch (NumberFormatException e) { return fallback; }
    }

    private void selectResultsPositionMenu() {
        switch (workbenchDock.position()) {
            case "right" -> resultsRightMenuItem.setSelected(true);
            case "bottom" -> resultsBottomMenuItem.setSelected(true);
            default -> resultsAutoMenuItem.setSelected(true);
        }
    }

    private void setResultsPosition(String position) {
        workbenchDock.position(position);
        selectResultsPositionMenu();
        preferenceService.saveOrUpdatePreferences(new Preference("workbench_results_position",workbenchDock.position()));
    }

    private void saveWorkbenchSizes() {
        workbenchDock.rememberDividers();
        if (!horizontalSplitPane.getDividers().isEmpty()) lastHorizontalDividerPos=horizontalSplitPane.getDividerPositions()[0];
        Map<String,String> sizes=Map.of("workbench_sidebar_divider",String.valueOf(lastHorizontalDividerPos),
                "workbench_right_divider",String.valueOf(workbenchDock.rightDivider()),
                "workbench_bottom_divider",String.valueOf(workbenchDock.bottomDivider()));
        layoutPreferenceWriter.execute(() -> sizes.forEach((code,value) -> preferenceService.saveOrUpdatePreferences(new Preference(code,value))));
    }

    @FXML private void resetWorkbenchLayout() {
        setResultsPosition("auto");
        lastHorizontalDividerPos=horizontalSplitPane.getWidth()>0 ? 240/horizontalSplitPane.getWidth() : .24;
        lastVerticalDividerPos=.7;
        isLeftPanelPinned=true; isBottomPanelPinned=true;
        updateLeftPanelDisplay(); updateBottomPanelDisplay();
        workbenchDock.restoreDividers(.66,.7);
        preferenceService.saveOrUpdatePreferences(new Preference("left_panel_pinned","true"));
        preferenceService.saveOrUpdatePreferences(new Preference("bottom_panel_pinned","true"));
        saveWorkbenchSizes();
    }

    private void revealWorkbenchDetail() {
        if (!isBottomPanelPinned) toggleBottomPanel();
        if (workbenchDock!=null) workbenchDock.revealDetail();
    }

    private void updateWorkbenchTailStatus() {
        if (tailStatusLabel==null) return;
        LogSession active=tabSessionManager.getActive();
        boolean enabled=active!=null ? active.isTailModeEnabled() : tailModeEnabled;
        boolean disconnected=enabled && active!=null && active.getSessionType()==LogSession.SessionType.REMOTE
                && (active.getSshService()==null || !active.getSshService().isConnected());
        tailStatusLabel.setText(disconnected?"Disconnected":enabled?"Live":"Paused");
        tailStatusLabel.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("live"),enabled && !disconnected);
        tailStatusLabel.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("disconnected"),disconnected);
        followTailButton.setAccessibleText(followTailButton.isSelected()?"Follow new lines enabled":"Follow new lines disabled");
    }

    private void handleToggleBottomPanelPin() {
        toggleBottomPanel();
    }

    private void updateBottomPanelDisplay() {
        if (verticalSplitPane == null || detailPane == null) {
            return;
        }

        if (workbenchDock != null) workbenchDock.detailVisible(isBottomPanelPinned);
        if (toggleBottomPanelButton != null) toggleBottomPanelButton.setSelected(isBottomPanelPinned);

        if (showBottomPanelMenuItem != null) {
            showBottomPanelMenuItem.setSelected(isBottomPanelPinned);
        }
    }

    private void setupKeyboardShortcuts() {
        menuBar.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                installKeyboardShortcuts(newScene);
            }
        });
        if (menuBar.getScene() != null) {
            installKeyboardShortcuts(menuBar.getScene());
        }
    }

    private void installKeyboardShortcuts(Scene scene) {
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
            controller.setServerCatalog(serverManagementService);
            controller.setPreferenceService(preferenceService);
            controller.setSshServiceFactory(sshServiceFactory);

            Stage dialog = new Stage();
            addAppIcon(dialog);
            dialog.setTitle("Open File");
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(mainStage);
            dialog.setScene(AppTheme.scene(root));
            var screens = javafx.stage.Screen.getScreensForRectangle(
                    mainStage.getX() + mainStage.getWidth() / 2, mainStage.getY() + mainStage.getHeight() / 2, 1, 1);
            var bounds = (screens.isEmpty() ? javafx.stage.Screen.getPrimary() : screens.getFirst()).getVisualBounds();
            dialog.setMinWidth(Math.min(800, bounds.getWidth()));
            dialog.setMinHeight(Math.min(520, bounds.getHeight()));
            dialog.setWidth(Math.min(1000, bounds.getWidth()));
            dialog.setHeight(Math.min(640, bounds.getHeight()));

            controller.setHost(new FileManagerDialogHost(dialog, preferenceService, sshServiceFactory,
                    this::showError, DialogPresenter.showAndWait()));

            dialog.showAndWait();

            FileInfo selectedFile = controller.getSelectedFile();
            SSHService sshService = controller.getSshService();
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

    private void openRemoteLogFile(String remotePath, String remoteFileName, SSHService sshService, SSHServerModel sshServer) {
        openRemoteLogFile(remotePath, remoteFileName, sshService, sshServer, 0);
    }

    private void openRemoteLogFile(String remotePath, String remoteFileName, SSHService sshService,
            SSHServerModel sshServer, int targetLine) {
        for (Map.Entry<Tab, LogSession> entry : tabSessionManager.sessions().entrySet()) {
            LogSession s = entry.getValue();
            boolean sameRemoteStream = s.getSessionType() == LogSession.SessionType.REMOTE
                    && remotePath.equals(s.getRemotePath())
                    && s.getSshServer() != null && sshServer != null
                    && s.getSshServer().getId() != null
                    && s.getSshServer().getId().equals(sshServer.getId());
            boolean sameDownloadedRemoteFile = s.getSessionType() == LogSession.SessionType.LOCAL
                    && s.getLogFileRecord() != null
                    && s.getLogFileRecord().isRemote()
                    && remotePath.equals(s.getLogFileRecord().getFilePath())
                    && sshServer != null && sshServer.getId() != null
                    && sshServer.getId().equals(s.getLogFileRecord().getSshServerID());
            if (sameRemoteStream || sameDownloadedRemoteFile) {
                logger.info("Remote file already open, selecting existing tab instead of re-downloading: {}",
                        remotePath);
                if (logTabPane != null) {
                    logTabPane.getSelectionModel().select(entry.getKey());
                }
                recentPanelController.markOpened(s);
                if (sshService != null) {
                    sshService.disconnect();
                }
                return;
            }
        }

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
                        new ProgressCallback() {
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

        for (Map.Entry<Tab, LogSession> entry : tabSessionManager.sessions().entrySet()) {
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
                        if (updateRecentFilesList) recentPanelController.markOpened(s);
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
                        if (updateRecentFilesList) recentPanelController.markOpened(s);
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
        tabSessionManager.register(tab, session);
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
            ParsingConfig parsingConfig = ParsingConfig.createRawConfig();
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
                existingLogFile.setSize(FileUtils.formatFileSize(file.length()));
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
                newLogFile.setSize(FileUtils.formatFileSize(file.length()));

                logFileService.insertLogFile(newLogFile);
                logger.info("Successfully created LogFile with ID: {}", newLogFile.getId());
                return newLogFile;
            }
        } catch (Exception ex) {
            logger.error("Error in getOrCreateLogFile for: {}", file.getAbsolutePath(), ex);
            return null;
        }
    }

    @FXML
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
            ServerManagementDialogController serverController = loader.getController();
            serverController.setSshServiceFactory(sshServiceFactory);
            serverController.setPreferenceService(preferenceService);

            Stage dialog = new Stage();
            dialog.setTitle("SSH Server Management");
            dialog.initOwner(mainStage);
            dialog.initModality(Modality.APPLICATION_MODAL);
            addAppIcon(dialog);
            serverController.configureWindow(dialog);
            DialogWindowSupport.restoreWindow(mainStage, wasMaximized, oldX, oldY, oldWidth, oldHeight, root, dialog);

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
            dialog.setScene(AppTheme.scene(root));
            dialog.setResizable(true);
            var screens = javafx.stage.Screen.getScreensForRectangle(mainStage.getX(), mainStage.getY(), 1, 1);
            var bounds = (screens.isEmpty() ? javafx.stage.Screen.getPrimary() : screens.getFirst()).getVisualBounds();
            dialog.setMinWidth(Math.min(600, bounds.getWidth()));
            dialog.setMinHeight(Math.min(420, bounds.getHeight()));
            dialog.setWidth(Math.min(660, bounds.getWidth()));
            dialog.setHeight(Math.min(460, bounds.getHeight()));
            dialog.setOnHidden(event -> controller.discardChanges());

            dialog.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open preferences dialog", e);
            showError("Preferences Error", "Could not open preferences: " + e.getMessage());
        }
    }

    private void loadPreferences() {
        logger.info("Loading preferences...");

        String fontFamily = preferenceService.getPreferencesByCode("app_font_family").orElse("Consolas");
        String fontSizeStr = preferenceService.getPreferencesByCode("app_font_size").orElse("13");
        int fontSize = 12;
        try {
            fontSize = Integer.parseInt(fontSizeStr);
        } catch (NumberFormatException e) {
            logger.warn("Invalid font size preference: {}", fontSizeStr);
        }

        String fontStyle = String.format("-fx-font-family: '%s'; -fx-font-size: %dpx;", fontFamily, fontSize);
        detailPaneController.setFontStyle(fontStyle);

        String threadsStr = preferenceService.getPreferencesByCode("ssh_download_threads").orElse("4");
        try {
            this.sshDownloadThreads = Integer.parseInt(threadsStr);
        } catch (NumberFormatException e) {
            logger.warn("Invalid ssh threads preference: {}", threadsStr);
        }

        this.sshDownloadDirectory = preferenceService.getPreferencesByCode("ssh_download_directory").orElse("");

        boolean autoPrettifyJson = Boolean
                .parseBoolean(preferenceService.getPreferencesByCode("main_auto_prettify_json").orElse("false"));
        boolean autoPrettifyXml = Boolean
                .parseBoolean(preferenceService.getPreferencesByCode("main_auto_prettify_xml").orElse("false"));

        String tailWindowStr = preferenceService.getPreferencesByCode("main_tail_window_size").orElse("20000");
        try {
            this.tailWindowSize = Integer.parseInt(tailWindowStr);
        } catch (NumberFormatException e) {
            logger.warn("Invalid tail window size preference: {}", tailWindowStr);
        }

        logger.info("Preferences loaded: font={} {}, threads={}, tailWindowSize={}", fontFamily, fontSize,
                sshDownloadThreads, tailWindowSize);

        boolean defaultFormatted = preferenceService.getPreferencesByCode("main_detail_formatted")
                .map(Boolean::parseBoolean).orElse(autoPrettifyJson || autoPrettifyXml);
        detailPaneController.setDefaultFormatted(defaultFormatted);
    }

    static boolean isTailModeRecent(RecentFilesDto recentFile) {
        return recentFile != null && RecentFile.MODE_TAIL.equalsIgnoreCase(recentFile.openMode());
    }

    private void handleRecentFileSelected(RecentFilesDto recentFile) {
        if (recentFile == null || recentFile.logFile() == null) return;
        LogFile logFile = recentFile.logFile();

        boolean tailMode = isTailModeRecent(recentFile);

        if (logFile.isRemote()) {
            String filePath = logFile.getFilePath();
            if (filePath == null || filePath.isBlank()) {
                validateRemoteTailPath(filePath);
                return;
            }
            if (RemoteLogPaths.isWindowsLocalPath(filePath)) {
                logger.warn("Recent remote file has local temp path stored: {}", filePath);
                File localCopy = new File(filePath);
                if (localCopy.exists()) {
                    openLocalLogFile(localCopy, false);
                    return;
                } else {
                    showRemotePathError(filePath);
                    return;
                }
            }

            for (Map.Entry<Tab, LogSession> entry : tabSessionManager.sessions().entrySet()) {
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
                    recentPanelController.markOpened(s);
                    return;
                }
            }

            SSHServerModel cachedServer = logFile.getSshServerID() == null ? null
                    : serverManagementService.getServerById(logFile.getSshServerID());
            File cachedDownload = downloadedRemoteFiles.get(remoteDownloadKey(logFile.getFilePath(), logFile.getSshServerID()));

            if (!tailMode && cachedDownload != null && cachedDownload.isFile()) {
                logger.info("Opening cached remote download from Recent: {}", cachedDownload.getAbsolutePath());
                openLocalLogFile(cachedDownload, logFile.getName(), cachedServer, logFile.getFilePath(), true);
                return;
            }

            hideLoading();
            cancelCurrentLoadingTask();

            Task<SSHService> connectTask = new RemoteConnectTask(
                    logFile.getSshServerID(), serverManagementService, sshServiceFactory, dialogPresenter);

            showLoading("Connecting to remote server...");
            connectTask.runningProperty().addListener((obs, wasRunning, isRunning) -> {
                if (!isRunning) {
                    hideLoading();
                }
            });

            connectTask.setOnSucceeded(e -> {
                SSHService sshService = connectTask.getValue();
                if (sshService != null) {
                    SSHServerModel server = serverManagementService.getServerById(logFile.getSshServerID());
                    resetFilters();
                    if (tailMode) {
                        logger.info("SSH connected for remote recent file, resuming TAIL mode.");
                        startRemoteTail(logFile.getFilePath(), sshService, server);
                    } else {
                        logger.info("SSH connected for remote recent file, opening normally.");
                        openRemoteLogFile(logFile.getFilePath(), logFile.getName(), sshService, server);
                    }
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

            for (Map.Entry<Tab, LogSession> entry : tabSessionManager.sessions().entrySet()) {
                LogSession s = entry.getValue();
                if (s.getSessionType() == LogSession.SessionType.LOCAL && s.getLocalFile() != null) {
                    try {
                        if (s.getLocalFile().getCanonicalPath().equalsIgnoreCase(file.getCanonicalPath())) {
                            if (logTabPane != null) {
                                logTabPane.getSelectionModel().select(entry.getKey());
                            }
                            recentPanelController.markOpened(s);
                            return;
                        }
                    } catch (IOException ignored) {
                        if (s.getLocalFile().getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
                            if (logTabPane != null) {
                                logTabPane.getSelectionModel().select(entry.getKey());
                            }
                            recentPanelController.markOpened(s);
                            return;
                        }
                    }
                }
            }

            logger.info("Opening recent file: {} with RAW config (tail={})", file.getName(), tailMode);
            resetFilters();
            openLocalLogFile(file, true, tailMode);
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

    private void clearDetail() {
        detailPaneController.clear();
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
        if (isBottomPanelPinned && workbenchDock != null) workbenchDock.revealDetail();
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
            dialog.setScene(AppTheme.scene(root));

            dialog.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open About dialog", e);
            showError("Error", "Could not open About dialog: " + e.getMessage());
        }
    }

    @FXML
    public void handleOpenMicrosoftStore() {
        try {
            StoreLinks.open(StoreLinks.configuredProductId(), uri -> Desktop.getDesktop().browse(uri));
        } catch (Exception e) {
            logger.error("Failed to open Microsoft Store", e);
            showError("Microsoft Store", "Could not open Microsoft Store. Search for SeeLoggyPlus in Microsoft Store.");
        }
    }

    private void handleHelpContents() {
        try {
            DocumentationLinks.open(uri -> Desktop.getDesktop().browse(uri));
        } catch (Exception e) {
            logger.error("Failed to open help", e);
            showError("Help Error", "Could not open documentation. Visit " + DocumentationLinks.HOME + " in your browser. Details: " + e.getMessage());
        }
    }

    private void handleExit() {
        for (LogSession session : tabSessionManager.all()) {
            try {
                session.close();
            } catch (Exception e) {
                logger.warn("Error closing session {}: {}", session.getTitle(), e.getMessage());
            }
        }
        tabSessionManager.clear();
        stopLocalTail();
        if (activeTailSshService != null) {
            stopRemoteTail();
        }
        logger.info("Application exiting, stopped tailers and closed all sessions.");
        Platform.exit();
    }

    @FXML
    private void enableTail() {
        boolean isRemote = tabSessionManager.getActive() != null
                ? tabSessionManager.getActive().getSessionType() == LogSession.SessionType.REMOTE
                : currentLogFromDb != null && currentLogFromDb.isRemote();
        boolean isLocal = tabSessionManager.getActive() != null
                ? tabSessionManager.getActive().getSessionType() == LogSession.SessionType.LOCAL
                : currentFile != null && currentFile.exists();

        if (!isRemote && !isLocal) {
            showInfo("Tail Mode", "No active file to tail.");
            if (tailButton.isSelected())
                tailButton.setSelected(false);
            return;
        }

        if (currentParsingConfig == null && tabSessionManager.getActive() != null) {
            currentParsingConfig = tabSessionManager.getActive().getParsingConfig();
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

        if (tabSessionManager.getActive() != null && tabSessionManager.getActive().getParsingConfig() == null) {
            tabSessionManager.getActive().setParsingConfig(currentParsingConfig);
        }

        tailModeEnabled = true;
        if (tabSessionManager.getActive() != null) {
            tabSessionManager.getActive().setTailModeEnabled(true);
        }
        if (!tailButton.isSelected()) {
            isProgrammaticUpdate = true;
            try {
                tailButton.setSelected(true);
            } finally {
                isProgrammaticUpdate = false;
            }
        }

        if (tabSessionManager.getActive() != null) {
            boolean isStreamMode = (tabSessionManager.getActive().getSessionType() == LogSession.SessionType.REMOTE && tabSessionManager.getActive().getReader() == null);
            tabSessionManager.getActive().getLiveTailList().clear();
            synchronized (tabSessionManager.getActive().getTailBuffer()) {
                tabSessionManager.getActive().getTailBuffer().clear();
            }
            tabSessionManager.getActive().setTailSearchScannedUpTo(0);
            if (isStreamMode) {
                if (tabSessionManager.getActive().getCanvasLogViewer() != null) {
                    tabSessionManager.getActive().getCanvasLogViewer().resetView();
                    tabSessionManager.getActive().getCanvasLogViewer().setTailBuffer(tabSessionManager.getActive().getLiveTailList());
                    tabSessionManager.getActive().getCanvasLogViewer().refreshTail();
                }
            } else {
                if (tabSessionManager.getActive().getCanvasLogViewer() != null) {
                    tabSessionManager.getActive().getCanvasLogViewer().setTailBuffer(tabSessionManager.getActive().getLiveTailList());
                    tabSessionManager.getActive().getCanvasLogViewer().refreshTail();
                }
            }
            updateTabBadge(tabSessionManager.getActive());
            updateBottomBarLineCount(tabSessionManager.getActive());
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
            if (tabSessionManager.getActive() != null) {
                tabSessionManager.getActive().setCurrentTailFilterPredicate(currentTailFilterPredicate);
            }
        } catch (Exception e) {
            logger.warn("Tail filter error", e);
        }

        if (isRemote) {
            startRemoteTailInternal();
        } else {
            if (tabSessionManager.getActive() != null) {
                File fileToTail = tabSessionManager.getActive().getLocalFile() != null ? tabSessionManager.getActive().getLocalFile() : currentFile;
                startLocalTail(tabSessionManager.getActive(), fileToTail);
            } else {
                startLocalTail(currentFile);
            }
        }
    }

    private void startRemoteTailInternal() {
        if (tabSessionManager.getActive() != null && tabSessionManager.getActive().getSessionType() == LogSession.SessionType.REMOTE) {
            final LogSession targetSession = tabSessionManager.getActive();
            if (!validateRemoteTailPath(targetSession.getRemotePath())) {
                disableTail(targetSession, false);
                return;
            }
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

            SSHService existingSsh = targetSession.getSshService();
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
            Optional<String> resolvedSecret = ServerCredentials.resolveSecret(server, dialogPresenter);
            if (!server.usesKeyAuth() && resolvedSecret.isEmpty()) {
                disableTail(targetSession, false);
                return;
            }
            final String finalPassword = resolvedSecret.orElse(null);
            showLoading("Connecting to " + server.getHost() + "...");

            SSHService newSsh = sshServiceFactory.create();
            Task<Boolean> connectTask = new ServerConnectTask(newSsh, finalServer, finalPassword);
            connectTask.setOnSucceeded(e -> {
                hideLoading();
                if (connectTask.getValue()) {
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
            });
            connectTask.setOnFailed(e -> {
                hideLoading();
                Throwable ex = connectTask.getException();
                logger.error("SSH connection failed", ex);
                showError("Connection Failed", ex != null ? ex.getMessage() : "Unknown error");
                disableTail(targetSession, false);
            });
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

        String remotePath = (tabSessionManager.getActive() != null && tabSessionManager.getActive().getRemotePath() != null)
                ? tabSessionManager.getActive().getRemotePath()
                : currentLogFromDb.getFilePath();
        if (!validateRemoteTailPath(remotePath)) {
            disableTail();
            return;
        }

        Optional<String> resolvedSecret = ServerCredentials.resolveSecret(server, dialogPresenter);
        if (!server.usesKeyAuth() && resolvedSecret.isEmpty()) {
            disableTail();
            return;
        }
        final String finalPassword = resolvedSecret.orElse(null);

        showLoading("Connecting to " + server.getHost() + "...");

        SSHService sshService = sshServiceFactory.create();
        Task<Boolean> connectTask = new ServerConnectTask(sshService, server, finalPassword);
        connectTask.setOnSucceeded(e -> {
            hideLoading();
            if (connectTask.getValue()) {
                startRemoteTail(remotePath, sshService, server);
            } else {
                hideLoading();
                showError("Connection Failed", "Could not connect to " + server.getHost());
                disableTail();
            }
        });
        connectTask.setOnFailed(e -> {
            hideLoading();
            showError("Connection Error",
                    connectTask.getException() != null ? connectTask.getException().getMessage()
                            : "Failed to connect");
            disableTail();
        });
        Thread.ofVirtual().start(connectTask);
    }

    private void disableTail() {
        disableTail(false);
    }

    private void disableTail(boolean skipReindex) {
        if (tabSessionManager.getActive() != null) {
            disableTail(tabSessionManager.getActive(), skipReindex);
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
        if (session == tabSessionManager.getActive()) cancelSearchWork();
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

        session.getTailFlushScheduled().set(false);
        session.setCurrentTailSearchPattern(null);
        session.setTailSearchScannedUpTo(0);

        if (session.getCanvasLogViewer() != null) {
            session.getCanvasLogViewer().exitTailMode();
        }
        updateTabBadge(session);

        if (session == tabSessionManager.getActive()) {
            tailModeEnabled = false;
            if (tailButton != null && tailButton.isSelected()) {
                isProgrammaticUpdate = true;
                tailButton.setSelected(false);
                isProgrammaticUpdate = false;
            }
        }

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

    private void startRemoteTail(String remotePath, SSHService sshService, SSHServerModel server) {
        startRemoteTail(remotePath, sshService, server, 0, -1);
    }

    private void startRemoteTail(String remotePath, SSHService sshService, SSHServerModel server,
            int tailLines, int tailJumpIndex) {
        if (!validateRemoteTailPath(remotePath)) {
            return;
        }

        for (Map.Entry<Tab, LogSession> entry : tabSessionManager.sessions().entrySet()) {
            LogSession s = entry.getValue();
            if (s.getSessionType() == LogSession.SessionType.REMOTE &&
                    remotePath.equals(s.getRemotePath()) &&
                    s.getSshServer() != null && server != null &&
                    s.getSshServer().getId().equals(server.getId())) {
                if (logTabPane != null) {
                    logTabPane.getSelectionModel().select(entry.getKey());
                }
                recentPanelController.markOpened(s);
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
        tabSessionManager.register(tab, session);
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

    private void setupAdaptiveLogTabs() {
        logTabPane.setMinWidth(0);
        var width = Bindings.createDoubleBinding(() -> {
            int count = Math.max(1, logTabPane.getTabs().size());
            double available = Math.max(0, logTabPane.getWidth() - LOG_TAB_OVERFLOW_RESERVE);
            double content = Math.floor(available / count) - LOG_TAB_HORIZONTAL_PADDING;
            return Math.max(MIN_LOG_TAB_CONTENT_WIDTH, Math.min(MAX_LOG_TAB_CONTENT_WIDTH, content));
        }, logTabPane.widthProperty(), logTabPane.getTabs());
        logTabPane.tabMinWidthProperty().bind(width);
        logTabPane.tabMaxWidthProperty().bind(width);
    }

    private boolean validateRemoteTailPath(String remotePath) {
        boolean missing = remotePath == null || remotePath.isBlank();
        if (!missing && !RemoteLogPaths.isWindowsLocalPath(remotePath)) {
            return true;
        }
        logger.warn("Remote tail rejected: {}; location={}",
                missing ? "missing server location" : "Windows-style local location", remotePath);
        showRemotePathError(remotePath);
        return false;
    }

    private LogFile saveRemoteTailToRecent(String remotePath, SSHServerModel server) {
        try {
            ParsingConfig parsingConfig = ParsingConfig.createRawConfig();
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
                logFile.setSize("-");
                logFileService.updateLogFile(logFile);
                logger.info("Updated existing LogFile for remote tail: id={}, serverId={}, path={}", logFile.getId(), serverId, remotePath);
            }

            this.currentLogFromDb = logFile;
            this.monitoringRemotePath = remotePath;
            recentPanelController.markTailOpened(logFile, remotePath);
            return logFile;
        } catch (Exception e) {
            logger.error("Failed to save remote tail to recent for path {}", remotePath, e);
            return null;
        }
    }

    private synchronized void handleTailLineBackground(String line) {
        if (tabSessionManager.getActive() != null) {
            handleTailLineBackground(tabSessionManager.getActive(), line);
        }
    }

    private void handleTailLineBackground(LogSession session, String line) {
        if (session == null || session.isClosed()) return;
        long lineNumber = session.incrementRemoteTailLineCounter();
        LogEntry entry = logParserService.parseLine(line, lineNumber);
        long entryBytes = 128L + 2L * line.length();
        if (session.getLiveTailList() instanceof com.seeloggyplus.shared.tail.TailHistory history) entry = history.prepare(entry);
        synchronized (session.getTailBuffer()) {
            while (!session.isClosed() && (session.getTailBuffer().size() >= MAX_TAIL_BUFFER_SIZE
                    || (!session.getTailBuffer().isEmpty() && session.getTailBufferBytes() + entryBytes > 4L * 1024 * 1024))) {
                scheduleTailFlush(session);
                try {session.getTailBuffer().wait(50);}
                catch (InterruptedException error) {Thread.currentThread().interrupt(); return;}
            }
            if (session.isClosed()) return;
            session.getTailBuffer().add(entry);
            session.adjustTailBufferBytes(entryBytes);
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
        if (tabSessionManager.getActive() != null) {
            scheduleTailFlush(tabSessionManager.getActive());
        }
    }

    private void scheduleTailFlush(LogSession session) {
        if (session == null || !session.getTailFlushScheduled().compareAndSet(false, true)) {
            return;
        }

        Platform.runLater(() -> {
            session.getTailFlushScheduled().set(false);

            if (!session.isTailModeEnabled() || session.isClosed()) {
                synchronized (session.getTailBuffer()) {
                    session.getTailBuffer().clear();
                    session.adjustTailBufferBytes(-session.getTailBufferBytes());
                    session.getTailBuffer().notifyAll();
                }
                return;
            }

            try {
                List<LogEntry> toAdd;
                synchronized (session.getTailBuffer()) {
                    if (session.getTailBuffer().isEmpty()) {
                        return;
                    }
                    int batchSize = Math.min(500, session.getTailBuffer().size());
                    toAdd = new ArrayList<>(session.getTailBuffer().subList(0, batchSize));
                    session.getTailBuffer().subList(0, batchSize).clear();
                    long released = toAdd.stream().mapToLong(entry -> session.getLiveTailList() instanceof com.seeloggyplus.shared.tail.TailHistory history
                            ? history.entryBytes(entry) : 128L + 2L * entry.getRawLog().length()).sum();
                    session.adjustTailBufferBytes(-released);
                    session.getTailBuffer().notifyAll();
                    if (!session.getTailBuffer().isEmpty()) scheduleTailFlush(session);
                }

                int oldSize = session.getLiveTailList().size();
                session.getLiveTailList().addAll(toAdd);

                int trimmed = 0;
                if (session.getLiveTailList().size() > 50000) {
                    trimmed = session.getLiveTailList().size() - 50000;
                    session.getLiveTailList().subList(0, trimmed).clear();
                }

                if (session.isActive()) {
                    session.getCanvasLogViewer().refreshTail();

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

                    if (session == tabSessionManager.getActive() && currentTailSearchPattern != null && filteredIndexes != null) {
                        long tailOffset = canvasLogViewer != null ? canvasLogViewer.getFileLineCount() : 0;

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

                            while (filteredIndexes.size() > writeIdx) {
                                filteredIndexes.removeLast();
                            }
                            tailSearchScannedUpTo = Math.max(0, tailSearchScannedUpTo - trimmed);
                            if (removedMatchCount > 0 && searchResultPanel != null) {
                                searchResultPanel.trimFromStart(removedMatchCount);
                            }
                        }

                        scheduleIncrementalTailSearch(session);
                    } else if (session == tabSessionManager.getActive() && canvasLogViewer != null && canvasLogViewer.hasSearchHighlight()) {
                        if (searchNavigator != null) {
                            CanvasLogViewer viewer = canvasLogViewer;
                            viewer.countMatchesAsync((matches, truncated) -> {
                                if (session == tabSessionManager.getActive() && viewer == canvasLogViewer)
                                    searchNavigator.setMatchCount(matches, truncated);
                            });
                        }
                    }

                    if (session == tabSessionManager.getActive() && statusLabel != null) {
                        updateBottomBarLineCount(session);
                    }
                } else {
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

    private static long tailBase(List<LogEntry> rows) {
        return rows instanceof com.seeloggyplus.shared.tail.TailHistory history ? history.baseIndex() : 0;
    }

    private static void rebaseTailResults(IntArrayList results, long tailOffset, long trimmed) {
        int write = 0;
        for (int i = 0; i < results.size(); i++) {
            long value = results.get(i);
            if (value >= tailOffset) {
                value -= trimmed;
                if (value < tailOffset) continue;
            }
            results.set(write++, (int) value);
        }
        while (results.size() > write) results.removeLast();
    }

    private void scheduleIncrementalTailSearch(LogSession session) {
        if (session == null || session.isClosed() || session != tabSessionManager.getActive()
                || incrementalTailSearchToken != null || filteredIndexes == null || currentTailSearchPredicate == null) return;
        List<LogEntry> rows = session.getLiveTailList();
        int begin = Math.max(0, tailSearchScannedUpTo);
        if (begin >= rows.size()) return;
        List<LogEntry> snapshot;
        synchronized (rows) {
            snapshot = rows instanceof com.seeloggyplus.shared.tail.TailHistory history
                    ? history.snapshot() : new ArrayList<>(rows);
        }
        int end = snapshot.size();
        long base = snapshot instanceof com.seeloggyplus.shared.tail.TailHistory.Snapshot captured ? captured.baseIndex() : 0;
        long generation = searchGeneration.get();
        Object token = new Object();
        incrementalTailSearchToken = token;
        IntArrayList target = filteredIndexes;
        Predicate<String> predicate = currentTailSearchPredicate;
        long offset = canvasLogViewer != null ? canvasLogViewer.getFileLineCount() : 0;
        SEARCH_POOL.execute(() -> {
            try {
                IntArrayList matches = new IntArrayList();
                for (int i = begin; i < end; i++) {
                    if (generation != searchGeneration.get() || session.isClosed()) return;
                    LogEntry entry = snapshot.get(i);
                    if (predicate.test(entry == null ? "" : entry.getRawLog())) matches.add(i);
                }
                Platform.runLater(() -> {
                    if (incrementalTailSearchToken != token) return;
                    incrementalTailSearchToken = null;
                    if (generation != searchGeneration.get() || session != tabSessionManager.getActive()
                            || session.isClosed() || filteredIndexes != target) return;
                    long trimmed = tailBase(rows) - base;
                    for (int i = 0; i < matches.size(); i++) {
                        long shifted = matches.get(i) - trimmed;
                        if (shifted >= 0 && shifted < rows.size()) target.add((int) (offset + shifted));
                    }
                    tailSearchScannedUpTo = (int) Math.max(0, end - trimmed);
                    session.setTailSearchScannedUpTo(tailSearchScannedUpTo);
                    if (searchResultPanel != null) searchResultPanel.syncItemCount();
                    if (searchNavigator != null) searchNavigator.setMatchCount(target.size());
                    if (rows.size() > tailSearchScannedUpTo) scheduleIncrementalTailSearch(session);
                });
            } catch (RuntimeException error) {
                logger.warn("Incremental tail search failed", error);
                Platform.runLater(() -> {if (incrementalTailSearchToken == token) incrementalTailSearchToken = null;});
            } finally {
                if (snapshot instanceof com.seeloggyplus.shared.tail.TailHistory.Snapshot captured) captured.close();
            }
        });
    }

    private void stopRemoteTail() {
        if (activeTailSshService != null) {
            activeTailSshService.stopTailing();
            activeTailSshService = null;
        }

        monitoringRemotePath = null;
        recentPanelController.refreshTree();
    }

    private void cleanupTempFiles() {
        Set<String> dirsToClean = new LinkedHashSet<>();
        dirsToClean.add(System.getProperty("java.io.tmpdir"));
        if (sshDownloadDirectory != null && !sshDownloadDirectory.isBlank()) {
            dirsToClean.add(sshDownloadDirectory);
        }

        Set<String> activeDownloadPaths = tabSessionManager.all().stream()
                .filter(session -> !session.isClosed() && session.getLocalFile() != null)
                .map(session -> {
                    try {
                        return session.getLocalFile().getCanonicalPath();
                    } catch (IOException e) {
                        return session.getLocalFile().getAbsolutePath();
                    }
                })
                .collect(Collectors.toSet());
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
            Alert alert = ThemedAlerts.create(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(message);
            showAndWaitAndRestore(alert);
        });
    }

    private void showRemotePathError(String remotePath) {
        Platform.runLater(() -> showAndWaitAndRestore(createRemotePathError(remotePath)));
    }

    static Alert createRemotePathError(String remotePath) {
        boolean missing = remotePath == null || remotePath.isBlank();
        Alert alert = ThemedAlerts.create(Alert.AlertType.ERROR);
        alert.setTitle("Unable to monitor log");
        alert.setHeaderText("Unable to monitor log from server");
        alert.setContentText(missing ? RemoteLogPaths.MISSING_LOCATION_MESSAGE : RemoteLogPaths.LOCAL_LOCATION_MESSAGE);

        TextArea details = new TextArea("File location: " + (missing ? "Not saved" : remotePath)
                + "\nReason: " + (missing ? "Missing server location" : "Windows-style local location"));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefColumnCount(48);
        details.setPrefRowCount(3);
        DialogPane pane = alert.getDialogPane();
        pane.setExpandableContent(details);
        pane.setPrefWidth(520);
        pane.getStyleClass().add("remote-path-error");
        alert.setOnShown(event -> addDialogIcon(pane.getScene().getWindow()));
        return alert;
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = ThemedAlerts.create(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(title);
            alert.setContentText(message);
            showAndWaitAndRestore(alert);
        });
    }

    private <T> Optional<T> showAndWaitAndRestore(Dialog<T> dialog) {
        if (menuBar == null || menuBar.getScene() == null || menuBar.getScene().getWindow() == null) {
            return dialogPresenter.present(dialog);
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

        Optional<T> result = dialogPresenter.present(dialog);

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
            Image icon = new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/images/app-icon.png")));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            logger.warn("Failed to load app icon for dialog", e);
        }
    }

    static void addDialogIcon(Window window) {
        try {
            Image icon = new Image(
                    Objects.requireNonNull(MainController.class.getResourceAsStream("/images/app-icon.png")));
            ((Stage) window).getIcons().add(icon);
        } catch (Exception e) {
            LoggerFactory.getLogger(MainController.class)
                    .warn("Failed to load app icon for dialog", e);
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
        memoryMonitorShowing = memoryStatusLabel.sceneProperty()
                .flatMap(Scene::windowProperty)
                .flatMap(Window::showingProperty)
                .orElse(false);
        memoryMonitorShowing.addListener((observable, wasShowing, showing) -> {
            if (showing) {
                updateMemoryStatus();
                timeline.play();
            } else {
                timeline.stop();
            }
        });
        if (memoryMonitorShowing.getValue()) {
            timeline.play();
        }
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

        double usedMb = used / (1024.0 * 1024.0);
        double totalMb = total / (1024.0 * 1024.0);

        double progress = total > 0 ? (double) used / total : 0;
        progress = Math.max(0, Math.min(1, progress));

        String text = String.format("Used: %.0f MB / Total: %.0f MB", usedMb, totalMb);
        memoryStatusLabel.setText(String.format("Heap: %.0f MiB", usedMb));
        Tooltip memoryTooltip = memoryStatusLabel.getTooltip();
        if (memoryTooltip == null) {
            memoryTooltip = new Tooltip();
            memoryStatusLabel.setTooltip(memoryTooltip);
        }
        memoryTooltip.setText(text);
        if (resourceDetailLabel != null) resourceDetailLabel.setText(String.format(
                "Java heap used: %.0f MiB%nCommitted: %.0f MiB%nMaximum: %.0f MiB%n%nHeap is not total process RAM. See operating-system tools for private memory and working set.",
                usedMb, totalMb, runtime.maxMemory() / (1024.0 * 1024.0)));
        updateWorkbenchTailStatus();

        memoryBar.getStyleClass().removeAll("memory-ok", "memory-warn", "memory-high");
        if (progress > 0.85) {
            memoryBar.getStyleClass().add("memory-high");
        } else if (progress > 0.60) {
            memoryBar.getStyleClass().add("memory-warn");
        } else {
            memoryBar.getStyleClass().add("memory-ok");
        }

        memoryBar.setProgress(progress);
    }

    private void startLocalTail(File file) {
        if (tabSessionManager.getActive() != null) {
            startLocalTail(tabSessionManager.getActive(), file);
        } else {
            stopLocalTail();
            liveTailList.clear();
            if (canvasLogViewer != null) {
                canvasLogViewer.setTailBuffer(liveTailList);
                canvasLogViewer.refreshTail();
            }

            boolean needsContext = (mappedFileReader == null || lineOffsetIndex == null);

            logger.info("Starting local tail (TailService) for: {} (loadContext={})", file.getAbsolutePath(),
                    needsContext);
            Thread starterThread = new Thread(() -> tailService.startLocalTail(
                    file,
                    this::handleTailLineBackground,
                    ex -> {
                        if (ex instanceof InterruptedException) {
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
        if (tabSessionManager.getActive() != null && tabSessionManager.getActive().getTailService() != null) {
            tabSessionManager.getActive().getTailService().stopTail();
        }
        if (tailService != null) {
            tailService.stopTail();
        }
    }

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

        FontAwesomeIconView fileIcon = new FontAwesomeIconView();
        fileIcon.setGlyphName(session.getSessionType() == LogSession.SessionType.LOCAL ? "FILE_TEXT" : "SERVER");
        fileIcon.setSize("12");
        fileIcon.getStyleClass().add("log-tab-icon");

        Circle liveDot = new Circle(3.5);
        liveDot.getStyleClass().add("tab-live-indicator");
        liveDot.setVisible(session.isTailModeEnabled());
        liveDot.setManaged(session.isTailModeEnabled());
        session.setLiveIndicatorDot(liveDot);

        StackPane iconBox = new StackPane(fileIcon, liveDot);
        iconBox.getStyleClass().add("log-tab-icon-container");
        StackPane.setAlignment(liveDot, Pos.TOP_RIGHT);

        Label badge = new Label();
        badge.getStyleClass().add("tab-badge-unread");
        badge.setVisible(false);
        badge.setManaged(false);
        session.setUnreadBadgeLabel(badge);

        graphicBox.getChildren().addAll(iconBox, badge);
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
            } else {
                tabSessionManager.sessions().entrySet().stream().filter(entry -> entry.getValue()==session).findFirst()
                        .ifPresent(entry -> logTabPane.getSelectionModel().select(entry.getKey()));
                if (session==tabSessionManager.getActive()) handleReload();
            }
        });

        MenuItem clearItem = new MenuItem("Clear Log View");
        clearItem.setOnAction(e -> {
            if (session == tabSessionManager.getActive()) handleClearLog(); else clearSessionLogView(session);
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
                        new ProcessBuilder(FileExplorer.selectionCommand(
                                FileExplorer.windowsDirectory(), session.getLocalFile())).start();
                    } else {
                        Desktop.getDesktop().open(session.getLocalFile().getParentFile());
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to open file explorer for {}", session.getLocalFile(), ex);
                }
            }
        });

        menu.getItems().addAll(closeItem, closeRightItem, closeOthersItem, closeAllItem, new SeparatorMenuItem(), reloadItem, clearItem, new SeparatorMenuItem(), copyPathItem, showInExplorerItem);
        return menu;
    }

    private void setupViewerCallbacks(LogSession session, CanvasLogViewer viewer) {
        if (session == null || viewer == null) return;

        viewer.setOnLineClick((lineNumber, lineContent) -> {
            session.setSelectedLine(lineNumber);
            session.setSelectedLineContent(lineContent);
            if (session == tabSessionManager.getActive()) {
                displayLogDetailFromCanvas(lineNumber, lineContent);
            }
        });

        viewer.setOnStatusUpdate((currentLine, totalLines, percentage) -> {
            if (session == tabSessionManager.getActive() && statusLabel != null) {
                if (totalLines <= 0) {
                    statusLabel.setText("Line: 0 / 0");
                } else {
                    statusLabel.setText(String.format("Line: %,d / %,d (%.1f%%)", currentLine, totalLines, percentage));
                }
            }
        });

        viewer.setOnFollowTailChanged(following -> {
            session.setFollowTail(following);
            if (session == tabSessionManager.getActive() && followTailButton != null && followTailButton.isSelected() != following) {
                isProgrammaticUpdate = true;
                followTailButton.setSelected(following);
                isProgrammaticUpdate = false;
            }
        });

        viewer.setOnSelectionChanged(summary -> {
            if (session == tabSessionManager.getActive()) {
                updateSelectionStatus(summary);
            }
        });
    }

    private void updateSelectionStatus(String summary) {
        if (selectionStatusLabel == null) {
            return;
        }
        boolean visible = summary != null && !summary.isEmpty();
        selectionStatusLabel.setText(visible ? "Selected: " + summary : "");
        selectionStatusLabel.setVisible(visible);
        selectionStatusLabel.setManaged(visible);
    }

    private void onTabSelected(Tab oldTab, Tab newTab) {
        if (oldTab != null) {
            LogSession oldSession = tabSessionManager.get(oldTab);
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
            LogSession newSession = tabSessionManager.get(newTab);
            onActiveSessionChanged(newSession);
        } else {
            onActiveSessionChanged(null);
        }

        updateEmptyState();
    }

    private void onActiveSessionChanged(LogSession session) {
        cancelSearchWork();
        if (tabSessionManager.getActive() != null && tabSessionManager.getActive() != session) {
            tabSessionManager.getActive().setActive(false);
        }

        tabSessionManager.setActive(session);

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
            currentTailSearchPredicate = currentTailSearchPattern == null ? null
                    : session.isRegex() ? raw -> currentTailSearchPattern.matcher(raw).find()
                    : createBooleanSearchPredicate(session.getSearchQuery(), session.isCaseSensitive());
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
            }
            if (followTailButton != null) {
                followTailButton.setSelected(session.isFollowTail());
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
                                        .map(Pattern::quote)
                                        .collect(Collectors.joining("|"));
                                highlightIsRegex = true;
                            }
                        }
                        canvasLogViewer.setSearchHighlight(highlightPattern, highlightIsRegex, session.isCaseSensitive());
                    }
                } else {
                    canvasLogViewer.clearSearchHighlight();
                }
            }

            if (filteredIndexes != null) {
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
                    } catch (Exception e) {
                        logger.debug("Ignoring an invalid highlight pattern", e);
                    }
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

            recentPanelController.selectForSession(session);
            updateTailButtonState();
            updateBottomBarLineCount(session);
            if (filteredIndexes == null && session.getSearchQuery() != null && !session.getSearchQuery().isBlank()) {
                performSearch();
            }
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
            recentPanelController.clearSelection();
            updateBottomBarLineCount(null);
        }

        updateSelectionStatus(canvasLogViewer == null ? null : canvasLogViewer.getSelectionSummary());
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
            tabSessionManager.remove(tab);
            logTabPane.getTabs().remove(tab);
        }

        if (tabSessionManager.getActive() == session) {
            Tab nextSelected = logTabPane != null ? logTabPane.getSelectionModel().getSelectedItem() : null;
            if (nextSelected != null) {
                onActiveSessionChanged(tabSessionManager.get(nextSelected));
            } else {
                onActiveSessionChanged(null);
            }
        }

        updateEmptyState();
        logger.info("Closed session: {}", session.getTitle());
    }

    @FXML
    public void handleCloseCurrentTab() {
        if (tabSessionManager.getActive() != null) {
            closeSession(tabSessionManager.getActive());
        }
    }

    @FXML
    public void handleCloseAllTabs() {
        List<LogSession> sessions = tabSessionManager.all();
        for (LogSession s : sessions) {
            closeSession(s);
        }
    }

    private void handleCloseOtherTabs(LogSession keepSession) {
        List<LogSession> sessions = tabSessionManager.all();
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
            LogSession s = tabSessionManager.get(t);
            if (s != null) {
                closeSession(s);
            }
        }
    }

    private boolean isNodeRelated(Node a, Node b) {
        if (a == b) return true;
        Node p = b;
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
