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
import javafx.geometry.Side;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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
    private VBox collapsedLeftPanel;
    @FXML
    private Button expandLeftPanelButton;
    @FXML
    private ListView<RecentFilesDto> recentFilesListView;
    @FXML
    private Button clearRecentButton;
    @FXML
    private Button pinLeftPanelButton;

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
    private HBox collapsedBottomPanel;
    @FXML
    private Button expandBottomPanelButton;
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
    private static final int MAX_TAIL_BUFFER_SIZE = 5000;
    private int sshDownloadThreads = 4;
    private String sshDownloadDirectory = ""; // Custom download dir, empty = system temp
    private int tailWindowSize = 20000;
    private boolean tailModeEnabled = false;
    private SSHServiceImpl activeTailSshService;
    private long remoteTailLineCounter = 0;
    private String monitoringRemotePath;

    // state
    private final java.util.concurrent.atomic.AtomicBoolean tailFlushScheduled = new java.util.concurrent.atomic.AtomicBoolean(
            false);
    private boolean tailColumnsAutoResized = false;
    private boolean isProgrammaticUpdate = false; // Prevent listener loops

    private boolean autoPrettifyJson = false;
    private boolean autoPrettifyXml = false;
    private Predicate<LogEntry> currentTailFilterPredicate = null;
    private Pattern currentTailSearchPattern = null; // Compiled pattern for incremental tail search
    private int tailSearchScannedUpTo = 0; // liveTailList index up to which search has been performed
    private final boolean isSkippingFilterTrigger = false;

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
    private com.seeloggyplus.ui.search.SearchResultPanel searchResultPanel;
    private SplitPane searchSplitPane;

    // Persistent buffer for live tailing (CanvasLogViewer reads this)
    private final List<LogEntry> liveTailList = Collections.synchronizedList(new ArrayList<>());

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
        boolean isRemote = (currentLogFromDb != null && currentLogFromDb.isRemote());
        boolean isLocal = (currentFile != null && currentFile.exists());

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
        recentFilesListView.setItems(FXCollections.observableArrayList(recentFileService.findAll()));
        recentFilesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        recentFilesListView.setContextMenu(leftPanelContextMenu);

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
        expandLeftPanelButton.setOnAction(e -> handleToggleLeftPanelPin());
        updateLeftPanelDisplay();
    }

    private void handleToggleLeftPanelPin() {
        isLeftPanelPinned = !isLeftPanelPinned;
        updateLeftPanelDisplay();
    }

    private void updateLeftPanelDisplay() {
        FontAwesomeIconView pinIcon = (FontAwesomeIconView) pinLeftPanelButton.getGraphic();
        FontAwesomeIconView expandIcon = (FontAwesomeIconView) expandLeftPanelButton.getGraphic();

        if (horizontalSplitPane == null) {
            return;
        }

        if (isLeftPanelPinned) {
            pinIcon.setGlyphName("ANGLE_DOUBLE_LEFT");

            leftPanel.setVisible(true);
            leftPanel.setManaged(true);
            collapsedLeftPanel.setVisible(false);
            collapsedLeftPanel.setManaged(false);

            Platform.runLater(() -> {
                horizontalSplitPane.applyCss();
                horizontalSplitPane.layout();
                double pos = lastHorizontalDividerPos > 0 ? lastHorizontalDividerPos : 0.2;
                horizontalSplitPane.setDividerPositions(clampDivider(pos));
            });

            if (toggleLeftPanelButton != null) {
                toggleLeftPanelButton.setSelected(true);
            }
        } else {
            if (horizontalSplitPane.getDividerPositions().length > 0) {
                lastHorizontalDividerPos = horizontalSplitPane.getDividerPositions()[0];
            }

            expandIcon.setGlyphName("ANGLE_DOUBLE_RIGHT");

            // Keep SplitPane items intact; just hide the left pane and move divider fully
            // left.
            leftPanel.setVisible(false);
            leftPanel.setManaged(false);

            collapsedLeftPanel.setVisible(true);
            collapsedLeftPanel.setManaged(false);

            Platform.runLater(() -> {
                horizontalSplitPane.applyCss();
                horizontalSplitPane.layout();
                // push divider to extreme left so right area takes all the space
                horizontalSplitPane.setDividerPositions(0.0);
            });
        }

        showLeftPanelMenuItem.setSelected(isLeftPanelPinned);
        if (toggleLeftPanelButton != null) {
            toggleLeftPanelButton.setSelected(isLeftPanelPinned);
        }
    }

    /**
     * Selects the given file in the recent files list view.
     * This improves UX by keeping the current file highlighted.
     */
    private void selectRecentFile(File file) {
        if (file == null) return;
        selectRecentFileByPath(file.getAbsolutePath());
    }

    /**
     * Selects the recent file entry matching the given path (local absolute path or remote path).
     */
    private void selectRecentFileByPath(String targetPath) {
        if (targetPath == null || recentFilesListView == null) {
            return;
        }
        Platform.runLater(() -> {
            recentFilesListView.getSelectionModel().clearSelection();
            for (RecentFilesDto dto : recentFilesListView.getItems()) {
                if (dto != null && dto.logFile() != null && targetPath.equals(dto.logFile().getFilePath())) {
                    recentFilesListView.getSelectionModel().select(dto);
                    recentFilesListView.scrollTo(dto);
                    return;
                }
            }
        });
    }

    private static final String KEYWORDS_REGEX = "(?i)(ERROR|FATAL|EXCEPTION|WARN|INFO|DEBUG|TRACE)";
    private static final Pattern KEYWORDS_PATTERN = Pattern.compile(KEYWORDS_REGEX);

    private void setupCenterPanel() {
        canvasLogViewer = new CanvasLogViewer();
        canvasLogViewer.setTailBuffer(liveTailList);
        if (followTailButton != null) {
            followTailButton.setSelected(canvasLogViewer.isFollowTail());
            canvasLogViewer.setOnFollowTailChanged(
                    isFollowing -> Platform.runLater(() -> followTailButton.setSelected(isFollowing)));
            followTailButton.selectedProperty()
                    .addListener((obs, oldVal, newVal) -> canvasLogViewer.setFollowTail(newVal));
        }

        logContainer.getChildren().add(canvasLogViewer);
        logContainer.setMinSize(0, 0);
        StackPane.setAlignment(canvasLogViewer, javafx.geometry.Pos.CENTER);

        canvasLogViewer.prefWidthProperty().bind(logContainer.widthProperty());
        canvasLogViewer.prefHeightProperty().bind(logContainer.heightProperty());
        canvasLogViewer.setMinSize(0, 0);
        canvasLogViewer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Search Result Panel — shown to the right of the log viewer when search is active
        searchResultPanel = new com.seeloggyplus.ui.search.SearchResultPanel();
        searchResultPanel.setOnLineSelected((listIndex, globalLine, content) -> {
            canvasLogViewer.jumpToLine(globalLine);
            canvasLogViewer.selectLine(globalLine);
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

        canvasLogViewer.setOnLineClick((lineNumber, lineContent) -> {
            displayLogDetailFromCanvas(lineNumber, Objects.requireNonNullElse(lineContent, ""));
        });

        canvasLogViewer.setOnLineDoubleClick((lineNumber, content) -> {
            logger.info("Double clicked line {}", lineNumber + 1);
            if (filteredIndexes != null) {
                logger.info("Clearing search and jumping to original line {}", lineNumber + 1);
                searchField.clear();
                filteredIndexes = null;
                canvasLogViewer.clearFilter();
                canvasLogViewer.clearSearchHighlight();
                hideSearchResultPanel();
                if (searchNavigator != null) searchNavigator.clear();
                canvasLogViewer.jumpToLine(lineNumber);
            }

            displayLogDetailFromCanvas(lineNumber, content);
        });

        canvasLogViewer.setOnStatusUpdate((currentLine, total, percentage) -> {
            if (statusLabel != null) {
                String formatted;
                if (tailModeEnabled) {
                    formatted = String.format("Line: %,d / %,d", currentLine, total);
                } else {
                    formatted = String.format("Line: %,d / %,d (%.1f%%)", currentLine, total, percentage);
                }
                statusLabel.setText(formatted);
            }
        });

        setupDetailPanel();

        searchField.setOnAction(e -> {
            if (searchNavigator != null && searchNavigator.isVisible()
                    && searchField.getText() != null && !searchField.getText().isEmpty()) {
                // Results already showing — Enter navigates to next match
                navigateNextMatch();
            } else {
                performSearch();
            }
        });
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                clearSearch();
            }
        });

        // Incremental search: debounce 300ms after typing
        searchDebounce = new PauseTransition(Duration.millis(300));
        searchDebounce.setOnFinished(evt -> performSearch());
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.isEmpty()) {
                searchDebounce.playFromStart();
            } else {
                searchDebounce.stop();
                // Only clear if there was actually a previous search
                if (oldText != null && !oldText.isEmpty()) {
                    filteredIndexes = null;
                    currentMatchIndex = -1;
                    searchGeneration++;
                    if (currentSearchTask != null && currentSearchTask.isRunning()) {
                        currentSearchTask.cancel(true);
                    }
                    canvasLogViewer.clearFilter();
                    canvasLogViewer.clearSearchHighlight();
                    if (searchNavigator != null) searchNavigator.clear();
                    hideSearchResultPanel();
                    if (tailModeEnabled && !canvasLogViewer.isFollowTail()) {
                        canvasLogViewer.setFollowTail(true);
                    }
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

        if (button.isSelected()) {
            button.setStyle(TOGGLE_SELECTED_STYLE);
        } else {
            button.setStyle(TOGGLE_DESELECTED_STYLE);
        }

        button.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                button.setStyle(TOGGLE_SELECTED_STYLE);
            } else {
                button.setStyle(TOGGLE_DESELECTED_STYLE);
            }
        });
    }

    private void normalizeLayoutState() {
        Platform.runLater(() -> {
            try {
                // Left panel state
                if (horizontalSplitPane != null) {
                    if (isLeftPanelPinned) {
                        if (leftPanel != null) {
                            leftPanel.setVisible(true);
                            leftPanel.setManaged(true);
                        }
                        if (collapsedLeftPanel != null) {
                            collapsedLeftPanel.setVisible(false);
                            collapsedLeftPanel.setManaged(false);
                        }
                        horizontalSplitPane.applyCss();
                        horizontalSplitPane.layout();
                        double pos = lastHorizontalDividerPos > 0 ? lastHorizontalDividerPos : 0.2;
                        horizontalSplitPane.setDividerPositions(clampDivider(pos));
                    } else {
                        if (leftPanel != null) {
                            leftPanel.setVisible(false);
                            leftPanel.setManaged(false);
                        }
                        if (collapsedLeftPanel != null) {
                            collapsedLeftPanel.setVisible(true);
                            collapsedLeftPanel.setManaged(false);
                        }
                        horizontalSplitPane.applyCss();
                        horizontalSplitPane.layout();
                        horizontalSplitPane.setDividerPositions(0.0);
                    }
                }

                // Bottom panel state
                if (verticalSplitPane != null) {
                    if (isBottomPanelPinned) {
                        if (bottomPanel != null) {
                            bottomPanel.setVisible(true);
                            bottomPanel.setManaged(true);
                        }
                        if (collapsedBottomPanel != null) {
                            collapsedBottomPanel.setVisible(false);
                            collapsedBottomPanel.setManaged(false);
                        }
                        verticalSplitPane.applyCss();
                        verticalSplitPane.layout();
                        double pos = lastVerticalDividerPos > 0 ? lastVerticalDividerPos : 0.7;
                        verticalSplitPane.setDividerPositions(clampDivider(pos));
                    } else {
                        if (bottomPanel != null) {
                            bottomPanel.setVisible(false);
                            bottomPanel.setManaged(false);
                        }
                        if (collapsedBottomPanel != null) {
                            collapsedBottomPanel.setVisible(true);
                            collapsedBottomPanel.setManaged(false);
                        }
                        verticalSplitPane.applyCss();
                        verticalSplitPane.layout();
                        verticalSplitPane.setDividerPositions(1.0);
                    }
                }
            } catch (Exception ex) {
                logger.warn("normalizeLayoutState failed", ex);
            }
        });
    }

    private void displayLogDetailFromCanvas(long lineNumber, String content) {
        if (detailCodeArea != null && content != null) {
            detailCodeArea.clear();
            detailCodeArea.replaceText(0, 0, content);
            detailLabel.setText("Line " + (lineNumber + 1));

            if (autoPrettifyJson) {
                prettifyJson();
            } else if (autoPrettifyXml) {
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

        if ((mappedFileReader == null || lineOffsetIndex == null) && !tailModeEnabled) {
            logger.warn("No file loaded for search");
            return;
        }

        if (searchText == null || searchText.trim().isEmpty()) {
            clearSearch();
            return;
        }

        if (tailModeEnabled) {
            // TAIL MODE: Highlight + Search Result Panel (same UX as local file mode)
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

            // Compile the search pattern for matching against tail buffer lines
            final Pattern matchPattern;
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                matchPattern = Pattern.compile(
                        highlightIsRegex ? highlightPattern : Pattern.quote(highlightPattern), flags);
            } catch (Exception ex) {
                logger.warn("Invalid search pattern for tail mode", ex);
                return;
            }

            filteredIndexes = new IntArrayList();
            canvasLogViewer.setFilteredIndexes(null); // Show all lines
            canvasLogViewer.setSearchHighlight(highlightPattern, highlightIsRegex, caseSensitive);

            // Scan FILE lines first (if a file is loaded alongside tail)
            long tailOffset = canvasLogViewer.getFileLineCount();
            if (mappedFileReader != null && lineOffsetIndex != null && tailOffset > 0) {
                int fileLines = lineOffsetIndex.getLineCount();
                for (int i = 0; i < fileLines; i++) {
                    CharSequence line = mappedFileReader.readLine(lineOffsetIndex, i);
                    if (line != null && matchPattern.matcher(line).find()) {
                        filteredIndexes.add(i);
                    }
                }
                logger.info("Tail search: {} matches in file lines (0..{})", filteredIndexes.size(), fileLines);
            }

            // Then scan tail buffer lines.
            // Canvas sees tail buffer as fileLineCount + bufferIndex.
            List<LogEntry> snapshot;
            synchronized (liveTailList) {
                snapshot = new ArrayList<>(liveTailList);
            }
            for (int i = 0; i < snapshot.size(); i++) {
                LogEntry entry = snapshot.get(i);
                String raw = entry != null ? entry.getRawLog() : "";
                if (matchPattern.matcher(raw).find()) {
                    filteredIndexes.add((int) (tailOffset + i));
                }
            }

            // Pause follow-tail so user can navigate search results
            if (canvasLogViewer.isFollowTail()) {
                canvasLogViewer.setFollowTail(false);
            }

            if (searchNavigator != null) {
                searchNavigator.setMatchCount(filteredIndexes.size());
            }

            // Show search result panel with tail-mode resolver
            if (filteredIndexes.size() > 0) {
                showSearchResultPanelForTail(filteredIndexes, matchPattern);
                // Jump to first match
                int firstLine = filteredIndexes.get(0);
                canvasLogViewer.jumpToLine(firstLine);
                canvasLogViewer.selectLine(firstLine);
                currentMatchIndex = 1;
                if (searchNavigator != null) {
                    searchNavigator.updateStatus(1, filteredIndexes.size());
                }
            } else {
                hideSearchResultPanel();
                currentMatchIndex = -1;
            }

            // Store pattern for incremental tail search
            currentTailSearchPattern = matchPattern;
            tailSearchScannedUpTo = snapshot.size(); // mark how far we've scanned

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

            // Store matches for navigation but do NOT filter the view —
            // all lines remain visible, matches are only highlighted.
            filteredIndexes = matches;
            canvasLogViewer.setFilteredIndexes(null); // Show all lines

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

            canvasLogViewer.setSearchHighlight(highlightPattern, highlightIsRegex, caseSensitive);

            // Compile pattern for search result panel highlight
            Pattern compiledHighlight = null;
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                compiledHighlight = Pattern.compile(
                        highlightIsRegex ? highlightPattern : Pattern.quote(highlightPattern), flags);
            } catch (Exception ignored) {}

            if (searchNavigator != null) {
                searchNavigator.setMatchCount(matches.size());
            }

            // Show search result panel with clickable list of matched lines
            if (matches.size() > 0) {
                showSearchResultPanel(matches, compiledHighlight);
                // Jump to first match
                int firstLine = matches.get(0);
                canvasLogViewer.jumpToLine(firstLine);
                canvasLogViewer.selectLine(firstLine);
                currentMatchIndex = 1;
                if (searchNavigator != null) {
                    searchNavigator.updateStatus(1, matches.size());
                }
            } else {
                hideSearchResultPanel();
                currentMatchIndex = -1;
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
        canvasLogViewer.jumpToLine(globalLine);
        canvasLogViewer.selectLine(globalLine);
        currentMatchIndex = nextIdx + 1; // advance for next call
        if (searchNavigator != null) {
            searchNavigator.updateStatus(nextIdx + 1, total);
        }
        if (searchResultPanel != null) {
            searchResultPanel.selectIndex(nextIdx);
        }
    }

    private void navigatePreviousMatch() {
        // Unified navigation using filteredIndexes for both local and tail mode
        if (filteredIndexes == null || filteredIndexes.size() == 0) return;
        int total = filteredIndexes.size();

        int prevIdx = currentMatchIndex - 2; // currentMatchIndex is 1-based "next to visit"
        if (prevIdx < 0) prevIdx = total - 1; // wrap

        int globalLine = filteredIndexes.get(prevIdx);
        canvasLogViewer.jumpToLine(globalLine);
        canvasLogViewer.selectLine(globalLine);
        currentMatchIndex = prevIdx + 1;
        if (searchNavigator != null) {
            searchNavigator.updateStatus(prevIdx + 1, total);
        }
        if (searchResultPanel != null) {
            searchResultPanel.selectIndex(prevIdx);
        }
    }

    private void clearSearch() {
        searchField.clear();
        filteredIndexes = null;
        currentMatchIndex = -1;
        currentTailSearchPattern = null;
        tailSearchScannedUpTo = 0;
        // Invalidate any in-flight search tasks
        searchGeneration++;
        if (currentSearchTask != null && currentSearchTask.isRunning()) {
            currentSearchTask.cancel(true);
        }
        canvasLogViewer.clearFilter();
        canvasLogViewer.clearSearchHighlight();
        if (searchNavigator != null) {
            searchNavigator.clear();
        }
        hideSearchResultPanel();
        // Restore follow-tail when clearing search in tail mode
        if (tailModeEnabled && !canvasLogViewer.isFollowTail()) {
            canvasLogViewer.setFollowTail(true);
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
        long tailOffset = canvasLogViewer.getFileLineCount();
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
    private void cleanupFileResources() {
        if (mappedFileReader != null) {
            mappedFileReader.close();
            mappedFileReader = null;
        }
        lineOffsetIndex = null;
        filteredIndexes = null;
        totalEntries = 0;
        canvasLogViewer.clearFilter();
        canvasLogViewer.clearSearchHighlight();
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
                MappedFileReader reader = new com.seeloggyplus.util.MappedFileReader(file);
                lineOffsetIndex = index;
                mappedFileReader = reader;
                totalEntries = index.getLineCount();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            logger.info("Index complete! {} entries available", totalEntries);

            Platform.runLater(() -> {
                canvasLogViewer.loadFile(mappedFileReader, lineOffsetIndex);
                if (jumpToEnd && totalEntries > 0) {
                    canvasLogViewer.jumpToLine(totalEntries - 1);
                } else {
                    canvasLogViewer.jumpToLine(0);
                }
                canvasLogViewer.requestCanvasFocus();
                logger.info("Canvas viewer initialized");
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
                selectRecentFile(file);
            }

            hideLoading();
            updateTailButtonState();
            currentLoadingTask = null;
            logger.info("File loaded with Canvas-based viewer. RAM usage minimal.");
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
        if (server == null || currentParsingConfig == null) {
            showError("Reload Error", "Missing server or parsing configuration.");
            return;
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
            }
        });
        copyButton.setOnAction(e -> copyDetailToClipboard());
        clearDetailButton.setOnAction(e -> clearDetail());
        pinBottomPanelButton.setOnAction(e -> handleToggleBottomPanelPin());
        expandBottomPanelButton.setOnAction(e -> handleToggleBottomPanelPin());

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
        if (detailCodeArea.getText() == null || detailCodeArea.getText().isEmpty()) {
            return;
        }

        if (autoPrettifyJson) {
            prettifyJson();
        }
        if (autoPrettifyXml) {
            prettifyXml();
        }
    }

    private void handleToggleBottomPanelPin() {
        isBottomPanelPinned = !isBottomPanelPinned;
        updateBottomPanelDisplay();
    }

    private void updateBottomPanelDisplay() {
        FontAwesomeIconView pinIcon = (FontAwesomeIconView) pinBottomPanelButton.getGraphic();
        FontAwesomeIconView expandIcon = (FontAwesomeIconView) expandBottomPanelButton.getGraphic();

        if (verticalSplitPane == null) {
            return;
        }

        if (isBottomPanelPinned) {
            pinIcon.setGlyphName("ANGLE_DOUBLE_DOWN");

            bottomPanel.setVisible(true);
            bottomPanel.setManaged(true);
            collapsedBottomPanel.setVisible(false);
            collapsedBottomPanel.setManaged(false);

            Platform.runLater(() -> {
                verticalSplitPane.applyCss();
                verticalSplitPane.layout();
                double pos = lastVerticalDividerPos > 0 ? lastVerticalDividerPos : 0.7;
                verticalSplitPane.setDividerPositions(clampDivider(pos));
            });

            if (toggleBottomPanelButton != null) {
                toggleBottomPanelButton.setSelected(true);
            }
        } else {
            if (verticalSplitPane.getDividerPositions().length > 0) {
                lastVerticalDividerPos = verticalSplitPane.getDividerPositions()[0];
            }

            expandIcon.setGlyphName("ANGLE_DOUBLE_LEFT");
            expandIcon.setRotate(90);

            // Keep SplitPane items intact; just hide the bottom pane and move divider fully
            // down.
            bottomPanel.setVisible(false);
            bottomPanel.setManaged(false);

            collapsedBottomPanel.setVisible(true);
            collapsedBottomPanel.setManaged(false);

            Platform.runLater(() -> {
                verticalSplitPane.applyCss();
                verticalSplitPane.layout();
                // push divider to extreme bottom so top area takes all the space
                verticalSplitPane.setDividerPositions(1.0);
            });
        }

        showBottomPanelMenuItem.setSelected(isBottomPanelPinned);
        if (toggleBottomPanelButton != null) {
            toggleBottomPanelButton.setSelected(isBottomPanelPinned);
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

            dialog.showAndWait();

            FileInfo selectedFile = controller.getSelectedFile();
            SSHServiceImpl sshService = controller.getSshService();
            UnifiedFileManagerDialogController.OpenAction action = controller.getOpenAction();
            SSHServerModel sshServer = controller.getActiveServer();

            if (selectedFile == null) {
                logger.info("No file selected from UnifiedFileManagerDialog, operation cancelled.");
                if (sshService != null) {
                    sshService.disconnect();
                }
                return;
            }

            if (selectedFile.getSourceType() == FileInfo.SourceType.LOCAL) {
                openLocalLogFile(new File(selectedFile.getPath()), true);
                if (sshService != null) {
                    sshService.disconnect();
                }
            } else {
                if (action == UnifiedFileManagerDialogController.OpenAction.OPEN) {
                    openRemoteLogFile(selectedFile.getPath(), selectedFile.getName(), sshService);
                } else if (action == UnifiedFileManagerDialogController.OpenAction.TAIL) {
                    startRemoteTail(selectedFile.getPath(), sshService, sshServer);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to open Unified File Manager", e);
            showError("Error Opening File Browser", "Could not open the file browser: " + e.getMessage());
        }
    }

    private void openRemoteLogFile(String remotePath, String remoteFileName, SSHServiceImpl sshService) {
        if (sshService == null || !sshService.isConnected()) {
            showError("Connection Error", "SSH connection is not active. Please re-select the file.");
            return;
        }
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
                logger.info("Downloading remote file {} to temporary path {}", remotePath,
                        localTmpFile.getAbsolutePath());
                boolean success = sshService.downloadFileConcurrent(remotePath, localTmpFile.getAbsolutePath(),
                        sshDownloadThreads,
                        new LogParser.ProgressCallback() {
                            @Override
                            public void onProgress(double progress, long bytesProcessed, long totalBytes) {
                                Platform.runLater(() -> {
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
            File localFile = downloadTask.getValue();
            hideLoading();
            openLocalLogFile(localFile, true); // Removed parsingConfig
            sshService.disconnect();
        });

        downloadTask.setOnFailed(e -> {
            hideLoading();
            Throwable ex = downloadTask.getException();
            logger.error("Failed to download remote file", ex);
            showError("Remote File Error", "Failed to download file: " + ex.getMessage());
            sshService.disconnect();
        });
        Thread.ofVirtual().start(downloadTask);
    }

    @FXML
    public void handleCancelProcessing() {
        logger.info("User requested processing cancellation.");
        cancelCurrentLoadingTask();
        hideLoading();
    }

    private void cancelCurrentLoadingTask() {
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            logger.info("Cancelling previous loading task...");
            currentLoadingTask.cancel(true);
            logger.info("Previous task cancellation requested.");
        }

        cleanupFileResources();
        Thread.ofVirtual().start(() -> {
            System.gc();
            logger.info("Memory cleanup (GC) triggered on background thread");
        });

        logger.info("Memory cleanup requested");
    }

    private void openLocalLogFile(File file, boolean updateRecentFilesList) {
        if (file == null || !file.exists()) {
            logger.error("File does not exist: {}", file);
            showError("File Error", "The selected file does not exist or cannot be accessed.");
            return;
        }

        ParsingConfig parsingConfig = ParsingConfig.createRawConfig(); // Created internally
        logger.info("Using default RAW configuration for local file: {}", file.getAbsolutePath());

        clearSearch();

        cancelCurrentLoadingTask();
        if (tailModeEnabled) {
            disableTail(true); // skipReindex: we're about to load a different file
        }
        if (followTailButton != null) {
            followTailButton.setSelected(false);
        }

        // Always clear tail buffers when loading a new file to prevent stale tail data from appearing
        synchronized (tailBuffer) {
            tailBuffer.clear();
        }
        liveTailList.clear();
        tailFlushScheduled.set(false);

        // Make sure UI layout state is consistent before starting a new load.
        normalizeLayoutState();

        currentFile = file;
        lastSession = LastSession.local(file);
        currentParsingConfig = parsingConfig;

        LogFile logFile = getOrCreateLogFile(file);

        if (logFile == null) {
            logger.error("Failed to get or create log file record for: {}", file.getAbsolutePath());
            showError("Database Error", "Failed to save log file information to database.");
            return;
        }

        this.currentLogFromDb = logFile;
        this.currentFile = file;

        long fileSizeInBytes = file.length();
        logger.info("Starting to load file: {} ({}) - Notepad++ Style (RAM-based)", file.getName(),
                FileUtils.formatFileSize(fileSizeInBytes));
        loadFileWithParallelParsing(file, logFile, updateRecentFilesList);
    }

    private LogFile getOrCreateLogFile(File file) {
        try {
            ParsingConfig parsingConfig = ParsingConfig.createRawConfig(); // Internal default
            LogFile existingLogFile = logFileService.getLogFileByPathAndName(file.getName(), file.getAbsolutePath());

            if (existingLogFile != null) {
                logger.info("LogFile found in database, updating metadata for: {}", file.getAbsolutePath());
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
                logger.info("LogFile not found in database, creating new entry for: {}", file.getAbsolutePath());
                LogFile newLogFile = new LogFile();
                newLogFile.setName(file.getName());
                newLogFile.setFilePath(file.getAbsolutePath());
                newLogFile.setRemote(false);
                newLogFile.setSshServerID(null);
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
        hideLoading();
        cancelCurrentLoadingTask();
        if (tailModeEnabled) {
            disableTail(true); // skipReindex: a new file/tail is about to be loaded
        }
        LogFile logFile = recentFile.logFile();

        if (logFile.isRemote()) {
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
                    logger.info("SSH connected for remote recent file, proceeding to tail.");
                    resetFilters();
                    startRemoteTail(logFile.getFilePath(), sshService,
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
        detailContainer.getChildren().add(vsp);
    }

    private StyleSpans<Collection<String>> computeHighlightingSpans(String text) {
        Matcher matcher = KEYWORDS_PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass = switch (matcher.group().toUpperCase()) {
                case "ERROR", "FATAL", "EXCEPTION" -> "error";
                case "WARN" -> "warn";
                case "INFO" -> "info";
                case "DEBUG" -> "debug";
                case "TRACE" -> "trace";
                default -> "default";
            };
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private void prettifyJson() {
        String fullText = detailCodeArea.getText();
        if (fullText == null || fullText.isEmpty()) {
            return;
        }

        StringBuilder newTextBuilder = new StringBuilder(fullText);
        int offset = 0;

        while (true) {
            String remainingText = newTextBuilder.substring(offset);
            String extractedJson = JsonPrettify.extractJson(remainingText);

            if (extractedJson == null) {
                break;
            }

            String prettifiedJson = JsonPrettify.prettify(extractedJson);

            int start = newTextBuilder.indexOf(extractedJson, offset);
            if (start == -1) {
                break;
            }
            int end = start + extractedJson.length();

            newTextBuilder.replace(start, end, prettifiedJson);
            offset = start + prettifiedJson.length();
        }

        if (!newTextBuilder.toString().equals(fullText)) {
            detailCodeArea.replaceText(newTextBuilder.toString());
            try {
                detailCodeArea.setStyleSpans(0, computeHighlightingSpans(newTextBuilder.toString()));
            } catch (Exception e) {
                logger.warn("CSS load fail in pretty json");
            }
        }
    }

    private void prettifyXml() {
        String fullText = detailCodeArea.getText();
        if (fullText == null || fullText.isEmpty()) {
            return;
        }

        StringBuilder newTextBuilder = new StringBuilder(fullText);
        int offset = 0;

        while (true) {
            String remainingText = newTextBuilder.substring(offset);
            String extractedXml = XmlPrettify.extractXml(remainingText);

            if (extractedXml == null) {
                break;
            }

            String prettifiedXml = XmlPrettify.prettify(extractedXml);

            int start = newTextBuilder.indexOf(extractedXml, offset);
            if (start == -1) {
                break;
            }
            int end = start + extractedXml.length();

            newTextBuilder.replace(start, end, prettifiedXml);
            offset = start + prettifiedXml.length();
        }

        if (!newTextBuilder.toString().equals(fullText)) {
            detailCodeArea.replaceText(newTextBuilder.toString());
            try {
                detailCodeArea.setStyleSpans(0, computeHighlightingSpans(newTextBuilder.toString()));
            } catch (Exception e) {
                logger.warn("CSS load fail in pretty xml");
            }
        }
    }

    private void copyDetailToClipboard() {
        String text = detailCodeArea.getText();
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private void clearDetail() {
        detailCodeArea.clear();
        detailLabel.setText("Log Detail");
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

                    if (logFile.isRemote() && monitoringRemotePath != null
                            && monitoringRemotePath.equals(logFile.getFilePath())) {
                        stopRemoteTail();
                    }

                    recentFileService.deleteByFileId(logFile.getId());
                    logFileService.deleteLogFileById(logFile.getId());
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
            recentFileService.deleteAll();
            logFileService.deleteAllLogFiles();
            stopRemoteTail();
            clearSearch();
            refreshRecentFilesList();
            cleanupTempFiles();
        }
    }

    private void refreshRecentFilesList() {
        recentFilesListView.setItems(FXCollections.observableArrayList(recentFileService.findAll()));
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
        stopLocalTail();
        if (activeTailSshService != null) {
            stopRemoteTail();
        }
        logger.info("Application exiting, stopped tailers.");
        Platform.exit();
    }

    @FXML
    private void enableTail() {
        boolean isRemote = (currentLogFromDb != null && currentLogFromDb.isRemote());
        boolean isLocal = (currentFile != null && currentFile.exists());

        if (!isRemote && !isLocal) {
            showInfo("Tail Mode", "No active file to tail.");
            if (tailButton.isSelected())
                tailButton.setSelected(false);
            return;
        }

        if (currentParsingConfig == null) {
            String cfgId = (currentLogFromDb != null) ? currentLogFromDb.getParsingConfigurationID() : null;
            if (cfgId != null && !cfgId.isBlank()) {
                currentParsingConfig = parsingConfigService.findById(cfgId).orElse(null);
            }
        }

        if (currentParsingConfig == null) {
            showError("Tail Error", "No parsing configuration available. Please select one.");
            if (tailButton.isSelected())
                tailButton.setSelected(false);
            return;
        }

        tailModeEnabled = true;
        if (!tailButton.isSelected()) {
            tailButton.setSelected(true);
        }
        // Style handled by listener

        // Clear live buffer for new session
        liveTailList.clear();
        canvasLogViewer.refreshTail(); // Updates view to show cleared state

        remoteTailLineCounter = 0;

        try {
            currentTailFilterPredicate = buildSearchPredicate(searchField.getText(), regexCheckBox.isSelected(),
                    caseSensitiveCheckBox.isSelected());
        } catch (Exception e) {
            logger.warn("Tail filter error", e);
        }

        if (isRemote) {
            startRemoteTailInternal();
        } else {
            startLocalTail(currentFile);
        }
    }

    private void startRemoteTailInternal() {
        String sshServerId = currentLogFromDb.getSshServerID();
        SSHServerModel server = serverManagementService.getServerById(sshServerId);
        if (server == null) {
            showError("Error", "Server not found");
            disableTail();
            return;
        }

        String remotePath = currentLogFromDb.getFilePath();
        if (remotePath == null || remotePath.isEmpty()) {
            showError("Error", "Remote path is missing");
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
                if (getValue()) {
                    hideLoading();
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
                showError("Connection Error", getException().getMessage());
                disableTail();
            }
        };

        new Thread(connectTask).start();
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
        tailModeEnabled = false;
        if (tailButton.isSelected()) {
            isProgrammaticUpdate = true;
            tailButton.setSelected(false);
            isProgrammaticUpdate = false;
        }
        tailButton.setStyle("");
        stopRemoteTail();
        stopLocalTail();

        // Clear both tail buffers to prevent stale data from bleeding into the next file load
        synchronized (tailBuffer) {
            tailBuffer.clear();
        }
        liveTailList.clear();
        tailFlushScheduled.set(false);
        currentTailSearchPattern = null;
        tailSearchScannedUpTo = 0;

        // Atomically transition the viewer out of tail mode
        canvasLogViewer.exitTailMode();

        // For local files: re-index so new lines appended during tail are visible,
        // but only when we're staying on the same file (not switching to a different one).
        if (!skipReindex && currentFile != null && currentFile.exists()) {
            logger.info("Re-indexing local file after tail: {}", currentFile.getName());
            LogFile logFile = currentLogFromDb != null ? currentLogFromDb : getOrCreateLogFile(currentFile);
            if (logFile != null) {
                loadFileWithParallelParsing(currentFile, logFile, false, true, false);
            }
        }

        logger.info("Tail mode DISABLED");
    }

    private void startRemoteTail(String remotePath, SSHServiceImpl sshService, SSHServerModel server) {
        stopRemoteTail();
        clearSearch(); // Reset search/filters when starting new tail

        saveRemoteTailToRecent(remotePath, server);

        // The parsing config is now created and set within saveRemoteTailToRecent
        // and assigned to currentLogFromDb.
        // We need to retrieve it from currentLogFromDb or re-create it here if not
        // already set.
        // For now, let's assume currentLogFromDb is set by saveRemoteTailToRecent
        // and we can get the config from there.
        ParsingConfig rawConfig = ParsingConfig.createRawConfig(); // Re-create for local use if not already set
        if (currentLogFromDb != null && currentLogFromDb.getParsingConfigurationID() != null) {
            rawConfig = parsingConfigService.findById(currentLogFromDb.getParsingConfigurationID())
                    .orElse(ParsingConfig.createRawConfig());
        }

        this.activeTailSshService = sshService;
        this.remoteTailLineCounter = 0;
        this.currentParsingConfig = rawConfig; // Used rawConfig
        this.currentFile = null;

        // Save for reload after clear
        this.lastSession = LastSession.remote(remotePath, this.currentLogFromDb, sshService);

        liveTailList.clear();
        canvasLogViewer.setTailBuffer(liveTailList);
        canvasLogViewer.resetView(); // Clean slate for remote tail
        logger.info("ListView ready for config: {}", rawConfig.getName()); // Used rawConfig
        tailFlushScheduled.set(false); // Force reset flush lock
        tailColumnsAutoResized = false; // Force reset log flag
        tailModeEnabled = true;
        if (!tailButton.isSelected()) {
            isProgrammaticUpdate = true;
            tailButton.setSelected(true);
            isProgrammaticUpdate = false;
        }

        try {
            currentTailFilterPredicate = buildSearchPredicate(searchField.getText(), regexCheckBox.isSelected(),
                    caseSensitiveCheckBox.isSelected());
            logger.info("Tail filter initialized from current UI filters.");
        } catch (Exception e) {
            logger.error("Failed to build tail filter predicate", e);
            showError("Tail Filter Error", e.getMessage());
            currentTailFilterPredicate = null;
        }

        updateTailButtonState();

        // Design Pattern Compliance: Use SSHService's tailFile which handles 'tail -n
        // <lines> -F'
        // This provides the initial lines (Pre-fetch) AND starts the real-time stream.
        sshService.tailFile(remotePath, tailWindowSize, line -> handleTailLineBackground(line),
                error -> Platform.runLater(() -> {
                    logger.error("Remote tail error: {}", error);
                    showError("Remote Tail Error", error);
                    disableTail();
                }));
    }

    private void saveRemoteTailToRecent(String remotePath, SSHServerModel server) {
        try {
            ParsingConfig parsingConfig = ParsingConfig.createRawConfig(); // Internal default
            String fileName = new File(remotePath).getName();
            LogFile logFile = logFileService.getLogFileByPathAndName(fileName, remotePath);

            if (logFile == null) {
                logFile = new LogFile();
                logFile.setName(fileName);
                logFile.setFilePath(remotePath);
                logFile.setRemote(true);
                logFile.setSshServerID(server.getId());
                logFile.setParsingConfigurationID(parsingConfig.getId());
                logFile.setModified(String.valueOf(System.currentTimeMillis()));
                logFile.setSize("-");

                logFileService.insertLogFile(logFile);
                logger.info("Created remote LogFile for tail: id={}, path={}", logFile.getId(), remotePath);
            } else {
                logFile.setRemote(true);
                logFile.setSshServerID(server.getId());
                logFile.setParsingConfigurationID(parsingConfig.getId());
                logFile.setModified(String.valueOf(System.currentTimeMillis()));
                logFile.setSize("-"); // Reset size for tail
                logFileService.updateLogFile(logFile);
                logger.info("Updated existing LogFile for remote tail: id={}, path={}", logFile.getId(), remotePath);
            }

            Optional<RecentFile> existingRecentOpt = recentFileService.findByFileId(logFile.getId());
            boolean createdNewRecent;

            if (existingRecentOpt.isEmpty()) {
                RecentFile recent = new RecentFile();
                recent.setFileId(logFile.getId());
                recent.setLastOpened(LocalDateTime.now());

                recentFileService.save(logFile, recent);
                createdNewRecent = true;

                logger.info("Created new RecentFile for remote tail: fileId={}", logFile.getId());
            } else {
                createdNewRecent = false;
                logger.info("Remote tail for existing recent fileId={}, NOT updating lastOpened (no re-sort)",
                        logFile.getId());
            }

            this.currentLogFromDb = logFile;
            this.monitoringRemotePath = remotePath;

            final String pathToSelect = remotePath;
            Platform.runLater(() -> {
                if (createdNewRecent) {
                    refreshRecentFilesList();
                } else {
                    recentFilesListView.refresh();
                }
                selectRecentFileByPath(pathToSelect);
            });
        } catch (Exception e) {
            logger.error("Failed to save remote tail to recent for path {}", remotePath, e);
        }
    }

    private synchronized void handleTailLineBackground(String line) {
        long lineNumber = ++remoteTailLineCounter;
        LogEntry entry = logParserService.parseLine(line, lineNumber);
        synchronized (tailBuffer) {
            if (tailBuffer.size() >= MAX_TAIL_BUFFER_SIZE) {
                tailBuffer.removeFirst();
            }
            tailBuffer.add(entry);
        }

        scheduleTailFlush();
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
        if (!tailFlushScheduled.compareAndSet(false, true)) {
            return;
        }

        Platform.runLater(() -> {
            tailFlushScheduled.set(false);

            // Guard: don't flush if tail mode was disabled while this was queued
            if (!tailModeEnabled) {
                synchronized (tailBuffer) {
                    tailBuffer.clear();
                }
                return;
            }

            try {
                List<LogEntry> toAdd;
                synchronized (tailBuffer) {
                    if (tailBuffer.isEmpty()) {
                        return;
                    }
                    toAdd = new ArrayList<>(tailBuffer);
                    tailBuffer.clear();
                }

                int oldSize = liveTailList.size();
                liveTailList.addAll(toAdd);

                // Limit Logic here
                int trimmed = 0;
                if (liveTailList.size() > 50000) {
                    trimmed = liveTailList.size() - 50000;
                    liveTailList.subList(0, trimmed).clear();
                }

                canvasLogViewer.refreshTail();

                // Incremental search: scan new lines and append matches to result panel
                if (tailModeEnabled && currentTailSearchPattern != null && filteredIndexes != null) {
                    long tailOffset = canvasLogViewer.getFileLineCount();

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
                    for (int i = tailSearchScannedUpTo; i < liveTailList.size(); i++) {
                        LogEntry entry = liveTailList.get(i);
                        String raw = entry != null ? entry.getRawLog() : "";
                        if (currentTailSearchPattern.matcher(raw).find()) {
                            int globalIdx = (int) (tailOffset + i);
                            filteredIndexes.add(globalIdx);
                            newMatches.add(globalIdx);
                        }
                    }
                    tailSearchScannedUpTo = liveTailList.size();

                    // Update search result panel — since panel.matchedLines is the same
                    // reference as filteredIndexes, we only need to sync itemCount/cache.
                    if (newMatches.size() > 0 && searchResultPanel != null) {
                        searchResultPanel.syncItemCount();
                    }
                    if (searchNavigator != null) {
                        searchNavigator.setMatchCount(filteredIndexes.size());
                    }
                } else if (tailModeEnabled && canvasLogViewer.hasSearchHighlight()) {
                    if (searchNavigator != null) {
                        searchNavigator.setMatchCount(canvasLogViewer.countMatches());
                    }
                }

                if (!tailColumnsAutoResized) {
                    tailColumnsAutoResized = true;
                    logger.debug("Tail batch displayed: {} lines", toAdd.size());
                }
            } catch (Throwable t) {
                logger.error("CRITICAL ERROR in tail flush", t);
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
        stopLocalTail();
        liveTailList.clear();
        canvasLogViewer.refreshTail();

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
                if (line > 0) {
                    canvasLogViewer.jumpToLine(line - 1);
                }
                followTailButton.setSelected(false);
            } catch (NumberFormatException e) {
                logger.warn("Invalid line number");
            }
        });
    }

    private void stopLocalTail() {
        if (tailService != null) {
            tailService.stopTail();
        }
    }
}
