package com.seeloggyplus.features.detail.presentation;

import com.seeloggyplus.shared.util.JsonPrettify;
import com.seeloggyplus.shared.util.SyntaxHighlighter;
import com.seeloggyplus.shared.util.XmlPrettify;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Collection;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Tooltip;

public class LogDetailPaneController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(LogDetailPaneController.class);

    @FXML
    private VBox bottomPanel;
    @FXML
    private Label detailLabel;
    @FXML
    private StackPane detailContainer;
    @FXML
    private Button copyButton;
    @FXML
    private Button clearDetailButton;
    @FXML
    private Button pinBottomPanelButton;

    @FXML private ToggleButton rawModeButton;
    @FXML private ToggleButton formattedModeButton;
    private boolean formattedMode;
    private boolean updatingMode;
    private StyleClassedTextArea detailCodeArea;
    private Label detailPlaceholderLabel;
    private String currentRawLogContent;
    private volatile long formatGeneration;
    private FutureTask<Void> pendingFormat;
    private Executor formatExecutor = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread worker = new Thread(runnable, "log-detail-format");
                worker.setDaemon(true);
                return worker;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private Runnable onCloseRequest = () -> { };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupDetailPanel();
        var modes = new javafx.scene.control.ToggleGroup();
        rawModeButton.setToggleGroup(modes); formattedModeButton.setToggleGroup(modes);
        modes.selectedToggleProperty().addListener((o,a,b) -> {
            if (updatingMode) return;
            if (b == null) { modes.selectToggle(a); return; }
            formattedMode = b == formattedModeButton;
            refreshFormatting();
        });
        syncMode(false, false, "Select a log line containing JSON or XML");

        copyButton.setOnAction(e -> copyDetailToClipboard());
        clearDetailButton.setOnAction(e -> clear());
        pinBottomPanelButton.setOnAction(e -> onCloseRequest.run());
    }

    public VBox getRoot() {
        return bottomPanel;
    }

    public StyleClassedTextArea getCodeArea() {
        return detailCodeArea;
    }

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest != null ? onCloseRequest : () -> { };
    }

    public void setFontStyle(String fontStyle) {
        if (detailCodeArea != null) {
            detailCodeArea.setStyle(fontStyle);
        }
    }

    public void setDefaultFormatted(boolean formatted) {
        formattedMode = formatted;
        refreshFormatting();
    }

    public void show(long lineNumber, String content) {
        if (detailCodeArea == null || content == null) {
            return;
        }
        if (detailPlaceholderLabel != null) {
            detailPlaceholderLabel.setVisible(false);
            detailPlaceholderLabel.setManaged(false);
        }
        this.currentRawLogContent = content;
        detailCodeArea.clear();
        detailCodeArea.replaceText(0, 0, content);
        detailLabel.setText("Line " + (lineNumber + 1));

        refreshFormatting();
    }

    public void clear() {
        Runnable r = () -> {
            cancelFormatting();
            if (detailCodeArea != null) {
                detailCodeArea.clear();
            }
            currentRawLogContent = null;
            syncMode(false, false, "Select a log line containing JSON or XML");
            if (detailLabel != null) {
                detailLabel.setText("Log Detail");
            }
            if (detailPlaceholderLabel != null) {
                detailPlaceholderLabel.setVisible(true);
                detailPlaceholderLabel.setManaged(true);
            }
        };
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    private void setupDetailPanel() {
        detailCodeArea = new StyleClassedTextArea();
        detailCodeArea.setEditable(false);
        detailCodeArea.setWrapText(true);
        detailCodeArea.getStyleClass().add("detail-area");
        try {
            detailCodeArea.getStylesheets()
                    .add(Objects.requireNonNull(getClass().getResource("/style/richtext.css")).toExternalForm());
        } catch (Exception e) {
            logger.warn("CSS load fail");
        }
        VirtualizedScrollPane<StyleClassedTextArea> vsp = new VirtualizedScrollPane<>(detailCodeArea);

        detailPlaceholderLabel = new Label(
                "Select a log line to view formatted details, JSON/XML, or stack trace");
        detailPlaceholderLabel.getStyleClass().add("muted-italic");
        detailPlaceholderLabel.setWrapText(true);
        detailPlaceholderLabel.setAlignment(Pos.CENTER);

        detailContainer.getChildren().addAll(vsp, detailPlaceholderLabel);
    }

    private StyleSpans<Collection<String>> computeHighlightingSpans(String text) {
        return SyntaxHighlighter.computeLogHighlighting(text);
    }

    private void cancelFormatting() {
        formatGeneration++;
        if (pendingFormat != null) {
            pendingFormat.cancel(true);
            pendingFormat = null;
        }
    }

    private record Formatted(String text, StyleSpans<Collection<String>> spans, boolean supported) {}

    private Formatted format(String raw, boolean displayFormatted) {
        String text = raw;
        int syntax = 0;
        if ((raw.indexOf('{') >= 0 || raw.indexOf('[') >= 0) && JsonPrettify.containsJson(raw)) {
            syntax = 1;
            if (displayFormatted) text = JsonPrettify.prettifyFromLog(raw);
        } else if (raw.indexOf('<') >= 0 && !raw.contains("<!DOCTYPE") && !raw.contains("<!ENTITY")
                && !Thread.currentThread().isInterrupted() && XmlPrettify.containsXml(raw)) {
            syntax = 2;
            if (displayFormatted) text = XmlPrettify.prettifyFromLog(raw);
        }
        if (Thread.currentThread().isInterrupted()) return null;
        StyleSpans<Collection<String>> spans = !displayFormatted || syntax == 0 ? computeHighlightingSpans(text)
                : syntax == 1 ? SyntaxHighlighter.computeJsonHighlighting(text) : SyntaxHighlighter.computeXmlHighlighting(text);
        return new Formatted(text, spans, syntax != 0);
    }

    private void syncMode(boolean formatted, boolean supported, String explanation) {
        updatingMode = true;
        try {
            formattedModeButton.setDisable(!supported);
            formattedModeButton.setTooltip(new Tooltip(explanation));
            if (formatted) formattedModeButton.setSelected(true); else rawModeButton.setSelected(true);
        } finally { updatingMode = false; }
    }

    private void refreshFormatting() {
        cancelFormatting();
        String raw = currentRawLogContent;
        if (detailCodeArea == null) return;
        if (raw == null || raw.isEmpty()) { syncMode(false, false, "Select a log line containing JSON or XML"); return; }
        long generation = formatGeneration;
        boolean requestedMode = formattedMode;
        if (!requestedMode && !raw.equals(detailCodeArea.getText())) detailCodeArea.replaceText(raw);
        syncMode(false, false, "Checking for JSON or XML...");
        if (raw.length() <= 4096) {
            applyFormatted(format(raw, requestedMode), generation);
            return;
        }
        pendingFormat = new FutureTask<>(() -> {
            if (generation != formatGeneration) return null;
            Formatted formatted = format(raw, requestedMode);
            if (generation == formatGeneration && !Thread.currentThread().isInterrupted()) {
                Platform.runLater(() -> applyFormatted(formatted, generation));
            }
            return null;
        });
        formatExecutor.execute(pendingFormat);
    }

    private void applyFormatted(Formatted formatted, long generation) {
        if (generation != formatGeneration || formatted == null) return;
        syncMode(formattedMode && formatted.supported, formatted.supported,
                formatted.supported ? "Format detected JSON or XML; original log stays unchanged" : "No supported JSON/XML payload found");
        if (!formatted.text.equals(detailCodeArea.getText())) detailCodeArea.replaceText(formatted.text);
        if (formatted.spans != null) detailCodeArea.setStyleSpans(0, formatted.spans);
    }

    private void copyDetailToClipboard() {
        String text = detailCodeArea.getText();
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }
}
