package com.seeloggyplus.features.search.presentation;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.DialogPresenter;

import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.PreviewLine;
import com.seeloggyplus.features.search.domain.RemoteLogSearchMatch;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.util.FxTextHighlighter;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import com.seeloggyplus.shared.util.TailJumpPlanner;
import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

@ExtendWith(ApplicationExtension.class)
public class RemoteLogSearchDialogControllerTest {

    private RemoteLogSearchDialogController controller;
    private Stage stage;
    private TableView<RemoteLogSearchMatch> resultTable;
    private ListView<PreviewLine> previewList;
    private TextField rootPathField;
    private TextField keywordField;
    private CheckBox regexCheckBox;
    private CheckBox caseSensitiveCheckBox;
    private ComboBox<Integer> contextSelector;
    private Button searchButton;
    private Button cancelSearchButton;
    private Button openButton;
    private Button tailButton;
    private Button copyLineButton;
    private Button copyPathButton;
    private Label statusLabel;
    private Label previewLabel;
    private final FakePresenter presenter = new FakePresenter();

    @Start
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RemoteLogSearchDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        controller.setDialogPresenter(presenter);
        stage.setScene(AppTheme.scene(root));

        stage.setWidth(1100);
        stage.setHeight(760);
        stage.show();
        stage.centerOnScreen();

        resultTable = getField("resultTable");
        previewList = getField("previewList");
        rootPathField = getField("rootPathField");
        keywordField = getField("keywordField");
        regexCheckBox = getField("regexCheckBox");
        caseSensitiveCheckBox = getField("caseSensitiveCheckBox");
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
        boolean failSearch;
        boolean failPreview;
        int searchCommandCount;
        String searchOutput = "/var/log/app.log:12804:request timeout\n/var/log/error.out:42:timeout after 30s\n";
        String previewOutput = "12803:reset\n12804:request timeout\n12805:retry\n";
        String lineCountOutput = "20000\n";
        final CountDownLatch searchStarted = new CountDownLatch(1);
        final CountDownLatch releaseSearch = new CountDownLatch(1);
        final CountDownLatch previewAttempted = new CountDownLatch(1);

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String executeCommand(String command) throws IOException {
            if (command.contains("grep")) {
                searchCommand = command;
                searchCommandCount++;
                if (failSearch) {
                    throw new IOException("search exploded");
                }
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
            previewAttempted.countDown();
            if (failPreview) {
                throw new IOException("preview exploded");
            }
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

        waitUntil(this::previewCellRendered);
    }

    private boolean previewCellRendered() throws Exception {
        return WaitForAsyncUtils.asyncFx(() -> {
            previewList.applyCss();
            previewList.layout();
            for (Node node : previewList.lookupAll(".list-cell")) {
                if (node instanceof ListCell<?> cell
                        && cell.getGraphic() instanceof HBox) {
                    return true;
                }
            }
            return false;
        }).get();
    }

    private Set<Node> findHighlights(Parent root) throws Exception {
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

        Set<Node> highlights = findHighlights(resultTable);
        assertFalse(highlights.isEmpty(), "Result content must highlight the searched keyword");
    }

    @Test
    public void previewHighlightsTheSearchedKeyword() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        selectFirstMatchAndWaitForPreview();

        Set<Node> highlights = findHighlights(previewList);
        assertFalse(highlights.isEmpty(), "Preview must highlight the searched keyword");
    }

    @Test
    public void previewCellsDoNotWrapAndAreClipped() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        selectFirstMatchAndWaitForPreview();

        Set<Node> cells = WaitForAsyncUtils.asyncFx(() -> {
            previewList.applyCss();
            previewList.layout();
            return previewList.lookupAll(".list-cell");
        }).get();

        assertFalse(cells.isEmpty(), "Preview cells must be rendered");
        boolean checkedGraphic = false;
        for (Node node : cells) {
            assertNotNull(node.getClip(), "Each preview cell must clip its content to stay single-line");
            if (node instanceof ListCell<?> cell
                    && cell.getGraphic() instanceof HBox) {
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
        assertTrue(ssh.searchStarted.await(3, TimeUnit.SECONDS));

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
        BorderPane root = getField("rootPane");
        assertTrue(root.getStyleClass().contains("remote-log-search-dialog"),
                "Dialog must carry the style class used for grabbable scrollbars/divider");
    }

    @Test
    public void columnsResizeToWindowWithFlexibleContent() throws Exception {
        assertSame(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN,
                resultTable.getColumnResizePolicy(), "Table must use a constrained resize policy");
        TableColumn<?, ?> line = getField("lineColumn");
        TableColumn<?, ?> content = getField("contentColumn");
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
        TailJumpPlanner.Plan[] captured = new TailJumpPlanner.Plan[1];
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

    @Test
    public void defaultRecommendationTailsWithTheSuggestedWindow() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.lineCountOutput = "100000\n";
        controller.setMaxTailWindow(100);
        presenter.responder = dialog -> pickButton(dialog, "Tail last 99951 lines");
        prepareSingleMatch(ssh, "/a/app.log:50:timeout\n");

        Platform.runLater(tailButton::fire);
        waitUntil(() -> controller.getChosenFile() != null);

        assertTrue(controller.isTailAction());
        assertFalse(controller.isOpenInstead());
        assertEquals(99951, controller.getTailWindowLines());
        assertEquals(0, controller.getTailJumpIndex());
        Alert alert = firstAlert();
        assertEquals("Tail Window Too Small", alert.getTitle());
        assertTrue(alert.getContentText().contains("100,000 lines"));
    }

    @Test
    public void defaultRecommendationCanOpenInsteadOfTailing() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.lineCountOutput = "100000\n";
        controller.setMaxTailWindow(100);
        presenter.responder = dialog -> pickButton(dialog, "Open & Jump (download)");
        prepareSingleMatch(ssh, "/a/app.log:50:timeout\n");

        Platform.runLater(tailButton::fire);
        waitUntil(() -> controller.getChosenFile() != null);

        assertFalse(controller.isTailAction());
        assertTrue(controller.isOpenInstead());
        assertEquals(50, controller.getTargetLine());
    }

    @Test
    public void defaultRecommendationCanBeCancelled() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.lineCountOutput = "100000\n";
        controller.setMaxTailWindow(100);
        presenter.responder = dialog -> pickButton(dialog, "Cancel");
        prepareSingleMatch(ssh, "/a/app.log:50:timeout\n");

        Platform.runLater(tailButton::fire);
        waitUntil(() -> !presenter.dialogs.isEmpty());
        WaitForAsyncUtils.waitForFxEvents();

        assertNull(controller.getChosenFile(), "cancelling the dialog must not choose a file");
        assertFalse(controller.isTailAction());
        assertFalse(controller.isOpenInstead());
    }

    @Test
    public void copyPathCopiesTheSelectedPath() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        Platform.runLater(() -> resultTable.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(copyPathButton::fire);
        WaitForAsyncUtils.waitForFxEvents();

        String copied = WaitForAsyncUtils.asyncFx(() -> Clipboard.getSystemClipboard().getString()).get();
        assertEquals("/var/log/app.log", copied);
        assertEquals("Path copied to clipboard.", statusLabel.getText());
    }

    @Test
    public void regexAndCaseSensitiveFlagsAreForwarded() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.searchOutput = "/var/log/app.log:1:Request Timeout\n";
        Platform.runLater(() -> {
            regexCheckBox.setSelected(true);
            caseSensitiveCheckBox.setSelected(true);
        });
        startSearch(ssh, "Time.*");
        waitUntil(() -> resultTable.getItems().size() == 1);

        assertTrue(ssh.searchCommand.contains("grep -nEH"), ssh.searchCommand);
        assertFalse(findHighlights(resultTable).isEmpty());

        ssh.searchOutput = "/var/log/app.log:2:Timeout\n";
        Platform.runLater(() -> regexCheckBox.setSelected(false));
        startSearch(ssh, "Timeout");
        waitUntil(() -> ssh.searchCommandCount == 2);

        assertTrue(ssh.searchCommand.contains("grep -nFH"), ssh.searchCommand);
    }

    @Test
    public void searchFailureReportsTheReasonAndReenablesTheButton() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.failSearch = true;
        startSearch(ssh, "timeout");

        waitUntil(() -> statusLabel.getText().startsWith("Search failed"));
        assertTrue(statusLabel.getText().contains("search exploded"));
        assertFalse(searchButton.isDisabled());
    }

    @Test
    public void previewFailureLeavesThePreviewEmpty() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.failPreview = true;
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);

        Platform.runLater(() -> resultTable.getSelectionModel().select(0));
        assertTrue(ssh.previewAttempted.await(3, TimeUnit.SECONDS));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(previewList.getItems().isEmpty(), "a failed preview must not render stale content");
    }

    @Test
    public void doubleClickOnARowChoosesTheFile() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);

        Node row = findNonEmptyRow();
        assertNotNull(row, "the result table must render row cells");
        Platform.runLater(() -> Event.fireEvent(row, doubleClickEvent()));

        waitUntil(() -> controller.getChosenFile() != null);
        assertEquals("/var/log/app.log", controller.getChosenFile().getPath());
        assertFalse(controller.isTailAction());
    }

    @Test
    public void enterKeyOnTheTableOpensTheSelection() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        startSearch(ssh, "timeout");
        waitUntil(() -> resultTable.getItems().size() == 2);
        Platform.runLater(() -> resultTable.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> Event.fireEvent(resultTable,
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false)));

        waitUntil(() -> controller.getChosenFile() != null);
        assertEquals("/var/log/app.log", controller.getChosenFile().getPath());
    }

    @Test
    public void enterInTheFieldsRunsTheSearch() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        controller.setContext(ssh, server(), "/var/log");
        Platform.runLater(() -> {
            keywordField.setText("timeout");
            rootPathField.fireEvent(new ActionEvent());
        });
        waitUntil(() -> ssh.searchCommandCount == 1);

        Platform.runLater(() -> {
            keywordField.setText("timeout");
            keywordField.fireEvent(new ActionEvent());
        });
        waitUntil(() -> ssh.searchCommandCount == 2);
    }

    @Test
    public void closingTheWindowCancelsRunningWork() throws Exception {
        SearchMockSsh ssh = new SearchMockSsh();
        ssh.blockSearch = true;
        startSearch(ssh, "timeout");
        assertTrue(ssh.searchStarted.await(3, TimeUnit.SECONDS));

        Platform.runLater(() -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ssh.cancelCalled, "closing the dialog must cancel the running remote command");
        assertTrue(statusLabel.getText().toLowerCase().contains("cancelled"));
        ssh.releaseSearch.countDown();
    }

    private Optional<ButtonType> pickButton(Dialog<?> dialog, String text) {
        if (dialog instanceof Alert alert) {
            return alert.getButtonTypes().stream()
                    .filter(button -> button.getText().equals(text))
                    .findFirst();
        }
        return Optional.empty();
    }

    private Alert firstAlert() {
        return (Alert) presenter.dialogs.stream().filter(d -> d instanceof Alert).findFirst().orElseThrow();
    }

    private Node findNonEmptyRow() throws Exception {
        return WaitForAsyncUtils.asyncFx(() -> {
            resultTable.applyCss();
            resultTable.layout();
            for (Node node : resultTable.lookupAll(".table-row-cell")) {
                if (node instanceof TableRow<?> row && row.getItem() != null) {
                    return node;
                }
            }
            return null;
        }).get();
    }

    private static MouseEvent doubleClickEvent() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, true, false, false, null);
    }

    private static final class FakePresenter implements DialogPresenter {
        final List<Dialog<?>> dialogs = new ArrayList<>();
        Function<Dialog<?>, Optional<?>> responder = dialog -> Optional.empty();

        @Override
        public void present(Stage dialog) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> present(Dialog<T> dialog) {
            dialogs.add(dialog);
            return (Optional<T>) responder.apply(dialog);
        }
    }
}
