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
    void restoreDefaults() throws Exception {
        save("app_font_size", "12");
        save("app_font_family", "Consolas");
        save("app_theme", "light");
        save("app_max_memory_gb", "4");
        save("main_tail_window_size", "20000");
        save("main_auto_prettify_json", "false");
        save("main_detail_formatted", "false");
        save("main_auto_prettify_xml", "false");
        save("lp_line_limit", "500");
        save("ssh_connection_timeout", "60");
        save("ssh_download_threads", "4");
        save("ssh_download_directory", "");
        save("file_double_click_action", "OPEN");
        onFx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT));
        if (launcherPropertiesPath == null) {
            return;
        }
        if (originalLauncherProperties == null) {
            Files.deleteIfExists(Path.of(launcherPropertiesPath));
        } else {
            Files.writeString(Path.of(launcherPropertiesPath), originalLauncherProperties);
        }
    }

    @Test
    void loadsSavedPreferencesIntoControls() throws Exception {
        save("app_font_size", "18");
        save("app_theme", "dark");
        save("main_tail_window_size", "5000");
        save("main_detail_formatted", "true");
        save("file_double_click_action", "TAIL");

        Loaded dialog = loadDialog();

        assertEquals(18, spinner(dialog.root(), "appFontSizeSpinner").getValue());
        assertEquals(5000, spinner(dialog.root(), "tailWindowSizeSpinner").getValue());
        assertEquals(Boolean.TRUE, checkBox(dialog.root(), "detailFormattedCheckBox").isSelected());
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
            checkBox(dialog.root(), "detailFormattedCheckBox").setSelected(true);
            textField(dialog.root(), "sshDownloadDirField").setText(tempDir.toString());
            button(dialog.root(), "saveButton").fire();
        });

        assertEquals("20", preferences.getPreferencesByCode("app_font_size").orElseThrow());
        assertEquals(family, preferences.getPreferencesByCode("app_font_family").orElseThrow());
        assertEquals("light", preferences.getPreferencesByCode("app_theme").orElseThrow());
        assertEquals("7", preferences.getPreferencesByCode("app_max_memory_gb").orElseThrow());
        assertEquals("42", preferences.getPreferencesByCode("ssh_connection_timeout").orElseThrow());
        assertEquals("TAIL", preferences.getPreferencesByCode("file_double_click_action").orElseThrow());
        assertEquals("true", preferences.getPreferencesByCode("main_detail_formatted").orElseThrow());
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

    @Test
    void downloadInfoFormatsKilobyteSizes() throws Exception {
        Files.write(tempDir.resolve("seeloggyplus-1-big.log"), new byte[2048]);
        save("ssh_download_directory", tempDir.toString());

        Loaded dialog = loadDialog();

        assertEquals("1 file(s), " + String.format("%.1f KB", 2.0),
                label(dialog.root(), "sshDownloadInfoLabel").getText());
    }

    @Test
    void formatSizeCoversEveryUnit() throws Exception {
        Loaded dialog = loadDialog();
        java.lang.reflect.Method formatSize =
                PreferencesDialogController.class.getDeclaredMethod("formatSize", long.class);
        formatSize.setAccessible(true);

        assertEquals("512 B", formatSize.invoke(dialog.controller(), 512L));
        assertEquals(String.format("%.1f KB", 1.5), formatSize.invoke(dialog.controller(), 1536L));
        assertEquals(String.format("%.1f MB", 2.5), formatSize.invoke(dialog.controller(), 2621440L));
        assertEquals(String.format("%.2f GB", 1.5), formatSize.invoke(dialog.controller(), 1610612736L));
    }

    @Test
    void cleanDownloadsReportsWhenNothingMatches() throws Exception {
        save("ssh_download_directory", tempDir.toString());
        Loaded dialog = loadDialog();

        onFx(() -> button(dialog.root(), "sshCleanDownloadsButton").fire());

        assertEquals("No download files found.", label(dialog.root(), "sshDownloadInfoLabel").getText());
    }

    @Test
    void launcherConfigFailuresAreTolerated() throws Exception {
        Loaded dialog = loadDialog();
        java.lang.reflect.Method updateLauncherConfig =
                PreferencesDialogController.class.getDeclaredMethod("updateLauncherConfig", int.class);
        updateLauncherConfig.setAccessible(true);
        String original = System.getProperty("seeloggyplus.launcherProperties");
        try {
            System.setProperty("seeloggyplus.launcherProperties", tempDir.toString());
            updateLauncherConfig.invoke(dialog.controller(), 4);

            Path blocker = Files.writeString(tempDir.resolve("blocker"), "x");
            System.setProperty("seeloggyplus.launcherProperties",
                    blocker.resolve("launcher.properties").toString());
            updateLauncherConfig.invoke(dialog.controller(), 4);
            assertFalse(Files.exists(blocker.resolve("launcher.properties")));
        } finally {
            if (original == null) {
                System.clearProperty("seeloggyplus.launcherProperties");
            } else {
                System.setProperty("seeloggyplus.launcherProperties", original);
            }
        }
    }

    @Test
    void loadsTheLightThemeLabel() throws Exception {
        save("app_theme", "light");

        Loaded dialog = loadDialog();

        assertEquals("Light", combo(dialog.root(), "themeComboBox").getValue());
    }

    @Test
    void generalDropdownsKeepTheirSizeWhenOpened() throws Exception {
        Loaded dialog = loadDialog();
        for (AppTheme.Theme theme : AppTheme.Theme.values()) {
            onFx(() -> {
                AppTheme.setTheme(theme);
                stage.setWidth(550);
                stage.setHeight(480);
                dialog.root().applyCss();
                dialog.root().layout();
            });
            for (String id : new String[] { "appFontFamilyComboBox", "themeComboBox", "fileDoubleClickComboBox" }) {
                onFx(() -> ((javafx.scene.control.ListView<?>) dialog.root().lookup("#settingsCategories"))
                        .getSelectionModel().select("appFontFamilyComboBox".equals(id) ? 1 : 0));
                var box = combo(dialog.root(), id);
                var before = new java.util.concurrent.atomic.AtomicReference<double[]>();
                var positions = new java.util.IdentityHashMap<javafx.scene.Node, javafx.geometry.Bounds>();
                onFx(() -> {
                    stage.getScene().getRoot().requestFocus();
                    dialog.root().applyCss();
                    dialog.root().layout();
                    for (String selector : new String[] { ".label", ".combo-box", ".spinner", ".arrow-button" }) {
                        for (var node : dialog.root().lookupAll(selector)) {
                            positions.put(node, node.localToScene(node.getBoundsInLocal()));
                        }
                    }
                    var display = ((javafx.scene.control.skin.ComboBoxListViewSkin<?>) box.getSkin()).getDisplayNode();
                    positions.put(display, display.localToScene(display.getBoundsInLocal()));
                    org.junit.jupiter.api.Assertions.assertFalse(positions.isEmpty());
                    before.set(new double[] { stage.getWidth(), stage.getHeight(), box.getWidth(), box.getHeight(),
                            box.localToScene(0, 0).getY(), ((javafx.scene.control.ListCell<?>) box.lookup(".list-cell")).getFont().getSize() });
                    box.requestFocus();
                    box.arm();
                    box.show();
                });
                onFx(() -> {
                    dialog.root().applyCss();
                    dialog.root().layout();
                    double[] actual = { stage.getWidth(), stage.getHeight(), box.getWidth(), box.getHeight(),
                            box.localToScene(0, 0).getY(), ((javafx.scene.control.ListCell<?>) box.lookup(".list-cell")).getFont().getSize() };
                    org.junit.jupiter.api.Assertions.assertArrayEquals(before.get(), actual, 0.1, theme + " " + id);
                    positions.forEach((node, expected) -> {
                        var bounds = node.localToScene(node.getBoundsInLocal());
                        assertEquals(expected.getMinX(), bounds.getMinX(), 0.1, theme + " " + id + " " + node + " x");
                        assertEquals(expected.getMinY(), bounds.getMinY(), 0.1, theme + " " + id + " " + node + " y");
                        assertEquals(expected.getWidth(), bounds.getWidth(), 0.1, theme + " " + id + " " + node + " width");
                        assertEquals(expected.getHeight(), bounds.getHeight(), 0.1, theme + " " + id + " " + node + " height");
                    });
                    var cell = (javafx.scene.control.ListCell<?>) ((javafx.scene.control.skin.ComboBoxListViewSkin<?>) box.getSkin()).getDisplayNode();
                    var surface = (javafx.scene.paint.Color) box.getBackground().getFills().getFirst().getFill();
                    org.junit.jupiter.api.Assertions.assertTrue(contrast((javafx.scene.paint.Color) cell.getTextFill(), surface) >= 4.5);
                    var arrow = (javafx.scene.layout.Region) box.lookup(".arrow");
                    var arrowColor = (javafx.scene.paint.Color) arrow.getBackground().getFills().getFirst().getFill();
                    org.junit.jupiter.api.Assertions.assertTrue(contrast(arrowColor, surface) >= 3);
                    box.disarm();
                    box.hide();
                });
            }
        }
    }

    @Test
    void themePreviewIsDiscardedOnCancelAndWindowClose() throws Exception {
        for (boolean cancel : new boolean[] { true, false }) {
            save("app_theme", "light");
            onFx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT));
            Loaded dialog = loadDialog();
            onFx(() -> combo(dialog.root(), "themeComboBox").setValue("Dark"));
            assertEquals(AppTheme.Theme.DARK, AppTheme.getTheme());
            assertEquals("light", preferences.getPreferencesByCode("app_theme").orElseThrow());
            onFx(() -> {
                if (cancel) button(dialog.root(), "cancelButton").fire();
                else stage.hide();
            });
            assertEquals(AppTheme.Theme.LIGHT, AppTheme.getTheme());
        }
    }

    @Test
    void escapeDiscardsThemePreview(org.testfx.api.FxRobot robot) throws Exception {
        onFx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT));
        Loaded dialog = loadDialog();
        onFx(() -> combo(dialog.root(), "themeComboBox").setValue("Dark"));
        robot.press(javafx.scene.input.KeyCode.ESCAPE).release(javafx.scene.input.KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(AppTheme.Theme.LIGHT, AppTheme.getTheme());
        org.junit.jupiter.api.Assertions.assertFalse(stage.isShowing());
    }

    private static double contrast(javafx.scene.paint.Color first, javafx.scene.paint.Color second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(javafx.scene.paint.Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
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
            stage.setOnHidden(event -> controller.discardChanges());
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

    private javafx.scene.Node find(Parent root, String id) {
        var node = root.lookup("#" + id);
        if (node != null) return node;
        for (String pageId : new String[] { "generalPage", "logViewerPage", "sshPage" }) {
            var page = (javafx.scene.control.ScrollPane) root.lookup("#" + pageId);
            node = page.getContent().lookup("#" + id);
            if (node != null) return node;
        }
        throw new AssertionError("Missing settings control: " + id);
    }

    @SuppressWarnings("unchecked")
    private Spinner<Integer> spinner(Parent root, String id) {
        return (Spinner<Integer>) find(root, id);
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> combo(Parent root, String id) {
        return (ComboBox<String>) find(root, id);
    }

    private CheckBox checkBox(Parent root, String id) {
        return (CheckBox) find(root, id);
    }

    private TextField textField(Parent root, String id) {
        return (TextField) find(root, id);
    }

    private Button button(Parent root, String id) {
        return (Button) find(root, id);
    }

    private Label label(Parent root, String id) {
        return (Label) find(root, id);
    }
}
