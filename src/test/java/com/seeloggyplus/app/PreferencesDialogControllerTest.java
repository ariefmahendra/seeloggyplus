package com.seeloggyplus.app;

import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class PreferencesDialogControllerTest {

    @TempDir
    Path tempDir;

    private Stage stage;
    private final PreferenceService preferences = new PreferenceServiceImpl();
    private String launcherPropertiesPath;
    private String originalLauncherProperties;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new StackPane()));
        stage.show();
    }

    @BeforeEach
    void captureLauncherProperties() throws Exception {
        launcherPropertiesPath = System.getProperty("seeloggyplus.launcherProperties");
        if (launcherPropertiesPath != null && Files.exists(Path.of(launcherPropertiesPath))) {
            originalLauncherProperties = Files.readString(Path.of(launcherPropertiesPath));
        }
    }

    @AfterEach
    void restoreLauncherProperties() throws Exception {
        if (launcherPropertiesPath == null) {
            return;
        }
        if (originalLauncherProperties == null) {
            Files.deleteIfExists(Path.of(launcherPropertiesPath));
        } else {
            Files.writeString(Path.of(launcherPropertiesPath), originalLauncherProperties);
        }
    }

    @AfterEach
    void restoreDefaults() {
        save("app_font_size", "12");
        save("app_font_family", "Consolas");
        save("app_theme", "graphite");
        save("app_max_memory_gb", "4");
        save("main_tail_window_size", "20000");
        save("main_auto_prettify_json", "false");
        save("main_auto_prettify_xml", "false");
        save("lp_line_limit", "500");
        save("ssh_connection_timeout", "60");
        save("ssh_download_threads", "4");
        save("ssh_download_directory", "");
        save("file_double_click_action", "OPEN");
        onFx(() -> AppTheme.setTheme(AppTheme.Theme.GRAPHITE));
    }

    @Test
    void loadsSavedPreferencesIntoControls() throws Exception {
        save("app_font_size", "18");
        save("app_theme", "dark");
        save("main_tail_window_size", "5000");
        save("main_auto_prettify_json", "true");
        save("file_double_click_action", "TAIL");

        Loaded dialog = loadDialog();

        assertEquals(18, spinner(dialog.root(), "appFontSizeSpinner").getValue());
        assertEquals(5000, spinner(dialog.root(), "tailWindowSizeSpinner").getValue());
        assertEquals(Boolean.TRUE, checkBox(dialog.root(), "mainAutoPrettifyJsonCheckBox").isSelected());
        assertEquals("Dark", combo(dialog.root(), "themeComboBox").getValue());
        assertEquals("Tail", combo(dialog.root(), "fileDoubleClickComboBox").getValue());
    }

    @Test
    void savePersistsEditedValuesAndUpdatesLauncherConfig() throws Exception {
        Loaded dialog = loadDialog();
        List<String> callbacks = new ArrayList<>();
        String family = combo(dialog.root(), "appFontFamilyComboBox").getItems().get(0);

        onFx(() -> {
            dialog.controller().setOnSaveCallback(() -> callbacks.add("saved"));
            spinner(dialog.root(), "appMaxMemorySpinner").getValueFactory().setValue(7);
            spinner(dialog.root(), "appFontSizeSpinner").getValueFactory().setValue(20);
            spinner(dialog.root(), "sshTimeoutSpinner").getValueFactory().setValue(42);
            combo(dialog.root(), "appFontFamilyComboBox").setValue(family);
            combo(dialog.root(), "themeComboBox").setValue("Light");
            combo(dialog.root(), "fileDoubleClickComboBox").setValue("Tail");
            checkBox(dialog.root(), "mainAutoPrettifyJsonCheckBox").setSelected(true);
            textField(dialog.root(), "sshDownloadDirField").setText(tempDir.toString());
            button(dialog.root(), "saveButton").fire();
        });

        assertEquals("20", preferences.getPreferencesByCode("app_font_size").orElseThrow());
        assertEquals(family, preferences.getPreferencesByCode("app_font_family").orElseThrow());
        assertEquals("light", preferences.getPreferencesByCode("app_theme").orElseThrow());
        assertEquals("7", preferences.getPreferencesByCode("app_max_memory_gb").orElseThrow());
        assertEquals("42", preferences.getPreferencesByCode("ssh_connection_timeout").orElseThrow());
        assertEquals("TAIL", preferences.getPreferencesByCode("file_double_click_action").orElseThrow());
        assertEquals("true", preferences.getPreferencesByCode("main_auto_prettify_json").orElseThrow());
        assertEquals(tempDir.toString(), preferences.getPreferencesByCode("ssh_download_directory").orElseThrow());
        assertEquals(List.of("saved"), callbacks);

        String launcherProperties = Files.readString(
                Path.of(System.getProperty("seeloggyplus.launcherProperties")));
        assertTrue(launcherProperties.contains("max.memory.gb=7"), launcherProperties);
        assertFalse(stage.isShowing(), "Save closes the dialog");
        onFx(stage::show);
    }

    @Test
    void invalidSpinnerTextKeepsThePreviousValue() throws Exception {
        save("app_font_size", "14");
        Loaded dialog = loadDialog();
        Spinner<Integer> fontSize = spinner(dialog.root(), "appFontSizeSpinner");

        onFx(() -> {
            fontSize.getEditor().setText("not-a-number");
            button(dialog.root(), "saveButton").fire();
        });

        assertEquals("14", preferences.getPreferencesByCode("app_font_size").orElseThrow());
        onFx(stage::show);
    }

    @Test
    void cancelClosesWithoutSavingChanges() throws Exception {
        save("app_font_size", "12");
        Loaded dialog = loadDialog();

        onFx(() -> {
            spinner(dialog.root(), "appFontSizeSpinner").getValueFactory().setValue(25);
            button(dialog.root(), "cancelButton").fire();
        });

        assertEquals("12", preferences.getPreferencesByCode("app_font_size").orElseThrow());
        assertFalse(stage.isShowing());
        onFx(stage::show);
    }

    @Test
    void downloadInfoSummarizesMatchingFiles() throws Exception {
        Files.writeString(tempDir.resolve("seeloggyplus-old.log"), "0123456789");
        Files.writeString(tempDir.resolve("other.txt"), "ignored");
        save("ssh_download_directory", tempDir.toString());

        Loaded dialog = loadDialog();

        assertEquals("1 file(s), 10 B", label(dialog.root(), "sshDownloadInfoLabel").getText());
    }

    @Test
    void downloadInfoReportsAnEmptyDirectory() throws Exception {
        save("ssh_download_directory", tempDir.toString());

        Loaded dialog = loadDialog();

        assertEquals("No download files", label(dialog.root(), "sshDownloadInfoLabel").getText());
    }

    private void save(String key, String value) {
        preferences.saveOrUpdatePreferences(new Preference(key, value));
    }

    private record Loaded(Parent root, PreferencesDialogController controller) {
    }

    private Loaded loadDialog() throws Exception {
        return WaitForAsyncUtils.asyncFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PreferencesDialog.fxml"));
            Parent root = loader.load();
            PreferencesDialogController controller = loader.getController();
            stage.setScene(AppTheme.scene(root));
            stage.show();
            return new Loaded(root, controller);
        }).get();
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
            WaitForAsyncUtils.waitForFxEvents();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Spinner<Integer> spinner(Parent root, String id) {
        return (Spinner<Integer>) root.lookup("#" + id);
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> combo(Parent root, String id) {
        return (ComboBox<String>) root.lookup("#" + id);
    }

    private CheckBox checkBox(Parent root, String id) {
        return (CheckBox) root.lookup("#" + id);
    }

    private TextField textField(Parent root, String id) {
        return (TextField) root.lookup("#" + id);
    }

    private Button button(Parent root, String id) {
        return (Button) root.lookup("#" + id);
    }

    private Label label(Parent root, String id) {
        return (Label) root.lookup("#" + id);
    }
}
