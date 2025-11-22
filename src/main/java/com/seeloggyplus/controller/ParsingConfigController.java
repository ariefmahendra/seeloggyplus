package com.seeloggyplus.controller;

import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.service.impl.LogParserService;

import java.util.Objects;
import java.util.Optional;

import com.seeloggyplus.service.ParsingConfigService;
import com.seeloggyplus.service.impl.ParsingConfigServiceImpl;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for Parsing Configuration Dialog Allows users to create, edit, and
 * test regex patterns with named groups
 */
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
    private TextField nameField;

    @FXML
    private TextArea descriptionArea;
    
    @FXML
    private TextField timestampFormatField;
    
    @FXML
    private Button autoDetectFormatButton;

    @FXML
    private TextArea regexPatternArea;

    @FXML
    private Label validationLabel;

    @FXML
    private ListView<String> groupNamesListView;

    @FXML
    private TextArea sampleLogArea;

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
    private ParsingConfig configSnapshot; // Snapshot of the config when editing starts
    
    private Runnable onConfigChangedCallback; // Callback to notify parent when config changes

    @FXML
    public void initialize() {
        logger.info("Initializing ParsingConfigController");

        parsingConfigService = new ParsingConfigServiceImpl();

        logParserService = new LogParserService();
        configList = FXCollections.observableArrayList(parsingConfigService.findAll());

        setupConfigList();
        setupDetailPanel();
        setupTestPanel();
        setupButtons();

        // Add a listener to the window's close request to check for unsaved changes
        Platform.runLater(() -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                if (isDirty()) {
                    handleCancel(); // Will show dialog if dirty
                    event.consume(); // Consume event to prevent auto-close
                } else {
                    // No changes, just close
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
    
    /**
     * Set callback to be invoked when parsing configuration changes
     * @param callback Runnable to execute when config is saved/updated
     */
    public void setOnConfigChangedCallback(Runnable callback) {
        this.onConfigChangedCallback = callback;
    }
    
    /**
     * Notify parent controller that configuration has changed
     */
    private void notifyConfigChanged() {
        if (onConfigChangedCallback != null) {
            logger.debug("Notifying parent controller of config changes");
            onConfigChangedCallback.run();
        }
    }

    /**
     * Setup configuration list view and its selection listener.
     */
    private void setupConfigList() {
        configListView.setItems(configList);
        configListView.setCellFactory(listView -> new ConfigListCell());

        configListView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (oldVal == newVal) {
                        return; // No change in selection
                    }

                    // Check for unsaved changes before switching away from a config
                    if (oldVal != null && isDirty()) {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Unsaved Changes");
                        alert.setHeaderText(
                                "You have unsaved changes for '" + oldVal.getName() + "'."
                        );
                        alert.setContentText("Do you want to save them before switching?");

                        ButtonType saveBtn = new ButtonType("Save");
                        ButtonType dontSaveBtn = new ButtonType("Don't Save");
                        ButtonType cancelBtn = new ButtonType(
                                "Cancel",
                                ButtonBar.ButtonData.CANCEL_CLOSE
                        );
                        alert.getButtonTypes().setAll(saveBtn, dontSaveBtn, cancelBtn);

                        Optional<ButtonType> result = alert.showAndWait();

                        if (result.isPresent()) {
                            if (result.get() == saveBtn) {
                                saveCurrentConfig();
                            } else if (result.get() == cancelBtn) {
                                // Revert selection safely without re-triggering the listener cascade
                                Platform.runLater(() -> configListView.getSelectionModel().select(oldVal));
                                return;
                            }
                            // If "Don't Save", proceed to load the new value
                        } else {
                            // Dialog was closed without a choice (e.g., 'X' button), treat as cancel
                            Platform.runLater(() -> configListView.getSelectionModel().select(oldVal));
                            return;
                        }
                    }

                    // If we are here, it's safe to load the new config
                    loadConfigToEditor(newVal);
                });
    }

    /**
     * Setup detail panel listeners.
     */
    private void setupDetailPanel() {
        nameField.textProperty().addListener((obs, o, n) -> updateButtonStates());
        descriptionArea
                .textProperty()
                .addListener((obs, o, n) -> updateButtonStates());
        timestampFormatField
                .textProperty()
                .addListener((obs, o, n) -> updateButtonStates());
        regexPatternArea
                .textProperty()
                .addListener((obs, o, n) -> {
                    validatePattern();
                    updateButtonStates();
                });

        groupNamesListView.setCellFactory(listView ->
                new ListCell<>() {
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
                }
        );
    }

    /**
     * Setup test panel
     */
    private void setupTestPanel() {
        fieldNameColumn.setCellValueFactory(
                new PropertyValueFactory<>("fieldName")
        );
        fieldValueColumn.setCellValueFactory(
                new PropertyValueFactory<>("fieldValue")
        );

        sampleLogArea.setPromptText(
                "Enter a sample log line to test the regex pattern..."
        );
        testParsingButton.setOnAction(e -> handleTestParsing());
    }

    /**
     * Setup buttons
     */
    private void setupButtons() {
        addButton.setOnAction(e -> handleAdd());
        editButton.setOnAction(e -> handleEdit());
        deleteButton.setOnAction(e -> handleDelete());
        duplicateButton.setOnAction(e -> handleDuplicate());
        autoDetectFormatButton.setOnAction(e -> handleAutoDetectFormat());

        saveButton.setOnAction(e -> handleSave());
        cancelButton.setOnAction(e -> handleCancel());
        applyButton.setOnAction(e -> handleApply());

        updateButtonStates();
    }

    /**
     * Load configuration to editor
     */
    private void loadConfigToEditor(ParsingConfig config) {
        this.selectedConfig = config;
        if (config != null) {
            this.configSnapshot = config.copy(); // Create snapshot to track changes

            nameField.setText(config.getName());
            descriptionArea.setText(config.getDescription());
            regexPatternArea.setText(config.getRegexPattern());
            timestampFormatField.setText(config.getTimestampFormat() != null ? config.getTimestampFormat() : "");

            setEditorDisabled(false);
            validatePattern();
        } else {
            this.configSnapshot = null;
            clearEditor();
            setEditorDisabled(true);
        }
        updateButtonStates(); // Update buttons based on new state (no changes initially)
    }

    /**
     * Clear editor fields
     */
    private void clearEditor() {
        nameField.clear();
        descriptionArea.clear();
        regexPatternArea.clear();
        timestampFormatField.clear();
        groupNamesListView.getItems().clear();
        validationLabel.setText("");
        previewTableView.getItems().clear();
        testResultLabel.setText("");
    }

    /**
     * Validate regex pattern
     */
    private void validatePattern() {
        String pattern = regexPatternArea.getText();

        if (pattern == null || pattern.trim().isEmpty()) {
            validationLabel.setText("Pattern is empty");
            validationLabel.getStyleClass().setAll("validation-warning");
            groupNamesListView.getItems().clear();
            return;
        }

        // Create temp config and set pattern
        ParsingConfig tempConfig = new ParsingConfig();
        tempConfig.setRegexPattern(pattern);

        // IMPORTANT: Call validatePattern() to extract named groups
        tempConfig.validatePattern();

        // Now check if valid and display results
        if (tempConfig.isValid()) {
            validationLabel.setText("✓ Pattern is valid");
            validationLabel.getStyleClass().setAll("validation-success");

            // Display extracted group names in the list
            if (tempConfig.getGroupNames() != null && !tempConfig.getGroupNames().isEmpty()) {
                groupNamesListView.setItems(
                        FXCollections.observableArrayList(tempConfig.getGroupNames())
                );
                logger.info("Detected {} named groups: {}",
                    tempConfig.getGroupNames().size(),
                    tempConfig.getGroupNames());
            } else {
                groupNamesListView.getItems().clear();
                logger.warn("No named groups detected in pattern");
            }
        } else {
            validationLabel.setText("✗ " + tempConfig.getValidationError());
            validationLabel.getStyleClass().setAll("validation-error");
            groupNamesListView.getItems().clear();
        }
    }

    /**
     * Handle test parsing
     */
    private void handleTestParsing() {
        String sampleLog = sampleLogArea.getText();
        String pattern = regexPatternArea.getText();

        if (sampleLog == null || sampleLog.trim().isEmpty()) {
            testResultLabel.setText("Please enter a sample log line");
            testResultLabel.getStyleClass().setAll("validation-warning");
            return;
        }

        if (pattern == null || pattern.trim().isEmpty()) {
            testResultLabel.setText("Please enter a regex pattern");
            testResultLabel.getStyleClass().setAll("validation-warning");
            return;
        }

        ParsingConfig testConfig = new ParsingConfig("Test", pattern);
        LogParserService.TestResult result = logParserService.testParsing(
                sampleLog,
                testConfig
        );

        if (result.isSuccess()) {
            testResultLabel.setText("✓ Pattern matched successfully!");
            testResultLabel.getStyleClass().setAll("validation-success");

            ObservableList<ParsedField> fields = FXCollections.observableArrayList();
            result
                    .getParsedFields()
                     .forEach((key, value) -> fields.add(new ParsedField(key, value)));
            previewTableView.setItems(fields);

            logger.info("Test parsing successful, displaying {} fields", fields.size());
        } else {
            testResultLabel.setText("✗ " + result.getMessage());
            testResultLabel.getStyleClass().setAll("validation-error");
            previewTableView.getItems().clear();
            logger.warn("Test parsing failed: {}", result.getMessage());
        }
    }

    /**
     * Handle add new configuration
     */
    private void handleAdd() {
        ParsingConfig newConfig = new ParsingConfig();
        newConfig.setName("New Configuration");
        newConfig.setDescription("Enter description here");
        newConfig.setRegexPattern("(?<timestamp>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(?<level>\\w+)\\s+(?<message>.*)");
        configList.add(newConfig);
        configListView.getSelectionModel().select(newConfig);
    }

    /**
     * Handle edit configuration
     */
    private void handleEdit() {
        nameField.requestFocus();
    }

    /**
     * Handle delete configuration
     */
    private void handleDelete() {
        ParsingConfig selected = configListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Configuration");
        alert.setHeaderText("Delete parsing configuration?");
        alert.setContentText(
                "Are you sure you want to delete '" + selected.getName() + "'?"
        );

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            parsingConfigService.delete(selected);
            configList.remove(selected);
            if (!configList.isEmpty()) {
                configListView.getSelectionModel().selectFirst();
            } else {
                clearEditor();
            }
            logger.info("Deleted parsing configuration: {}", selected.getName());
        }
    }

    /**
     * Handle duplicate configuration
     */
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
    
    /**
     * Handle auto-detect timestamp format from regex pattern
     */
    private void handleAutoDetectFormat() {
        String regexPattern = regexPatternArea.getText();
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Auto-Detect Format");
            alert.setHeaderText("Cannot auto-detect timestamp format");
            alert.setContentText("Please enter a regex pattern first.");
            alert.showAndWait();
            return;
        }
        
        // Create temp config to use autoDetect method
        ParsingConfig tempConfig = new ParsingConfig();
        tempConfig.setRegexPattern(regexPattern);
        
        String detectedFormat = tempConfig.autoDetectTimestampFormat();
        
        if (detectedFormat != null) {
            timestampFormatField.setText(detectedFormat);
            logger.info("✅ Auto-detected timestamp format: {}", detectedFormat);
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Auto-Detect Format");
            alert.setHeaderText("Timestamp format detected!");
            alert.setContentText("Detected format: " + detectedFormat + "\n\nYou can modify this if needed.");
            alert.showAndWait();
        } else {
            logger.warn("⚠️ Could not auto-detect timestamp format from pattern");
            
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Auto-Detect Format");
            alert.setHeaderText("Could not detect timestamp format");
            alert.setContentText("No timestamp group found in the pattern, or pattern is not recognized.\n\nPlease enter the format manually (e.g., yyyy-MM-dd HH:mm:ss.SSS)");
            alert.showAndWait();
        }
    }


    /**
     * Handle save
     */
    private void handleSave() {
        if (isDirty()) {
            saveCurrentConfig();
        }

        for (ParsingConfig config : configList) {
            if (config.getId() == null) {
                parsingConfigService.save(config);
            } else {
                parsingConfigService.update(config);
            }
        }
        
        // Notify parent controller of changes
        notifyConfigChanged();
        logger.info("Configuration saved and parent notified");

        closeDialog();
    }

    /**
     * Handle apply
     */
    private void handleApply() {
        if (isDirty()) {
            saveCurrentConfig();
        }

        for (ParsingConfig config : configList) {
            if (config.getId() == null) {
                parsingConfigService.save(config);
            } else {
                parsingConfigService.update(config);
            }
        }
        
        // Notify parent controller of changes
        notifyConfigChanged();
        logger.info("Configuration applied and parent notified");
    }

    /**
     * Handle cancel
     */
    private void handleCancel() {
        if (isDirty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
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

    /**
     * Save current configuration being edited to the object in the list
     */
    private void saveCurrentConfig() {
        if (selectedConfig == null) {
            return;
        }

        selectedConfig.setName(nameField.getText());
        selectedConfig.setDescription(descriptionArea.getText());
        selectedConfig.setRegexPattern(regexPatternArea.getText());
        selectedConfig.setTimestampFormat(timestampFormatField.getText().trim().isEmpty() ? null : timestampFormatField.getText().trim());

        configSnapshot = selectedConfig.copy();

        configListView.refresh();
        updateButtonStates();

        logger.info("Applied changes to configuration object: {}", selectedConfig.getName());
    }

    /**
     * Checks if the current editor fields differ from the snapshot.
     */
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
        String currentTimestamp = Optional.ofNullable(timestampFormatField.getText()).orElse("").trim();

        // Compare the normalized, trimmed strings.
        return !Objects.equals(snapshotName, currentName) ||
                !Objects.equals(snapshotDesc, currentDesc) ||
                !Objects.equals(snapshotPattern, currentPattern) ||
                !Objects.equals(snapshotTimestamp, currentTimestamp);
    }

    /**
     * Update button states based on selection and modification
     */
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
        testParsingButton.setDisable(disabled);
        sampleLogArea.setDisable(disabled);
    }

    /**
     * Close dialog
     */
    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Custom list cell for parsing configurations
     */
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

                // Clear old validation styles and apply new ones
                statusLabel.getStyleClass().removeAll("validation-success", "validation-error");
                if (item.isValid()) {
                    statusLabel.setText(
                            "✓ " + item.getGroupNames().size() + " groups"
                    );
                    statusLabel.getStyleClass().add("validation-success");
                } else {
                    statusLabel.setText("✗ Invalid pattern");
                    statusLabel.getStyleClass().add("validation-error");
                }

                setGraphic(vbox);
            }
        }
    }

    /**
     * Helper class for parsed field display in preview table
     */
    public static class ParsedField {
        private final SimpleStringProperty fieldName;
        private final SimpleStringProperty fieldValue;

        public ParsedField(String fieldName, String fieldValue) {
            this.fieldName = new SimpleStringProperty(fieldName);
            this.fieldValue = new SimpleStringProperty(fieldValue);
        }

        public String getFieldName() {
            return fieldName.get();
        }

        public SimpleStringProperty fieldNameProperty() {
            return fieldName;
        }

        public String getFieldValue() {
            return fieldValue.get();
        }

        public SimpleStringProperty fieldValueProperty() {
            return fieldValue;
        }
    }
}
