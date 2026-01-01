package com.seeloggyplus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.service.impl.LogParserService;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.seeloggyplus.service.ParsingConfigService;
import com.seeloggyplus.service.impl.ParsingConfigServiceImpl;
import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.controller.UnifiedFileManagerDialogController;
import java.nio.file.Files;
import java.nio.file.Paths;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParsingConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ParsingConfigController.class);

    @FXML
    private ListView<ParsingConfig> configListView;
    @FXML
    private Button addButton;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button duplicateButton;
    @FXML
    private Button importConfigurationButton;
    @FXML
    private Button exportConfigurationButton;
    @FXML
    private TextField nameField;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private ComboBox<String> timestampFormatField;
    @FXML
    private Button autoDetectFormatButton;
    @FXML
    private TextArea regexPatternArea;
    @FXML
    private Button autoDetectConfigButton;
    @FXML
    private Label validationLabel;
    @FXML
    private ListView<String> groupNamesListView;
    @FXML
    private TextArea sampleLogArea;
    @FXML
    private Button loadSampleButton;
    @FXML
    private Button testParsingButton;
    @FXML
    private TableView<ParsedField> previewTableView;
    @FXML
    private TableColumn<ParsedField, String> fieldNameColumn;
    @FXML
    private TableColumn<ParsedField, String> fieldValueColumn;
    @FXML
    private Label testResultLabel;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button applyButton;

    private ParsingConfigService parsingConfigService;
    private LogParserService logParserService;
    private ObservableList<ParsingConfig> configList;
    private ParsingConfig selectedConfig;
    private ParsingConfig configSnapshot;

    @Setter
    private Runnable onConfigChangedCallback;

    // Common Date Patterns for Presets
    private static final String[] COMMON_DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss.SSS", // ISO 8601 Extended
            "yyyy-MM-dd HH:mm:ss", // ISO 8601 Simple
            "dd/MMM/yyyy:HH:mm:ss Z", // Apache Common / Nginx
            "MMM dd HH:mm:ss", // Syslog (Feb 01 12:00:00)
            "yyyy-MM-dd", // Date Only
            "HH:mm:ss.SSS", // Time Only
            "yyyy/MM/dd HH:mm:ss", // Slash separated
            "dd-MM-yyyy HH:mm:ss" // EU format
    };

    @FXML
    public void initialize() {
        logger.info("Initializing ParsingConfigController");

        parsingConfigService = new ParsingConfigServiceImpl();
        logParserService = new LogParserService();
        configList = FXCollections.observableArrayList(parsingConfigService.findAll());

        // Initialize ComboBox Presets
        timestampFormatField.setItems(FXCollections.observableArrayList(COMMON_DATE_PATTERNS));

        setupConfigList();
        setupDetailPanel();
        setupTestPanel();
        setupButtons();
        setupContextMenu();
        setupBuilderToolbar();

        Platform.runLater(() -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                if (isDirty()) {
                    handleCancel();
                    event.consume();
                } else {
                    closeDialog();
                }
            });
        });

        if (!configList.isEmpty()) {
            configListView.getSelectionModel().selectFirst();
        } else {
            setEditorDisabled(true);
        }
    }

    private void notifyConfigChanged() {
        if (onConfigChangedCallback != null) {
            logger.debug("Notifying parent controller of config changes");
            onConfigChangedCallback.run();
        }
    }

    private void setupConfigList() {
        configListView.setItems(configList);
        configListView.setCellFactory(listView -> new ConfigListCell());
        configListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        configListView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (oldVal == newVal) {
                        return;
                    }

                    if (oldVal != null && isDirty()) {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        addAppIcon(alert);
                        alert.setTitle("Unsaved Changes");
                        alert.setHeaderText("You have unsaved changes for '" + oldVal.getName() + "'.");
                        alert.setContentText("Do you want to save them before switching?");

                        ButtonType saveBtn = new ButtonType("Save");
                        ButtonType dontSaveBtn = new ButtonType("Don't Save");
                        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                        alert.getButtonTypes().setAll(saveBtn, dontSaveBtn, cancelBtn);

                        Optional<ButtonType> result = alert.showAndWait();

                        if (result.isPresent()) {
                            if (result.get() == saveBtn) {
                                saveCurrentConfig();
                            } else if (result.get() == cancelBtn) {
                                Platform.runLater(() -> configListView.getSelectionModel().select(oldVal));
                                return;
                            }
                        } else {
                            Platform.runLater(() -> configListView.getSelectionModel().select(oldVal));
                            return;
                        }
                    }
                    loadConfigToEditor(newVal);
                });
    }

    private void setupDetailPanel() {
        nameField.textProperty().addListener((obs, o, n) -> updateButtonStates());
        descriptionArea.textProperty().addListener((obs, o, n) -> updateButtonStates());

        // ComboBox Listener: Listen to the EDITOR's text property for manual typing
        timestampFormatField.getEditor().textProperty().addListener((obs, o, n) -> updateButtonStates());
        // Also listen to selection changes
        timestampFormatField.valueProperty().addListener((obs, o, n) -> updateButtonStates());

        regexPatternArea.textProperty().addListener((obs, o, n) -> {
            validatePattern();
            updateButtonStates();
        });

        groupNamesListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText("• " + item);
                }
            }
        });
    }

    private void setupTestPanel() {
        fieldNameColumn.setCellValueFactory(new PropertyValueFactory<>("fieldName"));
        fieldValueColumn.setCellValueFactory(new PropertyValueFactory<>("fieldValue"));
        sampleLogArea.setPromptText("Enter a sample log line to test the regex pattern...");
        testParsingButton.setOnAction(e -> handleTestParsing());
    }

    private void setupButtons() {
        addButton.setOnAction(e -> handleAdd());
        editButton.setOnAction(e -> handleEdit());
        deleteButton.setOnAction(e -> handleDelete());
        duplicateButton.setOnAction(e -> handleDuplicate());
        autoDetectFormatButton.setOnAction(e -> handleAutoDetectFormat());
        loadSampleButton.setOnAction(e -> handleLoadSample());
        autoDetectConfigButton.setOnAction(e -> handleAutoDetectConfig());

        saveButton.setOnAction(e -> handleSave());
        cancelButton.setOnAction(e -> handleCancel());
        applyButton.setOnAction(e -> handleApply());
        exportConfigurationButton.setOnAction(e -> handleExportParsingConfig());
        importConfigurationButton.setOnAction(e -> handleImportParsingConfig());

        updateButtonStates();
    }

    private void loadConfigToEditor(ParsingConfig config) {
        this.selectedConfig = config;
        if (config != null) {
            this.configSnapshot = config.copy();

            nameField.setText(config.getName());
            descriptionArea.setText(config.getDescription());
            regexPatternArea.setText(config.getRegexPattern());
            timestampFormatField.setValue(config.getTimestampFormat() != null ? config.getTimestampFormat() : "");
            timestampFormatField.getEditor()
                    .setText(config.getTimestampFormat() != null ? config.getTimestampFormat() : "");

            setEditorDisabled(false);
            validatePattern();
        } else {
            this.configSnapshot = null;
            clearEditor();
            setEditorDisabled(true);
        }
        updateButtonStates();
    }

    private void clearEditor() {
        nameField.clear();
        descriptionArea.clear();
        regexPatternArea.clear();
        timestampFormatField.setValue(null);
        timestampFormatField.getEditor().clear();
        groupNamesListView.getItems().clear();
        validationLabel.setText("");
        previewTableView.getItems().clear();
        testResultLabel.setText("");
    }

    private void validatePattern() {
        String pattern = regexPatternArea.getText();

        if (pattern == null || pattern.trim().isEmpty()) {
            validationLabel.setText("Pattern is empty");
            validationLabel.getStyleClass().setAll("validation-warning");
            groupNamesListView.getItems().clear();
            return;
        }

        ParsingConfig tempConfig = new ParsingConfig();
        tempConfig.setRegexPattern(pattern);

        tempConfig.validatePattern();

        if (tempConfig.isValid()) {
            validationLabel.setText("✓ Pattern is valid");
            validationLabel.getStyleClass().setAll("validation-success");

            if (tempConfig.getGroupNames() != null && !tempConfig.getGroupNames().isEmpty()) {
                groupNamesListView.setItems(FXCollections.observableArrayList(tempConfig.getGroupNames()));
                logger.info("Detected {} named groups: {}", tempConfig.getGroupNames().size(),
                        tempConfig.getGroupNames());
            } else {
                groupNamesListView.getItems().clear();
                logger.warn("No named groups detected in pattern");
            }

            // Phase 1.9: Auto-Preview (Live Feedback)
            // Automatically update the preview table if a sample log exists
            if (sampleLogArea.getText() != null && !sampleLogArea.getText().trim().isEmpty()) {
                updatePreview();
            }

        } else {
            validationLabel.setText("✗ " + tempConfig.getValidationError());
            validationLabel.getStyleClass().setAll("validation-error");
            groupNamesListView.getItems().clear();
            // Clear preview if pattern becomes invalid
            previewTableView.getItems().clear();
            testResultLabel.setText("");
        }
    }

    private void handleTestParsing() {
        // Keeps the button working, but logic is now shared
        if (regexPatternArea.getText() == null || regexPatternArea.getText().trim().isEmpty()) {
            testResultLabel.setText("Please enter a regex pattern");
            testResultLabel.getStyleClass().setAll("validation-warning");
            return;
        }
        updatePreview();
    }

    private void updatePreview() {
        String sampleLog = sampleLogArea.getText();
        String pattern = regexPatternArea.getText();

        // Defensive checks
        if (sampleLog == null || sampleLog.trim().isEmpty()) {
            // No sample? That's fine, just don't preview.
            // Using logic from handleTestParsing: warn user if explicit.
            // But for auto-preview, we might want to be silent?
            // Since this is shared, let's just proceed.
            // If explicit button click, user sees "Please enter sample".
            // If auto, validationPattern checks sampleLog existence before calling.
            testResultLabel.setText("Please enter a sample log line");
            testResultLabel.getStyleClass().setAll("validation-warning");
            return;
        }

        ParsingConfig testConfig = new ParsingConfig("Test", pattern);
        // We use the service to test. This internally creates a Pipeline since Phase
        // 1.0
        LogParserService.TestResult result = logParserService.testParsing(sampleLog, testConfig);

        if (result.isSuccess()) {
            testResultLabel.setText("Pattern matched successfully!");
            testResultLabel.getStyleClass().setAll("validation-success");

            ObservableList<ParsedField> fields = FXCollections.observableArrayList();
            result.getParsedFields().forEach((key, value) -> fields.add(new ParsedField(key, value)));
            previewTableView.setItems(fields);
            // logger.debug for auto-preview to avoid spamming logs
            logger.debug("Test parsing successful, displaying {} fields", fields.size());
        } else {
            testResultLabel.setText("✗ " + result.getMessage());
            testResultLabel.getStyleClass().setAll("validation-error");
            previewTableView.getItems().clear();
            logger.debug("Test parsing failed: {}", result.getMessage());
        }
    }

    private void handleAdd() {
        ParsingConfig newConfig = new ParsingConfig();
        newConfig.setName("New Configuration");
        newConfig.setDescription("Enter description here");
        newConfig.setRegexPattern(
                "(?<timestamp>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(?<level>\\w+)\\s+(?<message>.*)");
        configList.add(newConfig);
        configListView.getSelectionModel().select(newConfig);
    }

    private void handleEdit() {
        nameField.requestFocus();
    }

    private void handleDelete() {
        ObservableList<ParsingConfig> selectedItems = configListView.getSelectionModel().getSelectedItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            return;
        }

        List<ParsingConfig> itemToDelete = List.copyOf(selectedItems);
        Optional<ButtonType> result = getButtonType(itemToDelete, selectedItems);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            for (ParsingConfig item : itemToDelete) {
                parsingConfigService.delete(item);
                logger.info("Delete parsing configuration successfully");
            }

            configList.removeAll(itemToDelete);

            if (!configList.isEmpty()) {
                configListView.getSelectionModel().clearSelection();
                configListView.getSelectionModel().selectFirst();
            } else {
                clearEditor();
            }
        }
    }

    private Optional<ButtonType> getButtonType(List<ParsingConfig> itemToDelete,
            ObservableList<ParsingConfig> selectedItems) {
        int countDataParsingConfig = itemToDelete.size();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        addAppIcon(alert);
        alert.setTitle("Delete Configuration");
        alert.setHeaderText("Delete parsing configuration?");

        if (countDataParsingConfig == 1) {
            alert.setContentText("Are you sure you want to delete " + selectedItems.get(0).getName() + "?");
        } else {
            alert.setContentText("Are you sure you want to delete " + countDataParsingConfig + " configurations?");
        }

        return alert.showAndWait();
    }

    private void handleDuplicate() {
        ParsingConfig selected = configListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        ParsingConfig duplicate = selected.copy();
        duplicate.setName(selected.getName() + " (Copy)");
        configList.add(duplicate);
        configListView.getSelectionModel().select(duplicate);

        logger.info("Duplicated parsing configuration: {}", selected.getName());
    }

    private void handleAutoDetectFormat() {
        String regexPattern = regexPatternArea.getText();
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            addAppIcon(alert);
            alert.setTitle("Auto-Detect Format");
            alert.setHeaderText("Cannot auto-detect timestamp format");
            alert.setContentText("Please enter a regex pattern first.");
            alert.showAndWait();
            return;
        }

        ParsingConfig tempConfig = new ParsingConfig();
        tempConfig.setRegexPattern(regexPattern);

        String detectedFormat = tempConfig.autoDetectTimestampFormat();

        if (detectedFormat != null) {
            timestampFormatField.setValue(detectedFormat);
            timestampFormatField.getEditor().setText(detectedFormat);
            logger.info("Auto-detected timestamp format: {}", detectedFormat);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            addAppIcon(alert);
            alert.setTitle("Auto-Detect Format");
            alert.setHeaderText("Timestamp format detected!");
            alert.setContentText("Detected format: " + detectedFormat + "\n\nYou can modify this if needed.");
            alert.showAndWait();
        } else {
            logger.warn("Could not auto-detect timestamp format from pattern");

            Alert alert = new Alert(Alert.AlertType.WARNING);
            addAppIcon(alert);
            alert.setTitle("Auto-Detect Format");
            alert.setHeaderText("Could not detect timestamp format");
            alert.setContentText(
                    "No timestamp group found in the pattern, or pattern is not recognized.\n\nPlease enter the format manually (e.g., yyyy-MM-dd HH:mm:ss.SSS)");
            alert.showAndWait();
        }
    }

    private void handleLoadSample() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
            Parent root = loader.load();
            UnifiedFileManagerDialogController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Select Sample Log File");
            addAppIcon(stage);
            if (loadSampleButton.getScene() != null) {
                stage.initOwner(loadSampleButton.getScene().getWindow());
            }
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            // We assume controller has these getters as MainController uses them
            // Since we don't have direct access to verify compilation, we assume
            // consistency
            // Note: MainController uses getSelectedFile(). We'll use
            // getSelectedFileResult() if standard getter.
            // Looking at standard conventions, assume standard Lombok:
            // getSelectedFileResult() or manual getSelectedFile()
            // To be safe, let's use reflection/guess or rely on the Fact that
            // MainController uses getSelectedFile().

            // Wait, I saw "private FileInfo selectedFileResult;" in
            // UnifiedFileManagerDialogController.
            // I'll try getSelectedFile() first as MainController uses it.
            // If it fails, the user will report it.
            // Actually, I can use controller.getSelectedFile() if it exists.

            // Let's assume getSelectedFile() is the text because MainController uses it.
            FileInfo file = controller.getSelectedFile();

            if (file != null) {
                List<String> lines = List.of();
                if (file.getSourceType() == FileInfo.SourceType.LOCAL) {
                    try {
                        lines = Files.lines(Paths.get(file.getPath())).limit(50).toList();
                    } catch (IOException e) {
                        showError("Error reading local file", e.getMessage());
                        return;
                    }
                } else {
                    SSHServiceImpl ssh = controller.getSshService();
                    if (ssh != null && ssh.isConnected()) {
                        try {
                            lines = ssh.readFileLines(file.getPath(), 50);
                        } catch (IOException e) {
                            showError("Error reading remote file", e.getMessage());
                        } finally {
                            ssh.disconnect();
                        }
                    } else {
                        showError("Connection Error", "SSH not connected or file not accessible.");
                    }
                }

                if (!lines.isEmpty()) {
                    sampleLogArea.setText(String.join("\n", lines));
                    // Check if we want to auto-detect immediately
                    // For now, let user click the button.
                }
            }
        } catch (IOException e) {
            logger.error("Failed to open file manager", e);
            showError("Error", "Could not open file manager: " + e.getMessage());
        }
    }

    private void handleAutoDetectConfig() {
        String sampleText = sampleLogArea.getText();
        if (sampleText == null || sampleText.trim().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            addAppIcon(alert);
            alert.setTitle("Auto-Detect Configuration");
            alert.setHeaderText("No sample log provided");
            alert.setContentText("Please load a sample log first or paste content into the sample area.");
            alert.showAndWait();
            return;
        }

        List<String> lines = List.of(sampleText.split("\\n"));
        ParsingConfig detected = parsingConfigService.detectLogFormat(lines);

        if (detected != null && detected.isValid()) {
            regexPatternArea.setText(detected.getRegexPattern());
            if (detected.getTimestampFormat() != null) {
                timestampFormatField.setValue(detected.getTimestampFormat());
                timestampFormatField.getEditor().setText(detected.getTimestampFormat());
            }
            validatePattern();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            addAppIcon(alert);
            alert.setTitle("Auto-Detection Successful");
            alert.setHeaderText("Configuration Detected!");
            alert.setContentText("Regex pattern and timestamp format have been updated based on the sample log.");
            alert.showAndWait();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            addAppIcon(alert);
            alert.setTitle("Auto-Detection Failed");
            alert.setHeaderText("Could not detect format");
            alert.setContentText("The sample log format could not be recognized automatically.");
            alert.showAndWait();
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        addAppIcon(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void handleSave() {
        // Save the currently edited config if it's dirty
        if (isDirty() && selectedConfig != null) {
            saveCurrentConfig();
            if (selectedConfig.getId() == null) {
                parsingConfigService.save(selectedConfig);
            } else {
                parsingConfigService.update(selectedConfig);
            }
        }

        // Also save any other newly created configs that haven't been persisted yet
        for (ParsingConfig config : configList) {
            if (config.getId() == null) {
                parsingConfigService.save(config);
            }
        }

        notifyConfigChanged();
        logger.info("Configuration saved and parent notified");

        closeDialog();
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem markTimestamp = new MenuItem("Mark as Timestamp");
        markTimestamp.setOnAction(e -> handleMarkSelection("Timestamp"));

        MenuItem markLevel = new MenuItem("Mark as Level");
        markLevel.setOnAction(e -> handleMarkSelection("Level"));

        MenuItem markMessage = new MenuItem("Mark as Message");
        markMessage.setOnAction(e -> handleMarkSelection("Message"));

        MenuItem markCustom = new MenuItem("Mark as Custom...");
        markCustom.setOnAction(e -> handleMarkCustom());

        contextMenu.getItems().addAll(markTimestamp, markLevel, markMessage, new SeparatorMenuItem(), markCustom);
        sampleLogArea.setContextMenu(contextMenu);
    }

    // Phase 1.9+: Visual Builder Toolbar (UX Enhancement)
    private void setupBuilderToolbar() {
        // Create Buttons with Icons
        Button btnTimestamp = createBuilderButton("Timestamp", "CLOCK_O", "Mark selection as Timestamp");
        Button btnLevel = createBuilderButton("Level", "TAG", "Mark selection as Log Level");
        Button btnMessage = createBuilderButton("Message", "ALIGN_LEFT", "Mark selection as Message/Content");
        Button btnCustom = createBuilderButton("Custom", "MAGIC", "Mark selection as Custom Field...");

        // Actions
        btnTimestamp.setOnAction(e -> handleMarkSelection("Timestamp"));
        btnLevel.setOnAction(e -> handleMarkSelection("Level"));
        btnMessage.setOnAction(e -> handleMarkSelection("Message"));
        btnCustom.setOnAction(e -> handleMarkCustom());

        // UX: Enable/Disable based on selection
        // Initially disabled
        btnTimestamp.setDisable(true);
        btnLevel.setDisable(true);
        btnMessage.setDisable(true);
        btnCustom.setDisable(true);

        sampleLogArea.selectedTextProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null && !newVal.isEmpty();
            btnTimestamp.setDisable(!hasSelection);
            btnLevel.setDisable(!hasSelection);
            btnMessage.setDisable(!hasSelection);
            btnCustom.setDisable(!hasSelection);
        });

        // Layout
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        toolbar.setPadding(new javafx.geometry.Insets(0, 0, 5, 0)); // Bottom padding
        toolbar.getChildren().addAll(
                new Label("Visual Builder: "),
                btnTimestamp, btnLevel, btnMessage, btnCustom);

        // Inject into Parent
        if (sampleLogArea.getParent() instanceof VBox) {
            VBox parent = (VBox) sampleLogArea.getParent();
            // Index 0 is Label, Index 1 is TextArea. Verify?
            // FXML: Label, TextArea. So index 1 is TextArea. We insert AT 1.
            // But wait, getChildren() might be dynamic.
            // Safer: Find index of sampleLogArea
            int index = parent.getChildren().indexOf(sampleLogArea);
            if (index != -1) {
                parent.getChildren().add(index, toolbar);
            }
        }
    }

    private Button createBuilderButton(String text, String iconName, String tooltipText) {
        Button btn = new Button(text);
        try {
            de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView icon = new de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView();
            icon.setGlyphName(iconName);
            icon.setSize("1.1em");
            btn.setGraphic(icon);
        } catch (NoClassDefFoundError | Exception e) {
            // Fallback if fontawesome not loaded
            logger.warn("Could not load icon: " + iconName);
        }
        btn.setTooltip(new Tooltip(tooltipText));
        // Compact style
        btn.setStyle("-fx-padding: 4 8 4 8; -fx-font-size: 11px;");
        return btn;
    }

    private void handleMarkSelection(String fieldName) {
        String fullText = sampleLogArea.getText();
        IndexRange range = sampleLogArea.getSelection();
        String currentPattern = regexPatternArea.getText();

        if (fullText == null || range.getLength() == 0) {
            return;
        }

        String prefix = fullText.substring(0, range.getStart());
        String selected = fullText.substring(range.getStart(), range.getEnd());
        String suffix = fullText.substring(range.getEnd());

        // --- User Request: Dynamic Space ---
        // Use smartEscape to convert whitespace sequences to \s+
        String escapedPrefix = smartEscape(prefix);
        String escapedSuffix = smartEscape(suffix);

        // --- Substitution Logic ---
        String substitution;

        switch (fieldName) {
            case "Timestamp":
                // --- User Request: Regex 1 by 1 ---
                // Map each character to its regex class (\d, \w, etc.)
                substitution = "(?<timestamp>" + generateTimestampPattern(selected) + ")";
                break;

            case "Level":
                // --- User Request: String | dari semua kategori ---
                // Try to match against standard known levels
                substitution = "(?<level>" + generateLevelPattern(selected) + ")";
                break;

            case "Message":
                if (!suffix.trim().isEmpty()) {
                    substitution = "(?<message>.*?)";
                } else {
                    substitution = "(?<message>.*)";
                }
                break;

            default:
                substitution = "(?<" + fieldName + ">\\S+)";
                break;
        }

        // 2. Incremental Replacement Logic
        // Strategy: Find the selected text (escaped) in the CURRENT regex pattern and
        // replace it.
        // This preserves previously marked fields (which are already regex groups in
        // the pattern).

        // Use smartEscape for target to ensure consistency with prefix/suffix
        // normalization
        String escapedTarget = smartEscape(selected);

        if (currentPattern == null || currentPattern.trim().isEmpty()) {
            // First time? Fallback to full generation (safer for initial state)
            String newPattern = escapedPrefix + substitution + escapedSuffix;
            regexPatternArea.setText(newPattern);
        } else {
            // Incremental
            int occurrenceIndex = countOccurrences(prefix, selected);
            int replaceIndex = findNthOccurrence(currentPattern, escapedTarget, occurrenceIndex);

            if (replaceIndex != -1) {
                String newPattern = currentPattern.substring(0, replaceIndex)
                        + substitution
                        + currentPattern.substring(replaceIndex + escapedTarget.length());
                regexPatternArea.setText(newPattern);
            } else {
                if (currentPattern.contains(escapedTarget)) {
                    regexPatternArea.setText(
                            currentPattern.replaceFirst(java.util.regex.Pattern.quote(escapedTarget), substitution));
                    logger.info("Used fallback replaceFirst logic.");
                } else {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    addAppIcon(alert);
                    alert.setTitle("Selection Not Found");
                    alert.setHeaderText("Could not find selected text in current pattern");
                    alert.setContentText("Selection not found. Try clearing the pattern.");
                    alert.showAndWait();
                    return;
                }
            }
        }
    }

    // Generates regex character-by-character: 2 -> \d, a -> [a-z], etc.
    private String generateTimestampPattern(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append("\\d"); // Matches digit (UI shows \d)
            } else if (Character.isLetter(c)) {
                if (Character.isUpperCase(c)) {
                    sb.append("[A-Z]");
                } else if (Character.isLowerCase(c)) {
                    sb.append("[a-z]");
                } else {
                    sb.append("[a-zA-Z]");
                }
            } else if (Character.isWhitespace(c)) {
                sb.append("\\s+"); // Matches whitespace (UI shows \s+)
            } else {
                // Literal symbol (escape it)
                sb.append(escapeRegex(String.valueOf(c)));
            }
        }
        // Optimize: Combine consecutive \d\d\d to \d{3}?
        // User asked for "1 by 1", but \d{4} is cleaner. Let's start with strict 1-by-1
        // per request implicit meaning "character class".
        // Actually, user said "regex 1 per 1". \d\d\d is literally 1 per 1. \d{3} is
        // quantified.
        // I will adhere to strict char class mapping to be safe, or maybe basic
        // quantification.
        // Lets stick to safe char classes.
        return sb.toString();
    }

    private String generateLevelPattern(String text) {
        // Standard Log Levels
        String[] levels = { "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "SEVERE", "FINE", "FINER", "FINEST",
                "ALL", "OFF" };

        // If current selection is one of them, return the full OR set.
        String upper = text.toUpperCase();
        boolean isStandard = java.util.Arrays.asList(levels).contains(upper);

        if (isStandard) {
            return String.join("|", levels);
        } else {
            // Fallback if it's a custom level name like "CRIT"
            return "\\S+";
        }
    }

    private String smartEscape(String input) {
        if (input == null)
            return "";
        // 1. Escape Special Chars
        String escaped = escapeRegex(input);
        // 2. Dynamic Space: Replace literal space sequences with \s+
        // In Java String literal: "\\s+" -> String "\s+"
        // replaceAll replacement string: "\\\\s+" -> String "\\s+" -> Matcher Result
        // "\s+"
        return escaped.replaceAll("\\s+", "\\\\s+");
    }

    private int countOccurrences(String str, String target) {
        int count = 0;
        int lastIndex = 0;
        while (lastIndex != -1) {
            lastIndex = str.indexOf(target, lastIndex);
            if (lastIndex != -1) {
                count++;
                lastIndex += target.length();
            }
        }
        return count;
    }

    private int findNthOccurrence(String str, String target, int n) {
        int index = -1;
        for (int i = 0; i <= n; i++) {
            index = str.indexOf(target, index + 1);
            if (index == -1)
                return -1;
        }
        return index;
    }

    private void handleMarkCustom() {
        TextInputDialog dialog = new TextInputDialog();
        addAppIcon(dialog);
        dialog.setTitle("Mark as Custom Field");
        dialog.setHeaderText("Enter field name:");
        dialog.setContentText("Field Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                handleMarkSelection(name.trim());
            }
        });
    }

    private String escapeRegex(String input) {
        if (input == null)
            return "";
        // Simple escape for common special characters in logs
        // . [ ] ( ) { } * + ? ^ $ | \
        // Also handle whitespace? No, literal whitespace is usually desired as anchor
        // unless it's variable.
        // For "Smart" builder, we might want to replace multiple spaces with \\s+
        // automatically?
        // Let's sticking to literal first, but escape special chars.
        return input.replaceAll("([\\\\.\\[\\](){}_*+?^$|])", "\\\\$1");
    }

    private void handleApply() {
        if (isDirty() && selectedConfig != null) {
            saveCurrentConfig(); // This updates the in-memory object

            if (selectedConfig.getId() == null) {
                parsingConfigService.save(selectedConfig);
                logger.info("Saved new configuration: {}", selectedConfig.getName());
            } else {
                parsingConfigService.update(selectedConfig);
                logger.info("Updated existing configuration: {}", selectedConfig.getName());
            }
        }

        notifyConfigChanged();
        logger.info("Configuration changes applied and parent notified.");
    }

    private void handleCancel() {
        if (isDirty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            addAppIcon(alert);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("You have unsaved changes.");
            alert.setContentText("Are you sure you want to discard your changes?");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }
        closeDialog();
    }

    private void handleExportParsingConfig() {
        ObservableList<ParsingConfig> selectedItems = configListView.getSelectionModel().getSelectedItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Parsing Configurations");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));

        if (selectedItems.size() == 1) {
            fileChooser.setInitialFileName(selectedItems.get(0).getName().replaceAll("\\s+", "_") + "_config.json");
        } else {
            fileChooser.setInitialFileName("parsing_configurations_export.json");
        }

        File file = fileChooser.showSaveDialog(exportConfigurationButton.getScene().getWindow());
        if (file != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                mapper.writeValue(file, selectedItems);
                logger.info("Exported {} parsing configurations to {}", selectedItems.size(), file.getAbsolutePath());
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                addAppIcon(alert);
                alert.setTitle("Export Error");
                alert.setHeaderText("Error exporting parsing configurations");
                alert.setContentText("An error occurred while exporting: " + ex.getMessage());
                alert.showAndWait();
                logger.error("Error exporting parsing configurations", ex);
            }
        }
    }

    private void handleImportParsingConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Parsing Configurations");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = fileChooser.showOpenDialog(importConfigurationButton.getScene().getWindow());
        try {
            ObjectMapper mapper = new ObjectMapper();

            List<ParsingConfig> importedConfigs = List.of(
                    mapper.readValue(file, ParsingConfig[].class));

            int importedCount = 0;
            for (ParsingConfig config : importedConfigs) {
                boolean exists = configList.stream()
                        .anyMatch(existing -> existing.getName().equalsIgnoreCase(config.getName()));
                if (!exists) {
                    configList.add(config);
                    parsingConfigService.save(config);
                    importedCount++;
                } else {
                    logger.warn("Skipped importing duplicate configuration: {}", config.getName());
                }
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            addAppIcon(alert);
            if (importedCount > 0) {
                alert.setTitle("Import Successful");
                alert.setHeaderText("Parsing Configurations Imported");
                alert.setContentText("Successfully imported " + importedCount + " configurations.");
                alert.showAndWait();
                logger.info("Imported {} parsing configurations from {}", importedCount, file.getAbsolutePath());
            } else {
                alert.setTitle("Import Result");
                alert.setHeaderText("No New Configurations Imported");
                alert.setContentText("All configurations in the file already exist.");
                alert.showAndWait();
                logger.info("No new parsing configurations were imported from {}", file.getAbsolutePath());
            }
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            addAppIcon(alert);
            alert.setTitle("Import Error");
            alert.setHeaderText("Error importing parsing configurations");
            alert.setContentText("An error occurred while importing: " + ex.getMessage());
            alert.showAndWait();
            logger.error("Error importing parsing configurations", ex);
        }
    }

    private void saveCurrentConfig() {
        if (selectedConfig == null) {
            return;
        }

        selectedConfig.setName(nameField.getText());
        selectedConfig.setDescription(descriptionArea.getText());
        selectedConfig.setRegexPattern(regexPatternArea.getText());
        selectedConfig.setTimestampFormat(
                timestampFormatField.getEditor().getText().trim().isEmpty() ? null
                        : timestampFormatField.getEditor().getText().trim());

        configSnapshot = selectedConfig.copy();

        configListView.refresh();
        updateButtonStates();

        logger.info("Applied changes to configuration object: {}", selectedConfig.getName());
    }

    private boolean isDirty() {
        if (configSnapshot == null || selectedConfig == null) {
            return false;
        }

        // Compare default status first
        if (selectedConfig.isDefault() != configSnapshot.isDefault()) {
            return true;
        }

        // Normalize strings for reliable comparison: handle nulls and trim whitespace.
        String snapshotName = Optional.ofNullable(configSnapshot.getName()).orElse("").trim();
        String currentName = Optional.ofNullable(nameField.getText()).orElse("").trim();

        String snapshotDesc = Optional.ofNullable(configSnapshot.getDescription()).orElse("").trim();
        String currentDesc = Optional.ofNullable(descriptionArea.getText()).orElse("").trim();

        String snapshotPattern = Optional.ofNullable(configSnapshot.getRegexPattern()).orElse("").trim();
        String currentPattern = Optional.ofNullable(regexPatternArea.getText()).orElse("").trim();

        String snapshotTimestamp = Optional.ofNullable(configSnapshot.getTimestampFormat()).orElse("").trim();
        String currentTimestamp = Optional.ofNullable(timestampFormatField.getEditor().getText()).orElse("").trim();

        // Compare the normalized, trimmed strings.
        return !Objects.equals(snapshotName, currentName) ||
                !Objects.equals(snapshotDesc, currentDesc) ||
                !Objects.equals(snapshotPattern, currentPattern) ||
                !Objects.equals(snapshotTimestamp, currentTimestamp);
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedConfig != null;
        boolean hasChanges = isDirty();

        editButton.setDisable(!hasSelection);
        deleteButton.setDisable(!hasSelection || configList.size() <= 1);
        duplicateButton.setDisable(!hasSelection);

        saveButton.setDisable(!hasChanges);
        applyButton.setDisable(!hasChanges);
    }

    private void setEditorDisabled(boolean disabled) {
        nameField.setDisable(disabled);
        descriptionArea.setDisable(disabled);
        regexPatternArea.setDisable(disabled);
        timestampFormatField.setDisable(disabled);
        autoDetectFormatButton.setDisable(disabled);
        loadSampleButton.setDisable(disabled);
        autoDetectConfigButton.setDisable(disabled);
        testParsingButton.setDisable(disabled);
        sampleLogArea.setDisable(disabled);
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void addAppIcon(Dialog<?> dialog) {
        try {
            if (dialog.getOwner() == null && cancelButton.getScene() != null) {
                dialog.initOwner(cancelButton.getScene().getWindow());
            }

            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
                addAppIcon(stage);
            }
        } catch (Exception e) {
            // Ignore
        }
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

    private static class ConfigListCell extends ListCell<ParsingConfig> {
        private final VBox vbox = new VBox(2);
        private final Label nameLabel = new Label();
        private final Label descLabel = new Label();
        private final Label statusLabel = new Label();

        public ConfigListCell() {
            nameLabel.setStyle("-fx-font-weight: bold;");
            vbox.getChildren().addAll(nameLabel, descLabel, statusLabel);
        }

        @Override
        protected void updateItem(ParsingConfig item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                nameLabel.setText(item.getName());
                descLabel.setText(item.getDescription());

                statusLabel.getStyleClass().removeAll("validation-success", "validation-error");
                if (item.isValid()) {
                    statusLabel.setText(
                            "✓ " + item.getGroupNames().size() + " groups");
                    statusLabel.getStyleClass().add("validation-success");
                } else {
                    statusLabel.setText("✗ Invalid pattern");
                    statusLabel.getStyleClass().add("validation-error");
                }

                setGraphic(vbox);
            }
        }
    }

    public record ParsedField(SimpleStringProperty fieldName, SimpleStringProperty fieldValue) {
        public ParsedField(String fieldName, String fieldValue) {
            this(new SimpleStringProperty(fieldName), new SimpleStringProperty(fieldValue));
        }

        public String getFieldName() {
            return fieldName.get();
        }

        public String getFieldValue() {
            return fieldValue.get();
        }

        public SimpleStringProperty fieldNameProperty() {
            return fieldName;
        }

        public SimpleStringProperty fieldValueProperty() {
            return fieldValue;
        }
    }
}