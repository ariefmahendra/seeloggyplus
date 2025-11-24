package com.seeloggyplus.controller;

import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the Log Preview Dialog.
 * Shows the raw content of a log file, line by line.
 */
public class LogPreviewDialogController {

    private static final Logger logger = LoggerFactory.getLogger(LogPreviewDialogController.class);
    private static final int PREVIEW_LINE_LIMIT = 500;

    @FXML
    private Label fileNameLabel;
    @FXML
    private TableView<LogLine> logTableView;
    @FXML
    private TableColumn<LogLine, Long> lineColumn;
    @FXML
    private TableColumn<LogLine, String> contentColumn;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private Button closeButton;

    private final ObservableList<LogLine> logLines = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        lineColumn.setCellValueFactory(new PropertyValueFactory<>("lineNumber"));
        contentColumn.setCellValueFactory(new PropertyValueFactory<>("content"));
        logTableView.setItems(logLines);

        // Enable cell selection and copy-paste
        logTableView.getSelectionModel().setCellSelectionEnabled(true);
        logTableView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        logTableView.setOnKeyPressed(event -> {
            if (new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.C, javafx.scene.input.KeyCombination.CONTROL_DOWN).match(event)) {
                copySelectionToClipboard(logTableView);
                event.consume();
            }
        });

        // Make columns resizable
        lineColumn.prefWidthProperty().bind(logTableView.widthProperty().multiply(0.1)); // 10% width
        contentColumn.prefWidthProperty().bind(logTableView.widthProperty().multiply(0.9)); // 90% width

        closeButton.setOnAction(e -> closeDialog());
    }

    /**
     * Loads a preview of the file content (local or remote) into the table,
     * limited to the first PREVIEW_LINE_LIMIT lines.
     * @param fileInfo The file to preview.
     * @param sshService An active SSH service, if the file is remote. Can be null for local files.
     */
    public void loadFile(FileInfo fileInfo, SSHServiceImpl sshService) {
        fileNameLabel.setText(String.format("Preview: %s (first %d lines)", fileInfo.getPath(), PREVIEW_LINE_LIMIT));
        progressIndicator.setVisible(true);

        Task<List<String>> loadTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                if (fileInfo.getSourceType() == FileInfo.SourceType.REMOTE) {
                    if (sshService == null || !sshService.isConnected()) {
                        throw new IOException("SSH service is not connected.");
                    }
                    return sshService.readFileLines(fileInfo.getPath(), PREVIEW_LINE_LIMIT);
                } else {
                    List<String> lines = new ArrayList<>();
                    try (BufferedReader reader = new BufferedReader(new FileReader(fileInfo.getPath()))) {
                        String line;
                        int count = 0;
                        while ((line = reader.readLine()) != null && count < PREVIEW_LINE_LIMIT) {
                            lines.add(line);
                            count++;
                        }
                    }
                    return lines;
                }
            }
        };

        loadTask.setOnSucceeded(e -> {
            List<String> lines = loadTask.getValue();
            List<LogLine> convertedLines = new ArrayList<>();
            long lineNum = 1;
            for (String line : lines) {
                convertedLines.add(new LogLine(lineNum++, line));
            }
            logLines.setAll(convertedLines);
            progressIndicator.setVisible(false);
        });

        loadTask.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            Throwable ex = loadTask.getException();
            logger.error("Failed to load file for preview: {}", ex.getMessage(), ex);
            logLines.clear();
            logLines.add(new LogLine(1L, "ERROR: Could not load file."));
            logLines.add(new LogLine(2L, ex.getMessage()));
        });

        new Thread(loadTask).start();
    }

    private void copySelectionToClipboard(final javafx.scene.control.TableView<?> table) {
        final javafx.collections.ObservableList<javafx.scene.control.TablePosition> selectedCells = table.getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) {
            return;
        }

        // Group by row index
        final java.util.Map<Integer, java.util.List<javafx.scene.control.TablePosition>> rowMap = new java.util.TreeMap<>();
        for (final javafx.scene.control.TablePosition pos : selectedCells) {
            rowMap.computeIfAbsent(pos.getRow(), k -> new java.util.ArrayList<>()).add(pos);
        }

        final StringBuilder clipboardString = new StringBuilder();
        for (final java.util.List<javafx.scene.control.TablePosition> row : rowMap.values()) {
            // Sort cells by column index
            row.sort(java.util.Comparator.comparingInt(javafx.scene.control.TablePosition::getColumn));

            final String rowString = row.stream()
                    .map(pos -> {
                        final Object cellData = table.getColumns().get(pos.getColumn()).getCellData(pos.getRow());
                        return cellData == null ? "" : cellData.toString();
                    })
                    .collect(java.util.stream.Collectors.joining("\t"));
            clipboardString.append(rowString).append('\n');
        }

        final javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(clipboardString.toString());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    private void closeDialog() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Helper class to represent a line in the log file.
     */
    public static class LogLine {
        private final long lineNumber;
        private final String content;

        public LogLine(long lineNumber, String content) {
            this.lineNumber = lineNumber;
            this.content = content;
        }

        public long getLineNumber() {
            return lineNumber;
        }

        public String getContent() {
            return content;
        }
    }
}

