package com.seeloggyplus.ui.search;

import com.seeloggyplus.util.IntArrayList;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canvas-based search result panel.
 * Renders matched lines with highlight, supports click-to-jump.
 */
public class SearchResultPanel extends VBox {

    // Rendering constants — match CanvasLogViewer style
    private static final int LINE_HEIGHT = 18;
    private static final int PADDING = 4;
    private static final Font MONO_FONT = Font.font("Consolas", FontWeight.NORMAL, 12);
    private static final Font LINE_NUM_FONT = Font.font("Consolas", FontWeight.NORMAL, 10);
    private static final Color BG_COLOR = Color.WHITE;
    private static final Color TEXT_COLOR = Color.BLACK;
    private static final Color LINE_NUM_COLOR = Color.GRAY;
    private static final Color LINE_NUM_BG = Color.rgb(245, 245, 245);
    private static final Color SELECTION_COLOR = Color.rgb(51, 153, 255, 0.3);
    private static final Color HIGHLIGHT_BG = Color.YELLOW;
    private static final Color HOVER_COLOR = Color.rgb(230, 240, 255);

    private final Label headerLabel;
    private final Canvas canvas;
    private final GraphicsContext gc;
    private final ScrollBar vScrollBar;
    private final ScrollBar hScrollBar;
    private final double charWidth;

    // Data
    private IntArrayList matchedLines;   // global line indexes
    private String[] lineCache;          // cached line content (preview)
    private int itemCount = 0;
    private long currentTopLine = 0;
    private double currentScrollX = 0;
    private int visibleLineCount = 30;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private int leftMargin = 60;

    // Search highlight
    private Pattern searchPattern;

    // Callbacks
    private SearchHitCallback onLineSelected;
    private LongFunction<String> lineContentResolver;

    @FunctionalInterface
    public interface SearchHitCallback {
        void onSelected(int listIndex, long globalLine, String content);
    }

    public SearchResultPanel() {
        // Measure char width
        Text measure = new Text("M");
        measure.setFont(MONO_FONT);
        charWidth = measure.getLayoutBounds().getWidth();

        headerLabel = new Label("Search Results");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 5;");

        canvas = new Canvas();
        gc = canvas.getGraphicsContext2D();
        gc.setTextBaseline(VPos.TOP);

        vScrollBar = new ScrollBar();
        vScrollBar.setOrientation(javafx.geometry.Orientation.VERTICAL);
        vScrollBar.setMin(0);
        vScrollBar.valueProperty().addListener((obs, o, n) -> {
            long newTop = Math.round(n.doubleValue());
            if (newTop != currentTopLine) {
                currentTopLine = newTop;
                render();
            }
        });

        hScrollBar = new ScrollBar();
        hScrollBar.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        hScrollBar.setMin(0);
        hScrollBar.valueProperty().addListener((obs, o, n) -> {
            double newX = n.doubleValue();
            if (newX != currentScrollX) {
                currentScrollX = newX;
                render();
            }
        });

        // Layout: canvas + scrollbar in a GridPane
        // Wrap canvas in a Pane so it resizes with layout
        javafx.scene.layout.Pane canvasHolder = new javafx.scene.layout.Pane(canvas);
        canvasHolder.setMinSize(0, 0);
        canvasHolder.setPrefSize(0, 0);

        // Bind canvas to holder size
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());

        GridPane canvasGrid = new GridPane();
        canvasGrid.setMinSize(0, 0);
        canvasGrid.add(canvasHolder, 0, 0);
        canvasGrid.add(vScrollBar, 1, 0);
        canvasGrid.add(hScrollBar, 0, 1);
        GridPane.setHgrow(canvasHolder, Priority.ALWAYS);
        GridPane.setVgrow(canvasHolder, Priority.ALWAYS);
        GridPane.setVgrow(vScrollBar, Priority.ALWAYS);
        GridPane.setHgrow(hScrollBar, Priority.ALWAYS);

        VBox.setVgrow(canvasGrid, Priority.ALWAYS);

        setSpacing(2);
        setPadding(new Insets(2));
        setMinWidth(120);
        setPrefWidth(250);
        setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(headerLabel, canvasGrid);

        setVisible(false);
        setManaged(false);

        // Resize handling — re-render and update scroll when canvas size changes
        canvas.widthProperty().addListener((obs, o, n) -> render());
        canvas.heightProperty().addListener((obs, o, n) -> {
            visibleLineCount = Math.max(1, (int) (n.doubleValue() / LINE_HEIGHT));
            updateScrollBar();
            render();
        });

        setupMouseHandlers();
    }

    public void setOnLineSelected(SearchHitCallback handler) {
        this.onLineSelected = handler;
    }

    public void setLineContentResolver(LongFunction<String> resolver) {
        this.lineContentResolver = resolver;
    }

    public void setSearchPattern(Pattern pattern) {
        this.searchPattern = pattern;
        render();
    }

    public void showResults(IntArrayList matchedIndexes, int totalLines) {
        if (matchedIndexes == null || matchedIndexes.size() == 0) {
            headerLabel.setText("No results");
            this.matchedLines = null;
            this.lineCache = null;
            this.itemCount = 0;
            this.selectedIndex = -1;
            currentTopLine = 0;
            setVisible(true);
            setManaged(true);
            render();
            return;
        }

        this.matchedLines = matchedIndexes;
        this.itemCount = matchedIndexes.size();
        this.lineCache = new String[itemCount];
        this.selectedIndex = -1;
        this.currentTopLine = 0;

        // Compute left margin based on max line number width
        int maxLineNum = matchedIndexes.get(itemCount - 1) + 1;
        int digits = String.valueOf(maxLineNum).length();
        leftMargin = (int) (digits * charWidth + 16);

        headerLabel.setText(itemCount + " results");
        setVisible(true);
        setManaged(true);
        updateScrollBar();
        render();
    }

    public void clear() {
        matchedLines = null;
        lineCache = null;
        itemCount = 0;
        selectedIndex = -1;
        hoveredIndex = -1;
        currentTopLine = 0;
        currentScrollX = 0;
        searchPattern = null;
        headerLabel.setText("Search Results");
        setVisible(false);
        setManaged(false);
    }

    /**
     * Append new matched indexes to the existing result set (used for live tail mode).
     * The lineCache is expanded to accommodate new entries.
     */
    public void appendResults(IntArrayList newMatchedIndexes) {
        if (newMatchedIndexes == null || newMatchedIndexes.size() == 0) return;

        if (matchedLines == null) {
            matchedLines = new IntArrayList();
        }

        int oldCount = itemCount;
        for (int i = 0; i < newMatchedIndexes.size(); i++) {
            matchedLines.add(newMatchedIndexes.get(i));
        }
        itemCount = matchedLines.size();

        // Expand line cache
        String[] newCache = new String[itemCount];
        if (lineCache != null) {
            System.arraycopy(lineCache, 0, newCache, 0, Math.min(lineCache.length, oldCount));
        }
        lineCache = newCache;

        // Update left margin based on max line number
        if (itemCount > 0) {
            int maxLineNum = matchedLines.get(itemCount - 1) + 1;
            int digits = String.valueOf(maxLineNum).length();
            leftMargin = (int) (digits * charWidth + 16);
        }

        headerLabel.setText(itemCount + " results");
        updateScrollBar();
        render();
    }

    /**
     * Trim the first {@code count} entries from the result set (used when tail buffer
     * is trimmed to stay within size limits).
     */
    public void trimFromStart(int count) {
        if (matchedLines == null || count <= 0) return;
        int removeCount = Math.min(count, itemCount);

        // Build new matchedLines without the first removeCount entries
        IntArrayList trimmed = new IntArrayList();
        for (int i = removeCount; i < itemCount; i++) {
            trimmed.add(matchedLines.get(i));
        }
        matchedLines = trimmed;
        itemCount = matchedLines.size();

        // Rebuild line cache
        String[] newCache = new String[itemCount];
        if (lineCache != null && lineCache.length > removeCount) {
            System.arraycopy(lineCache, removeCount, newCache, 0,
                    Math.min(lineCache.length - removeCount, itemCount));
        }
        lineCache = newCache;

        // Adjust selection
        if (selectedIndex >= 0) {
            selectedIndex -= removeCount;
            if (selectedIndex < 0) selectedIndex = -1;
        }

        // Adjust scroll position
        currentTopLine = Math.max(0, currentTopLine - removeCount);

        headerLabel.setText(itemCount + " results");
        updateScrollBar();
        render();
    }

    /**
     * Sync panel state with the shared matchedLines reference (used when the caller
     * has already added entries directly to the IntArrayList that was passed to showResults).
     */
    public void syncItemCount() {
        if (matchedLines == null) return;
        int newCount = matchedLines.size();
        if (newCount == itemCount) return;

        // Expand line cache
        if (lineCache == null || lineCache.length < newCount) {
            String[] newCache = new String[newCount];
            if (lineCache != null) {
                System.arraycopy(lineCache, 0, newCache, 0, Math.min(lineCache.length, itemCount));
            }
            lineCache = newCache;
        }
        itemCount = newCount;

        if (itemCount > 0) {
            int maxLineNum = matchedLines.get(itemCount - 1) + 1;
            int digits = String.valueOf(maxLineNum).length();
            leftMargin = (int) (digits * charWidth + 16);
        }

        headerLabel.setText(itemCount + " results");
        updateScrollBar();
        render();
    }

    public void selectIndex(int index) {
        if (index >= 0 && index < itemCount) {
            selectedIndex = index;
            // Ensure visible
            if (index < currentTopLine) {
                currentTopLine = index;
                vScrollBar.setValue(currentTopLine);
            } else if (index >= currentTopLine + visibleLineCount) {
                currentTopLine = index - visibleLineCount + 1;
                vScrollBar.setValue(currentTopLine);
            }
            render();
        }
    }

    private void updateScrollBar() {
        long max = Math.max(0, itemCount - visibleLineCount);
        vScrollBar.setMax(max);
        vScrollBar.setVisibleAmount(visibleLineCount);
        vScrollBar.setBlockIncrement(Math.max(1, visibleLineCount - 1));

        // Horizontal scrollbar — estimate max content width
        double visibleWidth = canvas.getWidth() - leftMargin;
        double maxLineWidth = 500 * charWidth; // max cached line length
        double maxScroll = Math.max(0, maxLineWidth - visibleWidth);
        hScrollBar.setMax(maxScroll);
        hScrollBar.setVisibleAmount(visibleWidth);
        hScrollBar.setBlockIncrement(visibleWidth / 2);
    }

    private String getLineContent(int matchIndex) {
        if (matchIndex < 0 || matchIndex >= itemCount) return "";
        if (lineCache[matchIndex] != null) return lineCache[matchIndex];

        if (lineContentResolver != null && matchedLines != null) {
            try {
                long globalIdx = matchedLines.get(matchIndex);
                String full = lineContentResolver.apply(globalIdx);
                if (full != null) {
                    // Limit to 500 chars for rendering performance
                    lineCache[matchIndex] = full.length() > 500 ? full.substring(0, 500) : full;
                    return lineCache[matchIndex];
                }
            } catch (Exception ignored) {}
        }
        lineCache[matchIndex] = "";
        return "";
    }

    private void render() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;

        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        if (matchedLines == null || itemCount == 0) return;

        int linesToRender = visibleLineCount + 1;

        // Clip content area (right of line numbers)
        gc.save();
        gc.beginPath();
        gc.rect(leftMargin, 0, width - leftMargin, height);
        gc.clip();
        gc.setFont(MONO_FONT);

        for (int i = 0; i < linesToRender; i++) {
            int matchIdx = (int) currentTopLine + i;
            if (matchIdx >= itemCount) break;

            double y = PADDING + i * LINE_HEIGHT;
            if (y + LINE_HEIGHT < 0 || y > height) continue;

            long globalLine = matchedLines.get(matchIdx);
            String line = getLineContent(matchIdx);
            double drawX = leftMargin - currentScrollX;

            // Hover background
            if (matchIdx == hoveredIndex && matchIdx != selectedIndex) {
                gc.setFill(HOVER_COLOR);
                gc.fillRect(leftMargin, y, width - leftMargin, LINE_HEIGHT);
            }

            // Selection background
            if (matchIdx == selectedIndex) {
                gc.setFill(SELECTION_COLOR);
                gc.fillRect(leftMargin, y, width - leftMargin, LINE_HEIGHT);
            }

            // Search highlight
            if (searchPattern != null && !line.isEmpty()) {
                gc.setFill(HIGHLIGHT_BG);
                Matcher m = searchPattern.matcher(line);
                while (m.find()) {
                    double startX = drawX + m.start() * charWidth;
                    double hlWidth = (m.end() - m.start()) * charWidth;
                    // Skip if entirely off-screen
                    if (startX + hlWidth < leftMargin) continue;
                    if (startX > width) break;
                    gc.fillRect(startX, y, hlWidth, LINE_HEIGHT);
                }
            }

            // Text content — only visible portion
            int visStart = Math.max(0, (int) ((leftMargin - drawX) / charWidth));
            int visEnd = Math.min(line.length(), (int) ((width - drawX) / charWidth) + 1);
            if (visStart < visEnd && visEnd > 0) {
                gc.setFill(TEXT_COLOR);
                gc.fillText(line.substring(visStart, visEnd), drawX + visStart * charWidth, y + 2);
            }
        }
        gc.restore();

        // Line number gutter
        gc.setFill(LINE_NUM_BG);
        gc.fillRect(0, 0, leftMargin - 4, height);
        gc.setFont(LINE_NUM_FONT);

        for (int i = 0; i < linesToRender; i++) {
            int matchIdx = (int) currentTopLine + i;
            if (matchIdx >= itemCount) break;

            double y = PADDING + i * LINE_HEIGHT;
            if (y + LINE_HEIGHT < 0 || y > height) continue;

            long globalLine = matchedLines.get(matchIdx);
            String lineNumStr = String.valueOf(globalLine + 1);
            double numX = leftMargin - 8 - lineNumStr.length() * charWidth;
            gc.setFill(LINE_NUM_COLOR);
            gc.fillText(lineNumStr, numX, y + 2);
        }
    }

    private void setupMouseHandlers() {
        canvas.setOnScroll(e -> {
            if (itemCount == 0) return;

            // Shift+scroll or horizontal scroll → horizontal
            if (e.isShiftDown() || Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY())) {
                double delta = e.getDeltaX() != 0 ? e.getDeltaX() : e.getDeltaY();
                double newX = currentScrollX - delta;
                newX = Math.max(0, Math.min(newX, hScrollBar.getMax()));
                if (newX != currentScrollX) {
                    currentScrollX = newX;
                    hScrollBar.setValue(currentScrollX);
                    render();
                }
            } else {
                // Vertical scroll
                double delta = -Math.round(e.getDeltaY() / 40.0) * 3;
                long newTop = (long) (currentTopLine + delta);
                long maxTop = Math.max(0, itemCount - visibleLineCount);
                newTop = Math.max(0, Math.min(newTop, maxTop));
                if (newTop != currentTopLine) {
                    currentTopLine = newTop;
                    vScrollBar.setValue(currentTopLine);
                    render();
                }
            }
            e.consume();
        });

        canvas.setOnMouseMoved(e -> {
            int idx = getIndexAtY(e.getY());
            if (idx != hoveredIndex) {
                hoveredIndex = idx;
                render();
            }
        });

        canvas.setOnMouseExited(e -> {
            if (hoveredIndex != -1) {
                hoveredIndex = -1;
                render();
            }
        });

        canvas.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            int idx = getIndexAtY(e.getY());
            if (idx >= 0 && idx < itemCount && matchedLines != null) {
                selectedIndex = idx;
                render();
                long globalLine = matchedLines.get(idx);
                String content = getLineContent(idx);
                if (onLineSelected != null) {
                    onLineSelected.onSelected(idx, globalLine, content);
                }
            }
        });
    }

    private int getIndexAtY(double y) {
        int viewOffset = (int) ((y - PADDING) / LINE_HEIGHT);
        int idx = (int) currentTopLine + viewOffset;
        if (idx >= 0 && idx < itemCount) return idx;
        return -1;
    }
}
