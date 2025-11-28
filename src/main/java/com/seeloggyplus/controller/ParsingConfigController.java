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
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
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
    private ParsingConfig configSnapshot;

    @Setter
    private Runnable onConfigChangedCallback;

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
        timestampFormatField.textProperty().addListener((obs, o, n) -> updateButtonStates());
        regexPatternArea.textProperty().addListener((obs, o, n) -> {
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
            timestampFormatField.setText(config.getTimestampFormat() != null ? config.getTimestampFormat() : "");

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
        timestampFormatField.clear();
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
                logger.info("Detected {} named groups: {}", tempConfig.getGroupNames().size(), tempConfig.getGroupNames());
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
        LogParserService.TestResult result = logParserService.testParsing(sampleLog, testConfig);
        if (result.isSuccess()) {
            testResultLabel.setText("Pattern matched successfully!");
            testResultLabel.getStyleClass().setAll("validation-success");

            ObservableList<ParsedField> fields = FXCollections.observableArrayList();
            result.getParsedFields().forEach((key, value) -> fields.add(new ParsedField(key, value)));
            previewTableView.setItems(fields);
            logger.info("Test parsing successful, displaying {} fields", fields.size());
        } else {
            testResultLabel.setText("✗ " + result.getMessage());
            testResultLabel.getStyleClass().setAll("validation-error");
            previewTableView.getItems().clear();
            logger.warn("Test parsing failed: {}", result.getMessage());
        }
    }

    private void handleAdd() {
        ParsingConfig newConfig = new ParsingConfig();
        newConfig.setName("New Configuration");
        newConfig.setDescription("Enter description here");
        newConfig.setRegexPattern("(?<timestamp>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(?<level>\\w+)\\s+(?<message>.*)");
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

    private Optional<ButtonType> getButtonType(List<ParsingConfig> itemToDelete, ObservableList<ParsingConfig> selectedItems) {
        int countDataParsingConfig = itemToDelete.size();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
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
            timestampFormatField.setText(detectedFormat);
            logger.info("Auto-detected timestamp format: {}", detectedFormat);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Auto-Detect Format");
            alert.setHeaderText("Timestamp format detected!");
            alert.setContentText("Detected format: " + detectedFormat + "\n\nYou can modify this if needed.");
            alert.showAndWait();
        } else {
            logger.warn("Could not auto-detect timestamp format from pattern");

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Auto-Detect Format");
            alert.setHeaderText("Could not detect timestamp format");
            alert.setContentText("No timestamp group found in the pattern, or pattern is not recognized.\n\nPlease enter the format manually (e.g., yyyy-MM-dd HH:mm:ss.SSS)");
            alert.showAndWait();
        }
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
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );

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
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        File file = fileChooser.showOpenDialog(importConfigurationButton.getScene().getWindow());
        try {
            ObjectMapper mapper = new ObjectMapper();

            List<ParsingConfig> importedConfigs = List.of(
                    mapper.readValue(file, ParsingConfig[].class)
            );

            int importedCount = 0;
            for (ParsingConfig config : importedConfigs) {
                boolean exists = configList.stream().anyMatch(existing -> existing.getName().equalsIgnoreCase(config.getName()));
                if (!exists) {
                    configList.add(config);
                    parsingConfigService.save(config);
                    importedCount++;
                } else {
                    logger.warn("Skipped importing duplicate configuration: {}", config.getName());
                }
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
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
        selectedConfig.setTimestampFormat(timestampFormatField.getText().trim().isEmpty() ? null : timestampFormatField.getText().trim());

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
        String currentTimestamp = Optional.ofNullable(timestampFormatField.getText()).orElse("").trim();

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
        testParsingButton.setDisable(disabled);
        sampleLogArea.setDisable(disabled);
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
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