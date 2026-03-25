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
    private Spinner<Integer> appMaxMemorySpinner;
    @FXML
    private Spinner<Integer> tailWindowSizeSpinner;
    @FXML
    private CheckBox mainAutoPrettifyJsonCheckBox;
    @FXML
    private CheckBox mainAutoPrettifyXmlCheckBox;
    @FXML
    private Spinner<Integer> lpLineLimitSpinner;
    @FXML
    private Spinner<Integer> sshThreadsSpinner;
    @FXML
    private Spinner<Integer> sshTimeoutSpinner;
    @FXML
    private TextField sshDownloadDirField;
    @FXML
    private Button sshDownloadDirBrowseButton;
    @FXML
    private Button sshOpenDownloadDirButton;
    @FXML
    private Button sshCleanDownloadsButton;
    @FXML
    private Label sshDownloadInfoLabel;
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
        setupButtons();
        loadPreferences();
    }

    private void setupSpinners() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        appFontSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 30, 12));
        appMaxMemorySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 16, 4));
        tailWindowSizeSpinner
                .setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1000, 100000, 20000, 1000));
        lpLineLimitSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10000, 500, 50));

        // Max threads = available logical processors provided by the OS
        sshThreadsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, availableProcessors,
                Math.min(4, availableProcessors)));

        if (sshThreadsSpinner.getTooltip() != null) {
            sshThreadsSpinner.getTooltip().setText("Range: 1 to " + availableProcessors);
        } else {
            sshThreadsSpinner.setTooltip(new Tooltip("Range: 1 to " + availableProcessors));
        }

        sshTimeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 600, 60));
    }

    private void setupFontFamilyComboBox() {
        appFontFamilyComboBox.setItems(FXCollections.observableArrayList(javafx.scene.text.Font.getFamilies()));
    }

    private void setupButtons() {
        saveButton.setOnAction(e -> handleSave());
        cancelButton.setOnAction(e -> closeDialog());
        sshDownloadDirBrowseButton.setOnAction(e -> handleBrowseDownloadDir());
        sshOpenDownloadDirButton.setOnAction(e -> handleOpenDownloadDir());
        sshCleanDownloadsButton.setOnAction(e -> handleCleanDownloads());
    }

    private void loadPreferences() {
        appFontSizeSpinner.getValueFactory().setValue(Integer.parseInt(getPreference("app_font_size", "12")));
        appFontFamilyComboBox.getSelectionModel().select(getPreference("app_font_family", "Consolas"));
        appMaxMemorySpinner.getValueFactory().setValue(Integer.parseInt(getPreference("app_max_memory_gb", "4")));

        tailWindowSizeSpinner.getValueFactory()
                .setValue(Integer.parseInt(getPreference("main_tail_window_size", "20000")));
        mainAutoPrettifyJsonCheckBox
                .setSelected(Boolean.parseBoolean(getPreference("main_auto_prettify_json", "false")));
        mainAutoPrettifyXmlCheckBox.setSelected(Boolean.parseBoolean(getPreference("main_auto_prettify_xml", "false")));

        lpLineLimitSpinner.getValueFactory().setValue(Integer.parseInt(getPreference("lp_line_limit", "500")));

        sshThreadsSpinner.getValueFactory().setValue(Integer.parseInt(getPreference("ssh_download_threads", "4")));
        sshTimeoutSpinner.getValueFactory().setValue(Integer.parseInt(getPreference("ssh_connection_timeout", "60")));

        sshDownloadDirField.setText(getPreference("ssh_download_directory", ""));
        updateDownloadInfo();
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
        commitEditorText(appMaxMemorySpinner);
        commitEditorText(tailWindowSizeSpinner);
        commitEditorText(lpLineLimitSpinner);
        commitEditorText(sshThreadsSpinner);
        commitEditorText(sshTimeoutSpinner);

        savePreference("app_font_size", String.valueOf(appFontSizeSpinner.getValue()));
        savePreference("app_font_family", appFontFamilyComboBox.getValue());
        savePreference("app_max_memory_gb", String.valueOf(appMaxMemorySpinner.getValue()));

        // Update launcher.properties for max memory
        updateLauncherConfig(appMaxMemorySpinner.getValue());

        savePreference("main_tail_window_size", String.valueOf(tailWindowSizeSpinner.getValue()));
        savePreference("main_auto_prettify_json", String.valueOf(mainAutoPrettifyJsonCheckBox.isSelected()));
        savePreference("main_auto_prettify_xml", String.valueOf(mainAutoPrettifyXmlCheckBox.isSelected()));

        savePreference("lp_line_limit", String.valueOf(lpLineLimitSpinner.getValue()));

        savePreference("ssh_download_threads", String.valueOf(sshThreadsSpinner.getValue()));
        savePreference("ssh_connection_timeout", String.valueOf(sshTimeoutSpinner.getValue()));
        savePreference("ssh_download_directory", sshDownloadDirField.getText() != null ? sshDownloadDirField.getText().trim() : "");

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

    private void updateLauncherConfig(int maxMemoryGb) {
        try {
            java.io.File configFile = new java.io.File("launcher.properties");
            java.util.Properties props = new java.util.Properties();

            // Read existing properties if file exists
            if (configFile.exists()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                    props.load(fis);
                }
            }

            // Update max memory property
            props.setProperty("max.memory.gb", String.valueOf(maxMemoryGb));

            // Write properties back to file
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(configFile)) {
                props.store(fos, "SeeLoggy+ Launcher Configuration - Auto-generated");
            }

            logger.info("Updated launcher.properties with max memory: {}GB", maxMemoryGb);
        } catch (Exception e) {
            logger.error("Failed to update launcher.properties", e);
        }
    }

    private void handleBrowseDownloadDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Download Directory");
        String current = sshDownloadDirField.getText();
        if (current != null && !current.isBlank()) {
            File dir = new File(current);
            if (dir.isDirectory()) {
                chooser.setInitialDirectory(dir);
            }
        }
        File selected = chooser.showDialog(saveButton.getScene().getWindow());
        if (selected != null) {
            sshDownloadDirField.setText(selected.getAbsolutePath());
            updateDownloadInfo();
        }
    }

    private void handleOpenDownloadDir() {
        File dir = resolveDownloadDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            java.awt.Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            logger.error("Failed to open download directory", e);
        }
    }

    private void handleCleanDownloads() {
        File dir = resolveDownloadDir();
        File[] files = dir.listFiles((d, name) -> name.startsWith("seeloggyplus-"));
        if (files == null || files.length == 0) {
            sshDownloadInfoLabel.setText("No download files found.");
            return;
        }

        long totalSize = 0;
        for (File f : files) totalSize += f.length();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clean Downloads");
        confirm.setHeaderText("Delete " + files.length + " file(s)?");
        confirm.setContentText(String.format("Total size: %s\nDirectory: %s",
                formatSize(totalSize), dir.getAbsolutePath()));

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            int deleted = 0;
            for (File f : files) {
                if (f.delete()) deleted++;
            }
            logger.info("Cleaned {} of {} download files", deleted, files.length);
            updateDownloadInfo();
        }
    }

    private File resolveDownloadDir() {
        String path = sshDownloadDirField.getText();
        if (path != null && !path.isBlank()) {
            return new File(path);
        }
        return new File(System.getProperty("java.io.tmpdir"));
    }

    private void updateDownloadInfo() {
        File dir = resolveDownloadDir();
        File[] files = dir.listFiles((d, name) -> name.startsWith("seeloggyplus-"));
        if (files == null || files.length == 0) {
            sshDownloadInfoLabel.setText("No download files");
            return;
        }
        long totalSize = 0;
        for (File f : files) totalSize += f.length();
        sshDownloadInfoLabel.setText(files.length + " file(s), " + formatSize(totalSize));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}