package com.seeloggyplus.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.*;
import com.seeloggyplus.service.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
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
    private Button scrollToBottomButton;
    @FXML
    private Button refreshButton;

    // FXML Components - Bottom Panel (Log Detail)
    @FXML
    private VBox bottomPanel;
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
        FontAwesomeIconView currentPinIcon = (FontAwesomeIconView) pinLeftPanelButton.getGraphic();
        if (isLeftPanelPinned) {
            currentPinIcon.setGlyphName("THUMB_TACK");
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
            currentPinIcon.setGlyphName("ARROW_RIGHT"); // Or a suitable unpinned icon
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
        // Configure table view
        logTableView.setItems(visibleLogEntries);
        // Use UNCONSTRAINED_RESIZE_POLICY to allow horizontal scrolling
        logTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // Handle row selection
        logTableView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        displayLogDetail(newVal);
                    }
                });

        // Setup search functionality
        searchButton.setOnAction(e -> performSearch());
        clearSearchButton.setOnAction(e -> clearSearch());
        searchField.setOnAction(e -> performSearch());
        autoFitButton.setOnAction( e -> autoResizeColumns(logTableView));
        scrollToBottomButton.setOnAction(e -> handleScrollToBottom());
        refreshButton.setOnAction(e -> handleRecentFileSelected(recentFilesListView.getSelectionModel().getSelectedItem()));


        // Initialize with default columns
        updateTableColumns(null);

        // Add scroll listener for lazy loading
        logTableView.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                ScrollBar verticalBar = (ScrollBar) newSkin.getNode().lookup(".scroll-bar:vertical");
                if (verticalBar != null) {
                    verticalBar.valueProperty().addListener((vObs, oldVal, newVal) -> {
                        if (newVal.doubleValue() > 0.9) { // When scrolled to 90% of the bottom
                            loadMoreVisibleEntries();
                        }
                    });
                }
            }
        });
    }

    /**
     * Handles the action to scroll the log table to the very bottom.
     * This involves loading all remaining entries first.
     */
    private void handleScrollToBottom() {
        if (currentLogEntrySource == null || currentLogEntrySource.getTotalEntries() == 0) {
            return;
        }

        updateStatus("Loading all entries to scroll to bottom...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<List<LogEntry>> loadAllTask = new Task<>() {
            @Override
            protected List<LogEntry> call() throws Exception {
                int currentSize = visibleLogEntries.size();
                int totalAvailable = currentLogEntrySource.getTotalEntries();
                List<LogEntry> remainingEntries = List.of();

                if (currentSize < totalAvailable) {
                    // Load all remaining entries in one go
                    remainingEntries = currentLogEntrySource.getEntries(currentSize, totalAvailable - currentSize);
                }
                return remainingEntries;

            }
        };

        loadAllTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                List<LogEntry> loadedEntries = loadAllTask.getValue();
                if (!loadedEntries.isEmpty()) {
                    visibleLogEntries.addAll(loadedEntries);
                }

                if (!visibleLogEntries.isEmpty()) {
                    logTableView.scrollTo(visibleLogEntries.size() - 1);
                }
                progressBar.setVisible(false);
                updateStatus(String.format("Scrolled to bottom. Showing %d of %d entries from %s", visibleLogEntries.size(), currentLogEntrySource.getTotalEntries(), currentFile.getName()));
            });
        });

        loadAllTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            logger.error("Failed to load all entries for scrolling to bottom", loadAllTask.getException());
            showError("Scroll Error", "Failed to load all entries to scroll to bottom.");
            updateStatus("Scroll to bottom failed.");
        });

        new Thread(loadAllTask).start();
    }

    /**
     * Loads more entries into the visibleLogEntries list from the currentLogEntrySource.
     */
    private void loadMoreVisibleEntries() {
        if (currentLogEntrySource == null) {
            return;
        }

        int currentSize = visibleLogEntries.size();
        int totalAvailable = currentLogEntrySource.getTotalEntries();

        if (currentSize < totalAvailable) {
            List<LogEntry> newEntries = currentLogEntrySource.getEntries(currentSize, LAZY_LOAD_BATCH_SIZE);
            visibleLogEntries.addAll(newEntries);
            logger.debug("Loaded {} new entries. Total visible: {} / {}", newEntries.size(), visibleLogEntries.size(), totalAvailable);
            updateStatus(String.format("Showing %d of %d entries from %s", visibleLogEntries.size(), totalAvailable, currentFile.getName()));
        }
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
    }

    private void setupLogLevelFilter() {
        logLevelFilterComboBox.setItems(FXCollections.observableArrayList("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"));
        logLevelFilterComboBox.getSelectionModel().select("ALL");
        logLevelFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> performSearch());
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

        TableColumn<LogEntry, String> lineCol = new TableColumn<>("Line");
        lineCol.setCellValueFactory(cellData -> {
            LogEntry entry = cellData.getValue();
            if (entry.getLineNumber() != entry.getEndLineNumber()) {
                return new SimpleStringProperty(entry.getLineNumber() + "-" + entry.getEndLineNumber());
            } else {
                return new SimpleStringProperty(String.valueOf(entry.getLineNumber()));
            }
        });
        lineCol.setPrefWidth(80);
        lineCol.setMinWidth(80);
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

                if ("level".equalsIgnoreCase(groupName)) {
                    column.setCellFactory(col -> new TableCell<LogEntry, String>() {
                        private final String[] LEVEL_CLASSES = {
                                "log-level-error", "log-level-warn", "log-level-info",
                                "log-level-debug", "log-level-trace", "log-level-fatal"
                        };

                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);

                            if (item == null || empty) {
                                setText(null);
                                getStyleClass().removeAll(LEVEL_CLASSES);
                            } else {
                                setText(item);

                                getStyleClass().removeAll(LEVEL_CLASSES);

                                switch (item.toUpperCase()) {
                                    case "ERROR":
                                        getStyleClass().add("log-level-error");
                                        break;
                                    case "FATAL":
                                        getStyleClass().add("log-level-fatal");
                                        break;
                                    case "WARN":
                                    case "WARNING":
                                        getStyleClass().add("log-level-warn");
                                        break;
                                    case "INFO":
                                        getStyleClass().add("log-level-info");
                                        break;
                                    case "DEBUG":
                                        getStyleClass().add("log-level-debug");
                                        break;
                                    case "TRACE":
                                        getStyleClass().add("log-level-trace");
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                    });
                }

                column.setMinWidth(80);
                logTableView.getColumns().add(column);
            }
            logger.info("Created {} columns total (including line number)", logTableView.getColumns().size());
        } else {
            logger.warn("Config is null or invalid, using default raw log column");
            TableColumn<LogEntry, String> rawCol = new TableColumn<>("Log Message");
            rawCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRawLog()));
            rawCol.setPrefWidth(800);
            logTableView.getColumns().add(rawCol);
            logger.info("Created 2 columns (line number + raw log)");
        }
    }


    /**
     * Handle open file action
     */
    private void handleOpenFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Log File");
        fileChooser
                .getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("Log Files", "*.log", "*.txt"),
                        new FileChooser.ExtensionFilter("All Files", "*.*")
                );

        File file = fileChooser.showOpenDialog(menuBar.getScene().getWindow());
        if (file != null) {
            openLocalLogFile(file, true);
        }
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
     * Open and parse log file
     *
     * @param file                  The file to open
     * @param updateRecentFilesList true if the file should be added to the top of the recent files list
     */
    private void openLocalLogFile(File file, boolean updateRecentFilesList) {
        currentFile = file;
        currentParsingConfig = parsingConfigService.findDefault().orElse(null);

        if (currentParsingConfig == null) {
            logger.warn("No default parsing config found, creating default");
            showInfo("Log Parsing Configuration", "Parsing Configuration Not Ready, Please Setup First");
            return;
        }

        LogFile logFileByPathAndName = logFileService.getLogFileByPathAndName(currentFile.getName(), currentFile.getAbsolutePath());
        if (logFileByPathAndName == null){
            logger.info("LogFile not found in database, creating new entry for file: {}", currentFile.getAbsolutePath());
            logFileByPathAndName = new LogFile();
            logFileByPathAndName.setName(currentFile.getName());
            logFileByPathAndName.setFilePath(currentFile.getAbsolutePath());
            logFileByPathAndName.setRemote(false);
            logFileByPathAndName.setParsingConfigurationID(currentParsingConfig.getId());
            logFileByPathAndName.setModified(String.valueOf(currentFile.lastModified()));
            logFileByPathAndName.setSize(String.valueOf(currentFile.length()));

            logFileService.insertLogFile(logFileByPathAndName);
        }

        logger.info("Cache miss for file: {}. Parsing...", file.getName());
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        updateStatus("Loading file: " + file.getName());

        final ParsingConfig configToUse = this.currentParsingConfig;
        final LogFile finalLogFile = logFileByPathAndName;

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
                recentFileService.save(finalLogFile, recentFile);
                refreshRecentFilesList();
            }

            displayLogEntries(currentLogEntrySource, file, updateRecentFilesList);
            logger.info("Loaded {} log entries from {}, table now shows {} items", entries.size(), file.getName(), visibleLogEntries.size());
        });

        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            Throwable ex = task.getException();
            logger.error("Failed to load log file", ex);
            showError("Failed to load log file", ex.getMessage());
            updateStatus("Failed to load file");
        });

        new Thread(task).start();
    }

    /**
     * Handle recent file selected
     */
    private void handleRecentFileSelected(RecentFilesDto recentFile) {
        if (recentFile.logFile().isRemote()) {
            showInfo("Remote File", "Opening remote files is not yet implemented in this version.");
        } else {
            File file = new File(recentFile.logFile().getFilePath());
            if (file.exists()) {
                openLocalLogFile(file, false);
            } else {
                showError("File Not Found", "The file no longer exists: " + recentFile.logFile().getFilePath());
            }
            performSearch();
            autoResizeColumns(logTableView);
        }
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

        updateStatus("Searching...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                // Create a predicate based on search criteria
                Predicate<LogEntry> searchPredicate = entry -> {
                    // hide unparsed log
                    if (hideUnparsed){
                        if (!entry.isParsed()){
                            return false;
                        }
                    }

                    // Level filtering
                    if (selectedLevel != null && !selectedLevel.equals("ALL")) {
                        if (!entry.getLevel().equalsIgnoreCase(selectedLevel)) {
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

                // Apply the filter to the original LogEntrySource
                LogEntrySource filteredSource = originalLogEntrySource.filter(searchPredicate);

                Platform.runLater(() -> {
                    // Update the visible entries with the filtered source
                    currentLogEntrySource = filteredSource; // Update the source for subsequent loads
                    visibleLogEntries.clear();
                    loadMoreVisibleEntries(); // Load initial batch of filtered results
                    updateStatus(String.format("Showing first %d of %d matching entries", visibleLogEntries.size(), currentLogEntrySource.getTotalEntries()));
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
        if (originalLogEntrySource != null) {
            currentLogEntrySource = originalLogEntrySource; // Reset to the original unfiltered source
            visibleLogEntries.clear();
            loadMoreVisibleEntries(); // Load initial batch from the original source
            updateStatus(String.format("Showing %d of %d entries from %s", visibleLogEntries.size(), currentLogEntrySource.getTotalEntries(), currentFile.getName()));
        } else {
            visibleLogEntries.clear();
            updateStatus("Search cleared. No file loaded.");
        }
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

            // Refresh current file if one is loaded.
            // Re-adding to recents will update the parsing config and move it to the top.
            if (currentFile != null) {
                openLocalLogFile(currentFile, true);
            }
        } catch (IOException e) {
            logger.error("Failed to open parsing configuration dialog", e);
            showError("Failed to open parsing configuration", e.getMessage());
        }
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
     * Toggle bottom panel visibility
     */
    private void toggleBottomPanel() {
        boolean visible = showBottomPanelMenuItem.isSelected();

        if (!visible) {
            // Hide panel
            bottomPanel.setVisible(false);
            bottomPanel.setManaged(false);

            // Adjust split pane divider to expand center panel
            Platform.runLater(() -> verticalSplitPane.setDividerPositions(1.0));
        } else {
            // Show panel
            bottomPanel.setVisible(true);
            bottomPanel.setManaged(true);

            // Restore divider position
            Platform.runLater(() -> {
                double savedHeight = 200;
                double totalHeight = verticalSplitPane.getHeight();
                if (totalHeight > 0) {
                    double position = (totalHeight - savedHeight) / totalHeight;
                    verticalSplitPane.setDividerPositions(position);
                } else {
                    verticalSplitPane.setDividerPositions(0.75);
                }
            });
        }

        preferenceService.saveOrUpdatePreferences(new Preference("bottom_panel_show", String.valueOf(visible)));
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

        boolean bottomPanelShow = preferenceService.getPreferencesByCode("bottom_panel_show")
                .filter(Predicate.not(String::isBlank))
                .map(Boolean::parseBoolean)
                .orElse(true);

        bottomPanel.setVisible(bottomPanelShow);
        bottomPanel.setManaged(bottomPanelShow);
        showBottomPanelMenuItem.setSelected(bottomPanelShow);

        // Restore divider positions after scene is shown
        Platform.runLater(() -> {
            if (isLeftPanelPinned) {
                double savedWidth = 200; // Default value
                double totalWidth = horizontalSplitPane.getWidth();
                if (totalWidth > 0) {
                    double position = savedWidth / totalWidth;
                    horizontalSplitPane.setDividerPositions(position);
                }
            } else {
                horizontalSplitPane.setDividerPositions(0.0);
            }

            if (bottomPanelShow) {
                double savedHeight = 200; // Default value
                double totalHeight = verticalSplitPane.getHeight();
                if (totalHeight > 0) {
                    double position = (totalHeight - savedHeight) / totalHeight;
                    verticalSplitPane.setDividerPositions(position);
                }
            } else {
                verticalSplitPane.setDividerPositions(1.0);
            }
        });
    }

    /**
     * Method helper for display (from cache or new parse) to UI.
     */
    private void displayLogEntries(LogEntrySource source, File file, boolean updateRecentFilesList) {
        this.currentLogEntrySource = source;

        visibleLogEntries.clear();
        loadMoreVisibleEntries();

        updateTableColumns(this.currentParsingConfig);

        Platform.runLater(() -> autoResizeColumns(logTableView));

        logTableView.refresh();

        Platform.runLater(() -> {
            progressBar.setVisible(false);
            updateStatus(
                    String.format(
                            "Showing first %d of %d entries from %s",
                            visibleLogEntries.size(),
                            currentLogEntrySource.getTotalEntries(),
                            file.getName()
                    )
            );
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

        logger.info("Loaded {} log entries from {}, table now shows {} items (Total: {})", visibleLogEntries.size(), file.getName(), visibleLogEntries.size(), currentLogEntrySource.getTotalEntries());
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
     * It samples the data to avoid performance hits on very large datasets.
     */
    private void autoResizeColumns(TableView<LogEntry> tableView) {
        if (tableView.getItems() == null || tableView.getItems().isEmpty()) {
            return;
        }

        // Use a sample of the data to avoid performance issues with large files
        int sampleSize = Math.min(500, tableView.getItems().size());
        List<LogEntry> sample = tableView.getItems().subList(0, sampleSize);

        for (TableColumn<LogEntry, ?> col : tableView.getColumns()) {
            // Don't resize the "Line" column, its width is fixed
            if ("Line".equals(col.getText())) {
                continue;
            }

            double maxWidth = 0;

            // Calculate width of header text
            Text headerText = new Text(col.getText());
            maxWidth = Math.max(maxWidth, headerText.getLayoutBounds().getWidth());

            // Calculate width of cell content from sample
            for (LogEntry entry : sample) {
                if (col.getCellObservableValue(entry) != null && col.getCellObservableValue(entry).getValue() != null) {
                    String cellValue = col.getCellObservableValue(entry).getValue().toString();
                    Text text = new Text(cellValue);
                    maxWidth = Math.max(maxWidth, text.getLayoutBounds().getWidth());
                }
            }

            // Set the new width with some padding
            // Clamp the value between a min and a reasonable max width
            double newWidth = maxWidth + 40.0; // Add generous padding
            newWidth = Math.max(col.getMinWidth(), newWidth); // Honor min width
            newWidth = Math.min(800, newWidth); // Cap at a max width

            col.setPrefWidth(newWidth);
        }
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
                Label sizeLabel = new Label(logFile.getSize() + " bytes");
                vbox.getChildren().addAll(nameLabel, pathLabel, sizeLabel);
                setGraphic(vbox);
            }
        }
    }
}
