package com.seeloggyplus.controller;

import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.model.PreviewLine;
import com.seeloggyplus.model.RemoteLogSearchMatch;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.RemoteLogSearchService;
import com.seeloggyplus.service.impl.RemoteLogSearchServiceImpl;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.util.FxTextHighlighter;
import com.seeloggyplus.util.ScrollBarThumbEnhancer;
import com.seeloggyplus.util.TailJumpPlanner;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Dialog controller for finding log files on a remote server by searching
 * file contents, with lazy line-context preview and open-at-line support.
 */
public class RemoteLogSearchDialogController {

    private static final Logger logger = LoggerFactory.getLogger(RemoteLogSearchDialogController.class);

    private static final int DEFAULT_CONTEXT = 5;
    private static final int PREVIEW_DEBOUNCE_MS = 150;

    @FXML
    private javafx.scene.layout.BorderPane rootPane;
    @FXML
    private TextField rootPathField;
    @FXML
    private TextField keywordField;
    @FXML
    private CheckBox regexCheckBox;
    @FXML
    private CheckBox caseSensitiveCheckBox;
    @FXML
    private Button searchButton;
    @FXML
    private Button cancelSearchButton;
    @FXML
    private Button openButton;
    @FXML
    private Button tailButton;
    @FXML
    private Button copyPathButton;
    @FXML
    private Button copyLineButton;
    @FXML
    private Button closeButton;
    @FXML
    private TableView<RemoteLogSearchMatch> resultTable;
    @FXML
    private TableColumn<RemoteLogSearchMatch, String> fileColumn;
    @FXML
    private TableColumn<RemoteLogSearchMatch, String> lineColumn;
    @FXML
    private TableColumn<RemoteLogSearchMatch, String> contentColumn;
    @FXML
    private ListView<PreviewLine> previewList;
    @FXML
    private Label previewLabel;
    @FXML
    private ComboBox<Integer> contextSelector;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressIndicator progressIndicator;

    private SSHServiceImpl sshService;
    private SSHServerModel server;
    private RemoteLogSearchService searchService = new RemoteLogSearchServiceImpl();
    private Task<List<RemoteLogSearchMatch>> currentSearchTask;
    private Task<List<PreviewLine>> currentPreviewTask;
    private final AtomicLong previewGeneration = new AtomicLong();
    private PauseTransition previewDebounce;
    private Pattern highlightPattern;
    private FileInfo chosenFile;
    private int targetLine;
    private boolean tailAction;
    private boolean openInstead;
    private int tailWindowLines;
    private int tailJumpIndex = -1;
    private int maxTailWindow = 20000;
    private TailRecommendationDecision recommendationDecision = this::showTailRecommendationDialog;

    /** Decision returned when a match is in the head and the tail window must grow. */
    enum TailChoice {
        TAIL_RECOMMENDED, OPEN_INSTEAD, CANCEL
    }

    @FunctionalInterface
    interface TailRecommendationDecision {
        TailChoice decide(RemoteLogSearchMatch match, long totalLines, TailJumpPlanner.Plan plan);
    }

    public void setContext(SSHServiceImpl sshService, SSHServerModel server, String initialPath) {
        this.sshService = sshService;
        this.server = server;
        if (initialPath != null && !initialPath.isBlank() && rootPathField != null) {
            rootPathField.setText(initialPath);
        }
    }

    void setSearchService(RemoteLogSearchService searchService) {
        this.searchService = searchService;
    }

    void setMaxTailWindow(int maxTailWindow) {
        this.maxTailWindow = Math.max(1, maxTailWindow);
    }

    /** Injected for tests so the warning dialog can be resolved without user interaction. */
    void setRecommendationDecision(TailRecommendationDecision decision) {
        this.recommendationDecision = decision != null ? decision : this::showTailRecommendationDialog;
    }

    @FXML
    public void initialize() {
        if (rootPane != null) {
            rootPane.getStyleClass().add("remote-log-search-dialog");
        }
        fileColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().path()));
        lineColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(String.valueOf(cell.getValue().lineNumber())));
        contentColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().content()));
        lineColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        // Columns always fit the window: fixed narrow line column, content absorbs the rest.
        lineColumn.setResizable(false);
        lineColumn.setMinWidth(70);
        lineColumn.setPrefWidth(80);
        lineColumn.setMaxWidth(90);
        fileColumn.setMinWidth(120);
        contentColumn.setMinWidth(200);
        contentColumn.setMaxWidth(Double.MAX_VALUE);
        resultTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        contentColumn.setCellFactory(col -> new TableCell<>() {
            private final Rectangle clip = new Rectangle();
            {
                clip.widthProperty().bind(widthProperty());
                clip.heightProperty().bind(heightProperty());
                setClip(clip);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    setGraphic(FxTextHighlighter.build(item, highlightPattern));
                }
            }
        });

        resultTable.setItems(FXCollections.observableArrayList());
        resultTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    updateActionState(newVal);
                    schedulePreview();
                });
        resultTable.setRowFactory(tv -> {
            TableRow<RemoteLogSearchMatch> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    resultTable.getSelectionModel().select(row.getItem());
                    handleOpen();
                }
            });
            return row;
        });
        resultTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleOpen();
                event.consume();
            }
        });

        previewList.setItems(FXCollections.observableArrayList());
        previewList.setCellFactory(list -> new PreviewLineCell());

        contextSelector.setItems(FXCollections.observableArrayList(1, 3, 5, 10));
        contextSelector.getSelectionModel().select(Integer.valueOf(DEFAULT_CONTEXT));
        contextSelector.valueProperty().addListener((obs, oldVal, newVal) -> schedulePreview());

        keywordField.setOnAction(e -> handleSearch());
        rootPathField.setOnAction(e -> handleSearch());
        updateActionState(null);

        Platform.runLater(() -> {
            if (closeButton != null && closeButton.getScene() != null
                    && closeButton.getScene().getWindow() instanceof Stage stage) {
                stage.setOnCloseRequest(e -> cancelRunningWork());
            }
        });
    }

    @FXML
    private void handleSearch() {
        if (currentSearchTask != null && currentSearchTask.isRunning()) {
            return;
        }
        String root = rootPathField.getText();
        String keyword = keywordField.getText();
        if (root == null || root.isBlank() || keyword == null || keyword.isEmpty()) {
            statusLabel.setText("Folder and keyword are required.");
            return;
        }
        if (sshService == null || !sshService.isConnected()) {
            statusLabel.setText("SSH connection is not active.");
            return;
        }

        boolean regex = regexCheckBox.isSelected();
        boolean caseSensitive = caseSensitiveCheckBox.isSelected();
        highlightPattern = buildHighlightPattern(keyword, regex, caseSensitive);
        resultTable.getItems().clear();
        previewList.getItems().clear();
        previewLabel.setText("Preview");
        setSearching(true);
        statusLabel.setText("Searching...");

        Task<List<RemoteLogSearchMatch>> task = new Task<>() {
            @Override
            protected List<RemoteLogSearchMatch> call() throws Exception {
                return searchService.search(sshService, root, keyword, regex, caseSensitive);
            }
        };
        task.setOnSucceeded(e -> {
            if (task.isCancelled()) return;
            List<RemoteLogSearchMatch> results = task.getValue();
            resultTable.getItems().setAll(results);
            Platform.runLater(() -> ScrollBarThumbEnhancer.enhance(resultTable));
            statusLabel.setText(results.size() + " matching line(s)");
            setSearching(false);
        });
        task.setOnFailed(e -> {
            if (task.isCancelled()) return;
            Throwable ex = task.getException();
            logger.warn("Remote log search failed: {}", ex == null ? "unknown" : ex.getMessage());
            statusLabel.setText("Search failed: " + (ex == null ? "unknown error" : ex.getMessage()));
            setSearching(false);
        });
        task.setOnCancelled(e -> {
            statusLabel.setText("Search cancelled.");
            setSearching(false);
        });
        currentSearchTask = task;
        Thread worker = new Thread(task, "RemoteLogSearch");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleCancelSearch() {
        cancelRunningWork();
        statusLabel.setText("Search cancelled.");
        setSearching(false);
    }

    private void schedulePreview() {
        RemoteLogSearchMatch selected = resultTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            cancelPreview();
            previewList.getItems().clear();
            previewLabel.setText("Preview");
            return;
        }
        if (previewDebounce == null) {
            previewDebounce = new PauseTransition(Duration.millis(PREVIEW_DEBOUNCE_MS));
            previewDebounce.setOnFinished(e -> loadPreview());
        }
        previewDebounce.playFromStart();
    }

    private void loadPreview() {
        RemoteLogSearchMatch selected = resultTable.getSelectionModel().getSelectedItem();
        if (selected == null || sshService == null || !sshService.isConnected()) {
            previewList.getItems().clear();
            return;
        }
        cancelPreview();
        long generation = previewGeneration.incrementAndGet();
        int context = contextSelector.getValue() == null ? DEFAULT_CONTEXT : contextSelector.getValue();
        previewLabel.setText("Preview: " + selected.path() + "  (line " + selected.lineNumber() + ")");

        Task<List<PreviewLine>> task = new Task<>() {
            @Override
            protected List<PreviewLine> call() throws Exception {
                return searchService.preview(sshService, selected.path(), selected.lineNumber(), context, context);
            }
        };
        task.setOnSucceeded(e -> {
            if (generation != previewGeneration.get() || task.isCancelled()) return;
            previewList.getItems().setAll(task.getValue());
            Platform.runLater(() -> ScrollBarThumbEnhancer.enhance(previewList));
        });
        task.setOnFailed(e -> {
            if (generation != previewGeneration.get() || task.isCancelled()) return;
            previewList.getItems().clear();
        });
        currentPreviewTask = task;
        Thread worker = new Thread(task, "RemoteLogSearchPreview");
        worker.setDaemon(true);
        worker.start();
    }

    private void cancelPreview() {
        if (previewDebounce != null) {
            previewDebounce.stop();
        }
        if (currentPreviewTask != null && currentPreviewTask.isRunning()) {
            if (sshService != null) {
                sshService.cancelActiveCommand();
            }
            currentPreviewTask.cancel(true);
        }
    }

    private void cancelRunningWork() {
        cancelPreview();
        if (sshService != null) {
            sshService.cancelActiveCommand();
        }
        if (currentSearchTask != null && currentSearchTask.isRunning()) {
            currentSearchTask.cancel(true);
        }
    }

    private void setSearching(boolean searching) {
        searchButton.setDisable(searching);
        cancelSearchButton.setDisable(!searching);
        progressIndicator.setVisible(searching);
    }

    private void updateActionState(RemoteLogSearchMatch selected) {
        boolean hasSelection = selected != null;
        openButton.setDisable(!hasSelection);
        tailButton.setDisable(!hasSelection);
        copyPathButton.setDisable(!hasSelection);
        copyLineButton.setDisable(!hasSelection);
    }

    @FXML
    private void handleOpen() {
        RemoteLogSearchMatch selected = resultTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        chosenFile = buildFileInfo(selected);
        targetLine = selected.lineNumber();
        tailAction = false;
        openInstead = true;
        tailWindowLines = 0;
        tailJumpIndex = -1;
        closeDialog();
    }

    @FXML
    private void handleTail() {
        RemoteLogSearchMatch selected = resultTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (sshService == null || !sshService.isConnected()) {
            statusLabel.setText("SSH connection is not active.");
            return;
        }
        statusLabel.setText("Checking file length for tail...");
        Task<Long> task = new Task<>() {
            @Override
            protected Long call() throws Exception {
                return searchService.lineCount(sshService, selected.path());
            }
        };
        task.setOnSucceeded(e -> resolveTail(selected, task.getValue() == null ? -1 : task.getValue()));
        task.setOnFailed(e -> resolveTail(selected, -1));
        Thread worker = new Thread(task, "RemoteLogSearchLineCount");
        worker.setDaemon(true);
        worker.start();
    }

    private void resolveTail(RemoteLogSearchMatch selected, long totalLines) {
        TailJumpPlanner.Plan plan = TailJumpPlanner.plan(totalLines, selected.lineNumber(), maxTailWindow);
        statusLabel.setText("");

        if (plan.mode() == TailJumpPlanner.Mode.UNKNOWN) {
            chosenFile = buildFileInfo(selected);
            targetLine = selected.lineNumber();
            tailAction = true;
            openInstead = false;
            tailWindowLines = 0;
            tailJumpIndex = -1;
            closeDialog();
            return;
        }

        if (plan.mode() == TailJumpPlanner.Mode.REACHABLE) {
            chosenFile = buildFileInfo(selected);
            targetLine = selected.lineNumber();
            tailAction = true;
            openInstead = false;
            tailWindowLines = plan.tailLines();
            tailJumpIndex = plan.jumpIndex();
            closeDialog();
            return;
        }

        showTailRecommendation(selected, totalLines, plan);
    }

    private void showTailRecommendation(RemoteLogSearchMatch selected, long totalLines, TailJumpPlanner.Plan plan) {
        TailChoice choice = recommendationDecision.decide(selected, totalLines, plan);
        if (choice == null || choice == TailChoice.CANCEL) {
            return;
        }
        chosenFile = buildFileInfo(selected);
        targetLine = selected.lineNumber();
        if (choice == TailChoice.OPEN_INSTEAD) {
            tailAction = false;
            openInstead = true;
            tailWindowLines = 0;
            tailJumpIndex = -1;
        } else {
            tailAction = true;
            openInstead = false;
            tailWindowLines = plan.tailLines();
            tailJumpIndex = plan.jumpIndex();
        }
        closeDialog();
    }

    private TailChoice showTailRecommendationDialog(RemoteLogSearchMatch selected, long totalLines,
            TailJumpPlanner.Plan plan) {
        ButtonType tailRecommend = new ButtonType(
                "Tail last " + plan.neededLines() + " lines", ButtonBar.ButtonData.OK_DONE);
        ButtonType openJump = new ButtonType("Open & Jump (download)", ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.WARNING);
        if (closeButton != null && closeButton.getScene() != null) {
            alert.initOwner(closeButton.getScene().getWindow());
        }
        alert.setTitle("Tail Window Too Small");
        alert.setHeaderText("Line " + selected.lineNumber() + " is in the head of the file");
        alert.setContentText(String.format(
                "The file has %,d lines. The maximum tail window (%,d lines) only reaches line %,d to the end.%n%n"
                        + "To reach line %,d, tail the last %,d lines.%n%nChoose an action:",
                totalLines, maxTailWindow, plan.firstReachableLine(), selected.lineNumber(), plan.neededLines()));
        alert.getButtonTypes().setAll(tailRecommend, openJump, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancel) {
            return TailChoice.CANCEL;
        }
        return result.get() == openJump ? TailChoice.OPEN_INSTEAD : TailChoice.TAIL_RECOMMENDED;
    }

    @FXML
    private void handleCopyPath() {
        RemoteLogSearchMatch selected = resultTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        copyToClipboard(selected.path());
        statusLabel.setText("Path copied to clipboard.");
    }

    @FXML
    private void handleCopyLine() {
        RemoteLogSearchMatch selected = resultTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        copyToClipboard(selected.content());
        statusLabel.setText("Line copied to clipboard.");
    }

    private void copyToClipboard(String value) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
    }

    static Pattern buildHighlightPattern(String query, boolean regex, boolean caseSensitive) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        try {
            return Pattern.compile(regex ? query : Pattern.quote(query), flags);
        } catch (Exception e) {
            return Pattern.compile(Pattern.quote(query), flags);
        }
    }

    @FXML
    private void handleClose() {
        cancelRunningWork();
        closeDialog();
    }

    private void closeDialog() {
        if (closeButton != null && closeButton.getScene() != null
                && closeButton.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }

    private FileInfo buildFileInfo(RemoteLogSearchMatch match) {
        String path = match.path();
        String name = path;
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) {
            name = path.substring(slash + 1);
        }
        return new FileInfo(name, path, 0, false, 0, FileInfo.SourceType.REMOTE);
    }

    public FileInfo getChosenFile() {
        return chosenFile;
    }

    public int getTargetLine() {
        return targetLine;
    }

    public boolean isTailAction() {
        return tailAction;
    }

    /** True when the user chose Open & Jump even after a tail recommendation. */
    public boolean isOpenInstead() {
        return openInstead;
    }

    /** Number of trailing lines to tail (0 = use the default window). */
    public int getTailWindowLines() {
        return tailWindowLines;
    }

    /** 0-based index within the tail buffer of the target line, or -1. */
    public int getTailJumpIndex() {
        return tailJumpIndex;
    }

    private class PreviewLineCell extends ListCell<PreviewLine> {
        private final Rectangle clip = new Rectangle();

        private PreviewLineCell() {
            clip.widthProperty().bind(widthProperty());
            clip.heightProperty().bind(heightProperty());
            setClip(clip);
        }

        @Override
        protected void updateItem(PreviewLine item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().remove("preview-target-line");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(null);
            HBox row = new HBox();
            row.setSpacing(0);
            row.setFillHeight(false);
            row.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            javafx.scene.control.Label prefix = new javafx.scene.control.Label(
                    (item.target() ? "\u25B8 " : "  ") + item.lineNumber() + "  ");
            prefix.getStyleClass().add("preview-line-number");
            prefix.setPadding(javafx.geometry.Insets.EMPTY);
            row.getChildren().add(prefix);
            FxTextHighlighter.apply(row, item.text(), highlightPattern);
            setGraphic(row);
            if (item.target()) {
                getStyleClass().add("preview-target-line");
            }
        }
    }
}
