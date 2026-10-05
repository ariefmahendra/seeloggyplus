package com.seeloggyplus.features.detail.presentation;

import com.seeloggyplus.shared.ui.ToggleStyleSupport;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class LogDetailPaneController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(LogDetailPaneController.class);

    @FXML
    private VBox bottomPanel;
    @FXML
    private Label detailLabel;
    @FXML
    private StackPane detailContainer;
    @FXML
    private ToggleButton prettifyJsonButton;
    @FXML
    private ToggleButton prettifyXmlButton;
    @FXML
    private Button copyButton;
    @FXML
    private Button clearDetailButton;
    @FXML
    private Button pinBottomPanelButton;

    private StyleClassedTextArea detailCodeArea;
    private Label detailPlaceholderLabel;
    private String currentRawLogContent;
    private boolean autoPrettifyJson = false;
    private boolean autoPrettifyXml = false;
    private Runnable onCloseRequest = () -> { };
    private BiConsumer<String, Boolean> onPreferenceChanged = (code, value) -> { };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupDetailPanel();
        ToggleStyleSupport.configure(prettifyJsonButton);
        ToggleStyleSupport.configure(prettifyXmlButton);

        prettifyJsonButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
            autoPrettifyJson = newVal;
            onPreferenceChanged.accept("main_auto_prettify_json", newVal);
            if (newVal) {
                applyAutoPrettify();
            } else {
                restoreRawDetailIfNotPrettified();
            }
        });

        prettifyXmlButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
            autoPrettifyXml = newVal;
            onPreferenceChanged.accept("main_auto_prettify_xml", newVal);
            if (newVal) {
                applyAutoPrettify();
            } else {
                restoreRawDetailIfNotPrettified();
            }
        });

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

    public void setOnPreferenceChanged(BiConsumer<String, Boolean> onPreferenceChanged) {
        this.onPreferenceChanged = onPreferenceChanged != null ? onPreferenceChanged : (code, value) -> { };
    }

    public void setFontStyle(String fontStyle) {
        if (detailCodeArea != null) {
            detailCodeArea.setStyle(fontStyle);
        }
    }

    public void setAutoPrettify(boolean json, boolean xml) {
        this.autoPrettifyJson = json;
        this.autoPrettifyXml = xml;
        if (json || xml) {
            applyAutoPrettify();
        }
        prettifyJsonButton.setSelected(json);
        prettifyXmlButton.setSelected(xml);
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

        boolean prettified = false;
        if (autoPrettifyJson) {
            prettified = prettifyJson();
        }
        if (!prettified && autoPrettifyXml) {
            prettified = prettifyXml();
        }
        if (!prettified) {
            try {
                detailCodeArea.setStyleSpans(0, computeHighlightingSpans(content));
            } catch (Exception e) {
                logger.warn("Failed to apply detail highlighting", e);
            }
        }
    }

    public void clear() {
        Runnable r = () -> {
            if (detailCodeArea != null) {
                detailCodeArea.clear();
            }
            currentRawLogContent = null;
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
                "Select a log line from the table above to view formatted details, JSON/XML, or stack trace");
        detailPlaceholderLabel.getStyleClass().add("muted-italic");
        detailPlaceholderLabel.setWrapText(true);
        detailPlaceholderLabel.setAlignment(Pos.CENTER);

        detailContainer.getChildren().addAll(vsp, detailPlaceholderLabel);
    }

    private StyleSpans<Collection<String>> computeHighlightingSpans(String text) {
        return SyntaxHighlighter.computeLogHighlighting(text);
    }

    private void applyAutoPrettify() {
        if (currentRawLogContent == null && detailCodeArea != null) {
            currentRawLogContent = detailCodeArea.getText();
        }
        if (currentRawLogContent == null || currentRawLogContent.isEmpty()) {
            return;
        }

        boolean prettified = false;
        if (autoPrettifyJson) {
            prettified = prettifyJson();
        }
        if (!prettified && autoPrettifyXml) {
            prettifyXml();
        }
    }

    private void restoreRawDetailIfNotPrettified() {
        if (currentRawLogContent != null && detailCodeArea != null) {
            boolean prettified = false;
            if (autoPrettifyJson) {
                prettified = prettifyJson();
            }
            if (!prettified && autoPrettifyXml) {
                prettified = prettifyXml();
            }
            if (!prettified) {
                detailCodeArea.replaceText(currentRawLogContent);
                try {
                    detailCodeArea.setStyleSpans(0, computeHighlightingSpans(currentRawLogContent));
                } catch (Exception e) {
                    logger.warn("CSS load fail in restore raw detail");
                }
            }
        }
    }

    private boolean prettifyJson() {
        String sourceText = currentRawLogContent != null ? currentRawLogContent : detailCodeArea.getText();
        if (sourceText == null || sourceText.isEmpty()) {
            return false;
        }

        if (sourceText.length() > 4096) {
            CompletableFuture.supplyAsync(() -> {
                String prettified = JsonPrettify.prettifyFromLog(sourceText);
                if (prettified != null && !prettified.equals(sourceText)) {
                    StyleSpans<Collection<String>> spans = null;
                    try {
                        spans = SyntaxHighlighter.computeJsonHighlighting(prettified);
                    } catch (Exception ignored) {}
                    return new Object[] { prettified, spans };
                }
                return null;
            }).thenAccept(result -> {
                if (result != null && sourceText.equals(currentRawLogContent)) {
                    Platform.runLater(() -> {
                        String prettified = (String) result[0];
                        @SuppressWarnings("unchecked")
                        StyleSpans<Collection<String>> spans = (StyleSpans<Collection<String>>) result[1];
                        detailCodeArea.replaceText(prettified);
                        if (spans != null) {
                            try {
                                detailCodeArea.setStyleSpans(0, spans);
                            } catch (Exception ignored) {}
                        }
                    });
                }
            });
            return true;
        }

        String prettified = JsonPrettify.prettifyFromLog(sourceText);
        if (prettified != null && !prettified.equals(sourceText)) {
            detailCodeArea.replaceText(prettified);
            try {
                detailCodeArea.setStyleSpans(0, SyntaxHighlighter.computeJsonHighlighting(prettified));
            } catch (Exception e) {
                logger.warn("CSS load fail in pretty json", e);
            }
            return true;
        }
        return false;
    }

    private boolean prettifyXml() {
        String sourceText = currentRawLogContent != null ? currentRawLogContent : detailCodeArea.getText();
        if (sourceText == null || sourceText.isEmpty()) {
            return false;
        }

        if (sourceText.length() > 4096) {
            CompletableFuture.supplyAsync(() -> {
                String prettified = XmlPrettify.prettifyFromLog(sourceText);
                if (prettified != null && !prettified.equals(sourceText)) {
                    StyleSpans<Collection<String>> spans = null;
                    try {
                        spans = SyntaxHighlighter.computeXmlHighlighting(prettified);
                    } catch (Exception ignored) {}
                    return new Object[] { prettified, spans };
                }
                return null;
            }).thenAccept(result -> {
                if (result != null && sourceText.equals(currentRawLogContent)) {
                    Platform.runLater(() -> {
                        String prettified = (String) result[0];
                        @SuppressWarnings("unchecked")
                        StyleSpans<Collection<String>> spans = (StyleSpans<Collection<String>>) result[1];
                        detailCodeArea.replaceText(prettified);
                        if (spans != null) {
                            try {
                                detailCodeArea.setStyleSpans(0, spans);
                            } catch (Exception ignored) {}
                        }
                    });
                }
            });
            return true;
        }

        String prettified = XmlPrettify.prettifyFromLog(sourceText);
        if (prettified != null && !prettified.equals(sourceText)) {
            detailCodeArea.replaceText(prettified);
            try {
                detailCodeArea.setStyleSpans(0, SyntaxHighlighter.computeXmlHighlighting(prettified));
            } catch (Exception e) {
                logger.warn("CSS load fail in pretty xml", e);
            }
            return true;
        }
        return false;
    }

    private void copyDetailToClipboard() {
        String text = detailCodeArea.getText();
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }
}
