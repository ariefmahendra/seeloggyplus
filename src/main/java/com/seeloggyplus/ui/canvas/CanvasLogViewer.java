package com.seeloggyplus.ui.canvas;

import com.seeloggyplus.util.LineOffsetIndex;
import com.seeloggyplus.util.MappedFileReader;
import javafx.animation.AnimationTimer;
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
    private long selectionAnchor = -1; // Anchor for Shift+Click range selection
    private boolean isDragging = false;
    private long dragStartViewLine = -1;
    private long dragEndViewLine = -1;
    private final java.util.Set<Long> selectedLineIndexes = new java.util.HashSet<>();

    // Context Menu State
    private ContextMenu currentContextMenu;

    // Smooth scroll state
    private double smoothScrollY = 0;       // Current smooth position (fractional lines)
    private double targetScrollY = 0;       // Target position set by mouse wheel
    private double scrollOffsetY = 0;       // Sub-pixel Y offset for rendering (0..LINE_HEIGHT)
    private boolean renderDirty = false;    // Coalesce renders to one per frame
    private boolean suppressScrollBarSync = false; // Prevent feedback loop
    private static final double SCROLL_LERP = 0.55; // Interpolation factor (higher = snappier)
    private static final double SCROLL_SNAP_THRESHOLD = 0.3; // Snap when close enough
    private AnimationTimer scrollAnimator;

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
        vScrollBar.setUnitIncrement(1); // Arrow button clicks scroll 1 line
        // Stabilize scrollbar thickness so layout math doesn't oscillate
        vScrollBar.setPrefWidth(16);
        vScrollBar.setMinWidth(16);

        hScrollBar = new ScrollBar();
        hScrollBar.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        hScrollBar.setMin(0);
        hScrollBar.setMax(2000);
        hScrollBar.setVisibleAmount(800);
        hScrollBar.setUnitIncrement(20); // Arrow button clicks scroll 20px horizontally
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
        setupSmoothScrollAnimator();

        // Ensure this control does not try to outgrow its parent
        setMinSize(0, 0);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Initial render
        render();
    }

    /**
     * Resets the viewer state (clears file reader, index, and lines).
     * Used when switching to a mode that doesn't use a local file (e.g., Remote
     * Tail).
     */
    public void resetView() {
        this.reader = null;
        this.index = null;
        this.fileLineCount = 0;
        this.totalLines = tailBuffer.size();
        this.currentTopLine = 0;
        this.smoothScrollY = 0;
        this.targetScrollY = 0;
        this.scrollOffsetY = 0;
        this.selectedLine = -1;
        this.selectionAnchor = -1;
        this.selectedLineIndexes.clear();
        this.filteredIndexes = null;
        this.filteredCount = 0;
        this.lineCacheStartLine = -1; // Invalidate cache
        this.followTail = true; // Always start following tail when resetting
        if (onFollowTailChanged != null) {
            onFollowTailChanged.accept(true);
        }

        leftMargin = 80; // Reset to default
        updateScrollBar();
        render();
        logger.info("CanvasLogViewer reset.");
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
        this.smoothScrollY = 0;
        this.targetScrollY = 0;
        this.scrollOffsetY = 0;
        this.selectedLine = -1;
        this.selectionAnchor = -1;
        this.selectedLineIndexes.clear();
        this.lineCacheStartLine = -1; // Invalidate cache to prevent stale content from previous file

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
        this.smoothScrollY = 0;
        this.targetScrollY = 0;
        this.scrollOffsetY = 0;
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
        lineCacheStartLine = -1; // Invalidate cache — tail content changed

        // Clamp currentTopLine so it doesn't point beyond available lines
        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
        long maxTop = Math.max(0, effectiveLines - visibleLineCount);
        if (currentTopLine > maxTop) {
            currentTopLine = maxTop;
            vScrollBar.setValue(currentTopLine);
        }

        if (followTail) {
            scrollToBottom();
        } else {
            updateScrollBar();
            render();
            fireStatusUpdate(effectiveLines);
        }
    }

    /**
     * Atomically transition from tail mode to file-only viewing.
     * Recalculates totalLines from fileLineCount only (tail buffer should already
     * be cleared by caller), disables followTail, clamps scroll position, and
     * re-renders. This is the single entry point for tail→file transition to
     * avoid scattered state updates.
     */
    public void exitTailMode() {
        this.followTail = false;
        if (onFollowTailChanged != null) {
            onFollowTailChanged.accept(false);
        }

        // Recalculate total from file + whatever is left in tail buffer (should be 0)
        this.totalLines = fileLineCount + tailBuffer.size();

        // Reset filter state that may have been set during tail
        this.filteredIndexes = null;
        this.filteredCount = 0;

        // Invalidate line cache to prevent stale tail content from being rendered
        this.lineCacheStartLine = -1;

        // Clamp scroll position to valid range
        long effectiveLines = totalLines;
        long maxTop = Math.max(0, effectiveLines - visibleLineCount);
        if (currentTopLine > maxTop) {
            currentTopLine = maxTop;
        }

        // Sync smooth scroll state
        smoothScrollY = currentTopLine;
        targetScrollY = currentTopLine;
        scrollOffsetY = 0;

        // Clear selection state
        this.selectedLine = -1;
        this.selectionAnchor = -1;
        this.selectedLineIndexes.clear();

        // Sync scrollbar and render
        vScrollBar.setValue(currentTopLine);
        updateScrollBar();
        render();
        fireStatusUpdate(effectiveLines);

        logger.info("exitTailMode: totalLines={}, fileLineCount={}, currentTopLine={}",
                totalLines, fileLineCount, currentTopLine);
    }

    public void scrollToBottom() {
        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
        long max = Math.max(0, effectiveLines - visibleLineCount);
        currentTopLine = max;
        smoothScrollY = max;
        targetScrollY = max;
        scrollOffsetY = 0;
        vScrollBar.setValue(max);
        render();
        fireStatusUpdate(effectiveLines);
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
        if (followTail) {
            setFollowTail(false);
        }
        currentTopLine = Math.max(0, Math.min(line, totalLines - visibleLineCount));
        selectedLine = line;
        smoothScrollY = currentTopLine;
        targetScrollY = currentTopLine;
        scrollOffsetY = 0;

        vScrollBar.setValue(currentTopLine);
        render();
        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
        fireStatusUpdate(effectiveLines);
    }

    // Line content cache — avoids re-reading and re-allocating strings for lines
    // that haven't changed between frames during smooth scroll
    private String[] lineCache = new String[0];
    private long lineCacheStartLine = -1;

    private void render() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        // Allow rendering if we have either a reader OR a tail buffer
        if (reader == null && tailBuffer.isEmpty()) {
            return;
        }

        long effectiveLineCount = (filteredIndexes != null) ? filteredCount : totalLines;

        // Render one extra line for smooth sub-pixel scrolling
        int linesToRender = visibleLineCount + 1;

        // Populate line cache — reuse strings when currentTopLine hasn't changed
        if (lineCache.length < linesToRender) {
            lineCache = new String[linesToRender + 10]; // small over-alloc
            lineCacheStartLine = -1; // force refill
        }
        if (lineCacheStartLine != currentTopLine) {
            lineCacheStartLine = currentTopLine;
            for (int i = 0; i < linesToRender; i++) {
                long viewIndex = currentTopLine + i;
                if (viewIndex >= effectiveLineCount) {
                    lineCache[i] = null;
                    continue;
                }
                long actualLineIndex = (filteredIndexes != null)
                        ? filteredIndexes.get((int) viewIndex) : viewIndex;
                lineCache[i] = getLineContent(actualLineIndex);
            }
        }

        gc.save();
        gc.beginPath();
        gc.rect(leftMargin, 0, width - leftMargin, height);
        gc.clip();

        // Set font once for all content lines
        gc.setFont(MONO_FONT);

        for (int i = 0; i < linesToRender; i++) {
            long viewIndex = currentTopLine + i;
            if (viewIndex >= effectiveLineCount) {
                break;
            }

            long actualLineIndex = (filteredIndexes != null)
                    ? filteredIndexes.get((int) viewIndex) : viewIndex;

            double y = PADDING + i * LINE_HEIGHT - scrollOffsetY;
            if (y + LINE_HEIGHT < 0 || y > height) continue; // off-screen

            String line = lineCache[i];
            if (line == null) line = "";
            renderLineContent(line, leftMargin, y, actualLineIndex);
        }
        gc.restore();

        // Line number gutter
        gc.setFill(LINE_NUM_BG);
        gc.fillRect(0, 0, leftMargin - 5, height);

        // Set font once for all line numbers
        gc.setFont(LINE_NUM_FONT);

        for (int i = 0; i < linesToRender; i++) {
            long viewIndex = currentTopLine + i;
            if (viewIndex >= effectiveLineCount)
                break;

            long actualLineIndex = (filteredIndexes != null)
                    ? filteredIndexes.get((int) viewIndex) : viewIndex;

            double y = PADDING + i * LINE_HEIGHT - scrollOffsetY;
            if (y + LINE_HEIGHT < 0 || y > height) continue;

            gc.setFill(LINE_NUM_COLOR);
            String lineNumStr = String.valueOf(actualLineIndex + 1);
            double numX = leftMargin - 10 - lineNumStr.length() * charWidth;
            gc.fillText(lineNumStr, numX, y + 2);
        }
    }

    private void renderLineContent(String line, double x, double y, long globalIndex) {
        double drawX = x - currentScrollX;
        double canvasWidth = canvas.getWidth();

        double lineWidth = line.length() * charWidth;
        if (drawX + lineWidth < leftMargin || drawX > canvasWidth) {
            return;
        }

        // Compute visible character range to avoid rendering thousands of off-screen glyphs
        int visStart = Math.max(0, (int) ((leftMargin - drawX) / charWidth));
        int visEnd = Math.min(line.length(), (int) ((canvasWidth - drawX) / charWidth) + 1);

        Color baseColor = TEXT_COLOR;

        boolean isSelected = selectedLineIndexes.contains(globalIndex);

        // 1. Draw Selection Background (FIRST)
        if (isSelected) {
            gc.setFill(SELECTION_COLOR);
            gc.fillRect(drawX, y, canvasWidth - leftMargin + currentScrollX, LINE_HEIGHT);
        }

        // 2. Draw Search Highlights — only scan visible portion
        if (searchPattern != null) {
            Matcher m = searchPattern.matcher(line);
            // Skip matches entirely before visible area
            gc.setFill(HIGHLIGHT_BG);
            while (m.find()) {
                if (m.end() < visStart) continue;
                if (m.start() > visEnd) break;
                double startX = drawX + m.start() * charWidth;
                double highlightWidth = (m.end() - m.start()) * charWidth;
                gc.fillRect(startX, y, highlightWidth, LINE_HEIGHT);
            }
        }

        // Log level detection — scan only first 120 chars (level keyword is always near the start)
        CharSequence levelScanRange = line.length() > 120 ? line.subSequence(0, 120) : line;
        Matcher levelMatcher = LEVEL_PATTERN.matcher(levelScanRange);
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

        // Only render the visible substring
        gc.setFill(baseColor);
        if (visStart > 0 || visEnd < line.length()) {
            String visibleText = line.substring(visStart, visEnd);
            gc.fillText(visibleText, drawX + visStart * charWidth, y + 2);
        } else {
            gc.fillText(line, drawX, y + 2);
        }
    }

    private void setupMouseHandlers() {
        canvas.setOnScroll(e -> {
            if (reader == null && tailBuffer.isEmpty()) {
                return;
            }

            if (e.isShiftDown() || Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY())) {
                double delta = e.getDeltaX() != 0 ? e.getDeltaX() : e.getDeltaY();
                double newVal = hScrollBar.getValue() - delta;
                hScrollBar.setValue(Math.max(0, Math.min(hScrollBar.getMax(), newVal)));
            } else {
                double deltaY = e.getDeltaY();
                // Notepad++ style: 3 lines per scroll notch, immediate, no animation
                long linesToScroll = Math.round(-deltaY / 40.0) * 3;
                if (linesToScroll == 0 && deltaY != 0) {
                    linesToScroll = deltaY > 0 ? -1 : 1;
                }

                // Smart Follow Logic:
                if (linesToScroll < 0 && followTail) {
                    followTail = false;
                    if (onFollowTailChanged != null)
                        onFollowTailChanged.accept(false);
                }

                long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
                long maxScroll = Math.max(0, effectiveLines - visibleLineCount);

                long newTop = Math.max(0, Math.min(maxScroll, currentTopLine + linesToScroll));

                // If scrolling to BOTTOM -> Turn ON follow
                if (newTop >= maxScroll) {
                    newTop = maxScroll;
                    if (!followTail) {
                        followTail = true;
                        if (onFollowTailChanged != null)
                            onFollowTailChanged.accept(true);
                    }
                }

                // Immediate snap — no animation
                currentTopLine = newTop;
                smoothScrollY = newTop;
                targetScrollY = newTop;
                scrollOffsetY = 0;
                suppressScrollBarSync = true;
                vScrollBar.setValue(newTop);
                suppressScrollBarSync = false;
                render();
                fireStatusUpdate(effectiveLines);
            }
            e.consume();
        });

        canvas.setOnMousePressed(e -> {
            if (reader == null && tailBuffer.isEmpty()) {
                return;
            }

            // Grab focus so keyboard navigation works on canvas
            canvas.requestFocus();

            // Hide previous context menu if visible
            if (currentContextMenu != null) {
                currentContextMenu.hide();
                currentContextMenu = null;
            }

            long viewLine = (long) ((e.getY() - PADDING + scrollOffsetY) / LINE_HEIGHT);
            if (viewLine < 0 || viewLine >= visibleLineCount + 1) {
                return;
            }

            long listIndex = currentTopLine + viewLine;
            long globalIndex;
            if (filteredIndexes != null && listIndex < filteredIndexes.size()) {
                globalIndex = filteredIndexes.get((int) listIndex);
            } else {
                globalIndex = listIndex;
            }

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
                long effective = (filteredIndexes != null) ? filteredCount : totalLines;

                if (e.isShiftDown() && selectionAnchor >= 0) {
                    // Shift+Click: range select from anchor to clicked line
                    long rangeStart = Math.min(selectionAnchor, listIndex);
                    long rangeEnd = Math.max(selectionAnchor, listIndex);
                    selectedLineIndexes.clear();

                    for (long idx = rangeStart; idx <= rangeEnd && idx < effective; idx++) {
                        long mappedIdx;
                        if (filteredIndexes != null) {
                            mappedIdx = filteredIndexes.get((int) idx);
                        } else {
                            mappedIdx = idx;
                        }
                        selectedLineIndexes.add(mappedIdx);
                    }

                    selectedLine = listIndex;
                    render();
                    // Fire click handler for the clicked line
                    handleMouseClick(e);
                } else {
                    // Normal click: set anchor and select single line
                    isDragging = true;
                    dragStartViewLine = viewLine;
                    dragEndViewLine = viewLine;
                    selectionAnchor = listIndex;
                    selectedLineIndexes.clear();

                    if (listIndex < effective) {
                        selectedLineIndexes.add(globalIndex);
                    }

                    handleMouseClick(e);
                    render();
                }
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (!isDragging)
                return;

            long viewLine = (long) ((e.getY() - PADDING + scrollOffsetY) / LINE_HEIGHT);
            // Clamp to visible area
            viewLine = Math.max(0, Math.min(viewLine, visibleLineCount));

            if (viewLine != dragEndViewLine) {
                dragEndViewLine = viewLine;
                selectedLineIndexes.clear();

                long start = Math.min(dragStartViewLine, dragEndViewLine);
                long end = Math.max(dragStartViewLine, dragEndViewLine);
                long effective = (filteredIndexes != null) ? filteredCount : totalLines;

                for (long i = start; i <= end; i++) {
                    long listIdx = currentTopLine + i;
                    if (listIdx >= effective)
                        break;

                    long mappedIdx;
                    if (filteredIndexes != null) {
                        mappedIdx = filteredIndexes.get((int) listIdx);
                    } else {
                        mappedIdx = listIdx;
                    }
                    selectedLineIndexes.add(mappedIdx);
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
        if (reader == null && tailBuffer.isEmpty()) {
            return;
        }

        int clickedViewOffset = (int) ((e.getY() - PADDING + scrollOffsetY) / LINE_HEIGHT);
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

            String lineContent = getLineContent(actualLineIndex);
            if (e.getClickCount() == 1 && onLineClick != null) {
                onLineClick.handle(actualLineIndex, lineContent);
            } else if (e.getClickCount() == 2 && onLineDoubleClick != null) {
                onLineDoubleClick.handle(actualLineIndex, lineContent);
            }
        }
    }

    private void setupKeyboardHandlers() {
        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(e -> {
            if (reader == null && tailBuffer.isEmpty()) {
                return;
            }

            if (e.getCode() == KeyCode.C && e.isControlDown()) {
                copySelectedLines();
                e.consume();
                return;
            }

            long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
            long maxTop = Math.max(0, effectiveLines - visibleLineCount);

            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP) {
                // Move selection by one line and scroll if needed
                long currentSel = selectedLine >= 0 ? selectedLine : currentTopLine;
                long newSel;
                if (e.getCode() == KeyCode.DOWN) {
                    newSel = Math.min(currentSel + 1, effectiveLines - 1);
                } else {
                    newSel = Math.max(currentSel - 1, 0);
                }

                // Update selection
                selectedLine = newSel;
                selectionAnchor = newSel;
                selectedLineIndexes.clear();
                long globalIdx = (filteredIndexes != null) ? filteredIndexes.get((int) newSel) : newSel;
                selectedLineIndexes.add(globalIdx);

                // Scroll to keep selection visible
                if (newSel < currentTopLine) {
                    currentTopLine = newSel;
                } else if (newSel >= currentTopLine + visibleLineCount) {
                    currentTopLine = newSel - visibleLineCount + 1;
                }
                currentTopLine = Math.max(0, Math.min(currentTopLine, maxTop));
                smoothScrollY = currentTopLine;
                targetScrollY = currentTopLine;
                scrollOffsetY = 0;
                vScrollBar.setValue(currentTopLine);
                lineCacheStartLine = -1;
                render();
                fireStatusUpdate(effectiveLines);

                // Fire click handler so detail panel updates
                String content = getLineContent(globalIdx);
                if (onLineClick != null) {
                    onLineClick.handle(globalIdx, content);
                }

                e.consume();
                return;
            }

            long newTop = currentTopLine;

            if (e.getCode() == KeyCode.PAGE_DOWN) {
                newTop = Math.min(currentTopLine + visibleLineCount, maxTop);
            } else if (e.getCode() == KeyCode.PAGE_UP) {
                newTop = Math.max(currentTopLine - visibleLineCount, 0);
            } else if (e.getCode() == KeyCode.HOME && e.isControlDown()) {
                newTop = 0;
            } else if (e.getCode() == KeyCode.END && e.isControlDown()) {
                newTop = maxTop;
            }

            if (newTop != currentTopLine) {
                currentTopLine = Math.max(0, newTop);
                smoothScrollY = currentTopLine;
                targetScrollY = currentTopLine;
                scrollOffsetY = 0;
                vScrollBar.setValue(currentTopLine);
                lineCacheStartLine = -1;
                render();
                fireStatusUpdate(effectiveLines);
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
            if (suppressScrollBarSync) return;
            long newTop = newVal.longValue();
            if (newTop != currentTopLine) {
                currentTopLine = newTop;
                // Sync smooth scroll state when scrollbar is dragged directly
                smoothScrollY = newTop;
                targetScrollY = newTop;
                scrollOffsetY = 0;
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

    private void setupSmoothScrollAnimator() {
        scrollAnimator = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double diff = targetScrollY - smoothScrollY;
                if (Math.abs(diff) < SCROLL_SNAP_THRESHOLD) {
                    // Close enough — snap to target
                    if (smoothScrollY != targetScrollY) {
                        smoothScrollY = targetScrollY;
                        applySmoothScroll();
                    }
                    if (renderDirty) {
                        renderDirty = false;
                        render();
                    }
                    // Animation complete — stop ticking until next scroll input
                    stop();
                    scrollAnimating = false;
                } else {
                    // Lerp toward target
                    smoothScrollY += diff * SCROLL_LERP;
                    applySmoothScroll();
                    renderDirty = false;
                    render();
                }
            }
        };
        // Don't start immediately — only start when scroll input arrives
        scrollAnimating = false;
    }

    private boolean scrollAnimating = false;

    /**
     * Kick the smooth scroll animation if not already running.
     */
    private void startScrollAnimation() {
        if (!scrollAnimating) {
            scrollAnimating = true;
            scrollAnimator.start();
        }
    }

    /**
     * Applies the smooth scroll position to currentTopLine and scrollOffsetY.
     */
    private void applySmoothScroll() {
        long newTopLine = (long) Math.floor(smoothScrollY);
        double fractional = smoothScrollY - newTopLine;
        scrollOffsetY = fractional * LINE_HEIGHT;

        if (newTopLine != currentTopLine) {
            currentTopLine = Math.max(0, newTopLine);
            suppressScrollBarSync = true;
            vScrollBar.setValue(currentTopLine);
            suppressScrollBarSync = false;
            long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
            fireStatusUpdate(effectiveLines);
        }
        renderDirty = true;
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
        void handle(long lineNumber, String lineContent);
    }

    @FunctionalInterface
    public interface LineDoubleClickHandler {
        void handle(long lineNumber, String lineContent);
    }

    public int getCurrentTopLine() {
        return (int) currentTopLine;
    }

    /** Returns the number of lines from the indexed file (excludes tail buffer). */
    public long getFileLineCount() {
        return fileLineCount;
    }

    /** Request focus on the canvas so keyboard navigation works. */
    public void requestCanvasFocus() {
        canvas.requestFocus();
    }

    public int getSelectedIndex() {
        return (int) selectedLine;
    }

    public int getItemCount() {
        return (int) ((filteredIndexes != null) ? filteredCount : totalLines);
    }

    public void selectLine(long line) {
        if (line >= 0 && line < getItemCount()) {
            selectedLine = line;
            selectedLineIndexes.clear();

            // Map view index to global index for selection set
            long globalIndex;
            if (filteredIndexes != null) {
                globalIndex = filteredIndexes.get((int) line);
            } else {
                globalIndex = line;
            }
            selectedLineIndexes.add(globalIndex);

            render();
        }
    }

    /**
     * Scans for the next line matching the current search pattern.
     * Used in Tail Mode (Highlight Only).
     */
    public int findNextMatch(int startLine, boolean forward) {
        if (searchPattern == null) {
            return -1;
        }

        int count = getItemCount();
        int current = startLine;

        // Safety bound to prevent infinite loops or freezing UI on massive logs
        // We scan at most 5000 lines per call for responsiveness
        int scanned = 0;
        int maxScan = 5000;

        while (scanned < maxScan) {
            if (current < 0 || current >= count) {
                break;
            }

            // Get content (mapped from filter if active, though tail mode usually isn't
            // filtered here)
            long globalIndex;
            if (filteredIndexes != null) {
                globalIndex = filteredIndexes.get(current);
            } else {
                globalIndex = current;
            }

            String lineContent = getLineContent(globalIndex);
            if (searchPattern.matcher(lineContent).find()) {
                return current;
            }

            if (forward) {
                current++;
            } else {
                current--;
            }
            scanned++;
        }

        return -1;
    }

    public int countMatches() {
        if (searchPattern == null) {
            return 0;
        }

        int count = getItemCount();
        int matches = 0;

        // Safety: Limit scan to avoid main thread freeze
        int scanLimit = 50000;
        int limit = Math.min(count, scanLimit);

        for (int i = 0; i < limit; i++) {
            // Get content
            long globalIndex;
            if (filteredIndexes != null) {
                globalIndex = filteredIndexes.get(i);
            } else {
                globalIndex = i;
            }

            String lineContent = getLineContent(globalIndex);
            if (searchPattern.matcher(lineContent).find()) {
                matches++;
            }
        }

        return matches;
    }

    public boolean hasSearchHighlight() {
        return searchPattern != null;
    }

}
