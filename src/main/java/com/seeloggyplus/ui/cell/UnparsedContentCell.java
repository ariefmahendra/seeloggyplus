package com.seeloggyplus.ui.cell;

import com.seeloggyplus.model.LogEntry;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;

public class UnparsedContentCell extends TableCell<LogEntry, String> {
    private static final String STYLE_UNPARSED = "-fx-padding: 5px;";

    private Label contentLabel;

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setText(null);
            setGraphic(null);
            return;
        }

        TableRow<LogEntry> row = getTableRow();
        if (row == null) {
            setText(item);
            setGraphic(null);
            return;
        }

        LogEntry entry = row.getItem();
        if (entry == null || entry.isParsed()) {
            setText(item);
            setGraphic(null);
            return;
        }

        if (contentLabel == null) {
            contentLabel = new Label();
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(Double.MAX_VALUE);
            contentLabel.setStyle(STYLE_UNPARSED);
        }

        contentLabel.setText(formatUnparsedContent(item));
        setText(null);
        setGraphic(contentLabel);
    }

    private String formatUnparsedContent(String rawContent) {
        if (rawContent == null || rawContent.isEmpty()) {
            return rawContent;
        }

        int maxChars = 400;
        if (rawContent.length() <= maxChars) {
            return rawContent;
        }

        return rawContent.substring(0, maxChars) + "...";
    }
}
