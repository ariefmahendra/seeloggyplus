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
import com.seeloggyplus.util.PasswordPromptDialog;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
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
    private Button tailButton;
    @FXML
    private Button prevWindowButton;
    @FXML
    private Button nextWindowButton;
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
    private HBox collapsedBottomPanel;
    @FXML
    private Button expandBottomPanelButton;
    @FXML
    private Button pinBottomPanelButton;
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
    private ServerManagementService serverManagementService;

    private LogFile currentLogDb;
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
    private static final int WINDOW_SIZE = 5000;
    private int currentWindowStartIndex = 0;
    private boolean tailModeEnabled = false;
    private SSHServiceImpl activeTailSshService;
    private long remoteTailLineCounter = 0;
    private String monitoringRemotePath;
    private final List<LogEntry> tailBuffer = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean tailFlushScheduled = false;
    private boolean tailColumnsAutoResized = false;

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");

        parsingConfigService = new ParsingConfigServiceImpl();
        recentFileService = new RecentConfigServiceImpl();
        preferenceService = new PreferenceServiceImpl();
        logParserService = new LogParserService();
        logFileService = new LogFileServiceImpl();
        serverManagementService = new ServerManagementServiceImpl();

        logFileWatcher = new LogFileWatcher();
        try {
            logFileWatcher.start();
            logger.info("LogFileWatcher started successfully");
        } catch (Exception e) {
            logger.error("Failed to start LogFileWatcher", e);
        }

        visibleLogEntries = FXCollections.observableArrayList();

        setupMenuBar();
        setupLeftPanel();
        setupCenterPanel();
        setupBottomPanel();
        setupLogLevelFilter();
        setupKeyboardShortcuts();

        restorePanelVisibility();

        updateStatus("Ready");
        progressBar.setVisible(false);
    }


    private void setupMenuBar() {
        exitMenuItem.setOnAction(e -> handleExit());

        showLeftPanelMenuItem.setSelected(true);
        showBottomPanelMenuItem.setSelected(true);
        showLeftPanelMenuItem.setOnAction(e -> toggleLeftPanel());
        showBottomPanelMenuItem.setOnAction(e -> toggleBottomPanel());

        autoRefreshMenuItem = new CheckMenuItem("Auto-Refresh");
        autoRefreshMenuItem.setSelected(true);
        autoRefreshMenuItem.setOnAction(e -> toggleAutoRefresh());
        viewMenu.getItems().add(2, autoRefreshMenuItem);

        parsingConfigMenuItem.setOnAction(e -> handleParsingConfiguration());
        serverManagementMenuItem.setOnAction(e -> handleServerManagement());

        aboutMenuItem.setOnAction(e -> handleAbout());
    }

    private void setupLeftPanel() {
        recentFilesListView.setCellFactory(listView -> new RecentFileListCell());
        recentFilesListView.setItems(FXCollections.observableArrayList(recentFileService.findAll()));
        recentFilesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
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

    private void handleToggleLeftPanelPin() {
        isLeftPanelPinned = !isLeftPanelPinned;
        updateLeftPanelDisplay();
    }

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

    private void setupCenterPanel() {
        logTableView.setItems(visibleLogEntries);
        logTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        logTableView.setFixedCellSize(24.0);
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
        prevWindowButton.setOnAction(e -> showPreviousWindow());
        nextWindowButton.setOnAction(e -> showNextWindow());
        tailButton.setOnAction(e -> {
            if (tailModeEnabled) {
                disableTail();
            } else {
                enableTail();
            }
        });

        logTableView.setOnScroll(event -> {
            if (tailModeEnabled && event.getDeltaY() > 0) {
                disableTail();
                logger.info("Tail mode disabled because user scrolled up");
            }
        });

        updateDateTimeFilterPromptText(null);
        updateTableColumns(null);
        logger.info("Virtual scrolling enabled with performance optimizations - smooth scrolling for large datasets");
    }

    private void handleScrollToTop() {
        if (currentLogEntrySource == null || currentLogEntrySource.getTotalEntries() == 0) {
            return;
        }

        int totalAvailable = currentLogEntrySource.getTotalEntries();

        updateStatus(String.format("Jumping to top... Loading first %d entries",
                Math.min(WINDOW_SIZE, totalAvailable)));
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<Void> loadTopTask = new Task<>() {
            @Override
            protected Void call() {
                Platform.runLater(() -> loadWindow(0, false));
                return null;
            }
        };

        loadTopTask.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            int displayedCount = visibleLogEntries.size();
            updateStatus(String.format("At top. Showing first %d of %d entries from %s",
                    displayedCount, totalAvailable,
                    currentFile != null ? currentFile.getName() : ""));
            logger.info("Scrolled to top, showing {} entries (window)", displayedCount);
        });

        loadTopTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            logger.error("Failed to scroll to top", loadTopTask.getException());
            showError("Scroll Error", "Failed to jump to top: " + loadTopTask.getException().getMessage());
            updateStatus("Scroll to top failed.");
        });

        new Thread(loadTopTask).start();
    }

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

    private void handleToggleBottomPanelPin() {
        isBottomPanelPinned = !isBottomPanelPinned;
        updateBottomPanelDisplay();
    }

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
        showBottomPanelMenuItem.setSelected(isBottomPanelPinned);
    }

    private void setupLogLevelFilter() {
        logLevelFilterComboBox.setItems(FXCollections.observableArrayList("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "UNPARSED"));
        logLevelFilterComboBox.getSelectionModel().select("ALL");
    }

    private void setupKeyboardShortcuts() {
        if (menuBar.getScene() == null) {
            Platform.runLater(this::setupKeyboardShortcuts);
            return;
        }

        Scene scene = menuBar.getScene();

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.PAGE_UP),
                this::showPreviousWindow
        );

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.PAGE_DOWN),
                this::showNextWindow
        );
    }

    private void updateTableColumns(ParsingConfig config) {
        logger.info("Updating table columns with config: {}", config != null ? config.getName() : "null");
        logTableView.getColumns().clear();

        TableColumn<LogEntry, String> lineCol = getLogEntryStringTableLineColumn();
        logTableView.getColumns().add(lineCol);

        if (config != null && config.isValid()) {
            List<String> groupNames = config.getGroupNames();
            logger.info("Config has {} named groups: {}", groupNames.size(), groupNames);

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
            rawCol.setSortable(false);
            logTableView.getColumns().add(rawCol);
            logger.info("Created 2 columns (line number + raw log)");
        }
    }

    private int determineUnparsedColumnIndex(List<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            return 0;
        }

        final int size = groupNames.size();
        int partialMatchIndex = -1;

        for (int i = 0; i < size; i++) {
            String name = groupNames.get(i);
            int len = name.length();

            if (len >= 3 && len <= 11) {
                char firstChar = name.charAt(0);

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

        if (partialMatchIndex != -1) {
            return partialMatchIndex;
        }

        String firstName = groupNames.get(0);
        if (firstName.equalsIgnoreCase("level")) {
            return size > 1 ? 1 : 0;
        }

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


    @FXML
    public void handleOpen() {
        try {
            Stage mainStage = (Stage) menuBar.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
            Parent root = loader.load();
            UnifiedFileManagerDialogController controller = loader.getController();

            Stage dialog = new Stage();
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

            ParsingConfig selectedConfig = showParsingConfigSelectionDialog();
            if (selectedConfig == null) {
                logger.info("No parsing configuration selected, operation cancelled.");
                if (sshService != null) {
                    sshService.disconnect();
                }
                return;
            }

            if (selectedFile.getSourceType() == FileInfo.SourceType.LOCAL) {
                openLocalLogFile(new File(selectedFile.getPath()), true, selectedConfig);
                if (sshService != null) {
                    sshService.disconnect();
                }
            } else {
                if (action == UnifiedFileManagerDialogController.OpenAction.OPEN) {
                    openRemoteLogFile(selectedFile, sshService, selectedConfig);
                } else if (action == UnifiedFileManagerDialogController.OpenAction.TAIL) {
                    startRemoteTail(selectedFile.getPath(), sshService, selectedConfig, sshServer);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to open Unified File Manager", e);
            showError("Error Opening File Browser", "Could not open the file browser: " + e.getMessage());
        }
    }

    private void openRemoteLogFile(FileInfo remoteFile, SSHServiceImpl sshService, ParsingConfig parsingConfig) {
        openRemoteLogFile(remoteFile.getPath(), remoteFile.getName(), sshService, parsingConfig);
    }

    private void openRemoteLogFile(String remotePath, String remoteFileName, SSHServiceImpl sshService, ParsingConfig parsingConfig) {
        if (sshService == null || !sshService.isConnected()) {
            showError("Connection Error", "SSH connection is not active. Please re-select the file.");
            return;
        }

        updateStatus("Downloading remote file: " + remoteFileName);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                String tempDir = System.getProperty("java.io.tmpdir");
                String sanitizedName = new File(remoteFileName).getName();
                File localTmpFile = new File(tempDir,
                        "seeloggyplus-" + System.currentTimeMillis() + "-" + sanitizedName);

                logger.info("Downloading remote file {} to temporary path {}",
                        remotePath, localTmpFile.getAbsolutePath());

                boolean success = sshService.downloadFileConcurrent(
                        remotePath,
                        localTmpFile.getAbsolutePath(),
                        4,
                        new LogParserService.ProgressCallback() {
                            @Override
                            public void onProgress(double progress, long bytesProcessed, long totalBytes) {
                                Platform.runLater(() -> {
                                    progressBar.setProgress(progress);
                                    updateStatus(String.format("Downloading... %.0f%% (%s / %s)",
                                            progress * 100,
                                            formatBytes(bytesProcessed),
                                            formatBytes(totalBytes)));
                                });
                            }

                            @Override
                            public void onComplete(long totalEntries) {
                                // no-op
                            }
                        }
                );

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
            openLocalLogFile(localFile, true, parsingConfig);
            sshService.disconnect();
        });

        downloadTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            Throwable ex = downloadTask.getException();
            logger.error("Failed to download remote file", ex);
            showError("Remote File Error", "Failed to download file: " + ex.getMessage());
            updateStatus("Failed to download remote file.");
            sshService.disconnect();
        });

        new Thread(downloadTask).start();
    }

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

        this.currentLogDb = logFile;
        this.currentFile = file;

        long fileSizeInBytes = file.length();
        logger.info("Starting to parse file: {} ({}) with config: {}",
                file.getName(),
                com.seeloggyplus.util.FileUtils.formatFileSize(fileSizeInBytes),
                parsingConfig.getName());

        logger.info("Using parallel parsing strategy with virtual scrolling for optimal performance");
        loadFileWithParallelParsing(file, parsingConfig, logFile, updateRecentFilesList);
    }

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

            updateTableColumns(currentParsingConfig);
            logger.info("Updated table columns for config: {}", currentParsingConfig.getName());

            Platform.runLater(() -> {
                int total = currentLogEntrySource.getTotalEntries();
                loadWindow(Math.max(0, total - WINDOW_SIZE), true);
                logger.info("Initial window loaded after parse");
                autoResizeColumns(logTableView);
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

    private ParsingConfig showParsingConfigSelectionDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ParsingConfigurationSelectionDialog.fxml"));
            DialogPane dialogPane = loader.load();

            ParsingConfigurationSelectionDialogController controller = loader.getController();

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Select Parsing Configuration");
            dialog.initModality(Modality.WINDOW_MODAL);
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

    private void handleParsingConfiguration() {
        try {
            Stage mainStage = (Stage) menuBar.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ParsingConfigDialog.fxml"));
            Parent root = loader.load();

            ParsingConfigController controller = loader.getController();
            controller.setOnConfigChangedCallback(this::handleParsingConfigChanged);

            Stage dialog = new Stage();
            dialog.setTitle("Parsing Configuration");
            dialog.initOwner(mainStage);
            dialog.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(root);
            dialog.setScene(scene);
            dialog.setWidth(1000);
            dialog.setHeight(800);

            dialog.showAndWait();
            logger.info("Parsing config dialog closed, returning to previous view");
        } catch (IOException e) {
            logger.error("Failed to open parsing configuration dialog", e);
            showError("Failed to open parsing configuration", e.getMessage());
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
            restoreWindow(mainStage, wasMaximized, oldX, oldY, oldWidth, oldHeight, root, dialog);

            logger.info("Server management dialog closed");
        } catch (IOException e) {
            logger.error("Failed to open server management dialog", e);
            showError("Failed to open server management", e.getMessage());
        }
    }

    static void restoreWindow(Stage mainStage, boolean wasMaximized, double oldX, double oldY, double oldWidth, double oldHeight, Parent root, Stage dialog) {
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

    private void handleParsingConfigChanged() {
        logger.info("Parsing configuration changed, checking if current file needs re-parsing");

        if (currentFile == null || currentParsingConfig == null) {
            logger.info("No file currently loaded, skipping re-parse");
            return;
        }

        Optional<ParsingConfig> updatedConfigOpt = parsingConfigService.findById(currentParsingConfig.getId());

        if (updatedConfigOpt.isEmpty()) {
            logger.warn("Current parsing config no longer exists in database");
            showInfo("Configuration Deleted",
                    "The current parsing configuration has been deleted. Please reload the file with a new configuration.");
            return;
        }

        ParsingConfig updatedConfig = updatedConfigOpt.get();

        if (configsAreEqual(currentParsingConfig, updatedConfig)) {
            logger.info("Configuration unchanged, no need to re-parse");
            return;
        }

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
            openLocalLogFile(currentFile, false, updatedConfig);
        } else {
            logger.info("User chose not to re-parse file");
            updateStatus("Configuration updated. Reload file to apply changes.");
        }
    }

    private boolean configsAreEqual(ParsingConfig config1, ParsingConfig config2) {
        if (config1 == null || config2 == null) {
            return config1 == config2;
        }

        return Objects.equals(config1.getName(), config2.getName()) &&
                Objects.equals(config1.getRegexPattern(), config2.getRegexPattern()) &&
                Objects.equals(config1.getDescription(), config2.getDescription()) &&
                Objects.equals(config1.getTimestampFormat(), config2.getTimestampFormat());
    }

    private void handleRecentFileSelected(RecentFilesDto recentFile) {
        LogFile logFile = recentFile.logFile();

        if (logFile.isRemote()) {
            String remotePath = logFile.getFilePath();
            String sshServerId = logFile.getSshServerID();

            logger.info("Recent REMOTE file selected: path={}, sshServerId={}, parsing_config_id={}",
                    remotePath,
                    sshServerId,
                    logFile.getParsingConfigurationID());

            if (sshServerId == null || sshServerId.isBlank()) {
                showError("Remote File",
                        "This remote file does not have SSH server information (sshServerID is empty).");
                return;
            }

            SSHServerModel server = serverManagementService.getServerById(sshServerId);
            if (server == null) {
                showError("SSH Server Not Found",
                        "SSH server configuration with ID " + sshServerId + " was not found.\n" +
                                "Please check Server Management.");
                return;
            }

            ParsingConfig parsingConfig = recentFile.parsingConfig();
            if (parsingConfig != null) {
                logger.info("ParsingConfig in method handleRecentFileSelected from DTO - ID: {}, Name: {}, Valid: {}", parsingConfig.getId(), parsingConfig.getName(), parsingConfig.isValid());
            } else {
                logger.info("ParsingConfig in method handleRecentFileSelected from DTO is NULL");
            }

            if (parsingConfig == null) {
                String parsingConfigId = logFile.getParsingConfigurationID();
                parsingConfig = getParsingConfig(parsingConfigId);
            }

            if (parsingConfig == null) {
                logger.warn("No parsing config associated with remote file: {}, showing selection dialog", remotePath);
                parsingConfig = showParsingConfigSelectionDialog();

                if (parsingConfig == null) {
                    logger.info("No parsing configuration selected for recent remote file, operation cancelled");
                    return;
                }
            }

            // --- Connect ke SSH server ---
            String password = server.getPassword();
            if (password == null || password.isBlank()) {
                logger.info("Password for server {} is not saved, prompting user.", server.getName());
                PasswordPromptDialog prompt = new PasswordPromptDialog(server.getHost(), server.getUsername());
                Optional<String> result = prompt.showAndWait();

                if (result.isEmpty() || result.get().isBlank()) {
                    logger.info("User cancelled password prompt for remote recent file.");
                    updateStatus("SSH connection cancelled.");
                    return;
                }
                password = result.get();
            }

            SSHServiceImpl sshService = new SSHServiceImpl();
            updateStatus("Connecting to " + server.getHost() + "...");
            boolean connected;
            try {
                connected = sshService.connect(server.getHost(),
                        server.getPort(),
                        server.getUsername(),
                        password);
            } catch (Exception e) {
                logger.error("Failed to connect to SSH server {} for recent file", server.getName(), e);
                showError("Connection Error", "Could not connect to server: " + e.getMessage());
                return;
            }

            if (!connected) {
                showError("Connection Error", "Could not connect to " + server.getHost());
                return;
            }

            logger.info("SSH connected for remote recent file, opening remote file {}", remotePath);

            resetFilters();
            startRemoteTail(remotePath, sshService, parsingConfig, server);
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
                parsingConfig = getParsingConfig(parsingConfigId);
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
        }
    }

    private ParsingConfig getParsingConfig(String parsingConfigId) {
        logger.info("Attempting to load config from database with ID: {}", parsingConfigId);
        ParsingConfig parsingConfig = null;

        if (parsingConfigId != null && !parsingConfigId.isEmpty()) {
            parsingConfig = parsingConfigService.findById(parsingConfigId).orElse(null);

            if (parsingConfig != null) {
                logger.info("Loaded ParsingConfig from database - ID: {}, Name: {}, Valid: {}",
                        parsingConfig.getId(), parsingConfig.getName(), parsingConfig.isValid());
            } else {
                logger.warn("ParsingConfig with ID {} not found in database", parsingConfigId);
            }
        }
        return parsingConfig;
    }

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

    private void clearDateFilter() {
        logger.info("Clearing date/time filter");
        dateTimeFromField.clear();
        dateTimeToField.clear();
    }

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
                logger.warn("Could not parse date/time string: {}", trimmed);
            }
        }

        try {
            java.time.LocalDate date = java.time.LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return date.atStartOfDay();
        } catch (DateTimeParseException e) {
            logger.warn(e.getMessage());
        }

        logger.warn("Could not parse date/time: {}", trimmed);
        return null;
    }

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
                    }
                }
            }

            String timestampStr = entry.getField("timestamp");
            if (timestampStr != null && !timestampStr.isEmpty()) {
                return parseDateTimeFilter(timestampStr);
            }
        }

        String rawLog = entry.getRawLog();
        if (rawLog != null && rawLog.length() > 19) {
            String possibleTimestamp = rawLog.substring(0, Math.min(23, rawLog.length())); // 23 for milliseconds
            return parseDateTimeFilter(possibleTimestamp);
        }

        return null;
    }

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
                final LocalDateTime filterFrom = parseDateTimeFilter(dateTimeFrom);
                final LocalDateTime filterTo = parseDateTimeFilter(dateTimeTo);
                final boolean hasDateFilter = filterFrom != null || filterTo != null;

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

                final String searchTextLower = (!isRegex && searchText != null && !caseSensitive)
                        ? searchText.toLowerCase() : searchText;
                final boolean hasTextSearch = searchText != null && !searchText.trim().isEmpty();

                final boolean hasLevelFilter = selectedLevel != null && !selectedLevel.equals("ALL");
                final boolean filterUnparsedOnly = "UNPARSED".equals(selectedLevel);

                Predicate<LogEntry> searchPredicate = entry -> {
                    if (hideUnparsed && !entry.isParsed()) {
                        return false;
                    }

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

                    if (hasTextSearch) {
                        if (isRegex) {
                            if (regexPattern == null) {
                                return true;
                            }

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

                Platform.runLater(() -> {
                    currentLogEntrySource = filteredSource;

                    if (totalFiltered == 0) {
                        visibleLogEntries.clear();
                        updateStatus("No matching entries found");
                        return;
                    }

                    loadWindow(0, false);

                    updateStatus(String.format("📍 Found %,d of %,d entries",
                            totalFiltered, originalLogEntrySource.getTotalEntries()));
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

    private void clearSearch() {
        searchField.clear();
        logLevelFilterComboBox.getSelectionModel().select("ALL");
        hideUnparsedCheckBox.setSelected(false);

        if (originalLogEntrySource != null) {
            currentLogEntrySource = originalLogEntrySource;

            int totalEntries = originalLogEntrySource.getTotalEntries();
            loadWindow(Math.max(0, totalEntries - WINDOW_SIZE), true);
        } else {
            visibleLogEntries.clear();
            updateStatus("Search cleared. No file loaded.");
        }
    }

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

    private void copyDetailToClipboard() {
        String text = detailTextArea.getText();
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        updateStatus("Copied to clipboard");
    }

    private void clearDetail() {
        detailTextArea.clear();
        detailLabel.setText("Log Detail");
    }

    private void toggleLeftPanel() {
        isLeftPanelPinned = !isLeftPanelPinned;
        updateLeftPanelDisplay();
        preferenceService.saveOrUpdatePreferences(new Preference("left_panel_pinned", String.valueOf(isLeftPanelPinned)));
    }

    private void toggleBottomPanel() {
        isBottomPanelPinned = !isBottomPanelPinned;
        updateBottomPanelDisplay();
        preferenceService.saveOrUpdatePreferences(new Preference("bottom_panel_pinned", String.valueOf(isBottomPanelPinned)));
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

    private void scrollToBottomAfterLoad() {
        if (currentLogEntrySource == null) {
            return;
        }

        int totalEntries = currentLogEntrySource.getTotalEntries();
        logger.info("Scroll to bottom using windowing (total={})", totalEntries);

        loadWindow(Math.max(0, totalEntries - WINDOW_SIZE), true);
    }

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

    private void handleAutoRefresh(File file) {
        if (!autoRefreshMenuItem.isSelected()) {
            return;
        }

        if (!tailModeEnabled) {
            logger.info("File changed, but tail mode is OFF. Skipping auto-refresh.");
            return;
        }

        logger.info("Auto-refreshing file: {}", file.getName());
        updateStatus("Auto-refreshing... (file changed)");
        if (currentParsingConfig != null) {
            openLocalLogFile(file, false, currentParsingConfig);
        }
    }

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

    private void handleClearRecentFiles() {
      ObservableList<RecentFilesDto> selected = recentFilesListView.getSelectionModel().getSelectedItems();

      if (selected != null && !selected.isEmpty()){
          int count = selected.size();

          Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
          alert.setTitle("Remove Selected Recent Files");
          alert.setHeaderText("Remove "+count+" selected recent file(s)?");
          alert.setContentText("This action cannot be undone.");
          Optional<ButtonType> result = showAndWaitAndRestore(alert);
          if (result.isPresent() && result.get() == ButtonType.OK){
              List<RecentFilesDto> listToDeleteRecentFiles = List.copyOf(selected);

              for (RecentFilesDto recentFilesDto : listToDeleteRecentFiles){
                  LogFile logFile = recentFilesDto.logFile();

                  if (logFile.isRemote() && monitoringRemotePath != null && monitoringRemotePath.equals(logFile.getFilePath())){
                      stopRemoteTail();
                  }

                  recentFileService.deleteByFileId(logFile.getId());
                  logFileService.deleteLogFileById(logFile.getId());
              }

              refreshRecentFilesList();
              cleanupTempFiles();
              updateStatus("Removed " + count + " selected recent file(s)");
          }
          return;
      }

      if (recentFilesListView.getItems().isEmpty()){
          updateStatus("No recent files to remove");
          return;
      }

      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      alert.setTitle("Clear Recent Files");
      alert.setHeaderText("Clear ALL recent files?");
      alert.setContentText("This action cannot be undone.");

      Optional<ButtonType> result = showAndWaitAndRestore(alert);
      if (result.isPresent() && result.get() == ButtonType.OK){
          recentFileService.deleteAll();
          logFileService.deleteAllLogFiles();
          logTableView.getItems().clear();
          stopRemoteTail();
          clearSearch();
          refreshRecentFilesList();
          cleanupTempFiles();
          updateStatus("All recent files cleared");
      }
    }

    private void refreshRecentFilesList() {
        recentFilesListView.setItems(
                FXCollections.observableArrayList(recentFileService.findAll())
        );
    }

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
                        
                        © 2025 SeeLoggyPlus"""
        );
        showAndWaitAndRestore(alert);
    }

    private void handleExit() {
        // Cleanup file watcher
        if (logFileWatcher != null) {
            logFileWatcher.stop();
            logger.info("LogFileWatcher stopped on application exit");
        }
        Platform.exit();
    }

    private void loadWindow(int startIndex, boolean scrollToBottom) {
        if (currentLogEntrySource == null) {
            return;
        }

        int total = currentLogEntrySource.getTotalEntries();
        if (total == 0) {
            visibleLogEntries.clear();
            updateStatus("No entries to display");
            return;
        }

        int from = Math.max(0, startIndex);
        if (from >= total) {
            from = Math.max(0, total - WINDOW_SIZE);
        }

        int to = Math.min(from + WINDOW_SIZE, total);
        int limit = to - from;

        currentWindowStartIndex = from;

        List<LogEntry> windowEntries = currentLogEntrySource.getEntries(from, limit);
        visibleLogEntries.setAll(windowEntries);

        if (!visibleLogEntries.isEmpty()) {
            if (scrollToBottom) {
                logTableView.scrollTo(visibleLogEntries.size() - 1);
            } else {
                logTableView.scrollTo(0);
            }
        }

        updateStatus(String.format(
                "Showing %,d–%,d of %,d entries%s",
                from + 1, to, total,
                currentFile != null ? " from " + currentFile.getName() : ""
        ));
    }

    private void showPreviousWindow() {
        if (currentLogEntrySource == null) {
            return;
        }

        int total = currentLogEntrySource.getTotalEntries();
        if (total == 0) {
            return;
        }

        int newStart = currentWindowStartIndex - WINDOW_SIZE;
        if (newStart < 0) {
            newStart = 0;
        }

        logger.info("Show previous window: startIndex={} (before={})", newStart, currentWindowStartIndex);
        loadWindow(newStart, false); // scroll ke atas window
    }

    private void showNextWindow() {
        if (currentLogEntrySource == null) {
            return;
        }

        int total = currentLogEntrySource.getTotalEntries();
        if (total == 0) {
            return;
        }

        int newStart = currentWindowStartIndex + WINDOW_SIZE;
        if (newStart >= total) {
            newStart = Math.max(0, total - WINDOW_SIZE);
        }

        logger.info("Show next window: startIndex={} (before={})", newStart, currentWindowStartIndex);
        loadWindow(newStart, false);
    }

    private void enableTail() {
        if (currentLogDb != null && currentLogDb.isRemote()) {
            String sshServerId = currentLogDb.getSshServerID();
            if (sshServerId == null || sshServerId.isBlank()) {
                showError("Tail Error",
                        "Current log is marked as remote, but SSH server ID is missing.");
                return;
            }

            SSHServerModel server = serverManagementService.getServerById(sshServerId);
            if (server == null) {
                showError("Tail Error",
                        "SSH server with ID " + sshServerId + " not found. Please check Server Management.");
                return;
            }

            if (currentParsingConfig == null) {
                String cfgId = currentLogDb.getParsingConfigurationID();
                if (cfgId != null && !cfgId.isBlank()) {
                    currentParsingConfig = parsingConfigService.findById(cfgId).orElse(null);
                }
            }

            if (currentParsingConfig == null) {
                showError("Tail Error",
                        "No parsing configuration available for this log. Please select one first.");
                return;
            }

            // connect SSH
            String password = server.getPassword();
            if (password == null || password.isBlank()) {
                PasswordPromptDialog prompt = new PasswordPromptDialog(server.getHost(), server.getUsername());
                Optional<String> result = prompt.showAndWait();
                if (result.isEmpty() || result.get().isBlank()) {
                    updateStatus("SSH connection cancelled.");
                    return;
                }
                password = result.get();
            }

            SSHServiceImpl sshService = activeTailSshService;
            if (sshService == null || !sshService.isConnected()) {
                sshService = new SSHServiceImpl();
                boolean connected;
                try {
                    connected = sshService.connect(server.getHost(),
                            server.getPort(),
                            server.getUsername(),
                            password);
                } catch (Exception e) {
                    logger.error("Failed to connect SSH in enableTail()", e);
                    showError("Tail Error", "Could not connect to SSH server: " + e.getMessage());
                    return;
                }
                if (!connected) {
                    showError("Tail Error", "Could not connect to SSH server " + server.getHost());
                    return;
                }
            }

            tailModeEnabled = true;
            tailButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
            logger.info("Tail mode ENABLED for remote log: {}", currentLogDb.getFilePath());
            updateStatus("Remote tail enabled (monitoring) for " + currentLogDb.getName());

            startRemoteTail(currentLogDb.getFilePath(), sshService, currentParsingConfig, server);
            return;
        }


        if (currentLogEntrySource == null) {
            showInfo("Tail Mode", "No file loaded. Open a log file first.");
            return;
        }

        tailModeEnabled = true;
        tailButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        int total = currentLogEntrySource.getTotalEntries();
        int startIndex = Math.max(0, total - WINDOW_SIZE);

        logger.info("Tail mode ENABLED (LOCAL). Showing last window from index {}", startIndex);
        loadWindow(startIndex, true);

        updateStatus("Tail mode enabled (following last entries like 'tail -f')");
    }

    private void disableTail() {
        tailModeEnabled = false;
        tailButton.setStyle("");

        stopRemoteTail();

        updateStatus("Tail mode disabled");
        logger.info("Tail mode DISABLED");
    }

    private void startRemoteTail(String remotePath,
                                 SSHServiceImpl sshService,
                                 ParsingConfig parsingConfig,
                                 SSHServerModel server) {
        stopRemoteTail();

        saveRemoteTailToRecent(remotePath, parsingConfig, server);

        this.activeTailSshService = sshService;
        this.remoteTailLineCounter = 0;
        this.currentParsingConfig = parsingConfig;
        this.currentFile = null;
        this.originalLogEntrySource = null;
        this.currentLogEntrySource = null;
        this.tailColumnsAutoResized = false;

        updateTableColumns(parsingConfig);
        visibleLogEntries.clear();

        tailModeEnabled = true;
        tailButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        updateStatus("Starting remote tail (parsed): " + remotePath);

        sshService.tailFile(
                remotePath,
                WINDOW_SIZE,
                line -> handleTailLineBackground(line, parsingConfig),
                error -> Platform.runLater(() -> {
                    logger.error("Remote tail error: {}", error);
                    showError("Remote Tail Error", error);
                    disableTail();
                })
        );
    }

    private void saveRemoteTailToRecent(String remotePath, ParsingConfig parsingConfig, SSHServerModel server) {
        try {
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
                logger.info("Remote tail for existing recent fileId={}, NOT updating lastOpened (no re-sort)", logFile.getId());
            }

            this.currentLogDb = logFile;
            this.monitoringRemotePath = remotePath;

            Platform.runLater(() -> {
                if (createdNewRecent) {
                    refreshRecentFilesList();
                } else {
                    recentFilesListView.refresh();
                }
            });
        } catch (Exception e) {
            logger.error("Failed to save remote tail to recent for path {}", remotePath, e);
        }
    }

    private void handleTailLineBackground(String line, ParsingConfig parsingConfig) {
        long lineNumber = ++remoteTailLineCounter;

        LogEntry entry = logParserService.parseLine(line, lineNumber, parsingConfig);

        synchronized (tailBuffer) {
            tailBuffer.add(entry);
        }

        scheduleTailFlush();
    }

    private void scheduleTailFlush() {
        if (tailFlushScheduled) return;
        tailFlushScheduled = true;

        Platform.runLater(() -> {
            tailFlushScheduled = false;

            List<LogEntry> toAdd;
            synchronized (tailBuffer) {
                if (tailBuffer.isEmpty()) return;
                toAdd = new ArrayList<>(tailBuffer);
                tailBuffer.clear();
            }

            visibleLogEntries.addAll(toAdd);

            int overflow = visibleLogEntries.size() - WINDOW_SIZE;
            if (overflow > 0) {
                visibleLogEntries.remove(0, overflow);
            }

            if (!visibleLogEntries.isEmpty()) {
                int lastIndex = visibleLogEntries.size() - 1;
                logTableView.scrollTo(lastIndex);

                LogEntry last = visibleLogEntries.get(lastIndex);
                detailLabel.setText("Remote Tail - Line " + last.getLineNumber());
            }

            if (!tailColumnsAutoResized && !visibleLogEntries.isEmpty()) {
                tailColumnsAutoResized = true;
                autoResizeColumns(logTableView);
                logger.info("Auto-resize columns after first tail batch");
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
        try {
            String tmpDirPath = System.getProperty("java.io.tmpdir");
            File tmpDir = new File(tmpDirPath);

            if (!tmpDir.exists() || !tmpDir.isDirectory()) {
                logger.warn("Temp directory does not exist or is not a directory: {}", tmpDirPath);
                return;
            }

            File[] files = tmpDir.listFiles((dir, name) -> name.startsWith("seeloggyplus-"));
            if (files == null || files.length == 0) {
                logger.info("No seeloggyplus temp files to delete in {}", tmpDirPath);
                return;
            }

            int successCount = 0;
            int failCount = 0;

            for (File f : files) {
                try {
                    if (f.delete()) {
                        successCount++;
                        logger.info("Deleted temp file: {}", f.getAbsolutePath());
                    } else {
                        failCount++;
                        logger.warn("Failed to delete temp file: {}", f.getAbsolutePath());
                    }
                } catch (Exception ex) {
                    failCount++;
                    logger.error("Error deleting temp file: {}", f.getAbsolutePath(), ex);
                }
            }

            logger.info("Temp cleanup completed. Deleted: {}, Failed: {}, Dir: {}",
                    successCount, failCount, tmpDirPath);
        } catch (Exception e) {
            logger.error("Error while cleaning up temp files", e);
        }
    }


    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
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

            logger.debug("Column '{}': width = {}", col.getText(), (int) newWidth);
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Auto-fit completed in {}ms", duration);
    }

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

    private class RecentFileListCell extends ListCell<RecentFilesDto> {
        @Override
        protected void updateItem(RecentFilesDto item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox vbox = new VBox(2);
                LogFile logFile = item.logFile();

                String displayName = logFile.getName();
                if (logFile.isRemote()
                        && monitoringRemotePath != null
                        && monitoringRemotePath.equals(logFile.getFilePath())) {
                    displayName = displayName + " (Monitoring)";
                }

                Label nameLabel = new Label(displayName);
                nameLabel.setStyle("-fx-font-weight: bold;");

                Label serverLabel = null;
                if (logFile.isRemote()) {
                    String serverNameText = "Server: -";
                    String serverId = logFile.getSshServerID();
                    if (serverId != null && !serverId.isBlank()) {
                        try {
                            SSHServerModel server = serverManagementService.getServerById(serverId);
                            if (server != null) {
                                serverNameText = "Server: " + server.getName();
                            } else {
                                serverNameText = "Server: (not found: " + serverId + ")";
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to load server name for id={}", serverId, e);
                            serverNameText = "Server: (error)";
                        }
                    }
                    serverLabel = new Label(serverNameText);
                    serverLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
                }

                Label pathLabel = new Label(logFile.getFilePath());
                Label sizeLabel = new Label(logFile.getSize());

                if (serverLabel != null) {
                    vbox.getChildren().addAll(nameLabel, serverLabel, pathLabel, sizeLabel);
                } else {
                    vbox.getChildren().addAll(nameLabel, pathLabel, sizeLabel);
                }

                setGraphic(vbox);
            }
        }
    }

    private static class UnparsedContentCell extends TableCell<LogEntry, String> {
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

        private String formatUnparsedContent(String rawContent) {
            if (rawContent == null || rawContent.isEmpty()) {
                return rawContent;
            }

            int maxChars = 400;
            if (rawContent.length() <= maxChars) {
                return rawContent;
            }

            return rawContent.substring(0, maxChars) + "...";
        }
    }
}
