package com.seeloggyplus.app;

import com.seeloggyplus.features.recent.domain.RecentFile;
import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.model.SSHServerModel;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class MainControllerHelperLogicTest {

    private MainController controller;

    @Start
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
    }

    @Test
    void clampDividerClampsToAllowedRange() throws Exception {
        assertEquals(0.05, (double) invokeStatic("clampDivider", new Class<?>[]{double.class}, -0.5), 1e-9);
        assertEquals(0.05, (double) invokeStatic("clampDivider", new Class<?>[]{double.class}, 0.05), 1e-9);
        assertEquals(0.42, (double) invokeStatic("clampDivider", new Class<?>[]{double.class}, 0.42), 1e-9);
        assertEquals(0.95, (double) invokeStatic("clampDivider", new Class<?>[]{double.class}, 1.4), 1e-9);
    }

    @Test
    void formatBytesFormatsBinaryUnits() throws Exception {
        assertEquals("0 B", invoke("formatBytes", new Class<?>[]{long.class}, 0L));
        assertEquals("1023 B", invoke("formatBytes", new Class<?>[]{long.class}, 1023L));
        assertEquals("1.0 KB", invoke("formatBytes", new Class<?>[]{long.class}, 1024L));
        assertEquals(String.format("%.1f KB", 1536 / 1024.0), invoke("formatBytes", new Class<?>[]{long.class}, 1536L));
        assertEquals(String.format("%.1f MB", 1500000 / 1024.0 / 1024.0),
                invoke("formatBytes", new Class<?>[]{long.class}, 1500000L));
        assertEquals("512.0 MB", invoke("formatBytes", new Class<?>[]{long.class}, 536870912L));
        assertEquals(String.format("%.1f GB", 2.5), invoke("formatBytes", new Class<?>[]{long.class}, 2684354560L));
    }

    @Test
    void blankQueryPredicateAcceptsEveryLine() throws Exception {
        Predicate<String> nullQuery = booleanPredicate(null, false);
        assertTrue(nullQuery.test(null));
        assertTrue(nullQuery.test("anything"));

        Predicate<String> blankQuery = booleanPredicate("   ", false);
        assertTrue(blankQuery.test("anything"));
        assertTrue(blankQuery.test(""));
    }

    @Test
    void nonEmptyQueryPredicateRejectsNullLine() throws Exception {
        Predicate<String> predicate = booleanPredicate("error", false);
        assertFalse(predicate.test(null));
        assertTrue(predicate.test("ERROR happened"));
    }

    @Test
    void notOnlyQueryMatchesLinesWithoutTerm() throws Exception {
        Predicate<String> predicate = booleanPredicate("NOT error", false);
        assertTrue(predicate.test("all clear"));
        assertFalse(predicate.test("ERROR happened"));
    }

    @Test
    void extractSearchTermsSkipsNotTermsAndDeduplicates() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> terms = (List<String>) invoke("extractSearchTerms", new Class<?>[]{String.class},
                "alpha AND beta OR NOT gamma OR alpha");
        assertEquals(List.of("alpha", "beta"), terms);

        @SuppressWarnings("unchecked")
        List<String> blank = (List<String>) invoke("extractSearchTerms", new Class<?>[]{String.class}, "   ");
        assertEquals(List.of(), blank);

        @SuppressWarnings("unchecked")
        List<String> nullQuery = (List<String>) invoke("extractSearchTerms", new Class<?>[]{String.class},
                (Object) null);
        assertEquals(List.of(), nullQuery);
    }

    @Test
    void remoteDownloadKeyIncludesServerIdAndNullSeparator() throws Exception {
        SSHServerModel server = new SSHServerModel("prod", "10.0.0.5", 22, "root");
        server.setId("srv-1");
        assertEquals("srv-1\u0000/var/log/app.log", invoke("remoteDownloadKey",
                new Class<?>[]{String.class, SSHServerModel.class}, "/var/log/app.log", server));
        assertEquals("\u0000/var/log/app.log", invoke("remoteDownloadKey",
                new Class<?>[]{String.class, SSHServerModel.class}, "/var/log/app.log", null));
        assertEquals("srv-2\u0000/var/log/app.log", invoke("remoteDownloadKey",
                new Class<?>[]{String.class, String.class}, "/var/log/app.log", "srv-2"));
        assertEquals("\u0000/var/log/app.log", invoke("remoteDownloadKey",
                new Class<?>[]{String.class, String.class}, "/var/log/app.log", (Object) null));
    }

    @Test
    void isTailModeRecentDetectsTailModeEntries() {
        LogFile logFile = new LogFile();
        assertTrue(MainController.isTailModeRecent(new RecentFilesDto(logFile, null, "prod", RecentFile.MODE_TAIL)));
        assertFalse(MainController.isTailModeRecent(new RecentFilesDto(logFile, null, "prod", RecentFile.MODE_OPEN)));
        assertFalse(MainController.isTailModeRecent(new RecentFilesDto(logFile, null, "prod", null)));
        assertFalse(MainController.isTailModeRecent(new RecentFilesDto(logFile, null, "prod")));
        assertFalse(MainController.isTailModeRecent(null));
    }

    @Test
    void updateMemoryStatusFormatsLabelAndAppliesThresholdStyle() throws Exception {
        Platform.runLater(() -> {
            try {
                invoke("updateMemoryStatus", new Class<?>[0]);
                Label label = (Label) field("memoryStatusLabel");
                ProgressBar bar = (ProgressBar) field("memoryBar");

                assertTrue(label.getText().matches("Used: \\d+ MB / Total: \\d+ MB"),
                        "The label must show the usage with its heap total (no max): " + label.getText());
                double progress = bar.getProgress();
                assertTrue(progress >= 0 && progress <= 1, "Progress must be a ratio: " + progress);

                List<String> memoryClasses = bar.getStyleClass().stream()
                        .filter(styleClass -> styleClass.startsWith("memory-"))
                        .toList();
                assertEquals(1, memoryClasses.size(), "Exactly one memory state class is expected");
                String expected = progress > 0.85 ? "memory-high" : progress > 0.60 ? "memory-warn" : "memory-ok";
                assertEquals(expected, memoryClasses.get(0), "Memory style must match the reported usage");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void updateTailButtonStateTogglesWithFilePresence() throws Exception {
        ToggleButton tailButton = (ToggleButton) field("tailButton");
        ToggleButton followTailButton = (ToggleButton) field("followTailButton");
        File file = File.createTempFile("seeloggyplus-tail-state", ".log");
        file.deleteOnExit();

        Platform.runLater(() -> {
            try {
                invoke("updateTailButtonState", new Class<?>[0]);
                assertTrue(tailButton.isDisabled());
                assertTrue(followTailButton.isDisabled());
                assertFalse(followTailButton.isSelected());

                setField("currentFile", file);
                invoke("updateTailButtonState", new Class<?>[0]);
                assertFalse(tailButton.isDisabled());
                assertFalse(followTailButton.isDisabled());

                setField("currentFile", null);
                invoke("updateTailButtonState", new Class<?>[0]);
                assertTrue(tailButton.isDisabled());
                assertTrue(followTailButton.isDisabled());
                assertFalse(followTailButton.isSelected());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void updateEmptyStateShowsEmptyPaneWithoutTabs() throws Exception {
        Platform.runLater(() -> {
            try {
                TabPane pane = (TabPane) field("logTabPane");
                VBox emptyState = (VBox) field("emptyStatePane");
                invoke("updateEmptyState", new Class<?>[0]);

                assertTrue(emptyState.isVisible());
                assertTrue(emptyState.isManaged());
                assertFalse(pane.isVisible());
                assertFalse(pane.isManaged());
                assertTrue(((MenuItem) field("closeTabMenuItem")).isDisable());
                assertTrue(((MenuItem) field("closeAllTabsMenuItem")).isDisable());
                assertTrue(((TextField) field("searchField")).isDisable());
                assertTrue(((Button) field("searchButton")).isDisable());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void updateEmptyStateShowsTabPaneWithTabs() throws Exception {
        Platform.runLater(() -> {
            try {
                TabPane pane = (TabPane) field("logTabPane");
                VBox emptyState = (VBox) field("emptyStatePane");
                pane.getTabs().add(new Tab("one"));
                invoke("updateEmptyState", new Class<?>[0]);

                assertFalse(emptyState.isVisible());
                assertFalse(emptyState.isManaged());
                assertTrue(pane.isVisible());
                assertTrue(pane.isManaged());
                assertFalse(((MenuItem) field("closeTabMenuItem")).isDisable());
                assertFalse(((MenuItem) field("closeAllTabsMenuItem")).isDisable());
                assertFalse(((TextField) field("searchField")).isDisable());
                assertFalse(((Button) field("searchButton")).isDisable());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void selectNextTabWrapsAround() throws Exception {
        Platform.runLater(() -> {
            try {
                TabPane pane = (TabPane) field("logTabPane");
                pane.getTabs().addAll(new Tab("a"), new Tab("b"), new Tab("c"));
                pane.getSelectionModel().select(2);
                invoke("selectNextTab", new Class<?>[0]);
                assertEquals(0, pane.getSelectionModel().getSelectedIndex());
                invoke("selectNextTab", new Class<?>[0]);
                assertEquals(1, pane.getSelectionModel().getSelectedIndex());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void selectPreviousTabWrapsAround() throws Exception {
        Platform.runLater(() -> {
            try {
                TabPane pane = (TabPane) field("logTabPane");
                pane.getTabs().addAll(new Tab("a"), new Tab("b"), new Tab("c"));
                pane.getSelectionModel().select(0);
                invoke("selectPreviousTab", new Class<?>[0]);
                assertEquals(2, pane.getSelectionModel().getSelectedIndex());
                invoke("selectPreviousTab", new Class<?>[0]);
                assertEquals(1, pane.getSelectionModel().getSelectedIndex());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void getLineContentForGlobalReadsLiveTailBufferBounds() throws Exception {
        Platform.runLater(() -> {
            try {
                setField("tailModeEnabled", true);
                setField("liveTailList", new ArrayList<>(List.of(new LogEntry(1L, "raw tail line"))));
                setField("canvasLogViewer", null);
                setField("mappedFileReader", null);
                setField("lineOffsetIndex", null);

                assertEquals("raw tail line", invoke("getLineContentForGlobal", new Class<?>[]{long.class}, 0L));
                assertEquals("", invoke("getLineContentForGlobal", new Class<?>[]{long.class}, 1L));

                setField("tailModeEnabled", false);
                assertEquals("", invoke("getLineContentForGlobal", new Class<?>[]{long.class}, 0L));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void deleteCancelledDownloadFilesRemovesFileAndPartial() throws Exception {
        File file = File.createTempFile("seeloggyplus-cancelled", ".log");
        file.deleteOnExit();
        File partial = new File(file.getParentFile(), file.getName() + ".partial");
        partial.deleteOnExit();
        Files.writeString(file.toPath(), "cancelled");
        Files.writeString(partial.toPath(), "partial");

        invoke("deleteCancelledDownloadFiles", new Class<?>[]{File.class}, file);

        assertFalse(file.exists(), "The cancelled download must be deleted");
        assertFalse(partial.exists(), "The partial sibling must be deleted");
    }

    @Test
    void showLoadingSetsIndeterminateProgressAndHideLoadingResets() throws Exception {
        Platform.runLater(() -> {
            try {
                invoke("showLoading", new Class<?>[]{String.class}, "Preparing index");
                Label loadingLabel = (Label) field("loadingLabel");
                ProgressIndicator loadingProgress = (ProgressIndicator) field("loadingProgress");
                VBox overlay = (VBox) field("loadingOverlay");

                assertEquals("Preparing index", loadingLabel.getText());
                assertEquals(ProgressIndicator.INDETERMINATE_PROGRESS, loadingProgress.getProgress(), 1e-9);

                invoke("hideLoading", new Class<?>[0]);
                assertFalse(overlay.isVisible());
                assertFalse(overlay.isManaged());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void resetFiltersClearsSearchState() throws Exception {
        Platform.runLater(() -> {
            try {
                TextField searchField = (TextField) field("searchField");
                ToggleButton regex = (ToggleButton) field("regexCheckBox");
                ToggleButton caseSensitive = (ToggleButton) field("caseSensitiveCheckBox");
                searchField.setText("error");
                regex.setSelected(true);
                caseSensitive.setSelected(true);
                searchField.setStyle("bad-style");

                invoke("resetFilters", new Class<?>[0]);

                assertEquals("", searchField.getText());
                assertFalse(regex.isSelected());
                assertFalse(caseSensitive.isSelected());
                assertEquals("", searchField.getStyle());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void insertSearchTermAddsSpacingAroundTerm() throws Exception {
        Platform.runLater(() -> {
            try {
                TextField searchField = (TextField) field("searchField");

                searchField.setText("alpha");
                searchField.positionCaret(5);
                invoke("insertSearchTerm", new Class<?>[]{String.class}, "AND");
                assertEquals("alpha AND ", searchField.getText());
                assertEquals(10, searchField.getCaretPosition());

                searchField.setText("alpha beta");
                searchField.positionCaret(5);
                invoke("insertSearchTerm", new Class<?>[]{String.class}, "OR");
                assertEquals("alpha OR beta", searchField.getText());
                assertEquals(8, searchField.getCaretPosition());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void usesArrowKeysForItselfRecognizesTextControls() throws Exception {
        Platform.runLater(() -> {
            try {
                assertTrue((boolean) invokeStatic("usesArrowKeysForItself", new Class<?>[]{Object.class}, (Object) null));
                assertTrue((boolean) invokeStatic("usesArrowKeysForItself", new Class<?>[]{Object.class}, new TextField()));
                assertTrue((boolean) invokeStatic("usesArrowKeysForItself", new Class<?>[]{Object.class}, new ListView<String>()));
                assertTrue((boolean) invokeStatic("usesArrowKeysForItself", new Class<?>[]{Object.class}, new ScrollBar()));
                assertFalse((boolean) invokeStatic("usesArrowKeysForItself", new Class<?>[]{Object.class}, new Button("ok")));

                Label styled = new Label("styled");
                styled.getStyleClass().add("styled-text-area");
                assertTrue((boolean) invokeStatic("usesArrowKeysForItself", new Class<?>[]{Object.class}, styled));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void isNodeRelatedDetectsAncestorRelationship() throws Exception {
        Platform.runLater(() -> {
            try {
                HBox parent = new HBox();
                Label child = new Label("child");
                parent.getChildren().add(child);
                Label unrelated = new Label("unrelated");

                assertTrue((boolean) invoke("isNodeRelated", new Class<?>[]{Node.class, Node.class}, parent, child));
                assertTrue((boolean) invoke("isNodeRelated", new Class<?>[]{Node.class, Node.class}, child, parent));
                assertTrue((boolean) invoke("isNodeRelated", new Class<?>[]{Node.class, Node.class}, child, child));
                assertFalse((boolean) invoke("isNodeRelated", new Class<?>[]{Node.class, Node.class}, unrelated, child));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Predicate<String> booleanPredicate(String query, boolean caseSensitive) throws Exception {
        @SuppressWarnings("unchecked")
        Predicate<String> predicate = (Predicate<String>) invoke("createBooleanSearchPredicate",
                new Class<?>[]{String.class, boolean.class}, query, caseSensitive);
        return predicate;
    }

    private Object field(String name) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MainController.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(controller, args);
    }

    private Object invokeStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MainController.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
