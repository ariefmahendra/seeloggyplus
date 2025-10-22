package com.seeloggyplus.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.seeloggyplus.model.*;
import com.seeloggyplus.service.*;
import com.seeloggyplus.repository.RecentFileRepository;
import com.seeloggyplus.repository.ParsingConfigRepository;
import com.seeloggyplus.repository.ParsingConfigRepositoryImpl;
import com.seeloggyplus.repository.RecentFileRepositoryImpl;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
    private SplitPane horizontalSplitPane;
    @FXML
    private SplitPane verticalSplitPane;

    // FXML Components - Left Panel (Recent Files)
    @FXML
    private VBox leftPanel;
    @FXML
    private ListView<RecentFile> recentFilesListView;
    @FXML
    private Button clearRecentButton;

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
    private SSHService sshService;
    private final Cache<LogCacheKey, LogCacheValue> logCache = Caffeine.newBuilder()
            .maximumSize(5)
            .build();

    private ObservableList<LogEntry> allLogEntries;
    private FilteredList<LogEntry> filteredLogEntries;
    private ParsingConfig currentParsingConfig;
    private File currentFile;

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

        // Initialize data
        allLogEntries = FXCollections.observableArrayList();
        filteredLogEntries = new FilteredList<>(allLogEntries, p -> true);

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
                            newVal != null && (currentFile == null || !currentFile.getAbsolutePath().equals(newVal.getFilePath()))
                    ) {
                        handleRecentFileSelected(newVal);
                    }
                });

        // Clear recent button
        clearRecentButton.setOnAction(e -> handleClearRecentFiles());
    }

    /**
     * Setup center panel (Log Table)
     */
    private void setupCenterPanel() {
        // Configure table view
        logTableView.setItems(filteredLogEntries);
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

        // Initialize with default columns
        updateTableColumns(null);
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

        TableColumn<LogEntry, Long> lineCol = new TableColumn<>("Line");
        lineCol.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getLineNumber()));
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
            openLogFile(file, true);
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
    private void openLogFile(File file, boolean updateRecentFilesList) {
        currentFile = file;

        currentParsingConfig = parsingConfigService.findDefault().orElse(null);

        if (currentParsingConfig == null) {
            logger.warn("No default parsing config found, creating default");
            currentParsingConfig = new ParsingConfig(
                    "Default",
                    "Default log parsing configuration",
                    "(?<timestamp>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(?<level>\\w+)\\s+(?<message>.*)"
            );
            // Validate the pattern to extract group names
            if (!currentParsingConfig.validatePattern()) {
                logger.error(
                        "Failed to validate default parsing config: {}",
                        currentParsingConfig.getValidationError()
                );
                // Use a simpler pattern as fallback
                currentParsingConfig = new ParsingConfig(
                        "Simple Default",
                        "Simple default configuration",
                        "(?<line>.*)"
                );
                currentParsingConfig.validatePattern();
            }
            currentParsingConfig.setDefault(true);
            parsingConfigService.save(currentParsingConfig);
            logger.info(
                    "Created default parsing config with {} groups",
                    currentParsingConfig.getGroupNames().size()
            );
        }

        String configId = currentParsingConfig.getRegexPattern();
        LogCacheKey key = new LogCacheKey(
                file.getAbsolutePath(),
                file.lastModified(),
                file.length(),
                configId
        );

        LogCacheValue cachedValue = logCache.getIfPresent(key);
        if (cachedValue != null){
            logger.info("Cache hit for file: {}. Loading from memory", file.getName());
            updateStatus("Loading from cache: " + file.getName());

            displayLogEntries(cachedValue, file, updateRecentFilesList);
            return;
        }

        logger.info("Cache miss for file: {}. Parsing...", file.getName());

        // Show progress
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        updateStatus("Loading file: " + file.getName());

        final ParsingConfig configToUse = this.currentParsingConfig;

        // Parse file in background
        Task<List<LogEntry>> task = new Task<>() {
            @Override
            protected List<LogEntry> call() throws Exception {
                return logParserService.parseFile(
                        file,
                        configToUse,
                        new LogParserService.ProgressCallback() {
                            @Override
                            public void onProgress(
                                    double progress,
                                    long currentLine,
                                    long totalLines
                            ) {
                                updateProgress(currentLine, totalLines);
                                Platform.runLater(() -> {
                                    progressBar.setProgress(progress);
                                    updateStatus(
                                            String.format(
                                                    "Loading... %d / %d lines",
                                                    currentLine,
                                                    totalLines
                                            )
                                    );
                                });
                            }

                            @Override
                            public void onComplete(long totalLines) {
                                Platform.runLater(() -> {
                                    progressBar.setVisible(false);
                                    updateStatus(
                                            String.format(
                                                    "Loaded %d lines from %s",
                                                    totalLines,
                                                    file.getName()
                                            )
                                    );
                                });
                            }
                        }
                );
            }
        };

        task.setOnSucceeded(e -> {
            List<LogEntry> entries = task.getValue();
            logger.info("Parsing completed, got {} entries", entries.size());

            LogCacheValue newValue = new LogCacheValue(configToUse, entries);
            logCache.put(key, newValue);
            logger.info("Stored parsing result in cache for key: {}", key);

            allLogEntries.setAll(entries);
            logger.info("Set {} entries to allLogEntries", allLogEntries.size());

            updateTableColumns(currentParsingConfig);
            logger.info("Updated table columns for config: {}", currentParsingConfig.getName());

            // Auto-resize columns to fit content
            Platform.runLater(() -> autoResizeColumns(logTableView));

            // Force table refresh
            logTableView.refresh();
            logger.info("Table refresh called, visible items: {}", logTableView.getItems().size());

            // Add to recent files only if requested
            if (updateRecentFilesList) {
                RecentFile recentFile = new RecentFile(
                        file.getAbsolutePath(),
                        file.getName(),
                        file.length()
                );
                recentFile.setParsingConfig(currentParsingConfig);
                recentFileService.save(recentFile);
                refreshRecentFilesList();
            }

            displayLogEntries(newValue, file, updateRecentFilesList);
            logger.info("Loaded {} log entries from {}, table now shows {} items", entries.size(), file.getName(), logTableView.getItems().size());
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
    private void handleRecentFileSelected(RecentFile recentFile) {
        if (recentFile.isRemote()) {
            // Handle remote file
            showInfo("Remote File", "Opening remote files is not yet implemented in this version.");
        } else {
            // Handle local file
            File file = new File(recentFile.getFilePath());
            if (file.exists()) {
                // Open the file but do not reorder the recent files list
                openLogFile(file, false);
            } else {
                showError("File Not Found", "The file no longer exists: " + recentFile.getFilePath());
            }
        }
    }

    /**
     * Perform search on log entries
     */
    private void performSearch() {
        String searchText = searchField.getText();
        boolean isRegex = regexCheckBox.isSelected();
        boolean caseSensitive = caseSensitiveCheckBox.isSelected();
        boolean hideUnparsed = hideUnparsedCheckBox.isSelected();
        String selectedLevel = logLevelFilterComboBox.getSelectionModel().getSelectedItem();

        updateStatus("Searching...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                Platform.runLater(() -> {
                    filteredLogEntries.setPredicate(entry -> {
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
                    });
                });
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            int resultCount = filteredLogEntries.size();
            updateStatus(String.format("Found %d matching entries", resultCount));
        });

        new Thread(task).start();
    }

    /**
     * Clear search filter
     */
    private void clearSearch() {
        searchField.clear();
        filteredLogEntries.setPredicate(p -> true);
        updateStatus(String.format("Showing all %d entries", allLogEntries.size()));
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
            String extractedJson = JsonPrettifyService.extractJson(remainingText);

            if (extractedJson == null) {
                break; // No more JSON found
            }

            String prettifiedJson = JsonPrettifyService.prettify(extractedJson);

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
            String extractedXml = XmlPrettifyService.extractXml(remainingText);

            if (extractedXml == null) {
                break; // No more XML found
            }

            String prettifiedXml = XmlPrettifyService.prettify(extractedXml);

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
                openLogFile(currentFile, true);
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
        boolean visible = showLeftPanelMenuItem.isSelected();
        String resultVisiblePanel = "";


        if (!visible) {
            // Hide panel
            leftPanel.setVisible(false);
            leftPanel.setManaged(false);

            // Adjust split pane divider to expand center panel
            Platform.runLater(() -> {
                horizontalSplitPane.setDividerPositions(0.0);
            });

            resultVisiblePanel = "false";
        } else {
            // Show panel
            leftPanel.setVisible(true);
            leftPanel.setManaged(true);

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

            resultVisiblePanel = "true";
        }

        preferenceService.saveOrUpdatePreferences(new Preference("left_panel_show", resultVisiblePanel));
    }

    /**
     * Toggle bottom panel visibility
     */
    private void toggleBottomPanel() {
        boolean visible = showBottomPanelMenuItem.isSelected();
        String resultVisiblePanel = "";

        if (!visible) {
            // Hide panel
            bottomPanel.setVisible(false);
            bottomPanel.setManaged(false);

            // Adjust split pane divider to expand center panel
            Platform.runLater(() -> {
                verticalSplitPane.setDividerPositions(1.0);
            });

            resultVisiblePanel = "false";
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

            resultVisiblePanel = "true";
        }

        preferenceService.saveOrUpdatePreferences(new Preference("bottom_panel_show", resultVisiblePanel));
    }

    /**
     * Restore panel visibility from preferences
     */
    private void restorePanelVisibility() {
        boolean leftPanelShow = preferenceService.getPreferencesByCode("left_panel_show")
                .filter(Predicate.not(String::isBlank))
                .map(Boolean::parseBoolean)
                .orElse(true);

        boolean bottomPanelShow = preferenceService.getPreferencesByCode("bottom_panel_show")
                .filter(Predicate.not(String::isBlank))
                .map(Boolean::parseBoolean)
                .orElse(true);

        leftPanel.setVisible(leftPanelShow);
        leftPanel.setManaged(leftPanelShow);
        showLeftPanelMenuItem.setSelected(leftPanelShow);

        bottomPanel.setVisible(bottomPanelShow);
        bottomPanel.setManaged(bottomPanelShow);
        showBottomPanelMenuItem.setSelected(bottomPanelShow);

        // Restore divider positions after scene is shown
        Platform.runLater(() -> {
            if (leftPanelShow) {
                double savedWidth = 200; // Default value
                double totalWidth = horizontalSplitPane.getWidth();
                if (totalWidth > 0 && savedWidth > 0) {
                    double position = savedWidth / totalWidth;
                    horizontalSplitPane.setDividerPositions(position);
                }
            } else {
                horizontalSplitPane.setDividerPositions(0.0);
            }

            if (bottomPanelShow) {
                double savedHeight = 200; // Default value
                double totalHeight = verticalSplitPane.getHeight();
                if (totalHeight > 0 && savedHeight > 0) {
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
    private void displayLogEntries(LogCacheValue cacheValue, File file, boolean updateRecentFilesList) {
        this.currentParsingConfig = cacheValue.config();
        List<LogEntry> entries = cacheValue.entries();

        logger.info("Displaying {} entries for {}", entries.size(), file.getName());

        allLogEntries.setAll(entries);
        updateTableColumns(this.currentParsingConfig);

        Platform.runLater(() -> autoResizeColumns(logTableView));

        logTableView.refresh();

        Platform.runLater(() -> {
            progressBar.setVisible(false);
            updateStatus(
                    String.format(
                            "Loaded %d lines from %s",
                            entries.size(),
                            file.getName()
                    )
            );
        });

        if (updateRecentFilesList) {
            RecentFile recentFile = new RecentFile(
                    file.getAbsolutePath(),
                    file.getName(),
                    file.length()
            );
            recentFile.setParsingConfig(this.currentParsingConfig); // Gunakan config yang dipulihkan
            recentFileService.save(recentFile);
            refreshRecentFilesList();
        }

        logger.info("Loaded {} log entries from {}, table now shows {} items", entries.size(), file.getName(), logTableView.getItems().size());
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
    private static class RecentFileListCell extends ListCell<RecentFile> {

        @Override
        protected void updateItem(RecentFile item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox vbox = new VBox(2);
                Label nameLabel = new Label(item.getFileName());
                nameLabel.setStyle("-fx-font-weight: bold;");
                Label pathLabel = new Label(item.getFullPathDisplay());
                Label sizeLabel = new Label(item.getFormattedFileSize());
                vbox.getChildren().addAll(nameLabel, pathLabel, sizeLabel);
                setGraphic(vbox);
            }
        }
    }
}
