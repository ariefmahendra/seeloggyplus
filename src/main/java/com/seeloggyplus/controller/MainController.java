package com.seeloggyplus.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.*;
import com.seeloggyplus.service.*;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.seeloggyplus.service.impl.*;
import com.seeloggyplus.util.JsonPrettify;
import com.seeloggyplus.util.XmlPrettify;
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
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main controller for the SeeLoggyPlus application Manages the main UI components
 * and coordinates between services
 */
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final int LAZY_LOAD_BATCH_SIZE = 1000; // Number of entries to load at a time

    // FXML Components - MenuBar
    @FXML
    private MenuBar menuBar;
    @FXML
    private Menu fileMenu;
    @FXML
    private MenuItem openFileMenuItem;
    @FXML
    private MenuItem openRemoteMenuItem;
    @FXML
    private MenuItem exitMenuItem;
    @FXML
    private Menu viewMenu;
    @FXML
    private CheckMenuItem showLeftPanelMenuItem;
    @FXML
    private CheckMenuItem showBottomPanelMenuItem;
    @FXML
    private Menu settingsMenu;
    @FXML
    private MenuItem parsingConfigMenuItem;
    @FXML
    private Menu helpMenu;
    @FXML
    private MenuItem aboutMenuItem;

    // FXML Components - Main Layout
    @FXML
    private BorderPane mainBorderPane;
    @FXML
    private BorderPane mainContentPane; // New BorderPane to manage collapsed left panel
    @FXML
    private SplitPane horizontalSplitPane;
    @FXML
    private SplitPane verticalSplitPane;

    // FXML Components - Left Panel (Recent Files)
    @FXML
    private VBox leftPanel;
    @FXML
    private VBox collapsedLeftPanel; // New VBox for the collapsed left panel
    @FXML
    private Button expandLeftPanelButton; // Button to expand the collapsed left panel
    @FXML
    private ListView<RecentFilesDto> recentFilesListView;
    @FXML
    private Button clearRecentButton;
    @FXML
    private Button pinLeftPanelButton;

    // FXML Components - Center Panel (Log Table)
    @FXML
    private VBox centerPanel;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TextField searchField;
    @FXML
    private CheckBox regexCheckBox;
    @FXML
    private CheckBox caseSensitiveCheckBox;
    @FXML
    private Button searchButton;
    @FXML
    private Button clearSearchButton;
    @FXML
    private ComboBox<String> logLevelFilterComboBox;
    @FXML
    private TextField dateTimeFromField;
    @FXML
    private TextField dateTimeToField;
    @FXML
    private Button clearDateFilterButton;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    public CheckBox hideUnparsedCheckBox;
    @FXML
    public Button autoFitButton;
    @FXML
    private TableView<LogEntry> logTableView;
    @FXML
    private Button scrollToTopButton;
    @FXML
    private Button scrollToBottomButton;
    @FXML
    private Button refreshButton;

    // FXML Components - Bottom Panel (Log Detail)
    @FXML
    private VBox bottomPanel;
    @FXML
    private HBox collapsedBottomPanel; // New HBox for the collapsed bottom panel
    @FXML
    private Button expandBottomPanelButton; // Button to expand the collapsed bottom panel
    @FXML
    private Button pinBottomPanelButton; // Button to pin/unpin bottom panel
    @FXML
    private Label detailLabel;
    @FXML
    private CodeArea detailTextArea;
    @FXML
    private HBox detailButtonsBox;
    @FXML
    private Button prettifyJsonButton;
    @FXML
    private Button prettifyXmlButton;
    @FXML
    private Button prettifySqlButton;
    @FXML
    private Button copyButton;
    @FXML
    private Button clearDetailButton;

    // Services and Data
    private ParsingConfigService parsingConfigService;
    private RecentFileService recentFileService;
    private LogParserService logParserService;
    private PreferenceService preferenceService;
    private LogFileService logFileService;

    private SSHService sshService;
    private final Cache<LogCacheKey, LogCacheValue> logCache = Caffeine.newBuilder()
            .maximumSize(5)
            .build();

    private LogEntrySource currentLogEntrySource; // The full source of log entries (can be filtered)
    private LogEntrySource originalLogEntrySource; // Stores the original, unfiltered log entries
    private ObservableList<LogEntry> visibleLogEntries; // Only the entries currently displayed in the TableView
    private ParsingConfig currentParsingConfig;
    private File currentFile;
    private boolean isLeftPanelPinned = true; // Default to pinned
    private boolean isBottomPanelPinned = true; // Default to pinned
    private volatile boolean isLoadingMoreEntries = false; // Flag to prevent multiple simultaneous loads

    // Task management for cancellation
    private Task<?> currentLoadingTask = null; // Track current loading task for cancellation

    // File watcher for auto-refresh (like 'tail -f')
    private LogFileWatcher logFileWatcher;
    private CheckMenuItem autoRefreshMenuItem; // Toggle auto-refresh

    /**
     * Initialize the controller
     */
    @FXML
    public void initialize() {
        logger.info("Initializing MainController");

        parsingConfigService = new ParsingConfigServiceImpl();
        recentFileService = new RecentConfigServiceImpl();
        preferenceService = new PreferenceServiceImpl();
        logParserService = new LogParserService();
        sshService = new SSHService();
        logFileService = new LogFileServiceImpl();

        // Initialize file watcher for auto-refresh (tail -f behavior)
        logFileWatcher = new LogFileWatcher();
        try {
            logFileWatcher.start();
            logger.info("LogFileWatcher started successfully");
        } catch (Exception e) {
            logger.error("Failed to start LogFileWatcher", e);
        }

        // Initialize data
        visibleLogEntries = FXCollections.observableArrayList();

        // Setup UI components
        setupMenuBar();
        setupLeftPanel();
        setupCenterPanel();
        setupBottomPanel();
        setupKeyboardShortcuts();
        setupLogLevelFilter();

        // Restore panel visibility from preferences
        restorePanelVisibility();

        // Set default status
        updateStatus("Ready");
        progressBar.setVisible(false);
    }

    @FXML
    public void onLoginButtonClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginLdapDialog.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            Stage stage = new Stage();
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e){
            logger.error("Failed to open LDAP login dialog", e);
        }

    }

    /**
     * Setup menu bar actions
     */
    private void setupMenuBar() {
        // File Menu
        openFileMenuItem.setOnAction(e -> handleOpenFile());
        openRemoteMenuItem.setOnAction(e -> handleOpenRemote());
        exitMenuItem.setOnAction(e -> handleExit());

        // View Menu
        showLeftPanelMenuItem.setSelected(true);
        showBottomPanelMenuItem.setSelected(true);
        showLeftPanelMenuItem.setOnAction(e -> toggleLeftPanel());
        showBottomPanelMenuItem.setOnAction(e -> toggleBottomPanel());

        // Add Auto-Refresh menu item dynamically
        autoRefreshMenuItem = new CheckMenuItem("Auto-Refresh (tail -f)");
        autoRefreshMenuItem.setSelected(true); // Default: enabled
        autoRefreshMenuItem.setOnAction(e -> toggleAutoRefresh());
        viewMenu.getItems().add(2, autoRefreshMenuItem); // Add after show panels

        // Settings Menu
        parsingConfigMenuItem.setOnAction(e -> handleParsingConfiguration());

        // Help Menu
        aboutMenuItem.setOnAction(e -> handleAbout());
    }

    /**
     * Setup left panel (Recent Files)
     */
    private void setupLeftPanel() {
        // Configure recent files list view
        recentFilesListView.setCellFactory(listView -> new RecentFileListCell());
        recentFilesListView.setItems(FXCollections.observableArrayList(recentFileService.findAll()));

        // Handle selection
        recentFilesListView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (
                            newVal != null && (currentFile == null || !currentFile.getAbsolutePath().equals(newVal.logFile().getFilePath()))
                    ) {
                        handleRecentFileSelected(newVal);
                    }
                });

        // Clear recent button
        clearRecentButton.setOnAction(e -> handleClearRecentFiles());

        // Pin button action
        pinLeftPanelButton.setOnAction(e -> handleToggleLeftPanelPin());
        expandLeftPanelButton.setOnAction(e -> handleToggleLeftPanelPin());

        // Initial display state
        updateLeftPanelDisplay();
    }

    /**
     * Handles toggling the pin state of the left panel.
     */
    private void handleToggleLeftPanelPin() {
        isLeftPanelPinned = !isLeftPanelPinned;
        updateLeftPanelDisplay();
    }

    /**
     * Updates the display of the left panel based on its pinned state.
     * This method manages visibility, pin icon, and split pane divider positions.
     */
    private void updateLeftPanelDisplay() {
        FontAwesomeIconView pinIcon = (FontAwesomeIconView) pinLeftPanelButton.getGraphic();
        FontAwesomeIconView expandIcon = (FontAwesomeIconView) expandLeftPanelButton.getGraphic();
        
        if (isLeftPanelPinned) {
            pinIcon.setGlyphName("ANGLE_DOUBLE_LEFT");
            leftPanel.setVisible(true);
            leftPanel.setManaged(true);
            collapsedLeftPanel.setVisible(false);
            collapsedLeftPanel.setManaged(false);
            // Restore divider position
            Platform.runLater(() -> {
                double savedWidth = 200; // Default value
                double totalWidth = horizontalSplitPane.getWidth();
                if (totalWidth > 0) {
                    double position = savedWidth / totalWidth;
                    horizontalSplitPane.setDividerPositions(position);
                } else {
                    horizontalSplitPane.setDividerPositions(0.2);
                }
            });
        } else {
            expandIcon.setGlyphName("ANGLE_DOUBLE_RIGHT");
            leftPanel.setVisible(false);
            leftPanel.setManaged(false);
            collapsedLeftPanel.setVisible(true);
            collapsedLeftPanel.setManaged(true);
            // Adjust split pane divider to make space for the collapsed panel
            Platform.runLater(() -> {
                horizontalSplitPane.setDividerPositions(0.0);
            });
        }
        // Also update the CheckMenuItem in the View menu
        showLeftPanelMenuItem.setSelected(isLeftPanelPinned);
    }

    /**
     * Setup center panel (Log Table)
     */
    private void setupCenterPanel() {
        // Configure table view with performance optimizations
        logTableView.setItems(visibleLogEntries);
        
        // PERFORMANCE OPTIMIZATION 1: Use UNCONSTRAINED resize for better scrolling
        logTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        
        // PERFORMANCE OPTIMIZATION 2: Disable table menu button (reduces overhead)
        logTableView.setTableMenuButtonVisible(false);
        
        // PERFORMANCE OPTIMIZATION 3: Dynamic row height based on content
        // No custom row factory needed - use default behavior
        // Unparsed entries will be distinguished by badge only (not row color)
        
        // PERFORMANCE OPTIMIZATION 4: Disable placeholder (reduces rendering)
        logTableView.setPlaceholder(new javafx.scene.control.Label(""));

        // Handle row selection
        logTableView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        displayLogDetail(newVal);
                    }
                });

        // Handle double-click to jump to original position (when filtered)
        logTableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                LogEntry selectedEntry = logTableView.getSelectionModel().getSelectedItem();
                if (selectedEntry != null && isFilterActive()) {
                    jumpToOriginalPosition(selectedEntry);
                }
            }
        });

        // Setup search functionality
        searchButton.setOnAction(e -> performSearch());
        clearSearchButton.setOnAction(e -> clearSearch());
        
        // PERFORMANCE: Remove auto-trigger on Enter, use button only for better control
        // searchField.setOnAction(e -> performSearch());
        
        autoFitButton.setOnAction(e -> {
            autoResizeColumns(logTableView);
            logger.info("Manual auto-fit columns triggered");
        });
        scrollToTopButton.setOnAction(e -> handleScrollToTop());
        scrollToBottomButton.setOnAction(e -> handleScrollToBottom());
        refreshButton.setOnAction(e -> handleRefreshCurrentFile());
        
        // PERFORMANCE: Date/time filter - use button only, no auto-trigger
        // dateTimeFromField.setOnAction(e -> performSearch());
        // dateTimeToField.setOnAction(e -> performSearch());
        clearDateFilterButton.setOnAction(e -> clearDateFilter());
        
        // Set default prompt text for date/time filters (will be updated when config is loaded)
        updateDateTimeFilterPromptText(null);


        // Initialize with default columns
        updateTableColumns(null);

        // NOTE: Scroll listener DISABLED - we now load ALL entries upfront
        // JavaFX TableView has built-in virtual scrolling (only renders visible rows)
        // No need for lazy loading anymore!
        logger.info("Virtual scrolling enabled with performance optimizations - smooth scrolling for large datasets");
    }

    /**
     * Handles the action to scroll the log table to the very top.
     * Loads first 5000 entries and scrolls to top.
     */
    private void handleScrollToTop() {
        if (currentLogEntrySource == null || currentLogEntrySource.getTotalEntries() == 0) {
            return;
        }

        int totalAvailable = currentLogEntrySource.getTotalEntries();
        int currentSize = visibleLogEntries.size();

        final int TOP_VIEW_SIZE = 5000;

        if (currentSize >= totalAvailable && !visibleLogEntries.isEmpty()) {
            // Already loaded all entries, just scroll to top
            logTableView.scrollTo(0);
            updateStatus(String.format("At top. Showing all %d entries", totalAvailable));
            return;
        }

        updateStatus(String.format("Jumping to top... Loading first %d entries",
            Math.min(TOP_VIEW_SIZE, totalAvailable)));
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<List<LogEntry>> loadTopTask = new Task<>() {
            @Override
            protected List<LogEntry> call() {
                int limit = Math.min(TOP_VIEW_SIZE, totalAvailable);

                logger.info("Smart scroll to top: loading entries from 0 to {} (total: {})",
                    limit, totalAvailable);

                return currentLogEntrySource.getEntries(0, limit);
            }
        };

        loadTopTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                List<LogEntry> topEntries = loadTopTask.getValue();

                // Clear current view and show only top entries
                visibleLogEntries.clear();
                visibleLogEntries.addAll(topEntries);

                // Scroll to first entry
                if (!visibleLogEntries.isEmpty()) {
                    logTableView.scrollTo(0);
                }

                progressBar.setVisible(false);

                int displayedCount = visibleLogEntries.size();
                updateStatus(String.format("At top. Showing first %d of %d entries from %s",
                    displayedCount, totalAvailable,
                    currentFile != null ? currentFile.getName() : ""));

                logger.info("Scrolled to top, showing {} entries", displayedCount);
            });
        });

        loadTopTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            logger.error("Failed to scroll to top", loadTopTask.getException());
            showError("Scroll Error", "Failed to jump to top: " + loadTopTask.getException().getMessage());
            updateStatus("Scroll to top failed.");
        });

        new Thread(loadTopTask).start();
    }

    /**
     * Handles the action to scroll the log table to the very bottom.
     * Uses the same logic as initial load for consistency (like 'tail' command)
     */
    private void handleScrollToBottom() {
        if (currentLogEntrySource == null || currentLogEntrySource.getTotalEntries() == 0) {
            return;
        }

        int totalAvailable = currentLogEntrySource.getTotalEntries();

        updateStatus("📍 Jumping to bottom (tail mode)...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<Void> scrollTask = new Task<>() {
            @Override
            protected Void call() {
                // Use same logic as initial load for consistency
                Platform.runLater(() -> {
                    scrollToBottomAfterLoad();
                });
                return null;
            }
        };

        scrollTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                progressBar.setVisible(false);

                int displayedCount = visibleLogEntries.size();
                updateStatus(String.format("📍 At bottom. Showing last %,d of %,d entries from %s",
                    displayedCount, totalAvailable,
                    currentFile != null ? currentFile.getName() : ""));

                logger.info("Scrolled to bottom, showing {} entries (TAIL MODE)", displayedCount);
            });
        });

        scrollTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            logger.error("Failed to scroll to bottom", scrollTask.getException());
            showError("Scroll Error", "Failed to jump to bottom: " + scrollTask.getException().getMessage());
            updateStatus("Scroll to bottom failed.");
        });

        new Thread(scrollTask).start();
    }

    /**
     * Loads previous entries (before current first entry) for upward scrolling in tail mode.
     * Adds entries to the BEGINNING of visibleLogEntries list.
     */
    private void loadPreviousEntries() {
        if (currentLogEntrySource == null || visibleLogEntries.isEmpty()) {
            return;
        }

        // Prevent multiple simultaneous loads
        if (isLoadingMoreEntries) {
            logger.debug("Already loading entries, skipping request");
            return;
        }

        long firstLineNumber = visibleLogEntries.get(0).getLineNumber();
        
        if (firstLineNumber <= 1) {
            logger.debug("Already at beginning of file (line {})", firstLineNumber);
            return; // Already at the beginning
        }

        isLoadingMoreEntries = true;

        // Show loading indicator
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        updateStatus(String.format("Loading previous entries before line %d...", firstLineNumber));

        Task<List<LogEntry>> loadTask = new Task<>() {
            @Override
            protected List<LogEntry> call() {
                // FIXED: Use getEntriesBeforeLine for accurate line number tracking
                // For lazy loading with FileBasedLogEntrySource
                if (currentLogEntrySource instanceof com.seeloggyplus.service.impl.FileBasedLogEntrySource) {
                    com.seeloggyplus.service.impl.FileBasedLogEntrySource fileSource =
                        (com.seeloggyplus.service.impl.FileBasedLogEntrySource) currentLogEntrySource;
                    
                    logger.debug("Loading previous entries before line {} using accurate method", firstLineNumber);
                    return fileSource.getEntriesBeforeLine(firstLineNumber, LAZY_LOAD_BATCH_SIZE);
                } else {
                    // For in-memory source (shouldn't happen, but fallback)
                    int entriesToLoad = Math.min(LAZY_LOAD_BATCH_SIZE, (int) firstLineNumber - 1);
                    int startOffset = Math.max(0, (int) firstLineNumber - 1 - entriesToLoad);
                    
                    logger.debug("Loading previous entries: startOffset={}, limit={}, firstLine={}", 
                        startOffset, entriesToLoad, firstLineNumber);
                    
                    return currentLogEntrySource.getEntries(startOffset, entriesToLoad);
                }
            }
        };

        loadTask.setOnSucceeded(e -> {
            List<LogEntry> previousEntries = loadTask.getValue();
            
            if (previousEntries.isEmpty()) {
                logger.info("No previous entries to load");
                updateStatus("At beginning of file");
                progressBar.setVisible(false);
                isLoadingMoreEntries = false;
                return;
            }
            
            // CRITICAL VALIDATION: Detect and prevent infinite loop bugs
            long currentFirstLine = visibleLogEntries.get(0).getLineNumber();
            long loadedFirstLine = previousEntries.get(0).getLineNumber();
            long loadedLastLine = previousEntries.get(previousEntries.size() - 1).getLineNumber();
            
            // Check 1: Loaded entries must be BEFORE current entries
            if (loadedLastLine >= currentFirstLine) {
                logger.error("INVALID LOAD: Loaded entries (ending at line {}) overlap/after current entries (starting at line {})",
                    loadedLastLine, currentFirstLine);
                logger.error("This indicates a bug in line number calculation. Stopping to prevent infinite loop.");
                showError("Load Error", 
                    String.format("Invalid line numbers detected (loaded: %d-%d, current: %d+). Cannot load more entries.",
                        loadedFirstLine, loadedLastLine, currentFirstLine));
                updateStatus("Error: Invalid line numbers detected");
                progressBar.setVisible(false);
                isLoadingMoreEntries = false;
                return;
            }
            
            // Check 2: Detect duplicate line numbers within loaded entries
            Set<Long> lineNumberSet = new HashSet<>();
            for (LogEntry entry : previousEntries) {
                if (!lineNumberSet.add(entry.getLineNumber())) {
                    logger.error("DUPLICATE LINE NUMBER DETECTED: Line {} appears multiple times in loaded entries",
                        entry.getLineNumber());
                    showError("Load Error", 
                        String.format("Duplicate line number %d detected. This indicates a parsing bug.",
                            entry.getLineNumber()));
                    updateStatus("Error: Duplicate line numbers detected");
                    progressBar.setVisible(false);
                    isLoadingMoreEntries = false;
                    return;
                }
            }
            
            // Check 3: Verify line numbers are continuous (optional warning)
            long expectedLastLine = currentFirstLine - 1;
            if (loadedLastLine != expectedLastLine) {
                logger.warn("Gap detected: loaded entries end at line {}, but current entries start at line {} (gap: {})",
                    loadedLastLine, currentFirstLine, currentFirstLine - loadedLastLine - 1);
            }
            
            // Remember current scroll position
            int currentScrollIndex = logTableView.getSelectionModel().getSelectedIndex();
            
            // Add previous entries to the BEGINNING of the list
            visibleLogEntries.addAll(0, previousEntries);

            // Restore scroll position (adjusted for new entries)
            int newScrollIndex = currentScrollIndex + previousEntries.size();
            logTableView.scrollTo(newScrollIndex);

            logger.debug("Loaded {} previous entries (lines {} to {}). Total visible: {}",
                previousEntries.size(), loadedFirstLine, loadedLastLine, visibleLogEntries.size());

            int totalAvailable = currentLogEntrySource.getTotalEntries();
            updateStatus(String.format("Showing %d of %d entries from %s",
                visibleLogEntries.size(), totalAvailable,
                currentFile != null ? currentFile.getName() : ""));

            progressBar.setVisible(false);
            isLoadingMoreEntries = false;
        });

        loadTask.setOnFailed(e -> {
            logger.error("Failed to load previous entries", loadTask.getException());
            updateStatus("Failed to load previous entries");
            progressBar.setVisible(false);
            isLoadingMoreEntries = false;
        });

        new Thread(loadTask).start();
    }

    /**
     * Loads more entries into the visibleLogEntries list from the currentLogEntrySource.
     * Shows loading indicator and runs in background thread for better UX.
     * Uses flag to prevent multiple simultaneous loads.
     * FIXED: Properly handles lazy loading by using actual line numbers, not indices
     */
    private void loadMoreVisibleEntries() {
        if (currentLogEntrySource == null || visibleLogEntries.isEmpty()) {
            return;
        }

        // Prevent multiple simultaneous loads
        if (isLoadingMoreEntries) {
            logger.debug("Already loading more entries, skipping request");
            return;
        }

        int totalAvailable = currentLogEntrySource.getTotalEntries();
        long lastLineNumber = visibleLogEntries.get(visibleLogEntries.size() - 1).getLineNumber();

        // Check if we're already at the end of the file
        if (lastLineNumber >= totalAvailable) {
            logger.debug("Already at end of file (line {}/{}), no more to load", lastLineNumber, totalAvailable);
            return;
        }

        isLoadingMoreEntries = true;

        // Show loading indicator
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        updateStatus(String.format("Loading more entries after line %d...", lastLineNumber));

        Task<List<LogEntry>> loadTask = new Task<>() {
            @Override
            protected List<LogEntry> call() {
                // FIXED: offset is 0-based index, but line numbers are 1-based
                // To get entries AFTER lastLineNumber, we use lastLineNumber as offset (0-based)
                // This will start reading from line (lastLineNumber + 1)
                int offset = (int) lastLineNumber; // This is correct: offset is 0-based
                int limit = Math.min(LAZY_LOAD_BATCH_SIZE, totalAvailable - (int) lastLineNumber);
                
                logger.debug("Loading more entries: lastLineNumber={}, offset={}, limit={}", 
                    lastLineNumber, offset, limit);
                
                return currentLogEntrySource.getEntries(offset, limit);
            }
        };

        loadTask.setOnSucceeded(e -> {
            List<LogEntry> newEntries = loadTask.getValue();
            
            // Check if we actually got new entries
            if (newEntries.isEmpty()) {
                logger.info("No more entries to load - reached end of file at line {}", lastLineNumber);
                updateStatus(String.format("At end. Showing %d of %d entries from %s",
                    visibleLogEntries.size(), totalAvailable,
                    currentFile != null ? currentFile.getName() : ""));
                progressBar.setVisible(false);
                isLoadingMoreEntries = false;
                return;
            }
            
            // Filter out any duplicate entries (safety check)
            long lastLineBeforeAdd = visibleLogEntries.get(visibleLogEntries.size() - 1).getLineNumber();
            List<LogEntry> filteredNewEntries = newEntries.stream()
                .filter(entry -> entry.getLineNumber() > lastLineBeforeAdd)
                .toList();
            
            if (filteredNewEntries.isEmpty()) {
                logger.warn("All new entries were duplicates, reached end of file");
                progressBar.setVisible(false);
                isLoadingMoreEntries = false;
                return;
            }
            
            visibleLogEntries.addAll(filteredNewEntries);

            logger.debug("Loaded {} new entries (filtered: {}). Total visible: {} / {}",
                newEntries.size(), filteredNewEntries.size(), visibleLogEntries.size(), totalAvailable);

            updateStatus(String.format("Showing %d of %d entries from %s",
                visibleLogEntries.size(), totalAvailable,
                currentFile != null ? currentFile.getName() : ""));

            progressBar.setVisible(false);
            isLoadingMoreEntries = false;
        });

        loadTask.setOnFailed(e -> {
            logger.error("Failed to load more entries", loadTask.getException());
            updateStatus("Failed to load more entries");
            progressBar.setVisible(false);
            isLoadingMoreEntries = false;
        });

        new Thread(loadTask).start();
    }

    /**
     * Setup bottom panel (Log Detail)
     */
    private void setupBottomPanel() {
        // Configure detail text area
        detailTextArea = new CodeArea();
        detailTextArea.setEditable(false);
        detailTextArea.setWrapText(true);
        detailTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

        // Add to bottom panel if not already in FXML
        if (bottomPanel.getChildren().size() < 3) {
            bottomPanel.getChildren().add(1, detailTextArea);
            VBox.setVgrow(detailTextArea, Priority.ALWAYS);
        }

        // Setup buttons
        prettifyJsonButton.setOnAction(e -> prettifyJson());
        prettifyXmlButton.setOnAction(e -> prettifyXml());
        copyButton.setOnAction(e -> copyDetailToClipboard());
        clearDetailButton.setOnAction(e -> clearDetail());
        
        // Pin button action
        pinBottomPanelButton.setOnAction(e -> handleToggleBottomPanelPin());
        expandBottomPanelButton.setOnAction(e -> handleToggleBottomPanelPin());
        
        // Initial display state
        updateBottomPanelDisplay();
    }
    
    /**
     * Handles toggling the pin state of the bottom panel.
     */
    private void handleToggleBottomPanelPin() {
        isBottomPanelPinned = !isBottomPanelPinned;
        updateBottomPanelDisplay();
    }
    
    /**
     * Updates the display of the bottom panel based on its pinned state.
     * This method manages visibility, pin icon, and split pane divider positions.
     */
    private void updateBottomPanelDisplay() {
        FontAwesomeIconView pinIcon = (FontAwesomeIconView) pinBottomPanelButton.getGraphic();
        FontAwesomeIconView expandIcon = (FontAwesomeIconView) expandBottomPanelButton.getGraphic();
        
        if (isBottomPanelPinned) {
            pinIcon.setGlyphName("ANGLE_DOUBLE_DOWN");
            bottomPanel.setVisible(true);
            bottomPanel.setManaged(true);
            collapsedBottomPanel.setVisible(false);
            collapsedBottomPanel.setManaged(false);
            // Restore divider position
            Platform.runLater(() -> {
                double savedHeight = 200; // Default value
                double totalHeight = verticalSplitPane.getHeight();
                if (totalHeight > 0) {
                    double position = 1.0 - (savedHeight / totalHeight);
                    verticalSplitPane.setDividerPositions(position);
                } else {
                    verticalSplitPane.setDividerPositions(0.7);
                }
            });
        } else {
            expandIcon.setGlyphName("ANGLE_DOUBLE_LEFT");
            expandIcon.setRotate(90); // Rotate to point upward
            bottomPanel.setVisible(false);
            bottomPanel.setManaged(false);
            collapsedBottomPanel.setVisible(true);
            collapsedBottomPanel.setManaged(true);
            // Adjust split pane divider to hide bottom panel
            Platform.runLater(() -> {
                verticalSplitPane.setDividerPositions(1.0);
            });
        }
        // Also update the CheckMenuItem in the View menu
        showBottomPanelMenuItem.setSelected(isBottomPanelPinned);
    }

    private void setupLogLevelFilter() {
        logLevelFilterComboBox.setItems(FXCollections.observableArrayList("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "UNPARSED"));
        logLevelFilterComboBox.getSelectionModel().select("ALL");
        
        // PERFORMANCE: Remove auto-trigger, use manual search button only
        // logLevelFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> performSearch());
        
        // PERFORMANCE: Remove auto-trigger for checkbox
        // hideUnparsedCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> performSearch());
    }

    /**
     * Setup keyboard shortcuts
     */
    private void setupKeyboardShortcuts() {
        // Ctrl+O - Open File
        // Ctrl+F - Focus Search
        // Ctrl+R - Open Remote
        // etc.
    }

    /**
     * Update table columns based on parsing configuration
     */
    private void updateTableColumns(ParsingConfig config) {
        logger.info( "Updating table columns with config: {}", config != null ? config.getName() : "null");
        logTableView.getColumns().clear();

        // OPTIMIZED: Line number column with caching (simple, no badge)
        TableColumn<LogEntry, String> lineCol = new TableColumn<>("Line");
        lineCol.setCellValueFactory(cellData -> {
            LogEntry entry = cellData.getValue();
            String lineText;
            if (entry.getLineNumber() != entry.getEndLineNumber()) {
                lineText = entry.getLineNumber() + "-" + entry.getEndLineNumber();
            } else {
                lineText = String.valueOf(entry.getLineNumber());
            }
            return new SimpleStringProperty(lineText);
        });
        lineCol.setPrefWidth(80);
        lineCol.setMinWidth(80);
        lineCol.setMaxWidth(120);
        lineCol.setSortable(false); // Disable sorting for performance (prevents not responding)
        logTableView.getColumns().add(lineCol);

        if (config != null && config.isValid()) {
            List<String> groupNames = config.getGroupNames();
            logger.info( "Config has {} named groups: {}", groupNames.size(), groupNames);

            for (int i = 0; i < groupNames.size(); i++) {
                final int currentIndex = i;
                final String groupName = groupNames.get(i);

                TableColumn<LogEntry, String> column = new TableColumn<>(groupName);

                column.setCellValueFactory(cellData -> {
                    LogEntry entry = cellData.getValue();
                    if (entry.isParsed()) {
                        return new SimpleStringProperty(entry.getField(groupName));
                    } else {
                        if (currentIndex == 0) {
                            return new SimpleStringProperty(entry.getRawLog());
                        } else {
                            return new SimpleStringProperty("");
                        }
                    }
                });
                
                // Add cell factory for first column to support multi-line unparsed entries
                if (currentIndex == 0) {
                    column.setCellFactory(col -> new TableCell<LogEntry, String>() {
                        private static final int MAX_LINES_DISPLAY = 10; // Max lines to display
                        private static final int MAX_CHARS_PER_LINE = 200; // Max chars per line
                        private static final int MAX_TOTAL_CHARS = 2000; // Max total characters
                        
                        private Label contentLabel = null;
                        
                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);
                            
                            if (item == null || empty) {
                                setText(null);
                                setGraphic(null);
                                contentLabel = null;
                            } else {
                                LogEntry entry = getTableRow() != null ? getTableRow().getItem() : null;
                                
                                if (entry != null && !entry.isParsed()) {
                                    // UNPARSED ENTRY: Use Label with wrapping for multi-line display
                                    String displayText = formatUnparsedContent(item);
                                    
                                    // Create or reuse Label
                                    if (contentLabel == null) {
                                        contentLabel = new Label();
                                        contentLabel.setWrapText(true);
                                        contentLabel.setMaxWidth(Double.MAX_VALUE);
                                    }
                                    
                                    contentLabel.setText(displayText);
                                    
                                    // No special styling - use default colors
                                    contentLabel.setStyle("-fx-padding: 5px;");
                                    
                                    setText(null);
                                    setGraphic(contentLabel);
                                } else {
                                    // PARSED ENTRY: Normal single-line display
                                    setText(item);
                                    setGraphic(null);
                                    contentLabel = null;
                                }
                            }
                        }
                        
                        /**
                         * Format unparsed content with intelligent truncation
                         * - Shows first N lines
                         * - Limits line length
                         * - Adds "... (X more lines)" indicator
                         */
                        private String formatUnparsedContent(String rawContent) {
                            if (rawContent == null || rawContent.isEmpty()) {
                                return rawContent;
                            }
                            
                            // Split into lines
                            String[] lines = rawContent.split("\\r?\\n");
                            
                            if (lines.length == 1 && rawContent.length() <= MAX_CHARS_PER_LINE) {
                                // Single short line: display as-is
                                return rawContent;
                            }
                            
                            StringBuilder display = new StringBuilder();
                            int totalChars = 0;
                            int displayedLines = 0;
                            
                            for (int i = 0; i < lines.length && displayedLines < MAX_LINES_DISPLAY; i++) {
                                String line = lines[i];
                                
                                // Check if adding this line exceeds total char limit
                                if (totalChars + line.length() > MAX_TOTAL_CHARS) {
                                    break;
                                }
                                
                                // Truncate long lines
                                if (line.length() > MAX_CHARS_PER_LINE) {
                                    line = line.substring(0, MAX_CHARS_PER_LINE) + "...";
                                }
                                
                                if (displayedLines > 0) {
                                    display.append("\n");
                                }
                                display.append(line);
                                
                                totalChars += line.length();
                                displayedLines++;
                            }
                            
                            // Add indicator if there are more lines
                            int remainingLines = lines.length - displayedLines;
                            if (remainingLines > 0) {
                                display.append("\n... (");
                                display.append(remainingLines);
                                display.append(" more line");
                                if (remainingLines > 1) display.append("s");
                                display.append(")");
                            }
                            
                            return display.toString();
                        }
                    });
                }

                if ("level".equalsIgnoreCase(groupName)) {
                    // OPTIMIZED: Level cell with badge display (performance-focused with object reuse)
                    column.setCellFactory(col -> new TableCell<LogEntry, String>() {
                        private Label badge = null; // Reuse badge for performance
                        
                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);

                            if (item == null || empty) {
                                setText(null);
                                setGraphic(null);
                            } else {
                                LogEntry entry = getTableRow() != null ? getTableRow().getItem() : null;
                                
                                // Check if this is unparsed entry
                                if (entry != null && !entry.isParsed()) {
                                    // UNPARSED: Show "UNPARSED" badge
                                    if (badge == null) {
                                        badge = new Label();
                                    }
                                    badge.setText("UNPARSED");
                                    badge.setStyle(
                                        "-fx-background-color: #FF9800; " +  // Orange
                                        "-fx-text-fill: white; " +
                                        "-fx-padding: 3px 8px; " +
                                        "-fx-background-radius: 3px; " +
                                        "-fx-font-weight: bold; " +
                                        "-fx-font-size: 10px;"
                                    );
                                    setText(null);
                                    setGraphic(badge);
                                } else {
                                    // PARSED: Show level badge with appropriate color
                                    if (badge == null) {
                                        badge = new Label();
                                    }
                                    badge.setText(item);
                                    
                                    // Get badge color based on level
                                    String bgColor = getLevelColor(item);
                                    badge.setStyle(
                                        "-fx-background-color: " + bgColor + "; " +
                                        "-fx-text-fill: white; " +
                                        "-fx-padding: 3px 8px; " +
                                        "-fx-background-radius: 3px; " +
                                        "-fx-font-weight: bold; " +
                                        "-fx-font-size: 10px;"
                                    );
                                    setText(null);
                                    setGraphic(badge);
                                }
                            }
                        }
                        
                        // Get color for log level (performance: inline, no external calls)
                        private String getLevelColor(String level) {
                            if (level == null) return "#9E9E9E"; // Gray
                            
                            switch (level.toUpperCase()) {
                                case "ERROR":   return "#F44336"; // Red
                                case "FATAL":   return "#D32F2F"; // Dark Red
                                case "WARN":
                                case "WARNING": return "#FF9800"; // Orange
                                case "INFO":    return "#2196F3"; // Blue
                                case "DEBUG":   return "#4CAF50"; // Green
                                case "TRACE":   return "#9E9E9E"; // Gray
                                default:        return "#607D8B"; // Blue Gray
                            }
                        }
                    });
                }

                column.setMinWidth(80);
                column.setSortable(false); // Disable sorting for performance (prevents not responding)
                logTableView.getColumns().add(column);
            }
            logger.info("Created {} columns total (including line number)", logTableView.getColumns().size());
        } else {
            logger.warn("Config is null or invalid, using default raw log column");
            TableColumn<LogEntry, String> rawCol = new TableColumn<>("Log Message");
            rawCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRawLog()));
            rawCol.setPrefWidth(800);
            rawCol.setSortable(false); // Disable sorting for performance
            logTableView.getColumns().add(rawCol);
            logger.info("Created 2 columns (line number + raw log)");
        }
    }


    /**
     * Handle open file action
     */
    private void handleOpenFile() {
        // First, show file chooser to select log file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Log File");
        fileChooser
                .getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("Log Files", "*.log", "*.txt"),
                        new FileChooser.ExtensionFilter("All Files", "*.*")
                );

        File file = fileChooser.showOpenDialog(menuBar.getScene().getWindow());
        if (file == null) {
            logger.info("No file selected, operation cancelled");
            return;
        }

        // After file is selected, show parsing configuration selection dialog
        ParsingConfig selectedConfig = showParsingConfigSelectionDialog();

        if (selectedConfig == null) {
            logger.info("No parsing configuration selected, operation cancelled");
            return;
        }

        // Now open and parse the file with selected configuration
        openLocalLogFile(file, true, selectedConfig);
    }

    /**
     * Handle open remote file action
     */
    private void handleOpenRemote() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RemoteFileDialog.fxml"));
            Parent root = loader.load();

            Stage dialog = new Stage();
            dialog.setTitle("Open Remote File");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(menuBar.getScene().getWindow());
            dialog.setScene(new Scene(root));

            // Get controller and set callback
            // RemoteFileDialogController controller = loader.getController();
            // controller.setOnFileSelected(this::openRemoteFile);

            dialog.showAndWait();
        } catch (IOException e) {
            logger.error("Failed to open remote file dialog", e);
            showError("Failed to open remote file dialog", e.getMessage());
        }
    }


    /**
     * Cancel current loading task and cleanup memory
     */
    private void cancelCurrentLoadingTask() {
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            logger.info("⚠️ Cancelling previous loading task...");
            currentLoadingTask.cancel(true);
            
            // Wait briefly for cancellation
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            logger.info("✅ Previous task cancelled");
        }
        
        // Clear memory
        if (visibleLogEntries != null && !visibleLogEntries.isEmpty()) {
            int previousSize = visibleLogEntries.size();
            visibleLogEntries.clear();
            logger.info("🗑️ Cleared {} entries from memory", previousSize);
        }
        
        // Clear sources
        currentLogEntrySource = null;
        originalLogEntrySource = null;
        
        // Force garbage collection (optional, JVM will decide)
        System.gc();
        
        logger.info("✅ Memory cleanup complete");
    }

    /**
     * Open and parse log file with specified parsing configuration
     *
     * @param file                  The file to open
     * @param updateRecentFilesList true if the file should be added to the top of the recent files list
     * @param parsingConfig         The parsing configuration to use
     */
    private void openLocalLogFile(File file, boolean updateRecentFilesList, ParsingConfig parsingConfig) {
        if (file == null || !file.exists()) {
            logger.error("File does not exist: {}", file);
            showError("File Error", "The selected file does not exist or cannot be accessed.");
            return;
        }

        if (parsingConfig == null) {
            logger.warn("Parsing config is null");
            showInfo("Log Parsing Configuration", "Parsing Configuration Not Ready. Please Setup First.");
            return;
        }

        // CRITICAL: Cancel any existing loading task and cleanup memory
        cancelCurrentLoadingTask();

        currentFile = file;
        currentParsingConfig = parsingConfig;
        
        // IMPORTANT: Update date filter prompt BEFORE parsing starts
        // This ensures user sees correct format hint immediately
        updateDateTimeFilterPromptText(parsingConfig);
        logger.info("📅 Updated date filter prompt to match parsing config: {} (format: {})",
            parsingConfig.getName(),
            parsingConfig.getTimestampFormat() != null ? parsingConfig.getTimestampFormat() : "default");

        // Step 1: Get or create LogFile record in database
        LogFile logFile = getOrCreateLogFile(file, parsingConfig);

        if (logFile == null) {
            logger.error("Failed to get or create log file record for: {}", file.getAbsolutePath());
            showError("Database Error", "Failed to save log file information to database.");
            return;
        }

        // NEW STRATEGY: Always use parallel parsing + virtual scrolling for all file sizes
        long fileSizeInBytes = file.length();
        logger.info("Starting to parse file: {} ({}) with config: {}",
            file.getName(),
            com.seeloggyplus.util.FileUtils.formatFileSize(fileSizeInBytes),
            parsingConfig.getName());

        logger.info("Using parallel parsing strategy with virtual scrolling for optimal performance");
        loadFileWithParallelParsing(file, parsingConfig, logFile, updateRecentFilesList);
    }

    /**
     * UNIFIED: Load file with parallel parsing (for ALL file sizes)
     * - Uses multi-threaded parallel parsing for speed
     * - Loads ALL entries into memory
     * - Virtual scrolling handles large datasets efficiently
     * - Simple, fast, and consistent for any file size
     */
    private void loadFileWithParallelParsing(File file, ParsingConfig parsingConfig, LogFile logFile, boolean updateRecentFilesList) {
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        updateStatus("Parsing file: " + file.getName());

        final ParsingConfig configToUse = parsingConfig;

        Task<List<LogEntry>> task = new Task<>() {
            @Override
            protected List<LogEntry> call() throws IOException {
                // Use parallel parsing for speed
                return logParserService.parseFileParallel(
                    file,
                    configToUse,
                    new LogParserService.ProgressCallback() {
                        @Override
                        public void onProgress(double progress, long bytesProcessed, long totalBytes) {
                            updateProgress(bytesProcessed, totalBytes);
                            Platform.runLater(() -> {
                                progressBar.setProgress(progress);
                                updateStatus(String.format(
                                    "Parsing... %.1f%% (%s / %s)",
                                    progress * 100,
                                    formatBytes(bytesProcessed),
                                    formatBytes(totalBytes)
                                ));
                            });
                        }

                        @Override
                        public void onComplete(long totalEntries) {
                            Platform.runLater(() -> {
                                logger.info("✅ Parsed {} entries", totalEntries);
                            });
                        }
                    }
                );
            }
        };

        task.setOnSucceeded(e -> {
            List<LogEntry> entries = task.getValue();
            logger.info("✅ Parsing complete! Loaded {} entries", entries.size());

            // Create source and populate table
            originalLogEntrySource = new ListLogEntrySourceImpl(entries);
            currentLogEntrySource = originalLogEntrySource;

            // Populate visible entries (all entries for virtual scrolling)
            visibleLogEntries.clear();
            visibleLogEntries.addAll(entries);

            updateTableColumns(currentParsingConfig);
            logger.info("Updated table columns for config: {}", currentParsingConfig.getName());

            // Scroll to bottom and auto-fit columns after load complete
            Platform.runLater(() -> {
                if (!visibleLogEntries.isEmpty()) {
                    logTableView.scrollTo(visibleLogEntries.size() - 1);
                }
                
                // Auto-fit columns ONCE after load complete
                autoResizeColumns(logTableView);
                logger.info("Auto-fit columns completed after load");
            });

            // Add to recent files
            if (updateRecentFilesList) {
                RecentFile recentFile = new RecentFile();
                recentFile.setFileId(logFile.getId());
                recentFile.setLastOpened(LocalDateTime.now());
                recentFileService.save(logFile, recentFile);
                refreshRecentFilesList();
                logger.info("Added file to recent files: {}", file.getName());
            }

            // Setup file watcher for auto-refresh
            setupFileWatcher(file);

            int totalEntries = entries.size();
            updateStatus(String.format("📍 Showing all %,d entries from %s (virtual scrolling ⚡)",
                totalEntries, file.getName()));
            progressBar.setVisible(false);
            
            // Clear task reference
            currentLoadingTask = null;
        });

        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            Throwable ex = task.getException();
            logger.error("Failed to parse file", ex);
            showError("Failed to load file", ex.getMessage());
            updateStatus("Failed to load file");
            currentLoadingTask = null;
        });
        
        task.setOnCancelled(e -> {
            progressBar.setVisible(false);
            logger.info("⚠️ Parsing cancelled by user");
            updateStatus("Parsing cancelled");
            currentLoadingTask = null;
        });

        // Store task reference for cancellation
        currentLoadingTask = task;
        
        new Thread(task).start();
    }

    /**
     * @deprecated Use loadFileWithParallelParsing instead
     * Load file with lazy file-based strategy (for large files)
     * Only builds line offset index, reads entries on-demand
     */
    @Deprecated
    private void loadFileWithLazyStrategy_OLD(File file, ParsingConfig parsingConfig, LogFile logFile, boolean updateRecentFilesList) {
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        long fileSizeInMB = file.length() / (1024 * 1024);
        updateStatus(String.format("Indexing file: %s (%.2f MB) - Estimating line count...",
            file.getName(), fileSizeInMB / 1.0));

        // NEW STREAMING: Load entries progressively while displaying them!
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Initializing file source...");
                updateProgress(0, 100);

                // Create source
                com.seeloggyplus.service.impl.FileBasedLogEntrySource source =
                    new com.seeloggyplus.service.impl.FileBasedLogEntrySource(file, parsingConfig);

                originalLogEntrySource = source;
                currentLogEntrySource = originalLogEntrySource;

                updateProgress(10, 100);
                updateMessage("Loading entries (streaming)...");
                
                // STREAMING: Load entries in batches and update UI progressively
                int totalEntries = source.getTotalEntries();
                int batchSize = 10000; // Load 10k entries per batch
                int offset = 0;
                
                while (offset < totalEntries && !isCancelled()) {
                    // Check cancellation before loading
                    if (isCancelled()) {
                        updateMessage("Cancelled");
                        logger.info("⚠️ Loading cancelled at {} / {} entries", offset, totalEntries);
                        break;
                    }
                    
                    int limit = Math.min(batchSize, totalEntries - offset);
                    List<LogEntry> batch = source.getEntries(offset, limit);
                    
                    if (batch.isEmpty()) break;
                    
                    // Check cancellation before updating UI
                    if (isCancelled()) break;
                    
                    // Update UI with new batch (on JavaFX thread)
                    final List<LogEntry> batchToAdd = new ArrayList<>(batch);
                    Platform.runLater(() -> {
                        visibleLogEntries.addAll(batchToAdd);
                        
                        // Auto-scroll to bottom
                        if (!visibleLogEntries.isEmpty()) {
                            logTableView.scrollTo(visibleLogEntries.size() - 1);
                        }
                    });
                    
                    offset += batch.size();
                    
                    // Update progress
                    double progress = (double) offset / totalEntries * 100;
                    updateProgress(progress, 100);
                    updateMessage(String.format("Loading entries: %,d / %,d (%d%%)", 
                        offset, totalEntries, (int)progress));
                    
                    // Small delay to allow UI updates (prevents overwhelming UI thread)
                    Thread.sleep(50);
                }
                
                if (isCancelled()) {
                    updateMessage("Cancelled");
                    return null;
                }
                
                updateProgress(100, 100);
                updateMessage("Loading complete");
                return null;
            }
        };

        // Update UI with task progress
        task.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null) {
                Platform.runLater(() -> updateStatus(newMsg));
            }
        });

        // Setup table columns at start (before data loads)
        updateTableColumns(currentParsingConfig);
        
        task.setOnSucceeded(e -> {
            logger.info("✅ Streaming load complete! Loaded {} entries", visibleLogEntries.size());

            Platform.runLater(() -> {
                autoResizeColumns(logTableView);
                
                // Final scroll to bottom
                if (!visibleLogEntries.isEmpty()) {
                    logTableView.scrollTo(visibleLogEntries.size() - 1);
                }
            });

            // Add to recent files
            if (updateRecentFilesList) {
                RecentFile recentFile = new RecentFile();
                recentFile.setFileId(logFile.getId());
                recentFile.setLastOpened(LocalDateTime.now());
                recentFileService.save(logFile, recentFile);
                refreshRecentFilesList();
                logger.info("Added file to recent files: {}", file.getName());
            }

            // Setup file watcher
            setupFileWatcher(file);

            int totalEntries = visibleLogEntries.size();
            updateStatus(String.format("📍 Showing all %,d entries from %s (streaming complete ✅)",
                totalEntries, file.getName()));
            progressBar.setVisible(false);
            
            // Clear task reference
            currentLoadingTask = null;
        });

        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            Throwable ex = task.getException();
            logger.error("Failed to load file", ex);
            showError("Failed to load file", ex.getMessage());
            updateStatus("Failed to load file");
            currentLoadingTask = null;
        });
        
        task.setOnCancelled(e -> {
            progressBar.setVisible(false);
            logger.info("⚠️ Loading cancelled by user");
            updateStatus("Loading cancelled");
            currentLoadingTask = null;
        });

        // Store task reference for cancellation
        currentLoadingTask = task;
        
        new Thread(task).start();
    }

    /**
     * @deprecated Use loadFileWithParallelParsing instead
     * Load file with in-memory strategy (for small files)
     * Parse all entries into memory for fast access
     */
    @Deprecated
    private void loadFileWithInMemoryStrategy_OLD(File file, ParsingConfig parsingConfig, LogFile logFile, boolean updateRecentFilesList) {
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        updateStatus("Loading file: " + file.getName());

        final ParsingConfig configToUse = parsingConfig;

        Task<List<LogEntry>> task = new Task<>() {
            @Override
            protected List<LogEntry> call() throws IOException {
                return logParserService.parseFileParallel(
                        file,
                        configToUse,
                        new LogParserService.ProgressCallback() {
                            @Override
                            public void onProgress(
                                    double progress,
                                    long bytesProcessed,
                                    long totalBytes
                            ) {
                                updateProgress(bytesProcessed, totalBytes);
                                Platform.runLater(() -> {
                                    progressBar.setProgress(progress);
                                    updateStatus(
                                            String.format(
                                                    "Parsing... %.1f%% (%s / %s)",
                                                    progress * 100,
                                                    formatBytes(bytesProcessed),
                                                    formatBytes(totalBytes)
                                            )
                                    );
                                });
                            }

                            @Override
                            public void onComplete(long totalEntries) {
                                Platform.runLater(() -> {
                                    progressBar.setVisible(false);
                                });
                            }
                        }
                );
            }
        };

        task.setOnSucceeded(e -> {
            List<LogEntry> entries = task.getValue();
            logger.info("Parsing completed, got {} entries", entries.size());

            originalLogEntrySource = new ListLogEntrySourceImpl(entries);
            currentLogEntrySource = originalLogEntrySource;

            updateTableColumns(currentParsingConfig);
            logger.info("Updated table columns for config: {}", currentParsingConfig.getName());

            Platform.runLater(() -> autoResizeColumns(logTableView));

            logTableView.refresh();

            // Add to recent files only if requested
            if (updateRecentFilesList) {
                RecentFile recentFile = new RecentFile();
                recentFile.setFileId(logFile.getId());
                recentFile.setLastOpened(LocalDateTime.now());
                recentFileService.save(logFile, recentFile);
                refreshRecentFilesList();
                logger.info("Added file to recent files: {}", file.getName());
            }

            displayLogEntries(currentLogEntrySource, file, updateRecentFilesList);
            updateStatus(String.format("Loaded %s (%d entries) - In-memory mode",
                file.getName(), entries.size()));
            logger.info("Loaded {} log entries from {}, table now shows {} items", entries.size(), file.getName(), visibleLogEntries.size());
            
            // Clear task reference
            currentLoadingTask = null;
        });

        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            Throwable ex = task.getException();
            logger.error("Failed to load log file", ex);
            showError("Failed to load log file", ex.getMessage());
            updateStatus("Failed to load file");
            currentLoadingTask = null;
        });
        
        task.setOnCancelled(e -> {
            progressBar.setVisible(false);
            logger.info("⚠️ Loading cancelled by user");
            updateStatus("Loading cancelled");
            currentLoadingTask = null;
        });

        // Store task reference for cancellation
        currentLoadingTask = task;
        
        new Thread(task).start();
    }

    /**
     * Get existing LogFile from database or create new one
     * This method handles both insert and update scenarios
     *
     * @param file the physical file
     * @param parsingConfig the parsing configuration to use
     * @return LogFile entity or null if failed
     */
    private LogFile getOrCreateLogFile(File file, ParsingConfig parsingConfig) {
        try {
            // Try to find existing LogFile record
            LogFile existingLogFile = logFileService.getLogFileByPathAndName(file.getName(), file.getAbsolutePath());

            if (existingLogFile != null) {
                // Update existing record with new metadata
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
                    // Return existing data even if update fails
                    return existingLogFile;
                }
            } else {
                // Create new LogFile record
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

    /**
     * Show parsing configuration selection dialog and return selected config
     * @return Selected ParsingConfig or null if cancelled
     */
    private ParsingConfig showParsingConfigSelectionDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ParsingConfigurationSelectionDialog.fxml"));
            DialogPane dialogPane = loader.load();

            ParsingConfigurationSelectionDialogController controller = loader.getController();

            // Create Dialog with the loaded DialogPane
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Select Parsing Configuration");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(menuBar.getScene().getWindow());
            dialog.setDialogPane(dialogPane);

            // Show dialog and wait for result
            Optional<ButtonType> result = dialog.showAndWait();

            // Check if OK was pressed and selection is valid
            if (result.isPresent() && result.get() == ButtonType.OK) {
                if (controller.isValidSelection()) {
                    ParsingConfig selected = controller.getSelectedConfig();
                    logger.info("Selected parsing configuration: {}", selected != null ? selected.getName() : "null");
                    return selected;
                } else {
                    logger.info("Invalid configuration selected");
                    return null;
                }
            }

            logger.info("Dialog cancelled or closed");
            return null;
        } catch (IOException e) {
            logger.error("Failed to open parsing configuration selection dialog", e);
            showError("Failed to open dialog", e.getMessage());
            return null;
        }
    }

    /**
     * Handle parsing configuration
     */
    private void handleParsingConfiguration() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ParsingConfigDialog.fxml"));
            Parent root = loader.load();

            Stage dialog = new Stage();
            dialog.initOwner(menuBar.getScene().getWindow());
            Scene scene = new Scene(root);
            dialog.setScene(scene);
            dialog.setWidth(1000);
            dialog.setHeight(800);

            dialog.showAndWait();

            // Just return to previous view - no need to reload/re-parse file
            logger.info("Parsing config dialog closed, returning to previous view");
        } catch (IOException e) {
            logger.error("Failed to open parsing configuration dialog", e);
            showError("Failed to open parsing configuration", e.getMessage());
        }
    }

    /**
     * Handle recent file selected
     */
    private void handleRecentFileSelected(RecentFilesDto recentFile) {
        if (recentFile.logFile().isRemote()) {
            showInfo("Remote File", "Opening remote files is not yet implemented in this version.");
        } else {
            File file = new File(recentFile.logFile().getFilePath());
            if (!file.exists()) {
                showError("File Not Found", "The file no longer exists: " + recentFile.logFile().getFilePath());
                return;
            }

            logger.info("Recent file selected: {}, LogFile parsing_config_id: {}",
                file.getName(), recentFile.logFile().getParsingConfigurationID());

            // Get parsing config from the recent file DTO (already loaded from database)
            ParsingConfig parsingConfig = recentFile.parsingConfig();

            if (parsingConfig != null) {
                logger.info("ParsingConfig from DTO - ID: {}, Name: {}, Valid: {}",
                    parsingConfig.getId(), parsingConfig.getName(), parsingConfig.isValid());
            } else {
                logger.info("ParsingConfig from DTO is NULL");
            }

            if (parsingConfig == null) {
                // If no config associated, try to get from LogFile record
                String parsingConfigId = recentFile.logFile().getParsingConfigurationID();
                logger.info("Attempting to load config from database with ID: {}", parsingConfigId);

                if (parsingConfigId != null && !parsingConfigId.isEmpty()) {
                    parsingConfig = parsingConfigService.findById(parsingConfigId).orElse(null);

                    if (parsingConfig != null) {
                        logger.info("Loaded ParsingConfig from database - ID: {}, Name: {}, Valid: {}",
                            parsingConfig.getId(), parsingConfig.getName(), parsingConfig.isValid());
                    } else {
                        logger.warn("ParsingConfig with ID {} not found in database", parsingConfigId);
                    }
                }
            }

            if (parsingConfig == null) {
                // Fallback: show dialog to select parsing config
                logger.warn("No parsing config associated with file: {}, showing selection dialog", file.getName());
                parsingConfig = showParsingConfigSelectionDialog();

                if (parsingConfig == null) {
                    logger.info("No parsing configuration selected for recent file, operation cancelled");
                    return;
                }
            }

            logger.info("Opening recent file: {} with parsing config: {} (ID: {})",
                file.getName(), parsingConfig.getName(), parsingConfig.getId());

            // Reset filters when opening new file for clean slate
            resetFilters();
            
            // Open file with the associated parsing config
            // Note: openLocalLogFile() will update date filter prompt automatically
            // Don't update recent files list (already in recent files)
            openLocalLogFile(file, false, parsingConfig);

            // PERFORMANCE: No need to search after opening file (all data already loaded)
            // performSearch();
            autoResizeColumns(logTableView);
        }
    }

    /**
     * Reset all filters to default state.
     * Called when opening a new file to provide clean slate.
     */
    private void resetFilters() {
        logger.info("🔄 Resetting all filters to default state");
        
        // Reset search field
        searchField.clear();
        
        // Reset log level filter to "ALL"
        logLevelFilterComboBox.getSelectionModel().select("ALL");
        
        // Reset checkboxes
        regexCheckBox.setSelected(false);
        caseSensitiveCheckBox.setSelected(false);
        hideUnparsedCheckBox.setSelected(false);
        
        // Reset date/time filters
        dateTimeFromField.clear();
        dateTimeToField.clear();
        
        logger.info("✅ Filters reset: Level=ALL, Search='', Regex=false, CaseSensitive=false, HideUnparsed=false, DateTime=empty");
    }
    
    /**
     * Clear date/time filter
     */
    private void clearDateFilter() {
        logger.info("🗑️ Clearing date/time filter");
        dateTimeFromField.clear();
        dateTimeToField.clear();
        // PERFORMANCE: Don't auto-search, let user click Search button
        // performSearch();
    }
    
    /**
     * Parse date/time filter input
     * Supports multiple formats for user convenience
     */
    private LocalDateTime parseDateTimeFilter(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = input.trim();
        
        // Try multiple common formats (with and without milliseconds)
        List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        
        // If only date is provided (no time), try parsing as date and set time to start/end of day
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return date.atStartOfDay();
        } catch (DateTimeParseException e) {
            // Ignore
        }
        
        logger.warn("Could not parse date/time: {}", trimmed);
        return null;
    }
    
    /**
     * Parse timestamp from log entry
     * PERFORMANCE: Uses timestamp format from parsing config for faster, accurate parsing
     */
    private LocalDateTime parseEntryTimestamp(LogEntry entry) {
        if (entry.isParsed()) {
            // Priority 1: Get already-parsed LocalDateTime field (fastest)
            LocalDateTime timestamp = entry.getTimestamp();
            if (timestamp != null) {
                return timestamp;
            }
            
            // Priority 2: Use timestamp format from config for precise parsing (PERFORMANCE BOOST)
            if (currentParsingConfig != null && currentParsingConfig.getTimestampFormat() != null) {
                String timestampStr = entry.getField("timestamp");
                if (timestampStr != null && !timestampStr.isEmpty()) {
                    try {
                        DateTimeFormatter configFormatter = DateTimeFormatter.ofPattern(currentParsingConfig.getTimestampFormat());
                        return LocalDateTime.parse(timestampStr, configFormatter);
                    } catch (Exception e) {
                        logger.debug("Failed to parse timestamp with config format: {}", e.getMessage());
                        // Fall through to generic parsing
                    }
                }
            }
            
            // Priority 3: Fallback to generic parsing (slower but flexible)
            String timestampStr = entry.getField("timestamp");
            if (timestampStr != null && !timestampStr.isEmpty()) {
                return parseDateTimeFilter(timestampStr);
            }
        }
        
        // Priority 4: Last resort - extract from raw log
        String rawLog = entry.getRawLog();
        if (rawLog != null && rawLog.length() > 19) {
            String possibleTimestamp = rawLog.substring(0, Math.min(23, rawLog.length())); // 23 for milliseconds
            return parseDateTimeFilter(possibleTimestamp);
        }
        
        return null;
    }
    
    /**
     * Perform search on log entries
     */
    private void performSearch() {
        if (currentLogEntrySource == null) {
            return;
        }

        String searchText = searchField.getText();
        boolean isRegex = regexCheckBox.isSelected();
        boolean caseSensitive = caseSensitiveCheckBox.isSelected();
        boolean hideUnparsed = hideUnparsedCheckBox.isSelected();
        String selectedLevel = logLevelFilterComboBox.getSelectionModel().getSelectedItem();
        String dateTimeFrom = dateTimeFromField.getText();
        String dateTimeTo = dateTimeToField.getText();

        logger.info("Performing search - Level: {}, Text: '{}', Regex: {}, CaseSensitive: {}, HideUnparsed: {}, DateFrom: '{}', DateTo: '{}'",
            selectedLevel, searchText, isRegex, caseSensitive, hideUnparsed, dateTimeFrom, dateTimeTo);
        
        updateStatus("Searching...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                // Parse date/time filters once (outside predicate for performance)
                LocalDateTime filterFrom = parseDateTimeFilter(dateTimeFrom);
                LocalDateTime filterTo = parseDateTimeFilter(dateTimeTo);
                
                // Create a predicate based on search criteria
                Predicate<LogEntry> searchPredicate = entry -> {
                    // hide unparsed log (checkbox)
                    if (hideUnparsed){
                        if (!entry.isParsed()){
                            return false;
                        }
                    }

                    // Date/Time filtering (performance: check this first as it's faster than regex)
                    if (filterFrom != null || filterTo != null) {
                        LocalDateTime entryTime = parseEntryTimestamp(entry);
                        if (entryTime != null) {
                            if (filterFrom != null && entryTime.isBefore(filterFrom)) {
                                return false; // Entry before start time
                            }
                            if (filterTo != null && entryTime.isAfter(filterTo)) {
                                return false; // Entry after end time
                            }
                        } else {
                            // No timestamp in entry, filter out if date filter is active
                            return false;
                        }
                    }

                    // Level filtering
                    if (selectedLevel != null && !selectedLevel.equals("ALL")) {
                        // Special case: UNPARSED filter
                        if (selectedLevel.equals("UNPARSED")) {
                            // Only show unparsed entries
                            return !entry.isParsed();
                        }
                        
                        // Normal level filtering (parsed entries only)
                        if (!entry.isParsed()) {
                            return false; // Unparsed entry, filter out for normal level filters
                        }
                        
                        String entryLevel = entry.getLevel();
                        if (entryLevel == null || entryLevel.isEmpty()) {
                            return false; // No level, filter out
                        }
                        if (!entryLevel.equalsIgnoreCase(selectedLevel)) {
                            return false;
                        }
                    }

                    // Text/Regex filtering
                    if (searchText == null || searchText.trim().isEmpty()) {
                        return true; // No text search, only level filter applies
                    }

                    if (isRegex) {
                        try {
                            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                            Pattern pattern = Pattern.compile(searchText, flags);
                            return pattern.matcher(entry.getRawLog()).find();
                        } catch (Exception e) {
                            logger.error("Invalid regex pattern: {}", e.getMessage());
                            return false; // Invalid regex, so no match
                        }
                    } else {
                        String searchFor = caseSensitive ? searchText : searchText.toLowerCase();
                        String searchIn = caseSensitive ? entry.getRawLog() : entry.getRawLog().toLowerCase();
                        return searchIn.contains(searchFor);
                    }
                };

                // Apply the filter to get all matching entries
                LogEntrySource filteredSource = originalLogEntrySource.filter(searchPredicate);
                
                // Get ALL filtered entries (for virtual scrolling)
                int totalFiltered = filteredSource.getTotalEntries();
                List<LogEntry> filteredEntries = filteredSource.getEntries(0, totalFiltered);

                Platform.runLater(() -> {
                    // Update with filtered results
                    currentLogEntrySource = filteredSource;
                    visibleLogEntries.clear();
                    visibleLogEntries.addAll(filteredEntries);
                    
                    // Update status
                    if (filteredEntries.isEmpty()) {
                        updateStatus("No matching entries found");
                    } else {
                        updateStatus(String.format("📍 Showing %,d matching entries (filtered from %,d total)",
                            filteredEntries.size(), originalLogEntrySource.getTotalEntries()));
                    }
                    
                    // Auto-fit table columns after search for better visibility
                    autoResizeColumns(logTableView);
                    logger.debug("✅ Auto-fit table columns after search/filter");
                    
                    // Scroll to top of filtered results
                    if (!visibleLogEntries.isEmpty()) {
                        logTableView.scrollTo(0);
                    }
                });
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            // Status updated in Platform.runLater inside the task
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            logger.error("Search failed", ex);
            showError("Search Failed", ex.getMessage());
            updateStatus("Search failed");
        });

        new Thread(task).start();
    }

    /**
     * Clear search filter
     */
    private void clearSearch() {
        searchField.clear();
        logLevelFilterComboBox.getSelectionModel().select("ALL");
        hideUnparsedCheckBox.setSelected(false);
        
        if (originalLogEntrySource != null) {
            currentLogEntrySource = originalLogEntrySource;
            visibleLogEntries.clear();
            
            // Reload all entries
            int totalEntries = originalLogEntrySource.getTotalEntries();
            List<LogEntry> allEntries = originalLogEntrySource.getEntries(0, totalEntries);
            visibleLogEntries.addAll(allEntries);
            
            updateStatus(String.format("📍 Showing all %,d entries from %s",
                visibleLogEntries.size(), currentFile != null ? currentFile.getName() : ""));
            
            // Scroll to bottom after clearing search
            if (!visibleLogEntries.isEmpty()) {
                Platform.runLater(() -> logTableView.scrollTo(visibleLogEntries.size() - 1));
            }
        } else {
            visibleLogEntries.clear();
            updateStatus("Search cleared. No file loaded.");
        }
    }

    /**
     * Check if any filter is currently active
     */
    private boolean isFilterActive() {
        // Check if current source is different from original (means filtering is active)
        if (currentLogEntrySource != originalLogEntrySource) {
            return true;
        }
        
        // Check individual filter controls
        String searchText = searchField.getText();
        boolean hasSearchText = searchText != null && !searchText.trim().isEmpty();
        
        String selectedLevel = logLevelFilterComboBox.getSelectionModel().getSelectedItem();
        boolean hasLevelFilter = selectedLevel != null && !selectedLevel.equals("ALL");
        
        boolean hideUnparsed = hideUnparsedCheckBox.isSelected();
        
        String dateTimeFrom = dateTimeFromField.getText();
        boolean hasFromDate = dateTimeFrom != null && !dateTimeFrom.trim().isEmpty();
        
        String dateTimeTo = dateTimeToField.getText();
        boolean hasToDate = dateTimeTo != null && !dateTimeTo.trim().isEmpty();
        
        return hasSearchText || hasLevelFilter || hideUnparsed || hasFromDate || hasToDate;
    }
    
    /**
     * Jump to original position of the selected entry (clears filters and scrolls to entry)
     * This provides intuitive navigation when user double-clicks a filtered row
     */
    private void jumpToOriginalPosition(LogEntry selectedEntry) {
        if (selectedEntry == null || originalLogEntrySource == null) {
            return;
        }
        
        long targetLineNumber = selectedEntry.getLineNumber();
        
        logger.info("🎯 Double-click detected: Jumping to original position (line {}) from filtered view", targetLineNumber);
        updateStatus(String.format("🔍 Jumping to line %,d...", targetLineNumber));
        
        Task<Void> jumpTask = new Task<>() {
            @Override
            protected Void call() {
                Platform.runLater(() -> {
                    // Clear all filters
                    searchField.clear();
                    logLevelFilterComboBox.getSelectionModel().select("ALL");
                    hideUnparsedCheckBox.setSelected(false);
                    dateTimeFromField.clear();
                    dateTimeToField.clear();
                    
                    // Restore original source
                    currentLogEntrySource = originalLogEntrySource;
                    
                    // Reload all entries
                    int totalEntries = originalLogEntrySource.getTotalEntries();
                    List<LogEntry> allEntries = originalLogEntrySource.getEntries(0, totalEntries);
                    visibleLogEntries.clear();
                    visibleLogEntries.addAll(allEntries);
                    
                    // Find the entry with matching line number
                    int targetIndex = -1;
                    for (int i = 0; i < allEntries.size(); i++) {
                        if (allEntries.get(i).getLineNumber() == targetLineNumber) {
                            targetIndex = i;
                            break;
                        }
                    }
                    
                    if (targetIndex >= 0) {
                        final int indexToSelect = targetIndex;
                        
                        // Scroll to the entry and select it
                        Platform.runLater(() -> {
                            logTableView.scrollTo(Math.max(0, indexToSelect - 5)); // Scroll with context
                            logTableView.getSelectionModel().select(indexToSelect);
                            
                            updateStatus(String.format("✅ Jumped to line %,d (row %,d of %,d)",
                                targetLineNumber, indexToSelect + 1, totalEntries));
                            
                            logger.info("✅ Successfully jumped to line {} at index {}", targetLineNumber, indexToSelect);
                        });
                    } else {
                        updateStatus(String.format("⚠️ Line %,d not found in original data", targetLineNumber));
                        logger.warn("Line {} not found in original source", targetLineNumber);
                    }
                });
                return null;
            }
        };
        
        jumpTask.setOnFailed(e -> {
            Throwable ex = jumpTask.getException();
            logger.error("Failed to jump to original position", ex);
            showError("Jump Failed", "Could not jump to original position: " + ex.getMessage());
            updateStatus("Jump to original position failed");
        });
        
        new Thread(jumpTask).start();
    }
    
    /**
     * Update date/time filter prompt text based on parsing config timestamp format
     */
    private void updateDateTimeFilterPromptText(ParsingConfig config) {
        String promptText;
        
        if (config != null && config.getTimestampFormat() != null) {
            // Use format from config
            promptText = config.getTimestampFormat();
            logger.info("🎯 Setting date filter prompt to config format: '{}' (from config: {})", 
                promptText, config.getName());
        } else {
            // Default generic prompt
            promptText = "yyyy-MM-dd HH:mm:ss";
             logger.info("🎯 Setting date filter prompt to default format: '{}' (config: {})",
                promptText, config != null ? "null format" : "null config");
        }
        
        // IMPORTANT: Set prompt text on JavaFX Application Thread
        Platform.runLater(() -> {
            dateTimeFromField.setPromptText(promptText);
            dateTimeToField.setPromptText(promptText);
            logger.debug("✅ Prompt text set to fields: '{}'", promptText);
        });
        
        // Add tooltips with more info
        String tooltipText = "Enter date/time in format: " + promptText + 
                           "\n\nSupported formats:" +
                           "\n• " + promptText +
                           "\n• yyyy-MM-dd (date only)" +
                           "\n• dd-MM-yyyy HH:mm:ss" +
                           "\nOr any common date format";
        
        final String finalPromptText = promptText;
        Platform.runLater(() -> {
            Tooltip fromTooltip = new Tooltip(tooltipText);
            Tooltip toTooltip = new Tooltip(tooltipText);
            
            dateTimeFromField.setTooltip(fromTooltip);
            dateTimeToField.setTooltip(toTooltip);
            logger.debug("✅ Tooltips set for date filter fields");
        });
    }
    
    /**
     * Display log entry detail
     */
    private void displayLogDetail(LogEntry entry) {
        if (entry == null) {
            detailTextArea.clear();
            detailLabel.setText("Log Detail");
            return;
        }

        detailLabel.setText("Log Detail - Line " + entry.getLineNumber());
        detailTextArea.clear();

        detailTextArea.replaceText(entry.getRawLog());
    }

    /**
     * Prettify JSON in detail view
     */
    private void prettifyJson() {
        String fullText = detailTextArea.getText();
        StringBuilder newTextBuilder = new StringBuilder(fullText);
        int offset = 0;

        while (true) {
            String remainingText = newTextBuilder.substring(offset);
            String extractedJson = JsonPrettify.extractJson(remainingText);

            if (extractedJson == null) {
                break; // No more JSON found
            }

            String prettifiedJson = JsonPrettify.prettify(extractedJson);

            int start = newTextBuilder.indexOf(extractedJson, offset);
            if (start == -1) {
                break; // Should not happen if extractJson worked correctly
            }
            int end = start + extractedJson.length();

            newTextBuilder.replace(start, end, prettifiedJson);
            offset = start + prettifiedJson.length(); // Continue search after the replaced text
        }

        if (!newTextBuilder.toString().equals(fullText)) {
            detailTextArea.replaceText(newTextBuilder.toString());
            updateStatus("All JSON occurrences prettified");
        } else {
            showInfo("No JSON Found", "No valid JSON found in the log detail.");
        }
    }

    /**
     * Prettify XML in detail view
     */
    private void prettifyXml() {
        String fullText = detailTextArea.getText();
        StringBuilder newTextBuilder = new StringBuilder(fullText);
        int offset = 0;

        while (true) {
            String remainingText = newTextBuilder.substring(offset);
            String extractedXml = XmlPrettify.extractXml(remainingText);

            if (extractedXml == null) {
                break; // No more XML found
            }

            String prettifiedXml = XmlPrettify.prettify(extractedXml);

            int start = newTextBuilder.indexOf(extractedXml, offset);
            if (start == -1) {
                break; // Should not happen if extractXml worked correctly
            }
            int end = start + extractedXml.length();

            newTextBuilder.replace(start, end, prettifiedXml);
            offset = start + prettifiedXml.length(); // Continue search after the replaced text
        }

        if (!newTextBuilder.toString().equals(fullText)) {
            detailTextArea.replaceText(newTextBuilder.toString());
            updateStatus("All XML occurrences prettified");
        } else {
            showInfo("No XML Found", "No valid XML found in the log detail.");
        }
    }

    /**
     * Copy detail to clipboard
     */
    private void copyDetailToClipboard() {
        String text = detailTextArea.getText();
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        updateStatus("Copied to clipboard");
    }

    /**
     * Clear detail view
     */
    private void clearDetail() {
        detailTextArea.clear();
        detailLabel.setText("Log Detail");
    }


    /**
     * Toggle left panel visibility
     */
    private void toggleLeftPanel() {
        isLeftPanelPinned = !isLeftPanelPinned;
        updateLeftPanelDisplay();
        preferenceService.saveOrUpdatePreferences(new Preference("left_panel_pinned", String.valueOf(isLeftPanelPinned)));
    }

    /**
     * Toggle bottom panel visibility (from menu)
     */
    private void toggleBottomPanel() {
        isBottomPanelPinned = !isBottomPanelPinned;
        updateBottomPanelDisplay();
        preferenceService.saveOrUpdatePreferences(new Preference("bottom_panel_pinned", String.valueOf(isBottomPanelPinned)));
    }

    /**
     * Restore panel visibility from preferences
     */
    private void restorePanelVisibility() {
        // Restore left panel pinned state
        isLeftPanelPinned = preferenceService.getPreferencesByCode("left_panel_pinned")
                .filter(Predicate.not(String::isBlank))
                .map(Boolean::parseBoolean)
                .orElse(true); // Default to pinned
        updateLeftPanelDisplay(); // Apply the restored state

        // Restore bottom panel pinned state
        isBottomPanelPinned = preferenceService.getPreferencesByCode("bottom_panel_pinned")
                .filter(Predicate.not(String::isBlank))
                .map(Boolean::parseBoolean)
                .orElse(true); // Default to pinned
        updateBottomPanelDisplay(); // Apply the restored state
    }

    /**
     * Method helper for display (from cache or new parse) to UI.
     */
    /**
     * NEW STRATEGY: Always show ALL entries using Virtual Scrolling (like glogg/kubelog)
     * - Load all line numbers and metadata
     * - TableView only renders visible rows (virtual scrolling built-in to JavaFX)
     * - Lazy load actual content when row is visible
     * - Fast and efficient for millions of lines!
     */
    private void scrollToBottomAfterLoad() {
        if (currentLogEntrySource == null) {
            return;
        }

        visibleLogEntries.clear();

        // NEW: ALWAYS load ALL entries (use virtual scrolling for performance)
        // JavaFX TableView has built-in virtual scrolling - only renders visible rows!
        int totalEntries = currentLogEntrySource.getTotalEntries();
        
        logger.info("Loading ALL {} entries for virtual scrolling (table only renders visible rows)...", totalEntries);
        long startTime = System.currentTimeMillis();
        
        // Load all entries (fast with backward reading for file-based)
        List<LogEntry> allEntries = currentLogEntrySource.getEntries(0, totalEntries);
        visibleLogEntries.addAll(allEntries);
        
        long loadTime = System.currentTimeMillis() - startTime;
        logger.info("Loaded ALL {} entries in {}ms - Virtual scrolling enabled ⚡", 
            allEntries.size(), loadTime);

        // Scroll to bottom after entries are loaded
        Platform.runLater(() -> {
            if (!visibleLogEntries.isEmpty()) {
                logTableView.scrollTo(visibleLogEntries.size() - 1);
                logger.debug("Auto-scrolled to bottom (entry #{})", visibleLogEntries.size());
            }
        });
    }

    /**
     * @deprecated This method is no longer used with new unified loading strategy
     */
    @Deprecated
    private void displayLogEntries(LogEntrySource source, File file, boolean updateRecentFilesList) {
        this.currentLogEntrySource = source;

        // Load entries based on source type:
        // - Lazy loading (file-based): tail mode (last 5000 entries)
        // - In-memory: load all entries
        scrollToBottomAfterLoad();

        updateTableColumns(this.currentParsingConfig);

        // Note: Auto-resize removed - now only done after full load in task.onSucceeded()
        logTableView.refresh();

        // Update status message
        Platform.runLater(() -> {
            progressBar.setVisible(false);

            int totalEntries = currentLogEntrySource.getTotalEntries();
            int displayedEntries = visibleLogEntries.size();

            // Determine loading mode
            boolean isLazyLoading = currentLogEntrySource instanceof com.seeloggyplus.service.impl.FileBasedLogEntrySource;

            if (isLazyLoading && displayedEntries < totalEntries) {
                // Lazy loading mode - showing tail
                updateStatus(
                    String.format(
                        "📍 At bottom. Showing last %,d of %,d entries from %s (lazy mode - scroll up to load more)",
                        displayedEntries,
                        totalEntries,
                        file.getName()
                    )
                );
            } else {
                // In-memory mode or all entries loaded
                updateStatus(
                    String.format(
                        "📍 Showing all %,d entries from %s (in-memory mode)",
                        displayedEntries,
                        file.getName()
                    )
                );
            }
        });

        // Recent file logic: save to database if requested
        if (updateRecentFilesList) {
            LogFile logFile = logFileService.getLogFileByPathAndName(file.getName(), file.getAbsolutePath());
            if (logFile != null) {
                RecentFile recentFile = new RecentFile();
                recentFileService.save(logFile, recentFile);
                refreshRecentFilesList();
            }
        }

        // Setup file watcher for auto-refresh (tail -f behavior)
        setupFileWatcher(file);

        logger.info("Loaded {} log entries from {}, table shows {} items (Total: {}) - TAIL MODE (bottom first)",
            visibleLogEntries.size(), file.getName(), visibleLogEntries.size(),
            currentLogEntrySource != null ? currentLogEntrySource.getTotalEntries() : 0);
    }

    /**
     * Setup file watcher for auto-refresh when file changes (like 'tail -f')
     */
    private void setupFileWatcher(File file) {
        if (logFileWatcher == null || !logFileWatcher.isRunning()) {
            logger.warn("LogFileWatcher is not running, auto-refresh disabled");
            return;
        }

        if (!autoRefreshMenuItem.isSelected()) {
            logger.info("Auto-refresh is disabled by user");
            return;
        }

        try {
            // Unwatch previous file if any
            if (currentFile != null && !currentFile.equals(file)) {
                logFileWatcher.unwatchFile(currentFile);
            }

            // Watch the new file
            logFileWatcher.watchFile(file, (modifiedFile, eventKind) -> {
                logger.info("📁 File change detected: {} - Event: {}",
                    modifiedFile.getName(), eventKind.name());

                // Auto-refresh on file modification
                Platform.runLater(() -> {
                    handleAutoRefresh(modifiedFile);
                });
            });

            logger.info("✅ Auto-refresh enabled for: {} (like 'tail -f')", file.getName());

        } catch (Exception e) {
            logger.error("Failed to setup file watcher", e);
            showError("Auto-Refresh Error",
                "Failed to enable auto-refresh for file: " + e.getMessage());
        }
    }

    /**
     * Handle manual refresh button click
     */
    private void handleRefreshCurrentFile() {
        if (currentFile == null) {
            logger.warn("No file is currently loaded");
            updateStatus("⚠️ No file to refresh");
            return;
        }
        
        if (currentParsingConfig == null) {
            logger.warn("No parsing config available");
            updateStatus("⚠️ No parsing config available");
            return;
        }
        
        logger.info("🔄 Manual refresh triggered for: {}", currentFile.getName());
        updateStatus("🔄 Refreshing file...");
        
        // Reload current file
        openLocalLogFile(currentFile, false, currentParsingConfig);
    }
    
    /**
     * Handle auto-refresh when file changes
     */
    private void handleAutoRefresh(File file) {
        if (!autoRefreshMenuItem.isSelected()) {
            return; // Auto-refresh disabled
        }

        logger.info("🔄 Auto-refreshing file: {}", file.getName());

        // Update status
        updateStatus("🔄 Auto-refreshing... (file changed)");

        // Reload file with current parsing config
        if (currentParsingConfig != null) {
            openLocalLogFile(file, false, currentParsingConfig);
        } else {
            logger.warn("No parsing config available for auto-refresh");
        }
    }

    /**
     * Toggle auto-refresh on/off
     */
    private void toggleAutoRefresh() {
        boolean enabled = autoRefreshMenuItem.isSelected();

        if (enabled) {
            logger.info("✅ Auto-refresh ENABLED");
            updateStatus("✅ Auto-refresh enabled (tail -f mode)");

            // Re-setup watcher for current file if any
            if (currentFile != null) {
                setupFileWatcher(currentFile);
            }
        } else {
            logger.info("❌ Auto-refresh DISABLED");
            updateStatus("❌ Auto-refresh disabled");

            // Unwatch current file
            if (currentFile != null && logFileWatcher != null) {
                logFileWatcher.unwatchFile(currentFile);
            }
        }
    }

    /**
     * Handle clear recent files
     */
    private void handleClearRecentFiles() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear Recent Files");
        alert.setHeaderText("Clear all recent files?");
        alert.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            recentFileService.deleteAll();
            refreshRecentFilesList();
        }
    }

    /**
     * Refresh recent files list view
     */
    private void refreshRecentFilesList() {
        recentFilesListView.setItems(
                FXCollections.observableArrayList(recentFileService.findAll())
        );
    }

    /**
     * Handle about dialog
     */
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About SeeLoggyPlus");
        alert.setHeaderText("SeeLoggyPlus v1.0.0");
        alert.setContentText(
                "High-performance log viewer with advanced parsing capabilities.\n\n" +
                        "Features:\n" +
                        "- Custom regex parsing with named groups\n" +
                        "- Local and remote (SSH) file access\n" +
                        "- JSON/XML prettification\n" +
                        "- Text and regex search\n" +
                        "- Performance optimized for large files\n\n" +
                        "© 2024 SeeLoggyPlus"
        );
        alert.showAndWait();
    }

    /**
     * Handle exit
     */
    private void handleExit() {
        // Cleanup file watcher
        if (logFileWatcher != null) {
            logFileWatcher.stop();
            logger.info("LogFileWatcher stopped on application exit");
        }
        Platform.exit();
    }

    /**
     * Formats a byte count into a human-readable string (e.g., 1.2 MB).
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Update status label
     */
    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
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
            alert.showAndWait();
        });
    }

    /**
     * Show info dialog
     */
    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Automatically resizes columns in a TableView to fit the content.
     * Uses smart sampling strategy for accurate width calculation.
     */
    private void autoResizeColumns(TableView<LogEntry> tableView) {
        if (tableView.getItems() == null || tableView.getItems().isEmpty()) {
            return;
        }

        logger.info("Auto-fitting columns for {} entries...", tableView.getItems().size());
        long startTime = System.currentTimeMillis();

        int totalSize = tableView.getItems().size();
        List<LogEntry> sample;
        
        if (totalSize <= 1000) {
            // Small dataset: use all entries
            sample = tableView.getItems();
            logger.debug("Using all {} entries for auto-fit", totalSize);
        } else {
            // Large dataset: smart sampling strategy
            // Sample from beginning, middle, and end for better accuracy
            int sampleSize = Math.min(3000, totalSize / 10); // 10% or max 3000
            sample = new ArrayList<>(sampleSize);
            
            // Sample from start (first 1000)
            int startSample = Math.min(1000, totalSize);
            sample.addAll(tableView.getItems().subList(0, startSample));
            
            // Sample from middle (1000)
            if (totalSize > 2000) {
                int midStart = (totalSize - 1000) / 2;
                int midEnd = Math.min(midStart + 1000, totalSize);
                sample.addAll(tableView.getItems().subList(midStart, midEnd));
            }
            
            // Sample from end (last 1000)
            if (totalSize > 1000) {
                int endStart = Math.max(startSample, totalSize - 1000);
                sample.addAll(tableView.getItems().subList(endStart, totalSize));
            }
            
            logger.debug("Using smart sample of {} entries from {} total for auto-fit", 
                sample.size(), totalSize);
        }

        // Create a text node with font matching the table for accurate measurement
        Text measureText = new Text();
        measureText.setStyle("-fx-font-family: 'System'; -fx-font-size: 12px;");

        for (TableColumn<LogEntry, ?> col : tableView.getColumns()) {
            // Don't resize the "Line" column, its width is fixed
            if ("Line".equals(col.getText())) {
                continue;
            }

            double maxWidth = 0;

            // Calculate width of header text
            measureText.setText(col.getText());
            maxWidth = Math.max(maxWidth, measureText.getLayoutBounds().getWidth());

            // Calculate width of cell content from sample
            for (LogEntry entry : sample) {
                try {
                    if (col.getCellObservableValue(entry) != null && 
                        col.getCellObservableValue(entry).getValue() != null) {
                        
                        String cellValue = col.getCellObservableValue(entry).getValue().toString();
                        
                        // For very long content, limit measurement to avoid performance issues
                        if (cellValue.length() > 500) {
                            cellValue = cellValue.substring(0, 500);
                        }
                        
                        measureText.setText(cellValue);
                        double width = measureText.getLayoutBounds().getWidth();
                        maxWidth = Math.max(maxWidth, width);
                    }
                } catch (Exception e) {
                    // Skip problematic entries
                    logger.debug("Error measuring cell width: {}", e.getMessage());
                }
            }

            // Set the new width with padding
            double padding = 50.0; // Padding for scrollbar, cell padding, etc.
            double newWidth = maxWidth + padding;
            
            // Apply constraints
            double minWidth = col.getMinWidth() > 0 ? col.getMinWidth() : 80.0;
            double maxAllowedWidth = 1200.0; // Max width to prevent extremely wide columns
            
            newWidth = Math.max(minWidth, newWidth);
            newWidth = Math.min(maxAllowedWidth, newWidth);

            col.setPrefWidth(newWidth);
            
            logger.debug("Column '{}': width = {}", col.getText(), (int)newWidth);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("✅ Auto-fit completed in {}ms", duration);
    }

    /**
     * Custom list cell for recent files
     */
    private static class RecentFileListCell extends ListCell<RecentFilesDto> {

        @Override
        protected void updateItem(RecentFilesDto item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox vbox = new VBox(2);
                LogFile logFile = item.logFile();
                Label nameLabel = new Label(logFile.getName());
                nameLabel.setStyle("-fx-font-weight: bold;");
                Label pathLabel = new Label(logFile.getFilePath());
                Label sizeLabel = new Label(logFile.getSize());
                vbox.getChildren().addAll(nameLabel, pathLabel, sizeLabel);
                setGraphic(vbox);
            }
        }
    }
}
