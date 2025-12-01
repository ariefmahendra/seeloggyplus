package com.seeloggyplus.controller;

import com.seeloggyplus.model.Preference;
import com.seeloggyplus.service.PreferenceService;
import com.seeloggyplus.service.impl.PreferenceServiceImpl;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.util.StringConverter;

import java.io.File;
import java.util.Optional;

public class PreferencesDialogController {

    private static final Logger logger = LoggerFactory.getLogger(PreferencesDialogController.class);

    @FXML
    private Spinner<Integer> appFontSizeSpinner;
    @FXML
    private ComboBox<String> appFontFamilyComboBox;
    @FXML
    private Spinner<Integer> mainWindowSizeSpinner;
    @FXML
    private ComboBox<String> mainDefaultLogLevelComboBox;
    @FXML
    private CheckBox mainAutoRefreshCheckBox;
    @FXML
    private CheckBox mainAutoPrettifyJsonCheckBox;
    @FXML
    private CheckBox mainAutoPrettifyXmlCheckBox;
    @FXML
    private TextField ufmDefaultPathField;
    @FXML
    private Button ufmBrowsePathButton;
    @FXML
    private CheckBox ufmShowHiddenFilesCheckBox;
    @FXML
    private Spinner<Integer> lpLineLimitSpinner;
    @FXML
    private Spinner<Integer> sshThreadsSpinner;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;

    private PreferenceService preferenceService;
    @Setter
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        preferenceService = new PreferenceServiceImpl();

        setupSpinners();
        setupFontFamilyComboBox();
        setupLogLevelComboBox();
        setupButtons();
        loadPreferences();
    }

    private void setupSpinners() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        appFontSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 30, 12));
        mainWindowSizeSpinner
                .setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(100, 50000, 5000, 100));
        lpLineLimitSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10000, 500, 50));

        // Max threads = available logical processors provided by the OS
        sshThreadsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, availableProcessors,
                Math.min(4, availableProcessors)));

        if (sshThreadsSpinner.getTooltip() != null) {
            sshThreadsSpinner.getTooltip().setText("Range: 1 to " + availableProcessors);
        } else {
            sshThreadsSpinner.setTooltip(new Tooltip("Range: 1 to " + availableProcessors));
        }
    }

    private void setupFontFamilyComboBox() {
        appFontFamilyComboBox.setItems(FXCollections.observableArrayList(javafx.scene.text.Font.getFamilies()));
    }

    private void setupLogLevelComboBox() {
        mainDefaultLogLevelComboBox.setItems(FXCollections.observableArrayList(
                "ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"));
    }

    private void setupButtons() {
        saveButton.setOnAction(e -> handleSave());
        cancelButton.setOnAction(e -> closeDialog());
        ufmBrowsePathButton.setOnAction(e -> handleBrowsePath());
    }

    private void loadPreferences() {
        appFontSizeSpinner.getValueFactory().setValue(Integer.parseInt(getPreference("app_font_size", "12")));
        appFontFamilyComboBox.getSelectionModel().select(getPreference("app_font_family", "Consolas"));

        mainWindowSizeSpinner.getValueFactory().setValue(Integer.parseInt(getPreference("main_window_size", "5000")));
        mainDefaultLogLevelComboBox.getSelectionModel().select(getPreference("main_default_log_level", "ALL"));
        mainAutoRefreshCheckBox.setSelected(Boolean.parseBoolean(getPreference("main_auto_refresh_enabled", "true")));
        mainAutoPrettifyJsonCheckBox
                .setSelected(Boolean.parseBoolean(getPreference("main_auto_prettify_json", "false")));
        mainAutoPrettifyXmlCheckBox.setSelected(Boolean.parseBoolean(getPreference("main_auto_prettify_xml", "false")));

        ufmDefaultPathField.setText(getPreference("ufm_default_local_path", System.getProperty("user.home")));
        ufmShowHiddenFilesCheckBox.setSelected(Boolean.parseBoolean(getPreference("ufm_show_hidden_files", "false")));

        lpLineLimitSpinner.getValueFactory().setValue(Integer.parseInt(getPreference("lp_line_limit", "500")));

        sshThreadsSpinner.getValueFactory().setValue(Integer.parseInt(getPreference("ssh_download_threads", "4")));
    }

    private String getPreference(String key, String defaultValue) {
        String val = preferenceService.getPreferencesByCode(key).orElse(defaultValue);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        return val;
    }

    private void handleSave() {
        logger.info("handleSave() triggered. Committing spinner values...");
        // Commit spinners to ensure latest typed value is captured
        commitEditorText(appFontSizeSpinner);
        commitEditorText(mainWindowSizeSpinner);
        commitEditorText(lpLineLimitSpinner);
        commitEditorText(sshThreadsSpinner);

        savePreference("app_font_size", String.valueOf(appFontSizeSpinner.getValue()));
        savePreference("app_font_family", appFontFamilyComboBox.getValue());

        savePreference("main_window_size", String.valueOf(mainWindowSizeSpinner.getValue()));
        savePreference("main_default_log_level", mainDefaultLogLevelComboBox.getValue());
        savePreference("main_auto_refresh_enabled", String.valueOf(mainAutoRefreshCheckBox.isSelected()));
        savePreference("main_auto_prettify_json", String.valueOf(mainAutoPrettifyJsonCheckBox.isSelected()));
        savePreference("main_auto_prettify_xml", String.valueOf(mainAutoPrettifyXmlCheckBox.isSelected()));

        savePreference("ufm_default_local_path", ufmDefaultPathField.getText());
        savePreference("ufm_show_hidden_files", String.valueOf(ufmShowHiddenFilesCheckBox.isSelected()));

        savePreference("lp_line_limit", String.valueOf(lpLineLimitSpinner.getValue()));

        savePreference("ssh_download_threads", String.valueOf(sshThreadsSpinner.getValue()));

        logger.info("Preferences saved.");

        if (onSaveCallback != null) {
            onSaveCallback.run();
        }

        closeDialog();
    }

    private <T> void commitEditorText(Spinner<T> spinner) {
        if (!spinner.isEditable())
            return;
        String text = spinner.getEditor().getText();
        SpinnerValueFactory<T> valueFactory = spinner.getValueFactory();
        if (valueFactory != null) {
            StringConverter<T> converter = valueFactory.getConverter();
            if (converter != null) {
                try {
                    T value = converter.fromString(text);
                    valueFactory.setValue(value);
                } catch (Exception e) {
                    // Ignore invalid input or show error to user
                    logger.warn("Invalid input for spinner {}: {}", spinner.getId(), text);
                }
            }
        }
    }

    private void savePreference(String key, String value) {
        preferenceService.saveOrUpdatePreferences(new Preference(key, value));
    }

    private void handleBrowsePath() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Default Path");
        File selectedDirectory = directoryChooser.showDialog(saveButton.getScene().getWindow());
        if (selectedDirectory != null) {
            ufmDefaultPathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}