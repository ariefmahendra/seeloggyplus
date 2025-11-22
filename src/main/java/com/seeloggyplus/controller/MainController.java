package com.seeloggyplus.controller;

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

    // FXML Components - MenuBar
    @FXML
    private MenuBar menuBar;
    @FXML
    private MenuItem exitMenuItem;
    @FXML
    private Menu viewMenu;
    @FXML
    private CheckMenuItem showLeftPanelMenuItem;
    @FXML
    private CheckMenuItem showBottomPanelMenuItem;
    @FXML
    private MenuItem parsingConfigMenuItem;
    @FXML
    private MenuItem serverManagementMenuItem;
    @FXML
    private MenuItem aboutMenuItem;

    // FXML Components - Main Layout
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
    private Button prettifyJsonButton;
    @FXML
    private Button prettifyXmlButton;
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

    private LogEntrySource currentLogEntrySource;
    private LogEntrySource originalLogEntrySource;
    private ObservableList<LogEntry> visibleLogEntries;
    private ParsingConfig currentParsingConfig;
    private File currentFile;
    private boolean isLeftPanelPinned = true;
    private boolean isBottomPanelPinned = true;

    private Task<?> currentLoadingTask = null;

    private LogFileWatcher logFileWatcher;
    private CheckMenuItem autoRefreshMenuItem;

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
        // The new unified openMenuItem is handled by onAction="#handleOpen" in the FXML
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
        serverManagementMenuItem.setOnAction(e -> handleServerManagement());

        // Help Menu
        aboutMenuItem.setOnAction(e -> handleAbout());
    }

    /**
     * Setup left panel (Recent Files)
     */
    private void setupLeftPanel() {
        recentFilesListView.setCellFactory(listView -> new RecentFileListCell());
        recentFilesListView.setItems(FXCollections.observableArrayList(recentFileService.findAll()));
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
        clearRecentButton.setOnAction(e -> handleClearRecentFiles());
        pinLeftPanelButton.setOnAction(e -> handleToggleLeftPanelPin());
        expandLeftPanelButton.setOnAction(e -> handleToggleLeftPanelPin());
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
            Platform.runLater(() -> {
                double savedWidth = 200;
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
            Platform.runLater(() -> horizontalSplitPane.setDividerPositions(0.0));
        }
        showLeftPanelMenuItem.setSelected(isLeftPanelPinned);
    }

    /**
     * Setup center panel (Log Table)
     */
    private void setupCenterPanel() {
        logTableView.setItems(visibleLogEntries);
        logTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        logTableView.setTableMenuButtonVisible(false);
        logTableView.setPlaceholder(new javafx.scene.control.Label(""));
        logTableView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        displayLogDetail(newVal);
                    }
                });
        logTableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                LogEntry selectedEntry = logTableView.getSelectionModel().getSelectedItem();
                if (selectedEntry != null && isFilterActive()) {
                    jumpToOriginalPosition(selectedEntry);
                }
            }
        });

        searchButton.setOnAction(e -> performSearch());
        clearSearchButton.setOnAction(e -> clearSearch());
        autoFitButton.setOnAction(e -> {
            autoResizeColumns(logTableView);
            logger.info("Manual auto-fit columns triggered");
        });
        scrollToTopButton.setOnAction(e -> handleScrollToTop());
        scrollToBottomButton.setOnAction(e -> handleScrollToBottom());
        refreshButton.setOnAction(e -> handleRefreshCurrentFile());
        clearDateFilterButton.setOnAction(e -> clearDateFilter());
        updateDateTimeFilterPromptText(null);
        updateTableColumns(null);
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
            logTableView.scrollTo(0);
            updateStatus(String.format("At top. Showing all %d entries", totalAvailable));
            return;
        }

        updateStatus(String.format("Jumping to top... Loading first %d entries", Math.min(TOP_VIEW_SIZE, totalAvailable)));
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

        loadTopTask.setOnSucceeded(e -> Platform.runLater(() -> {
            List<LogEntry> topEntries = loadTopTask.getValue();
            visibleLogEntries.clear();
            visibleLogEntries.addAll(topEntries);
            if (!visibleLogEntries.isEmpty()) {
                logTableView.scrollTo(0);
            }

            progressBar.setVisible(false);

            int displayedCount = visibleLogEntries.size();
            updateStatus(String.format("At top. Showing first %d of %d entries from %s",
                displayedCount, totalAvailable,
                currentFile != null ? currentFile.getName() : ""));

            logger.info("Scrolled to top, showing {} entries", displayedCount);
        }));

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

        updateStatus("Jumping to bottom (tail mode)...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<Void> scrollTask = new Task<>() {
            @Override
            protected Void call() {
                Platform.runLater(() -> scrollToBottomAfterLoad());
                return null;
            }
        };

        scrollTask.setOnSucceeded(e -> Platform.runLater(() -> {
            progressBar.setVisible(false);

            int displayedCount = visibleLogEntries.size();
            updateStatus(String.format("📍 At bottom. Showing last %,d of %,d entries from %s",
                displayedCount, totalAvailable,
                currentFile != null ? currentFile.getName() : ""));

            logger.info("Scrolled to bottom, showing {} entries (TAIL MODE)", displayedCount);
        }));

        scrollTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            logger.error("Failed to scroll to bottom", scrollTask.getException());
            showError("Scroll Error", "Failed to jump to bottom: " + scrollTask.getException().getMessage());
            updateStatus("Scroll to bottom failed.");
        });

        new Thread(scrollTask).start();
    }

    /**
     * Setup bottom panel (Log Detail)
     */
    private void setupBottomPanel() {
        detailTextArea = new CodeArea();
        detailTextArea.setEditable(false);
        detailTextArea.setWrapText(true);
        detailTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

        if (bottomPanel.getChildren().size() < 3) {
            bottomPanel.getChildren().add(1, detailTextArea);
            VBox.setVgrow(detailTextArea, Priority.ALWAYS);
        }

        prettifyJsonButton.setOnAction(e -> prettifyJson());
        prettifyXmlButton.setOnAction(e -> prettifyXml());
        copyButton.setOnAction(e -> copyDetailToClipboard());
        clearDetailButton.setOnAction(e -> clearDetail());
        pinBottomPanelButton.setOnAction(e -> handleToggleBottomPanelPin());
        expandBottomPanelButton.setOnAction(e -> handleToggleBottomPanelPin());
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
            Platform.runLater(() -> {
                double savedHeight = 200;
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
            expandIcon.setRotate(90);
            bottomPanel.setVisible(false);
            bottomPanel.setManaged(false);
            collapsedBottomPanel.setVisible(true);
            collapsedBottomPanel.setManaged(true);
            Platform.runLater(() -> verticalSplitPane.setDividerPositions(1.0));
        }
        // Also update the CheckMenuItem in the View menu
        showBottomPanelMenuItem.setSelected(isBottomPanelPinned);
    }

    private void setupLogLevelFilter() {
        logLevelFilterComboBox.setItems(FXCollections.observableArrayList("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "UNPARSED"));
        logLevelFilterComboBox.getSelectionModel().select("ALL");
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

        TableColumn<LogEntry, String> lineCol = getLogEntryStringTableLineColumn();
        logTableView.getColumns().add(lineCol);

        if (config != null && config.isValid()) {
            List<String> groupNames = config.getGroupNames();
            logger.info( "Config has {} named groups: {}", groupNames.size(), groupNames);

            // Determine where to place unparsed entries
            int unparsedColumnIndex = determineUnparsedColumnIndex(groupNames);
            logger.info("Unparsed entries will be displayed in column index: {} ({})", 
                unparsedColumnIndex, 
                unparsedColumnIndex < groupNames.size() ? groupNames.get(unparsedColumnIndex) : "N/A");

            for (int i = 0; i < groupNames.size(); i++) {
                final int currentIndex = i;
                final String groupName = groupNames.get(i);

                TableColumn<LogEntry, String> column = new TableColumn<>(groupName);

                column.setCellValueFactory(cellData -> {
                    LogEntry entry = cellData.getValue();
                    if (entry.isParsed()) {
                        return new SimpleStringProperty(entry.getField(groupName));
                    } else {
                        if (currentIndex == unparsedColumnIndex) {
                            return new SimpleStringProperty(entry.getRawLog());
                        } else {
                            return new SimpleStringProperty("");
                        }
                    }
                });

                if (currentIndex == unparsedColumnIndex) {
                    column.setCellFactory(col -> new UnparsedContentCell());
                }

                if ("level".equalsIgnoreCase(groupName)) {
                    column.setCellFactory(col -> new TableCell<>() {
                        private Label badge = null;

                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);

                            if (item == null || empty) {
                                setText(null);
                                setGraphic(null);
                            } else {
                                LogEntry entry = getTableRow() != null ? getTableRow().getItem() : null;
                                if (entry != null && !entry.isParsed()) {
                                    if (badge == null) {
                                        badge = new Label();
                                    }
                                    badge.setText("UNPARSED");
                                    badge.setStyle(
                                            "-fx-background-color: #FF9800; " +
                                                    "-fx-text-fill: white; " +
                                                    "-fx-padding: 3px 8px; " +
                                                    "-fx-background-radius: 3px; " +
                                                    "-fx-font-weight: bold; " +
                                                    "-fx-font-size: 10px;"
                                    );
                                    setText(null);
                                    setGraphic(badge);
                                } else {
                                    if (badge == null) {
                                        badge = new Label();
                                    }
                                    badge.setText(item);
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

                        private String getLevelColor(String level) {
                            if (level == null) return "#9E9E9E";

                            return switch (level.toUpperCase()) {
                                case "ERROR" -> "#F44336"; // Red
                                case "FATAL" -> "#D32F2F"; // Dark Red
                                case "WARN", "WARNING" -> "#FF9800"; // Orange
                                case "INFO" -> "#2196F3"; // Blue
                                case "DEBUG" -> "#4CAF50"; // Green
                                case "TRACE" -> "#9E9E9E"; // Gray
                                default -> "#607D8B"; // Blue Gray
                            };
                        }
                    });
                }

                column.setMinWidth(80);
                column.setSortable(false);
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
     * Determine the column index where unparsed entries should be displayed.
     * Uses efficient single-pass algorithm for optimal performance.
     * 
     * Priority:
     * 1. If "message" related column exists (message, messages, msg, log_message, etc.), use that
     * 2. If first column is "level", use second column (index 1)
     * 3. Otherwise, use first column (index 0)
     */
    /**
     * Highly optimized column detection for unparsed entries.
     * Single-pass O(n) with minimal allocations and string operations.
     */
    private int determineUnparsedColumnIndex(List<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            return 0;
        }

        final int size = groupNames.size();
        int partialMatchIndex = -1;
         
        // Single-pass: exact match check with switch-like performance
        for (int i = 0; i < size; i++) {
            String name = groupNames.get(i);
            int len = name.length();
            
            // Quick length-based pre-filter to avoid toLowerCase() when possible
            if (len >= 3 && len <= 11) {
                char firstChar = name.charAt(0);
                
                // Exact match check with optimized string comparison
                if (firstChar == 'm' || firstChar == 'M') {
                    if (len == 7 && name.equalsIgnoreCase("message")) {
                        return i;
                    }
                    if (len == 3 && name.equalsIgnoreCase("msg")) {
                        return i;
                    }
                    if (len == 8 && name.equalsIgnoreCase("messages")) {
                        return i;
                    }
                }
                
                // Partial match check (only if no exact match found yet)
                if (partialMatchIndex == -1) {
                    String lower = name.toLowerCase();
                    if ((firstChar == 'm' || firstChar == 'M') && 
                        (lower.contains("message") || lower.contains("msg"))) {
                        partialMatchIndex = i;
                    } else if ((firstChar == 't' || firstChar == 'T') && lower.contains("text")) {
                        partialMatchIndex = i;
                    } else if ((firstChar == 'c' || firstChar == 'C') && lower.contains("content")) {
                        partialMatchIndex = i;
                    }
                }
            }
        }
        
        // Return partial match if found
        if (partialMatchIndex != -1) {
            return partialMatchIndex;
        }

        // Check if first column is "level" - avoid it for unparsed entries
        String firstName = groupNames.get(0);
        if (firstName.length() == 5 && firstName.equalsIgnoreCase("level")) {
            return size > 1 ? 1 : 0;
        }

        // Default: use first column
        return 0;
    }

    private static TableColumn<LogEntry, String> getLogEntryStringTableLineColumn() {
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
        lineCol.setSortable(false);
        return lineCol;
    }


    /**
     * Handle open file action from the unified file manager.
     * This method is called from the FXML by the "Open..." menu item.
     */
    @FXML
    public void handleOpen() {
        try {
            // Get main stage and save its state before showing a modal dialog
            Stage mainStage = (Stage) menuBar.getScene().getWindow();
            boolean wasMaximized = mainStage.isMaximized();
            double oldX = mainStage.getX();
            double oldY = mainStage.getY();
            double oldWidth = mainStage.getWidth();
            double oldHeight = mainStage.getHeight();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
            Parent root = loader.load();
            UnifiedFileManagerDialogController controller = loader.getController();

            Stage dialog = new Stage();
            dialog.setTitle("Open File");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(mainStage);
            dialog.setScene(new Scene(root));

            // Show the modal dialog and wait for it to close
            dialog.showAndWait();

            // After dialog closes, restore the main stage's state
            // This is a workaround for a common bug in some Linux window managers
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

            FileInfo selectedFile = controller.getSelectedFile();
            SSHService sshService = controller.getSshService(); // Get the active SSH service

            if (selectedFile == null) {
                logger.info("No file selected from UnifiedFileManagerDialog, operation cancelled.");
                if (sshService != null) {
                    sshService.disconnect();
                }
                return;
            }

            // The parsing config dialog is also modal, so we need to wrap it too.
            ParsingConfig selectedConfig = showParsingConfigSelectionDialog();
            if (selectedConfig == null) {
                logger.info("No parsing configuration selected, operation cancelled.");
                if (sshService != null) {
                    sshService.disconnect(); // Disconnect if we're not proceeding
                }
                return;
            }

            if (selectedFile.getSourceType() == FileInfo.SourceType.LOCAL) {
                openLocalLogFile(new File(selectedFile.getPath()), true, selectedConfig);
            } else {
                openRemoteLogFile(selectedFile, sshService, selectedConfig);
            }

        } catch (IOException e) {
            logger.error("Failed to open Unified File Manager", e);
            showError("Error Opening File Browser", "Could not open the file browser: " + e.getMessage());
        }
    }

    /**
     * Downloads a remote file to a temporary local path and then opens it.
     *
     * @param remoteFile     The FileInfo object for the remote file.
     * @param sshService     The active SSHService connection.
     * @param parsingConfig  The parsing configuration to use.
     */
    private void openRemoteLogFile(FileInfo remoteFile, SSHService sshService, ParsingConfig parsingConfig) {
        if (sshService == null || !sshService.isConnected()) {
            showError("Connection Error", "SSH connection is not active. Please re-select the file.");
            return;
        }

        updateStatus("Downloading remote file: " + remoteFile.getName());
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                // Create a temporary file to store the remote log
                String tempDir = System.getProperty("java.io.tmpdir");
                // Sanitize the file name to avoid path traversal issues
                String sanitizedName = new File(remoteFile.getName()).getName();
                File localTmpFile = new File(tempDir, "seeloggyplus-" + System.currentTimeMillis() + "-" + sanitizedName);
                
                logger.info("Downloading remote file {} to temporary path {}", remoteFile.getPath(), localTmpFile.getAbsolutePath());

                boolean success = sshService.downloadFile(remoteFile.getPath(), localTmpFile.getAbsolutePath());

                if (!success) {
                    throw new IOException("Failed to download file from server.");
                }

                logger.info("Remote file downloaded successfully.");
                return localTmpFile;
            }
        };

        downloadTask.setOnSucceeded(e -> {
            File localFile = downloadTask.getValue();
            updateStatus("Download complete. Opening file: " + localFile.getName());
            progressBar.setVisible(false);

            // Now open the downloaded local file
            openLocalLogFile(localFile, true, parsingConfig);

            // Disconnect the SSH service as its job is done for this file
            if (sshService != null) {
                sshService.disconnect();
            }
        });

        downloadTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            Throwable ex = downloadTask.getException();
            logger.error("Failed to download remote file", ex);
            showError("Remote File Error", "Failed to download file: " + ex.getMessage());
            updateStatus("Failed to download remote file.");
            if (sshService != null) {
                sshService.disconnect();
            }
        });

        new Thread(downloadTask).start();
    }


    /**
     * Cancel current loading task and cleanup memory
     */
    private void cancelCurrentLoadingTask() {
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            logger.info("Cancelling previous loading task...");
            currentLoadingTask.cancel(true);
            
            // Wait briefly for cancellation
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            logger.info("Previous task cancelled");
        }

        if (visibleLogEntries != null && !visibleLogEntries.isEmpty()) {
            int previousSize = visibleLogEntries.size();
            visibleLogEntries.clear();
            logger.info("Cleared {} entries from memory", previousSize);
        }

        currentLogEntrySource = null;
        originalLogEntrySource = null;
        System.gc();
        
        logger.info("Memory cleanup complete");
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

        cancelCurrentLoadingTask();

        currentFile = file;
        currentParsingConfig = parsingConfig;
        updateDateTimeFilterPromptText(parsingConfig);
        logger.info("Updated date filter prompt to match parsing config: {} (format: {})", parsingConfig.getName(), parsingConfig.getTimestampFormat() != null ? parsingConfig.getTimestampFormat() : "default");
        LogFile logFile = getOrCreateLogFile(file, parsingConfig);

        if (logFile == null) {
            logger.error("Failed to get or create log file record for: {}", file.getAbsolutePath());
            showError("Database Error", "Failed to save log file information to database.");
            return;
        }

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
     * - Uses multithreaded parallel parsing for speed
     * - Loads ALL entries into memory
     * - Virtual scrolling handles large datasets efficiently
     * - Simple, fast, and consistent for any file size
     */
    private void loadFileWithParallelParsing(File file, ParsingConfig parsingConfig, LogFile logFile, boolean updateRecentFilesList) {
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        updateStatus("Parsing file: " + file.getName());

        Task<List<LogEntry>> task = getListTask(file, parsingConfig);

        task.setOnSucceeded(e -> {
            List<LogEntry> entries = task.getValue();
            logger.info("Parsing complete! Loaded {} entries", entries.size());
            originalLogEntrySource = new ListLogEntrySourceImpl(entries);
            currentLogEntrySource = originalLogEntrySource;

            visibleLogEntries.clear();
            visibleLogEntries.addAll(entries);

            updateTableColumns(currentParsingConfig);
            logger.info("Updated table columns for config: {}", currentParsingConfig.getName());

            Platform.runLater(() -> {
                if (!visibleLogEntries.isEmpty()) {
                    logTableView.scrollTo(visibleLogEntries.size() - 1);
                }

                autoResizeColumns(logTableView);
                logger.info("Auto-fit columns completed after load");
            });

            if (updateRecentFilesList) {
                RecentFile recentFile = new RecentFile();
                recentFile.setFileId(logFile.getId());
                recentFile.setLastOpened(LocalDateTime.now());
                recentFileService.save(logFile, recentFile);
                refreshRecentFilesList();
                logger.info("Added file to recent files: {}", file.getName());
            }

            setupFileWatcher(file);

            int totalEntries = entries.size();
            updateStatus(String.format("Showing all %,d entries from %s (virtual scrolling ⚡)", totalEntries, file.getName()));
            progressBar.setVisible(false);

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
            logger.info("Parsing cancelled by user");
            updateStatus("Parsing cancelled");
            currentLoadingTask = null;
        });

        currentLoadingTask = task;
        
        new Thread(task).start();
    }

    private Task<List<LogEntry>> getListTask(File file, ParsingConfig parsingConfig) {
        final ParsingConfig configToUse = parsingConfig;

        return new Task<>() {
            @Override
            protected List<LogEntry> call() throws IOException {
                return logParserService.parseFileParallel(file, configToUse, new LogParserService.ProgressCallback() {
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
                            Platform.runLater(() -> logger.info("Parsed {} entries", totalEntries));
                        }
                    }
                );
            }
        };
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

    /**
     * Show parsing configuration selection dialog and return selected config
     * @return Selected ParsingConfig or null if cancelled
     */
    private ParsingConfig showParsingConfigSelectionDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ParsingConfigurationSelectionDialog.fxml"));
            DialogPane dialogPane = loader.load();

            ParsingConfigurationSelectionDialogController controller = loader.getController();

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Select Parsing Configuration");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(menuBar.getScene().getWindow());
            dialog.setDialogPane(dialogPane);

            Optional<ButtonType> result = showAndWaitAndRestore(dialog);

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
            Stage mainStage = (Stage) menuBar.getScene().getWindow();
            boolean wasMaximized = mainStage.isMaximized();
            double oldX = mainStage.getX();
            double oldY = mainStage.getY();
            double oldWidth = mainStage.getWidth();
            double oldHeight = mainStage.getHeight();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ParsingConfigDialog.fxml"));
            Parent root = loader.load();
            
            // Get controller and set callback
            ParsingConfigController controller = loader.getController();
            controller.setOnConfigChangedCallback(this::handleParsingConfigChanged);

            Stage dialog = new Stage();
            dialog.setTitle("Parsing Configuration");
            dialog.initOwner(mainStage);
            dialog.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            dialog.setScene(scene);
            dialog.setWidth(1000);
            dialog.setHeight(800);

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

            logger.info("Parsing config dialog closed, returning to previous view");
        } catch (IOException e) {
            logger.error("Failed to open parsing configuration dialog", e);
            showError("Failed to open parsing configuration", e.getMessage());
        }
    }
    
    /**
     * Handle server management dialog
     */
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

            logger.info("Server management dialog closed");
        } catch (IOException e) {
            logger.error("Failed to open server management dialog", e);
            showError("Failed to open server management", e.getMessage());
        }
    }
    
    /**
     * Handle parsing configuration changes from ParsingConfigDialog
     * Re-parse current file with updated configuration if file is loaded
     */
    private void handleParsingConfigChanged() {
        logger.info("Parsing configuration changed, checking if current file needs re-parsing");
        
        if (currentFile == null || currentParsingConfig == null) {
            logger.info("No file currently loaded, skipping re-parse");
            return;
        }
        
        // Reload current parsing config from database (it may have been updated)
        Optional<ParsingConfig> updatedConfigOpt = parsingConfigService.findById(currentParsingConfig.getId());
        
        if (updatedConfigOpt.isEmpty()) {
            logger.warn("Current parsing config no longer exists in database");
            showInfo("Configuration Deleted", 
                "The current parsing configuration has been deleted. Please reload the file with a new configuration.");
            return;
        }
        
        ParsingConfig updatedConfig = updatedConfigOpt.get();
        
        // Check if config actually changed
        if (configsAreEqual(currentParsingConfig, updatedConfig)) {
            logger.info("Configuration unchanged, no need to re-parse");
            return;
        }
        
        // Ask user if they want to re-parse with new configuration
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Parsing Configuration Changed");
        alert.setHeaderText("The parsing configuration has been updated.");
        alert.setContentText(String.format(
            "Current file: %s\nCurrent config: %s\n\nDo you want to re-parse the file with the updated configuration?",
            currentFile.getName(),
            updatedConfig.getName()
        ));
        
        ButtonType reParseButton = new ButtonType("Re-parse Now");
        ButtonType laterButton = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(reParseButton, laterButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        
        if (result.isPresent() && result.get() == reParseButton) {
            logger.info("User chose to re-parse file with updated configuration");
            // Re-parse file with updated config
            openLocalLogFile(currentFile, false, updatedConfig);
        } else {
            logger.info("User chose not to re-parse file");
            updateStatus("Configuration updated. Reload file to apply changes.");
        }
    }
    
    /**
     * Compare two ParsingConfig objects for equality (by content, not reference)
     */
    private boolean configsAreEqual(ParsingConfig config1, ParsingConfig config2) {
        if (config1 == null || config2 == null) {
            return config1 == config2;
        }
        
        return Objects.equals(config1.getName(), config2.getName()) &&
               Objects.equals(config1.getRegexPattern(), config2.getRegexPattern()) &&
               Objects.equals(config1.getDescription(), config2.getDescription()) &&
               Objects.equals(config1.getTimestampFormat(), config2.getTimestampFormat());
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

            ParsingConfig parsingConfig = recentFile.parsingConfig();

            if (parsingConfig != null) {
                logger.info("ParsingConfig from DTO - ID: {}, Name: {}, Valid: {}",
                    parsingConfig.getId(), parsingConfig.getName(), parsingConfig.isValid());
            } else {
                logger.info("ParsingConfig from DTO is NULL");
            }

            if (parsingConfig == null) {
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
                logger.warn("No parsing config associated with file: {}, showing selection dialog", file.getName());
                parsingConfig = showParsingConfigSelectionDialog();

                if (parsingConfig == null) {
                    logger.info("No parsing configuration selected for recent file, operation cancelled");
                    return;
                }
            }

            logger.info("Opening recent file: {} with parsing config: {} (ID: {})", file.getName(), parsingConfig.getName(), parsingConfig.getId());

            resetFilters();

            openLocalLogFile(file, false, parsingConfig);

            autoResizeColumns(logTableView);
        }
    }

    /**
     * Reset all filters to default state.
     * Called when opening a new file to provide clean slate.
     */
    private void resetFilters() {
        logger.info("Resetting all filters to default state");
        searchField.clear();
        logLevelFilterComboBox.getSelectionModel().select("ALL");
        regexCheckBox.setSelected(false);
        caseSensitiveCheckBox.setSelected(false);
        hideUnparsedCheckBox.setSelected(false);
        dateTimeFromField.clear();
        dateTimeToField.clear();
        logger.info("Filters reset: Level=ALL, Search='', Regex=false, CaseSensitive=false, HideUnparsed=false, DateTime=empty");
    }
    
    /**
     * Clear date/time filter
     */
    private void clearDateFilter() {
        logger.info("🗑️ Clearing date/time filter");
        dateTimeFromField.clear();
        dateTimeToField.clear();
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
            LocalDateTime timestamp = entry.getTimestamp();
            if (timestamp != null) {
                return timestamp;
            }

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
    /**
     * High-performance search with optimized regex compilation and string matching.
     * Caches compiled patterns and pre-processes search strings for maximum efficiency.
     */
    private void performSearch() {
        if (currentLogEntrySource == null) {
            return;
        }

        final String searchText = searchField.getText();
        final boolean isRegex = regexCheckBox.isSelected();
        final boolean caseSensitive = caseSensitiveCheckBox.isSelected();
        final boolean hideUnparsed = hideUnparsedCheckBox.isSelected();
        final String selectedLevel = logLevelFilterComboBox.getSelectionModel().getSelectedItem();
        final String dateTimeFrom = dateTimeFromField.getText();
        final String dateTimeTo = dateTimeToField.getText();

        logger.info("Search - Level: {}, Text: '{}', Regex: {}, CaseSensitive: {}, HideUnparsed: {}", 
            selectedLevel, searchText, isRegex, caseSensitive, hideUnparsed);
        
        updateStatus("Searching...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                // Pre-parse date filters once (not per entry)
                final LocalDateTime filterFrom = parseDateTimeFilter(dateTimeFrom);
                final LocalDateTime filterTo = parseDateTimeFilter(dateTimeTo);
                final boolean hasDateFilter = filterFrom != null || filterTo != null;
                
                // Pre-compile regex pattern once (not per entry) - CRITICAL OPTIMIZATION
                final Pattern regexPattern;
                if (isRegex && searchText != null && !searchText.trim().isEmpty()) {
                    try {
                        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                        regexPattern = Pattern.compile(searchText, flags);
                    } catch (Exception e) {
                        logger.error("Invalid regex pattern: {}", e.getMessage());
                        Platform.runLater(() -> {
                            showError("Invalid Regex", "Pattern compilation failed: " + e.getMessage());
                            updateStatus("Invalid regex pattern");
                        });
                        return null;
                    }
                } else {
                    regexPattern = null;
                }
                
                // Pre-process text search (once, not per entry)
                final String searchTextLower = (!isRegex && searchText != null && !caseSensitive) 
                    ? searchText.toLowerCase() : searchText;
                final boolean hasTextSearch = searchText != null && !searchText.trim().isEmpty();
                
                // Pre-process level filter
                final boolean hasLevelFilter = selectedLevel != null && !selectedLevel.equals("ALL");
                final boolean filterUnparsedOnly = "UNPARSED".equals(selectedLevel);
                
                // Build optimized predicate with early exits
                Predicate<LogEntry> searchPredicate = entry -> {
                    // Fast path: hideUnparsed check (cheapest check first)
                    if (hideUnparsed && !entry.isParsed()) {
                        return false;
                    }
                    
                    // Level filter check (second cheapest)
                    if (hasLevelFilter) {
                        if (filterUnparsedOnly) {
                            return !entry.isParsed();
                        }
                        if (!entry.isParsed()) {
                            return false;
                        }
                        String entryLevel = entry.getLevel();
                        if (entryLevel == null || !entryLevel.equalsIgnoreCase(selectedLevel)) {
                            return false;
                        }
                    }
                    
                    // Date filter check (medium cost)
                    if (hasDateFilter) {
                        LocalDateTime entryTime = parseEntryTimestamp(entry);
                        if (entryTime == null) {
                            return false;
                        }
                        if (filterFrom != null && entryTime.isBefore(filterFrom)) {
                            return false;
                        }
                        if (filterTo != null && entryTime.isAfter(filterTo)) {
                            return false;
                        }
                    }
                    
                    // Text search (most expensive, checked last)
                    if (hasTextSearch) {
                        if (isRegex) {
                            return regexPattern.matcher(entry.getRawLog()).find();
                        } else {
                            if (caseSensitive) {
                                return entry.getRawLog().contains(searchTextLower);
                            } else {
                                return entry.getRawLog().toLowerCase().contains(searchTextLower);
                            }
                        }
                    }
                    
                    return true;
                };

                LogEntrySource filteredSource = originalLogEntrySource.filter(searchPredicate);
                int totalFiltered = filteredSource.getTotalEntries();
                List<LogEntry> filteredEntries = filteredSource.getEntries(0, totalFiltered);

                Platform.runLater(() -> {
                    currentLogEntrySource = filteredSource;
                    visibleLogEntries.clear();
                    visibleLogEntries.addAll(filteredEntries);

                    if (filteredEntries.isEmpty()) {
                        updateStatus("No matching entries found");
                    } else {
                        updateStatus(String.format("📍 Found %,d of %,d entries", 
                            filteredEntries.size(), originalLogEntrySource.getTotalEntries()));
                    }

                    autoResizeColumns(logTableView);

                    if (!visibleLogEntries.isEmpty()) {
                        logTableView.scrollTo(0);
                    }
                });
                return null;
            }
        };

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

            int totalEntries = originalLogEntrySource.getTotalEntries();
            List<LogEntry> allEntries = originalLogEntrySource.getEntries(0, totalEntries);
            visibleLogEntries.addAll(allEntries);
            
            updateStatus(String.format("📍 Showing all %,d entries from %s", visibleLogEntries.size(), currentFile != null ? currentFile.getName() : ""));

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
        if (currentLogEntrySource != originalLogEntrySource) {
            return true;
        }

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
        
        logger.info("Double-click detected: Jumping to original position (line {}) from filtered view", targetLineNumber);
        updateStatus(String.format("Jumping to line %,d...", targetLineNumber));
        
        Task<Void> jumpTask = new Task<>() {
            @Override
            protected Void call() {
                Platform.runLater(() -> {
                    searchField.clear();
                    logLevelFilterComboBox.getSelectionModel().select("ALL");
                    hideUnparsedCheckBox.setSelected(false);
                    dateTimeFromField.clear();
                    dateTimeToField.clear();

                    currentLogEntrySource = originalLogEntrySource;

                    int totalEntries = originalLogEntrySource.getTotalEntries();
                    List<LogEntry> allEntries = originalLogEntrySource.getEntries(0, totalEntries);
                    visibleLogEntries.clear();
                    visibleLogEntries.addAll(allEntries);

                    int targetIndex = -1;
                    for (int i = 0; i < allEntries.size(); i++) {
                        if (allEntries.get(i).getLineNumber() == targetLineNumber) {
                            targetIndex = i;
                            break;
                        }
                    }
                    
                    if (targetIndex >= 0) {
                        final int indexToSelect = targetIndex;
                        Platform.runLater(() -> {
                            logTableView.scrollTo(Math.max(0, indexToSelect - 5)); // Scroll with context
                            logTableView.getSelectionModel().select(indexToSelect);
                            
                            updateStatus(String.format("Jumped to line %,d (row %,d of %,d)", targetLineNumber, indexToSelect + 1, totalEntries));
                            
                            logger.info("Successfully jumped to line {} at index {}", targetLineNumber, indexToSelect);
                        });
                    } else {
                        updateStatus(String.format("Line %,d not found in original data", targetLineNumber));
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
            promptText = config.getTimestampFormat();
            logger.info("Setting date filter prompt to config format: '{}' (from config: {})",
                promptText, config.getName());
        } else {
            promptText = "yyyy-MM-dd HH:mm:ss";
             logger.info("Setting date filter prompt to default format: '{}' (config: {})",
                promptText, config != null ? "null format" : "null config");
        }

        Platform.runLater(() -> {
            dateTimeFromField.setPromptText(promptText);
            dateTimeToField.setPromptText(promptText);
            logger.debug("Prompt text set to fields: '{}'", promptText);
        });

        String tooltipText = "Enter date/time in format: " + promptText + 
                           "\n\nSupported formats:" +
                           "\n• " + promptText +
                           "\n• yyyy-MM-dd (date only)" +
                           "\n• dd-MM-yyyy HH:mm:ss" +
                           "\nOr any common date format";

        Platform.runLater(() -> {
            Tooltip fromTooltip = new Tooltip(tooltipText);
            Tooltip toTooltip = new Tooltip(tooltipText);
            
            dateTimeFromField.setTooltip(fromTooltip);
            dateTimeToField.setTooltip(toTooltip);
            logger.debug("Tooltips set for date filter fields");
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

    /**
     * NEW STRATEGY: Always show ALL entries using Virtual Scrolling
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
        int totalEntries = currentLogEntrySource.getTotalEntries();
        
        logger.info("Loading ALL {} entries for virtual scrolling (table only renders visible rows)...", totalEntries);
        long startTime = System.currentTimeMillis();

        List<LogEntry> allEntries = currentLogEntrySource.getEntries(0, totalEntries);
        visibleLogEntries.addAll(allEntries);
        
        long loadTime = System.currentTimeMillis() - startTime;
        logger.info("Loaded ALL {} entries in {}ms - Virtual scrolling enabled ⚡", allEntries.size(), loadTime);

        Platform.runLater(() -> {
            if (!visibleLogEntries.isEmpty()) {
                logTableView.scrollTo(visibleLogEntries.size() - 1);
                logger.debug("Auto-scrolled to bottom (entry #{})", visibleLogEntries.size());
            }
        });
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
            if (currentFile != null && !currentFile.equals(file)) {
                logFileWatcher.unwatchFile(currentFile);
            }


            logFileWatcher.watchFile(file, (modifiedFile, eventKind) -> {
                logger.info("File change detected: {} - Event: {}", modifiedFile.getName(), eventKind.name());

                Platform.runLater(() -> handleAutoRefresh(modifiedFile));
            });

            logger.info("Auto-refresh enabled for: {} (like 'tail -f')", file.getName());

        } catch (Exception e) {
            logger.error("Failed to setup file watcher", e);
            showError("Auto-Refresh Error", "Failed to enable auto-refresh for file: " + e.getMessage());
        }
    }

    /**
     * Handle manual refresh button click
     */
    private void handleRefreshCurrentFile() {
        if (currentFile == null) {
            logger.warn("No file is currently loaded");
            updateStatus("No file to refresh");
            return;
        }
        
        if (currentParsingConfig == null) {
            logger.warn("No parsing config available");
            updateStatus("No parsing config available");
            return;
        }
        
        logger.info("Manual refresh triggered for: {}", currentFile.getName());
        updateStatus("Refreshing file...");
        openLocalLogFile(currentFile, false, currentParsingConfig);
    }
    
    /**
     * Handle auto-refresh when file changes
     */
    private void handleAutoRefresh(File file) {
        if (!autoRefreshMenuItem.isSelected()) {
            return;
        }

        logger.info("Auto-refreshing file: {}", file.getName());
        updateStatus("Auto-refreshing... (file changed)");
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
            logger.info("Auto-refresh ENABLED");
            updateStatus("Auto-refresh enabled (tail -f mode)");

            if (currentFile != null) {
                setupFileWatcher(currentFile);
            }
        } else {
            logger.info("Auto-refresh DISABLED");
            updateStatus("Auto-refresh disabled");
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

        Optional<ButtonType> result = showAndWaitAndRestore(alert);
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
                """
                        High-performance log viewer with advanced parsing capabilities.
                        
                        Features:
                        - Custom regex parsing with named groups
                        - Local and remote (SSH) file access
                        - JSON/XML prettification
                        - Text and regex search
                        - Performance optimized for large files
                        
                        © 2024 SeeLoggyPlus"""
        );
        showAndWaitAndRestore(alert);
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
            showAndWaitAndRestore(alert);
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
            showAndWaitAndRestore(alert);
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
            sample = tableView.getItems();
            logger.debug("Using all {} entries for auto-fit", totalSize);
        } else {
            int sampleSize = Math.min(3000, totalSize / 10);
            sample = new ArrayList<>(sampleSize);

            int startSample = 1000;
            sample.addAll(tableView.getItems().subList(0, startSample));

            if (totalSize > 2000) {
                int midStart = (totalSize - 1000) / 2;
                int midEnd = Math.min(midStart + 1000, totalSize);
                sample.addAll(tableView.getItems().subList(midStart, midEnd));
            }

            int endStart = Math.max(startSample, totalSize - 1000);
            sample.addAll(tableView.getItems().subList(endStart, totalSize));

            logger.debug("Using smart sample of {} entries from {} total for auto-fit", sample.size(), totalSize);
        }

        Text measureText = new Text();
        measureText.setStyle("-fx-font-family: 'System'; -fx-font-size: 12px;");

        for (TableColumn<LogEntry, ?> col : tableView.getColumns()) {
            if ("Line".equals(col.getText())) {
                continue;
            }

            double maxWidth = 0;

            measureText.setText(col.getText());
            maxWidth = Math.max(maxWidth, measureText.getLayoutBounds().getWidth());

            for (LogEntry entry : sample) {
                try {
                    if (col.getCellObservableValue(entry) != null && 
                        col.getCellObservableValue(entry).getValue() != null) {
                        
                        String cellValue = col.getCellObservableValue(entry).getValue().toString();

                        if (cellValue.length() > 500) {
                            cellValue = cellValue.substring(0, 500);
                        }
                        
                        measureText.setText(cellValue);
                        double width = measureText.getLayoutBounds().getWidth();
                        maxWidth = Math.max(maxWidth, width);
                    }
                } catch (Exception e) {
                    logger.debug("Error measuring cell width: {}", e.getMessage());
                }
            }

            double padding = 50.0;
            double newWidth = maxWidth + padding;

            double minWidth = col.getMinWidth() > 0 ? col.getMinWidth() : 80.0;
            double maxAllowedWidth = 1200.0;
            
            newWidth = Math.max(minWidth, newWidth);
            newWidth = Math.min(maxAllowedWidth, newWidth);

            col.setPrefWidth(newWidth);
            
            logger.debug("Column '{}': width = {}", col.getText(), (int)newWidth);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Auto-fit completed in {}ms", duration);
    }

    /**
     * Shows a modal dialog and restores the main stage's state afterwards.
     * This is a workaround for a common bug on some Linux window managers where
     * the main stage shrinks after a modal dialog is closed.
     *
     * @param dialog The dialog to show and wait for.
     * @param <T> The result type of the dialog.
     * @return An Optional containing the dialog result.
     */
    private <T> Optional<T> showAndWaitAndRestore(Dialog<T> dialog) {
        Stage mainStage = (Stage) menuBar.getScene().getWindow();
        boolean wasMaximized = mainStage.isMaximized();
        double oldX = mainStage.getX();
        double oldY = mainStage.getY();
        double oldWidth = mainStage.getWidth();
        double oldHeight = mainStage.getHeight();

        // Ensure the dialog has an owner, which is crucial for modality behavior
        if (dialog.getOwner() == null) {
            dialog.initOwner(mainStage);
        }

        Optional<T> result = dialog.showAndWait();

        Platform.runLater(() -> {
            if (wasMaximized) {
                mainStage.setMaximized(true);
            } else {
                // Check if the stage was unintentionally moved or resized
                if (mainStage.getX() != oldX || mainStage.getY() != oldY ||
                    mainStage.getWidth() != oldWidth || mainStage.getHeight() != oldHeight) {
                    
                    mainStage.setX(oldX);
                    mainStage.setY(oldY);
                    mainStage.setWidth(oldWidth);
                    mainStage.setHeight(oldHeight);
                }
            }
        });

        return result;
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

    /**
     * High-performance TableCell for unparsed content display.
     * Optimized for minimal memory allocation and fast rendering.
     */
    private static class UnparsedContentCell extends TableCell<LogEntry, String> {
        private static final int MAX_LINES_DISPLAY = 10;
        private static final int MAX_CHARS_PER_LINE = 200;
        private static final int MAX_TOTAL_CHARS = 2000;
        private static final String STYLE_UNPARSED = "-fx-padding: 5px;";

        private Label contentLabel;

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (item == null || empty) {
                setText(null);
                setGraphic(null);
                return;
            }

            TableRow<LogEntry> row = getTableRow();
            if (row == null) {
                setText(item);
                setGraphic(null);
                return;
            }

            LogEntry entry = row.getItem();
            if (entry == null || entry.isParsed()) {
                setText(item);
                setGraphic(null);
                return;
            }

            // Unparsed entry - use Label with wrapping
            if (contentLabel == null) {
                contentLabel = new Label();
                contentLabel.setWrapText(true);
                contentLabel.setMaxWidth(Double.MAX_VALUE);
                contentLabel.setStyle(STYLE_UNPARSED);
            }

            contentLabel.setText(formatUnparsedContent(item));
            setText(null);
            setGraphic(contentLabel);
        }

        /**
         * Format unparsed content with intelligent truncation for optimal performance.
         * Avoids excessive string operations.
         */
        private String formatUnparsedContent(String rawContent) {
            if (rawContent == null || rawContent.isEmpty()) {
                return rawContent;
            }

            int contentLength = rawContent.length();
            if (contentLength <= MAX_CHARS_PER_LINE && rawContent.indexOf('\n') == -1) {
                return rawContent;
            }

            // Split into lines (reuse array)
            String[] lines = rawContent.split("\\r?\\n", MAX_LINES_DISPLAY + 1);
            
            if (lines.length == 1 && contentLength <= MAX_CHARS_PER_LINE) {
                return rawContent;
            }

            // Pre-allocate StringBuilder with estimated capacity
            StringBuilder display = new StringBuilder(Math.min(contentLength, MAX_TOTAL_CHARS));
            int totalChars = 0;
            int displayedLines = 0;

            for (int i = 0; i < lines.length && displayedLines < MAX_LINES_DISPLAY; i++) {
                String line = lines[i];
                int lineLength = line.length();
                
                if (totalChars + lineLength > MAX_TOTAL_CHARS) {
                    break;
                }

                if (displayedLines > 0) {
                    display.append('\n');
                }

                if (lineLength > MAX_CHARS_PER_LINE) {
                    display.append(line, 0, MAX_CHARS_PER_LINE).append("...");
                    totalChars += MAX_CHARS_PER_LINE + 3;
                } else {
                    display.append(line);
                    totalChars += lineLength;
                }

                displayedLines++;
            }

            int remainingLines = lines.length - displayedLines;
            if (remainingLines > 0) {
                display.append("\n... (").append(remainingLines)
                       .append(remainingLines > 1 ? " more lines)" : " more line)");
            }

            return display.toString();
        }
    }
}
