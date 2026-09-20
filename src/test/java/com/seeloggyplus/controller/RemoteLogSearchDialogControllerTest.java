package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;

import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.model.PreviewLine;
import com.seeloggyplus.model.RemoteLogSearchMatch;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.util.FxTextHighlighter;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for the Remote Log Search dialog: match results, lazy preview,
 * context selection, open-at-line, copy, validation and cancellation.
 */
@ExtendWith(ApplicationExtension.class)
public class RemoteLogSearchDialogControllerTest {

    private RemoteLogSearchDialogController controller;
    private TableView<RemoteLogSearchMatch> resultTable;
    private ListView<PreviewLine> previewList;
    private TextField rootPathField;
    private TextField keywordField;
    private ComboBox<Integer> contextSelector;
    private Button searchButton;
    private Button cancelSearchButton;
    private Button openButton;
    private Button tailButton;
    private Button copyLineButton;
    private Button copyPathButton;
    private Label statusLabel;
    private Label previewLabel;

    @Start
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RemoteLogSearchDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        // Explicit size so the split-pane preview area always has height to
        // render its cells (the shared headless stage can be resized by other tests).
        stage.setWidth(1100);
        stage.setHeight(760);
        stage.show();
        stage.centerOnScreen();

        resultTable = getField("resultTable");
        previewList = getField("previewList");
        rootPathField = getField("rootPathField");
        keywordField = getField("keywordField");
        contextSelector = getField("contextSelector");
        searchButton = getField("searchButton");
        cancelSearchButton = getField("cancelSearchButton");
        openButton = getField("openButton");
        tailButton = getField("tailButton");
        copyLineButton = getField("copyLineButton");
        copyPathButton = getField("copyPathButton");
        statusLabel = getField("statusLabel");
        previewLabel = getField("previewLabel");
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String name) throws Exception {
        Field field = RemoteLogSearchDialogController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(controller);
    }

    private SSHServerModel server() {
        SSHServerModel server = new SSHServerModel("Prod", "10.0.0.1", 22, "user");
        server.setId("srv-search");
        return server;
    }

    private static class SearchMockSsh extends SSHServiceImpl {
        String searchCommand;
        String previewCommand;
        boolean cancelCalled;
        boolean blockSearch;
        String searchOutput = "/var/log/app.log:12804:request timeout\n/var/log/error.out:42:timeout after 30s\n";
        String previewOutput = "12803:reset\n12804:request timeout\n12805:retry\n";
        String lineCountOutput = "20000\n";
        final CountDownLatch searchStarted = new CountDownLatch(1);
        final CountDownLatch releaseSearch = new CountDownLatch(1);

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String executeCommand(String command) throws IOException {
            if (command.contains("grep")) {
                searchCommand = command;
                if (blockSearch) {
                    searchStarted.countDown();
                    try {
                        releaseSearch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", e);
                    }
                }
                return searchOutput;
            }
            if (command.contains("wc -l")) {
                return lineCountOutput;
            }
            previewCommand = command;
            return previewOutput;
        }

        @Override
        public void cancelActiveCommand() {
            cancelCalled = true;
        }
    }

    private void prepareSingleMatch(SearchMockSsh ssh, String matchLine) throws Exception {
        ssh.searchOutput = matchLine;
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 1);
        Platform.runLater(() -> resultTable.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void waitUntil(Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            WaitForAsyncUtils.waitForFxEvents();
            if (condition.call()) return;
            Thread.sleep(40);
        }
        fail("Condition was not met within timeout");
    }

    private void startSearch(SearchMockSsh ssh, String keyword) throws Exception {
        controller.setContext(ssh, server(), "/var/log");
        Platform.runLater(() -> {
            keywordField.setText(keyword);
            searchButton.fire();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void selectFirstMatchAndWaitForPreview() throws Exception {
        Platform.runLater(() -> resultTable.getSelectionModel().select(0));
        waitUntil(() -> !previewList.getItems().isEmpty());
        // The preview cell graphics are built lazily; wait until at least one
        // cell rendered its content before tests inspect/highlight them.
        waitUntil(this::previewCellRendered);
    }

    private boolean previewCellRendered() throws Exception {
        return WaitForAsyncUtils.asyncFx(() -> {
            previewList.applyCss();
            previewList.layout();
            for (javafx.scene.Node node : previewList.lookupAll(".list-cell")) {
                if (node instanceof javafx.scene.control.ListCell<?> cell
                        && cell.getGraphic() instanceof javafx.scene.layout.HBox) {
                    return true;
                }
            }
            return false;
        }).get();
    }

    private Set<javafx.scene.Node> findHighlights(javafx.scene.Parent root) throws Exception {
        return WaitForAsyncUtils.asyncFx(() -> {
            root.applyCss();
            root.layout();
            return root.lookupAll("." + FxTextHighlighter.HIGHLIGHT_STYLE_CLASS);
        }).get();
    }

    @Test
    public void searchPopulatesFileLineAndContent() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);

        assertTrue(ssh.searchCommand.contains("grep -niFH"));
        assertEquals("/var/log/app.log", resultTable.getItems().get(0).path());
        assertEquals(12804, resultTable.getItems().get(0).lineNumber());
        assertEquals("request timeout", resultTable.getItems().get(0).content());
        assertEquals("/var/log/error.out", resultTable.getItems().get(1).path());
        assertTrue(statusLabel.getText().contains("2"));
    }

    @Test
    public void selectingMatchLoadsPreviewAndMarksTargetLine() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);

        selectFirstMatchAndWaitForPreview();

        assertTrue(ssh.previewCommand.startsWith("awk 'NR>=12799 && NR<=12809"));
        assertEquals(3, previewList.getItems().size());
        assertTrue(previewLabel.getText().contains("12804"));
        assertTrue(previewLabel.getText().contains("app.log"));

        PreviewLine target = previewList.getItems().stream().filter(PreviewLine::target).findFirst().orElse(null);
        assertNotNull(target);
        assertEquals(12804, target.lineNumber());
        assertEquals(1, previewList.getItems().stream().filter(PreviewLine::target).count());
    }

    @Test
    public void resultsHighlightTheSearchedKeyword() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);

        Set<javafx.scene.Node> highlights = findHighlights(resultTable);
        assertFalse(highlights.isEmpty(), "Result content must highlight the searched keyword");
    }

    @Test
    public void previewHighlightsTheSearchedKeyword() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        selectFirstMatchAndWaitForPreview();

        Set<javafx.scene.Node> highlights = findHighlights(previewList);
        assertFalse(highlights.isEmpty(), "Preview must highlight the searched keyword");
    }

    @Test
    public void previewCellsDoNotWrapAndAreClipped() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        selectFirstMatchAndWaitForPreview();

        Set<javafx.scene.Node> cells = WaitForAsyncUtils.asyncFx(() -> {
            previewList.applyCss();
            previewList.layout();
            return previewList.lookupAll(".list-cell");
        }).get();

        assertFalse(cells.isEmpty(), "Preview cells must be rendered");
        boolean checkedGraphic = false;
        for (javafx.scene.Node node : cells) {
            assertNotNull(node.getClip(), "Each preview cell must clip its content to stay single-line");
            if (node instanceof javafx.scene.control.ListCell<?> cell
                    && cell.getGraphic() instanceof javafx.scene.layout.HBox) {
                checkedGraphic = true;
            }
        }
        assertTrue(checkedGraphic, "At least one preview cell must contain a single-line HBox graphic");
    }

    @Test
    public void changingContextReloadsPreviewRange() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        selectFirstMatchAndWaitForPreview();

        Platform.runLater(() -> contextSelector.setValue(1));
        waitUntil(() -> ssh.previewCommand.contains("NR>=12803 && NR<=12805"));
    }

    @Test
    public void switchingSelectionRefreshesPreview() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        selectFirstMatchAndWaitForPreview();

        ssh.previewOutput = "42:timeout after 30s\n";
        Platform.runLater(() -> resultTable.getSelectionModel().select(1));
        waitUntil(() -> previewLabel.getText().contains("42"));
    }

    @Test
    public void openReturnsFileAndTargetLine() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        selectFirstMatchAndWaitForPreview();

        Platform.runLater(openButton::fire);
        WaitForAsyncUtils.waitForFxEvents();

        FileInfo chosen = controller.getChosenFile();
        assertNotNull(chosen);
        assertEquals("/var/log/app.log", chosen.getPath());
        assertEquals("app.log", chosen.getName());
        assertEquals(FileInfo.SourceType.REMOTE, chosen.getSourceType());
        assertEquals(12804, controller.getTargetLine());
        assertFalse(controller.isTailAction());
    }

    @Test
    public void tailReturnsTailActionWithTargetLine() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        Platform.runLater(() -> resultTable.getSelectionModel().select(1));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(tailButton::fire);
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(controller.getChosenFile());
        assertEquals("/var/log/error.out", controller.getChosenFile().getPath());
        assertEquals(42, controller.getTargetLine());
        assertTrue(controller.isTailAction());
    }

    @Test
    public void copyLineCopiesMatchContent() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        Platform.runLater(() -> resultTable.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(copyLineButton::fire);
        WaitForAsyncUtils.waitForFxEvents();

        String copied = WaitForAsyncUtils.asyncFx(() -> Clipboard.getSystemClipboard().getString()).get();
        assertEquals("request timeout", copied);
    }

    @Test
    public void actionButtonsDisabledWithoutSelectionThenEnabled() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);

        assertTrue(openButton.isDisabled());
        assertTrue(tailButton.isDisabled());
        assertTrue(copyLineButton.isDisabled());
        assertTrue(copyPathButton.isDisabled());

        Platform.runLater(() -> resultTable.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(openButton.isDisabled());
        assertFalse(tailButton.isDisabled());
        assertFalse(copyLineButton.isDisabled());
        assertFalse(copyPathButton.isDisabled());
    }

    @Test
    public void cancelStopsSearchAndDropsLateResults() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.blockSearch = true;
        startSearch(ssh, "timeout");
        assertTrue(ssh.searchStarted.await(3, java.util.concurrent.TimeUnit.SECONDS));

        Platform.runLater(cancelSearchButton::fire);
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ssh.cancelCalled, "Cancel must disconnect the active remote command");
        assertTrue(statusLabel.getText().toLowerCase().contains("cancelled"));
        ssh.releaseSearch.countDown();
        waitUntil(() -> !searchButton.isDisabled());
        assertTrue(resultTable.getItems().isEmpty(), "Cancelled search must not publish late results");
    }

    @Test
    public void dialogRootCarriesScrollbarEnhancementStyleClass() throws Exception {
        javafx.scene.layout.BorderPane root = getField("rootPane");
        assertTrue(root.getStyleClass().contains("remote-log-search-dialog"),
                "Dialog must carry the style class used for grabbable scrollbars/divider");
    }

    @Test
    public void columnsResizeToWindowWithFlexibleContent() throws Exception {
        assertSame(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN,
                resultTable.getColumnResizePolicy(), "Table must use a constrained resize policy");
        javafx.scene.control.TableColumn<?, ?> line = getField("lineColumn");
        javafx.scene.control.TableColumn<?, ?> content = getField("contentColumn");
        assertFalse(line.isResizable(), "Line column stays fixed");
        assertEquals(Double.MAX_VALUE, content.getMaxWidth(), "Content column absorbs remaining width");
        assertTrue(content.getMinWidth() >= 200);
    }

    @Test
    public void tailInsideWindowPlansJumpIndex() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.lineCountOutput = "1000\n";
        controller.setMaxTailWindow(1000);
        prepareSingleMatch(ssh, "/a/app.log:990:timeout\n");

        Platform.runLater(tailButton::fire);
        waitUntil(() -> controller.getChosenFile() != null);

        assertTrue(controller.isTailAction());
        assertFalse(controller.isOpenInstead());
        assertEquals(1000, controller.getTailWindowLines());
        assertEquals(989, controller.getTailJumpIndex());
        assertEquals(990, controller.getTargetLine());
    }

    @Test
    public void tailTargetInHeadRecommendsLargerWindow() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.lineCountOutput = "100000\n";
        controller.setMaxTailWindow(100);
        com.seeloggyplus.util.TailJumpPlanner.Plan[] captured = new com.seeloggyplus.util.TailJumpPlanner.Plan[1];
        controller.setRecommendationDecision((match, total, plan) -> {
            captured[0] = plan;
            return RemoteLogSearchDialogController.TailChoice.TAIL_RECOMMENDED;
        });
        prepareSingleMatch(ssh, "/a/app.log:50:timeout\n");

        Platform.runLater(tailButton::fire);
        waitUntil(() -> controller.getChosenFile() != null);

        assertTrue(controller.isTailAction());
        assertFalse(controller.isOpenInstead());
        assertEquals(99951, controller.getTailWindowLines());
        assertEquals(0, controller.getTailJumpIndex());
        assertNotNull(captured[0]);
        assertEquals(99951, captured[0].neededLines());
        assertEquals(99901, captured[0].firstReachableLine());
    }

    @Test
    public void tailRecommendationCanChooseOpenInstead() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.lineCountOutput = "100000\n";
        controller.setMaxTailWindow(100);
        controller.setRecommendationDecision(
                (match, total, plan) -> RemoteLogSearchDialogController.TailChoice.OPEN_INSTEAD);
        prepareSingleMatch(ssh, "/a/app.log:50:timeout\n");

        Platform.runLater(tailButton::fire);
        waitUntil(() -> controller.getChosenFile() != null);

        assertFalse(controller.isTailAction());
        assertTrue(controller.isOpenInstead());
        assertEquals(50, controller.getTargetLine());
    }

    @Test
    public void unknownLineCountTailsWithoutJump() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.lineCountOutput = "";
        prepareSingleMatch(ssh, "/a/app.log:50:timeout\n");

        Platform.runLater(tailButton::fire);
        waitUntil(() -> controller.getChosenFile() != null);

        assertTrue(controller.isTailAction());
        assertEquals(0, controller.getTailWindowLines());
        assertEquals(-1, controller.getTailJumpIndex());
    }

    @Test
    public void validationPreventsSearchWithoutKeywordOrFolder() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        controller.setContext(ssh, server(), "/var/log");
        Platform.runLater(searchButton::fire);
        WaitForAsyncUtils.waitForFxEvents();

        assertNull(ssh.searchCommand);
        assertTrue(statusLabel.getText().contains("required"));
        assertTrue(resultTable.getItems().isEmpty());
    }
}
