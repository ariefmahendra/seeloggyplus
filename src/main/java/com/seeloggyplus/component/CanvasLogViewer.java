package com.seeloggyplus.component;

import com.seeloggyplus.util.LineOffsetIndex;
import com.seeloggyplus.util.MappedFileReader;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.seeloggyplus.model.LogEntry;
import java.util.List;
import java.util.Collections;

/**
 * High-performance Canvas-based log viewer.
 * Renders only visible lines (~50) directly on Canvas.
 * No ListView, no ObservableList, no VirtualFlow.
 * Scroll: O(1) - just update currentTopLine and repaint
 * Memory: O(lineCount) for index, O(1) for rendering
 */
public class CanvasLogViewer extends GridPane {

    private static final Logger logger = LoggerFactory.getLogger(CanvasLogViewer.class);
    private static final int LINE_HEIGHT = 18;
    private static final int PADDING = 4;
    private int leftMargin = 80;
    private final double charWidth;
    private static final Font MONO_FONT = Font.font("Consolas", FontWeight.NORMAL, 13);
    private static final Font LINE_NUM_FONT = Font.font("Consolas", FontWeight.NORMAL, 11);

    private static final Color BG_COLOR = Color.WHITE;
    private static final Color TEXT_COLOR = Color.BLACK;
    private static final Color LINE_NUM_COLOR = Color.GRAY;
    private static final Color LINE_NUM_BG = Color.rgb(245, 245, 245);
    private static final Color SELECTION_COLOR = Color.rgb(51, 153, 255, 0.3);

    private static final Color ERROR_COLOR = Color.RED;
    private static final Color WARN_COLOR = Color.rgb(255, 140, 0);
    private static final Color INFO_COLOR = Color.rgb(0, 128, 0);
    private static final Color DEBUG_COLOR = Color.GRAY;

    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\b(ERROR|FATAL|WARN|WARNING|INFO|DEBUG|TRACE)\\b",
            Pattern.CASE_INSENSITIVE);

    private final Canvas canvas;
    private final ScrollBar vScrollBar;
    private final ScrollBar hScrollBar;
    private final GraphicsContext gc;

    private MappedFileReader reader;
    private LineOffsetIndex index;

    private long currentTopLine = 0;
    private double currentScrollX = 0;
    private long totalLines = 0;
    private long fileLineCount = 0; // Number of lines indexed from file
    private int visibleLineCount = 50;

    private List<LogEntry> tailBuffer = Collections.emptyList(); // Live buffer for tail mode

    private com.seeloggyplus.util.IntArrayList filteredIndexes = null;
    private long filteredCount = 0;

    private Pattern searchPattern = null;
    private static final Color HIGHLIGHT_BG = Color.YELLOW;

    private long selectedLine = -1;
    private boolean isDragging = false;
    private long dragStartViewLine = -1;
    private long dragEndViewLine = -1;
    private final java.util.Set<Long> selectedLineIndexes = new java.util.HashSet<>();

    // Context Menu State
    private ContextMenu currentContextMenu;

    @Setter
    private LineClickHandler onLineClick;
    @Setter
    private LineDoubleClickHandler onLineDoubleClick;

    // Status update callback for position info
    @FunctionalInterface
    public interface StatusUpdateHandler {
        void onStatusUpdate(long currentLine, long totalLines, double percentage);
    }

    @Setter
    private StatusUpdateHandler onStatusUpdate;

    public CanvasLogViewer() {
        Text text = new Text("A");
        text.setFont(MONO_FONT);
        this.charWidth = text.getLayoutBounds().getWidth();

        canvas = new Canvas(800, 600);
        gc = canvas.getGraphicsContext2D();
        gc.setTextBaseline(VPos.TOP);

        vScrollBar = new ScrollBar();
        vScrollBar.setOrientation(javafx.geometry.Orientation.VERTICAL);
        vScrollBar.setVisibleAmount(50);
        // Stabilize scrollbar thickness so layout math doesn't oscillate
        vScrollBar.setPrefWidth(16);
        vScrollBar.setMinWidth(16);

        hScrollBar = new ScrollBar();
        hScrollBar.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        hScrollBar.setMin(0);
        hScrollBar.setMax(2000);
        hScrollBar.setVisibleAmount(800);
        // Stabilize scrollbar thickness
        hScrollBar.setPrefHeight(16);
        hScrollBar.setMinHeight(16);

        // Simple layout: Canvas | ScrollBar
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.NEVER);
        getColumnConstraints().addAll(col1, col2);

        RowConstraints row1 = new RowConstraints();
        row1.setVgrow(Priority.ALWAYS);
        RowConstraints row2 = new RowConstraints();
        row2.setVgrow(Priority.NEVER);
        getRowConstraints().addAll(row1, row2);

        // Add children
        add(canvas, 0, 0);
        add(vScrollBar, 1, 0);
        add(hScrollBar, 0, 1);

        // Bindings
        setupScrollBarListener();
        setupMouseHandlers();
        setupKeyboardHandlers();
        setupResizeHandler();

        // Ensure this control does not try to outgrow its parent
        setMinSize(0, 0);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Initial render
        render();
    }

    /**
     * Load a file for viewing.
     */
    public void loadFile(MappedFileReader reader, LineOffsetIndex index) {
        this.reader = reader;
        this.index = index;
        this.fileLineCount = index.getLineCount();
        this.totalLines = fileLineCount + tailBuffer.size();
        this.currentTopLine = 0;
        this.selectedLine = -1;
        this.selectedLineIndexes.clear();

        // Calculate dynamic left margin based on max line number digits
        int maxDigits = String.valueOf(totalLines).length();
        leftMargin = (int) ((maxDigits + 1) * charWidth + 10); // +1 for padding, +10 for spacing

        updateScrollBar();
        render();

        logger.info("Loaded file with {} lines, leftMargin={}px", totalLines, leftMargin);
    }

    /**
     * Set filtered line indexes (for search results).
     * Pass null to clear filter and show all lines.
     */
    public void setFilteredIndexes(com.seeloggyplus.util.IntArrayList indexes) {
        this.filteredIndexes = indexes;
        this.filteredCount = (indexes != null) ? indexes.size() : totalLines;
        this.currentTopLine = 0;
        this.selectedLine = -1;
        this.selectedLineIndexes.clear();

        updateScrollBar();
        render();

        logger.info("Filter applied: showing {} of {} lines", filteredCount, totalLines);
    }

    /**
     * Clear filter and show all lines.
     */
    public void clearFilter() {
        setFilteredIndexes(null);
    }

    /**
     * Efficiently append a matching line to the current filter view.
     * Used for real-time tail filtering.
     */
    public void appendToFilter(long globalIndex) {
        if (filteredIndexes != null) {
            filteredIndexes.add((int) globalIndex);
            filteredCount++;
            // Don't need full render here, refreshTail() usually follows
        }
    }

    /**
     * Set the live tail buffer.
     * The viewer will treat these lines as continuation of the file.
     */
    private boolean followTail = true; // Default to true for tail mode

    // Callback to notify controller when internal state changes (e.g. auto disable)
    @Setter
    private java.util.function.Consumer<Boolean> onFollowTailChanged;

    public boolean isFollowTail() {
        return followTail;
    }

    public void setFollowTail(boolean followTail) {
        this.followTail = followTail;
        if (followTail) {
            scrollToBottom();
        }
    }

    /**
     * Set the live tail buffer.
     * The viewer will treat these lines as continuation of the file.
     */
    public void setTailBuffer(List<LogEntry> tailBuffer) {
        this.tailBuffer = (tailBuffer != null) ? tailBuffer : Collections.emptyList();
        this.totalLines = fileLineCount + this.tailBuffer.size();

        if (followTail) {
            scrollToBottom();
        } else {
            updateScrollBar(); // Just update bar size, don't move view
        }
    }

    /**
     * Refresh the view (e.g. when tail buffer updates).
     */
    public void refreshTail() {
        this.totalLines = fileLineCount + tailBuffer.size();

        if (followTail) {
            scrollToBottom();
        } else {
            updateScrollBar();
            render();
        }
    }

    public void scrollToBottom() {
        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
        long max = Math.max(0, effectiveLines - visibleLineCount);
        currentTopLine = max;
        vScrollBar.setValue(max);
        render();
    }

    /**
     * Set search term to highlight in rendered lines.
     */
    public void setSearchHighlight(String searchText, boolean isRegex, boolean caseSensitive) {
        if (searchText == null || searchText.isEmpty()) {
            this.searchPattern = null;
        } else {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                if (isRegex) {
                    this.searchPattern = Pattern.compile(searchText, flags);
                } else {
                    this.searchPattern = Pattern.compile(Pattern.quote(searchText), flags);
                }
            } catch (Exception e) {
                this.searchPattern = null;
            }
        }
        render();
    }

    /**
     * Clear search highlight.
     */
    public void clearSearchHighlight() {
        this.searchPattern = null;
        render();
    }

    /**
     * Jump to specific line number (0-based).
     */
    public void jumpToLine(long line) {
        currentTopLine = Math.max(0, Math.min(line, totalLines - visibleLineCount));
        selectedLine = line;

        vScrollBar.setValue(currentTopLine);
        render();
    }

    private void render() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        if (reader == null) {
            return;
        }

        long effectiveLineCount = (filteredIndexes != null) ? filteredCount : totalLines;
        gc.save();
        gc.beginPath();
        gc.rect(leftMargin, 0, width - leftMargin, height);
        gc.clip();

        for (int i = 0; i < visibleLineCount; i++) {
            long viewIndex = currentTopLine + i;
            if (viewIndex >= effectiveLineCount) {
                break;
            }

            long actualLineIndex;
            if (filteredIndexes != null) {
                actualLineIndex = filteredIndexes.get((int) viewIndex);
            } else {
                actualLineIndex = viewIndex;
            }

            int y = PADDING + i * LINE_HEIGHT;
            if (viewIndex == selectedLine) {
                gc.setFill(SELECTION_COLOR);
                gc.fillRect(leftMargin, y, width - leftMargin, LINE_HEIGHT);
            }

            String line = getLineContent(actualLineIndex);
            renderLineContent(line, leftMargin, y, i);
        }
        gc.restore();
        gc.setFill(LINE_NUM_BG);
        gc.fillRect(0, 0, leftMargin - 5, height);

        for (int i = 0; i < visibleLineCount; i++) {
            long viewIndex = currentTopLine + i;
            if (viewIndex >= effectiveLineCount)
                break;

            long actualLineIndex;
            if (filteredIndexes != null) {
                actualLineIndex = filteredIndexes.get((int) viewIndex);
            } else {
                actualLineIndex = viewIndex;
            }

            int y = PADDING + i * LINE_HEIGHT;

            gc.setFont(LINE_NUM_FONT);
            gc.setFill(LINE_NUM_COLOR);
            String lineNumStr = String.valueOf(actualLineIndex + 1);
            double numX = leftMargin - 10 - lineNumStr.length() * charWidth;
            gc.fillText(lineNumStr, numX, y + 2);
        }
    }

    private void renderLineContent(String line, double x, double y, long relativeIndex) {
        gc.setFont(MONO_FONT);
        double drawX = x - currentScrollX;

        double lineWidth = line.length() * charWidth;
        if (drawX + lineWidth < leftMargin || drawX > canvas.getWidth()) {
            return;
        }

        Color baseColor = TEXT_COLOR;

        long globalIndex = currentTopLine + relativeIndex;
        boolean isSelected = selectedLineIndexes.contains(globalIndex);

        // 1. Draw Selection Background (FIRST)
        if (isSelected) {
            gc.setFill(SELECTION_COLOR); // Transparent Blue
            gc.fillRect(drawX, y, canvas.getWidth() - leftMargin + currentScrollX, LINE_HEIGHT);
        }

        // 2. Draw Search Highlights (SECOND)
        if (searchPattern != null) {
            Matcher m = searchPattern.matcher(line);
            gc.setFill(HIGHLIGHT_BG);
            while (m.find()) {
                double startX = drawX + m.start() * charWidth;
                double highlightWidth = (m.end() - m.start()) * charWidth;
                gc.fillRect(startX, y, highlightWidth, LINE_HEIGHT);
            }
        }

        Matcher levelMatcher = LEVEL_PATTERN.matcher(line);
        if (levelMatcher.find()) {
            String level = levelMatcher.group(1).toUpperCase();
            baseColor = switch (level) {
                case "ERROR", "FATAL" -> ERROR_COLOR;
                case "WARN", "WARNING" -> WARN_COLOR;
                case "INFO" -> INFO_COLOR;
                case "DEBUG", "TRACE" -> DEBUG_COLOR;
                default -> TEXT_COLOR;
            };
        }

        gc.setFill(baseColor);
        gc.fillText(line, drawX, y + 2);
    }

    private void setupMouseHandlers() {
        canvas.setOnScroll(e -> {
            if (reader == null) {
                return;
            }

            if (e.isShiftDown() || Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY())) {
                double delta = e.getDeltaX() != 0 ? e.getDeltaX() : e.getDeltaY();
                double newVal = hScrollBar.getValue() - delta;
                hScrollBar.setValue(Math.max(0, Math.min(hScrollBar.getMax(), newVal)));
            } else {
                double deltaY = e.getDeltaY();
                long linesToScroll = Math.round(-deltaY / 30.0);
                if (linesToScroll == 0 && deltaY != 0) {
                    linesToScroll = deltaY > 0 ? -1 : 1;
                }

                // Smart Follow Logic:
                // If scrolling UP and follow is ON -> Turn OFF
                if (linesToScroll < 0 && followTail) {
                    followTail = false;
                    if (onFollowTailChanged != null)
                        onFollowTailChanged.accept(false);
                }

                long newTop = currentTopLine + linesToScroll;
                long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
                long maxScroll = Math.max(0, effectiveLines - visibleLineCount);

                // If scrolling to BOTTOM -> Turn ON
                if (newTop >= maxScroll) {
                    newTop = maxScroll;
                    if (!followTail) {
                        followTail = true;
                        if (onFollowTailChanged != null)
                            onFollowTailChanged.accept(true);
                    }
                }

                vScrollBar.setValue(Math.max(0, Math.min(maxScroll, newTop)));

                // Actual render happens in scrollbar listener
            }
            e.consume();
        });

        canvas.setOnMousePressed(e -> {
            if (reader == null) {
                return;
            }

            // Hide previous context menu if visible
            if (currentContextMenu != null) {
                currentContextMenu.hide();
                currentContextMenu = null;
            }

            long viewLine = (long) ((e.getY() - PADDING) / LINE_HEIGHT);
            if (viewLine < 0 || viewLine >= visibleLineCount) {
                return;
            }

            long globalIndex = currentTopLine + viewLine;

            if (e.getButton() == MouseButton.SECONDARY) {
                if (!selectedLineIndexes.contains(globalIndex)) {
                    selectedLineIndexes.clear();
                    selectedLineIndexes.add(globalIndex);
                    render();
                }

                ContextMenu contextMenu = new ContextMenu();
                MenuItem copyItem = new MenuItem("Copy Selection");
                copyItem.setOnAction(event -> copySelectedLines());
                contextMenu.getItems().add(copyItem);

                contextMenu.show(canvas, e.getScreenX(), e.getScreenY());
                currentContextMenu = contextMenu;
                return;
            }

            if (e.getButton() == MouseButton.PRIMARY) {
                isDragging = true;
                dragStartViewLine = viewLine;
                dragEndViewLine = viewLine;
                selectedLineIndexes.clear();
                selectedLineIndexes.add(globalIndex);
                handleMouseClick(e);
                render();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (!isDragging)
                return;

            long viewLine = (long) ((e.getY() - PADDING) / LINE_HEIGHT);
            // Clamp to visible area
            viewLine = Math.max(0, Math.min(viewLine, visibleLineCount - 1));

            if (viewLine != dragEndViewLine) {
                dragEndViewLine = viewLine;
                selectedLineIndexes.clear();

                long start = Math.min(dragStartViewLine, dragEndViewLine);
                long end = Math.max(dragStartViewLine, dragEndViewLine);

                for (long i = start; i <= end; i++) {
                    selectedLineIndexes.add(currentTopLine + i);
                }
                render();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                isDragging = false;
            }
        });
    }

    private void handleMouseClick(MouseEvent e) {
        if (reader == null) {
            return;
        }

        int clickedViewOffset = (int) ((e.getY() - PADDING) / LINE_HEIGHT);
        long clickedViewIndex = currentTopLine + clickedViewOffset;
        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;

        if (clickedViewIndex >= 0 && clickedViewIndex < effectiveLines) {
            selectedLine = clickedViewIndex;
            long actualLineIndex;
            if (filteredIndexes != null) {
                actualLineIndex = filteredIndexes.get((int) clickedViewIndex);
            } else {
                actualLineIndex = clickedViewIndex;
            }

            render();

            if (e.getClickCount() == 1 && onLineClick != null) {
                onLineClick.handle(actualLineIndex);
            } else if (e.getClickCount() == 2 && onLineDoubleClick != null) {
                onLineDoubleClick.handle(actualLineIndex, getLineContent(actualLineIndex));
            }
        }
    }

    private void setupKeyboardHandlers() {
        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(e -> {
            if (reader == null) {
                return;
            }

            if (e.getCode() == KeyCode.C && e.isControlDown()) {
                copySelectedLines();
                e.consume();
                return;
            }

            long newTop = currentTopLine;

            if (e.getCode() == KeyCode.DOWN) {
                newTop = Math.min(currentTopLine + 1, totalLines - visibleLineCount);
            } else if (e.getCode() == KeyCode.UP) {
                newTop = Math.max(currentTopLine - 1, 0);
            } else if (e.getCode() == KeyCode.PAGE_DOWN) {
                newTop = Math.min(currentTopLine + visibleLineCount, totalLines - visibleLineCount);
            } else if (e.getCode() == KeyCode.PAGE_UP) {
                newTop = Math.max(currentTopLine - visibleLineCount, 0);
            } else if (e.getCode() == KeyCode.HOME && e.isControlDown()) {
                newTop = 0;
            } else if (e.getCode() == KeyCode.END && e.isControlDown()) {
                newTop = totalLines - visibleLineCount;
            }

            if (newTop != currentTopLine) {
                currentTopLine = Math.max(0, newTop);
                vScrollBar.setValue(currentTopLine);
                render();
            }

            e.consume();
        });
    }

    private void copySelectedLines() {
        if (selectedLineIndexes.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        selectedLineIndexes.stream().sorted().forEach(globalIndex -> {
            long actualLineIndex = globalIndex;

            if (filteredIndexes != null) {
                if (globalIndex < filteredIndexes.size()) {
                    actualLineIndex = filteredIndexes.get(globalIndex.intValue());
                } else {
                    return;
                }
            }

            String line = getLineContent(actualLineIndex);
            sb.append(line).append(System.lineSeparator());
        });

        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void setupScrollBarListener() {
        vScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            long newTop = newVal.longValue();
            if (newTop != currentTopLine) {
                currentTopLine = newTop;
                render();
                // Update status bar
                long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
                fireStatusUpdate(effectiveLines);
            }
        });

        hScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            double newLeft = newVal.doubleValue();
            if (newLeft != currentScrollX) {
                currentScrollX = newLeft;
                render();
            }
        });
    }

    private void setupResizeHandler() {
        // Remove feedback loop: only react to *allocated* size changes, and compute
        // canvas size
        // using stable scrollbar thickness.
        widthProperty().addListener((obs, o, n) -> resizeCanvas());
        heightProperty().addListener((obs, o, n) -> resizeCanvas());

        // Also react if scrollbar gets skinned and its size changes
        vScrollBar.widthProperty().addListener((obs, o, n) -> resizeCanvas());
        hScrollBar.heightProperty().addListener((obs, o, n) -> resizeCanvas());

        // initial
        resizeCanvas();
    }

    private void resizeCanvas() {
        double sbW = vScrollBar.getWidth() > 0 ? vScrollBar.getWidth() : vScrollBar.getPrefWidth();
        double sbH = hScrollBar.getHeight() > 0 ? hScrollBar.getHeight() : hScrollBar.getPrefHeight();

        double canvasWidth = getWidth() - sbW;
        double canvasHeight = getHeight() - sbH;

        canvas.setWidth(Math.max(100, canvasWidth));
        canvas.setHeight(Math.max(100, canvasHeight));

        int newVisible = (int) (canvas.getHeight() / LINE_HEIGHT);
        if (newVisible != visibleLineCount) {
            visibleLineCount = Math.max(1, newVisible);
        }

        updateScrollBar();
        render();
    }

    private String getLineContent(long globalIndex) {
        if (globalIndex < fileLineCount) {
            return reader.readLine(index, globalIndex);
        } else {
            int bufferIndex = (int) (globalIndex - fileLineCount);
            if (bufferIndex >= 0 && bufferIndex < tailBuffer.size()) {
                LogEntry entry = tailBuffer.get(bufferIndex);
                return entry != null ? entry.getRawLog() : "";
            }
        }
        return "";
    }

    private void updateScrollBar() {
        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
        long max = Math.max(0, effectiveLines - visibleLineCount);
        vScrollBar.setMax(max);
        vScrollBar.setVisibleAmount(visibleLineCount);
        vScrollBar.setBlockIncrement(visibleLineCount - 1);
        double visibleWidth = canvas.getWidth() - leftMargin;
        double maxLineWidth = 30000;
        double maxScroll = Math.max(0, maxLineWidth - visibleWidth);
        hScrollBar.setMax(maxScroll);
        hScrollBar.setVisibleAmount(visibleWidth);
        hScrollBar.setBlockIncrement(visibleWidth / 2);

        // Fire status update
        fireStatusUpdate(effectiveLines);
    }

    private void fireStatusUpdate(long effectiveLines) {
        if (onStatusUpdate == null || effectiveLines <= 0)
            return;
        long currentLine = currentTopLine + 1; // 1-indexed for display
        double percentage = currentTopLine * 100.0 / effectiveLines;
        onStatusUpdate.onStatusUpdate(currentLine, effectiveLines, percentage);
    }

    @FunctionalInterface
    public interface LineClickHandler {
        void handle(long lineNumber);
    }

    @FunctionalInterface
    public interface LineDoubleClickHandler {
        void handle(long lineNumber, String lineContent);
    }
}
