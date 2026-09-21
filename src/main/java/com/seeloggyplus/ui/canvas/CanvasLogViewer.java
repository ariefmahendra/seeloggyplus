package com.seeloggyplus.ui.canvas;

import com.seeloggyplus.util.LineOffsetIndex;
import com.seeloggyplus.util.MappedFileReader;
import javafx.animation.AnimationTimer;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import javafx.scene.input.KeyEvent;

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

    // Palette — swapped at runtime between light and dark (Canvas ignores CSS).
    private Color bgColor;
    private Color textColor;
    private Color lineNumColor;
    private Color lineNumBg;
    private Color selectionColor;
    private Color fatalText;
    private Color errorText;
    private Color warnText;
    private Color infoText;
    private Color debugText;
    private Color traceText;
    private Color fatalBar;
    private Color errorBar;
    private Color warnBar;
    private Color fatalTint;
    private Color errorTint;
    private Color warnTint;
    private Color highlightBg;
    private boolean darkMode;

    // Level codes: 0 none, 1 FATAL, 2 ERROR, 3 WARN, 4 INFO, 5 DEBUG, 6 TRACE
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
        // Stabilize scrollbar thickness so layout math doesn't oscillate.
        // Slightly wider so the up/down step buttons are easy to click.
        vScrollBar.setPrefWidth(14);
        vScrollBar.setMinWidth(14);
        vScrollBar.setMaxWidth(14);
        vScrollBar.getStyleClass().add("log-scroll-bar");

        hScrollBar = new ScrollBar();
        hScrollBar.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        hScrollBar.setMin(0);
        hScrollBar.setMax(2000);
        hScrollBar.setVisibleAmount(800);
        hScrollBar.setUnitIncrement(20); // Arrow button clicks scroll 20px horizontally
        // Stabilize scrollbar thickness
        hScrollBar.setPrefHeight(10);
        hScrollBar.setMinHeight(10);
        hScrollBar.setMaxHeight(10);

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

        // Initial palette (light); switched automatically when the scene theme changes.
        applyPalette(false);

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Parent themeRoot = newScene.getRoot();
                if (themeRoot != null) {
                    themeRoot.getStyleClass().addListener(
                            (ListChangeListener<String>) change -> updateThemeFromScene());
                }
                updateThemeFromScene();
            }
        });

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
        this.tailBuffer = Collections.emptyList();
        this.totalLines = 0;
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
        suppressScrollBarSync = true;
        vScrollBar.setValue(0);
        hScrollBar.setValue(0);
        suppressScrollBarSync = false;
        render();
        fireStatusUpdate(getEffectiveLineCount());
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

        this.followTail = false;
        if (onFollowTailChanged != null) {
            onFollowTailChanged.accept(false);
        }

        updateScrollBar();
        suppressScrollBarSync = true;
        vScrollBar.setValue(0);
        hScrollBar.setValue(0);
        suppressScrollBarSync = false;
        render();
        fireStatusUpdate(getEffectiveLineCount());

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
        this.lineCacheStartLine = -1;

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

    public com.seeloggyplus.util.IntArrayList getFilteredIndexes() {
        return filteredIndexes;
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
        if (onFollowTailChanged != null) {
            onFollowTailChanged.accept(followTail);
        }
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
        updateScrollBar();
        suppressScrollBarSync = true;
        vScrollBar.setValue(currentTopLine);
        suppressScrollBarSync = false;
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
        updateScrollBar();
        suppressScrollBarSync = true;
        vScrollBar.setValue(max);
        suppressScrollBarSync = false;
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
     * If filtered, accepts either a view index (0 <= line < filteredCount) or a global line index.
     */
    public void jumpToLine(long line) {
        if (followTail) {
            setFollowTail(false);
        }
        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
        if (effectiveLines <= 0) {
            return;
        }

        long targetViewIndex = line;
        long targetGlobalLine = line;

        if (filteredIndexes != null) {
            if (line >= 0 && line < filteredCount) {
                targetViewIndex = line;
                targetGlobalLine = filteredIndexes.get((int) line);
            } else {
                int foundIdx = filteredIndexes.indexOf((int) line);
                if (foundIdx >= 0) {
                    targetViewIndex = foundIdx;
                    targetGlobalLine = line;
                } else {
                    targetViewIndex = 0;
                    targetGlobalLine = filteredIndexes.isEmpty() ? 0 : filteredIndexes.get(0);
                }
            }
        }

        long maxTop = Math.max(0, effectiveLines - visibleLineCount);
        currentTopLine = Math.max(0, Math.min(targetViewIndex, maxTop));
        selectedLine = targetViewIndex;
        selectedLineIndexes.clear();
        selectedLineIndexes.add(targetGlobalLine);
        smoothScrollY = currentTopLine;
        targetScrollY = currentTopLine;
        scrollOffsetY = 0;
        lineCacheStartLine = -1;

        updateScrollBar();
        suppressScrollBarSync = true;
        vScrollBar.setValue(currentTopLine);
        suppressScrollBarSync = false;
        render();
        fireStatusUpdate(effectiveLines);
    }

    // Line content cache — avoids re-reading and re-allocating strings for lines
    // that haven't changed between frames during smooth scroll
    private String[] lineCache = new String[0];
    private int[] lineLevelCache = new int[0];
    private long lineCacheStartLine = -1;

    private static int detectLevelCode(String line) {
        if (line == null || line.isEmpty()) return 0;
        CharSequence levelScanRange = line.length() > 120 ? line.subSequence(0, 120) : line;
        Matcher levelMatcher = LEVEL_PATTERN.matcher(levelScanRange);
        if (levelMatcher.find()) {
            String level = levelMatcher.group(1).toUpperCase();
            return switch (level) {
                case "FATAL" -> 1;
                case "ERROR" -> 2;
                case "WARN", "WARNING" -> 3;
                case "INFO" -> 4;
                case "DEBUG" -> 5;
                case "TRACE" -> 6;
                default -> 0;
            };
        }
        return 0;
    }

    private Color levelTextColor(int code) {
        return switch (code) {
            case 1 -> fatalText;
            case 2 -> errorText;
            case 3 -> warnText;
            case 4 -> infoText;
            case 5 -> debugText;
            case 6 -> traceText;
            default -> textColor;
        };
    }

    private Color levelBarColor(int code) {
        return switch (code) {
            case 1 -> fatalBar;
            case 2 -> errorBar;
            case 3 -> warnBar;
            default -> null;
        };
    }

    private Color levelTintColor(int code) {
        return switch (code) {
            case 1 -> fatalTint;
            case 2 -> errorTint;
            case 3 -> warnTint;
            default -> null;
        };
    }

    /**
     * Applies the light or dark palette. Canvas rendering cannot consume CSS, so
     * colors are switched programmatically when the scene theme changes.
     */
    private void applyPalette(boolean dark) {
        this.darkMode = dark;
        if (dark) {
            bgColor = Color.web("#1e2226");
            textColor = Color.web("#d7dbe0");
            lineNumColor = Color.web("#8b939c");
            lineNumBg = Color.web("#262b30");
            selectionColor = com.seeloggyplus.ui.SelectionColors.background(true);
            fatalText = Color.web("#ff8a80");
            errorText = Color.web("#f87171");
            warnText = Color.web("#fbbf24");
            infoText = Color.web("#6ea8fe");
            debugText = Color.web("#9aa3ac");
            traceText = Color.web("#9aa3ac");
            fatalBar = Color.web("#ef4444");
            errorBar = Color.web("#ef4444");
            warnBar = Color.web("#f59e0b");
            fatalTint = Color.web("#ef4444", 0.12);
            errorTint = Color.web("#ef4444", 0.10);
            warnTint = Color.web("#f59e0b", 0.10);
            highlightBg = Color.web("#facc15", 0.35);
        } else {
            bgColor = Color.web("#ffffff");
            textColor = Color.web("#1f2329");
            lineNumColor = Color.web("#6c757d");
            lineNumBg = Color.web("#f1f3f5");
            selectionColor = com.seeloggyplus.ui.SelectionColors.background(false);
            fatalText = Color.web("#7f1d1d");
            errorText = Color.web("#b91c1c");
            warnText = Color.web("#92400e");
            infoText = Color.web("#2b6cb0");
            debugText = Color.web("#6c757d");
            traceText = Color.web("#6c757d");
            fatalBar = Color.web("#b91c1c");
            errorBar = Color.web("#dc2626");
            warnBar = Color.web("#d97706");
            fatalTint = Color.web("#fdecec");
            errorTint = Color.web("#fdecec");
            warnTint = Color.web("#fdf8e8");
            highlightBg = Color.YELLOW;
        }
        setBackground(new Background(new BackgroundFill(bgColor, CornerRadii.EMPTY, Insets.EMPTY)));
        render();
    }

    public void setDarkMode(boolean dark) {
        if (this.darkMode == dark) {
            return;
        }
        applyPalette(dark);
    }

    private void updateThemeFromScene() {
        Scene scene = getScene();
        boolean dark = scene != null && scene.getRoot() != null
                && scene.getRoot().getStyleClass().contains("theme-dark");
        setDarkMode(dark);
    }

    private void render() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        gc.setFill(bgColor);
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
            lineLevelCache = new int[linesToRender + 10];
            lineCacheStartLine = -1; // force refill
        }
        if (lineCacheStartLine != currentTopLine) {
            lineCacheStartLine = currentTopLine;
            for (int i = 0; i < linesToRender; i++) {
                long viewIndex = currentTopLine + i;
                if (viewIndex >= effectiveLineCount) {
                    lineCache[i] = null;
                    lineLevelCache[i] = 0;
                    continue;
                }
                long actualLineIndex = (filteredIndexes != null)
                        ? filteredIndexes.get((int) viewIndex) : viewIndex;
                String content = getLineContent(actualLineIndex);
                lineCache[i] = content;
                lineLevelCache[i] = detectLevelCode(content);
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
            int level = lineLevelCache[i];
            renderLineContent(line, level, leftMargin, y, actualLineIndex);
        }
        gc.restore();

        // Line number gutter
        gc.setFill(lineNumBg);
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

            // Severity accent bar in the gutter gap (fixed; not affected by h-scroll)
            int level = (i < lineLevelCache.length) ? lineLevelCache[i] : 0;
            Color bar = levelBarColor(level);
            if (bar != null) {
                gc.setFill(bar);
                gc.fillRect(leftMargin - 5, y, 5, LINE_HEIGHT);
            }

            gc.setFill(lineNumColor);
            String lineNumStr = String.valueOf(actualLineIndex + 1);
            double numX = leftMargin - 10 - lineNumStr.length() * charWidth;
            gc.fillText(lineNumStr, numX, y + 2);
        }
    }

    private void renderLineContent(String line, int levelCode, double x, double y, long globalIndex) {
        double drawX = x - currentScrollX;
        double canvasWidth = canvas.getWidth();

        double lineWidth = line.length() * charWidth;
        if (drawX + lineWidth < leftMargin || drawX > canvasWidth) {
            return;
        }

        // Compute visible character range to avoid rendering thousands of off-screen glyphs
        int visStart = Math.max(0, (int) ((leftMargin - drawX) / charWidth));
        int visEnd = Math.min(line.length(), (int) ((canvasWidth - drawX) / charWidth) + 1);

        boolean isSelected = selectedLineIndexes.contains(globalIndex);

        // 0. Subtle row tint for severe levels (behind selection & highlights)
        Color tint = levelTintColor(levelCode);
        if (tint != null) {
            gc.setFill(tint);
            gc.fillRect(drawX, y, canvasWidth - leftMargin + currentScrollX, LINE_HEIGHT);
        }

        // 1. Draw Selection Background
        if (isSelected) {
            gc.setFill(selectionColor);
            gc.fillRect(drawX, y, canvasWidth - leftMargin + currentScrollX, LINE_HEIGHT);
        }

        // 2. Draw Search Highlights — only scan visible portion
        if (searchPattern != null) {
            Matcher m = searchPattern.matcher(line);
            // Skip matches entirely before visible area
            gc.setFill(highlightBg);
            while (m.find()) {
                if (m.end() < visStart) continue;
                if (m.start() > visEnd) break;
                double startX = drawX + m.start() * charWidth;
                double highlightWidth = (m.end() - m.start()) * charWidth;
                gc.fillRect(startX, y, highlightWidth, LINE_HEIGHT);
            }
        }

        // 3. Base text is always dark — readability over decoration
        gc.setFill(textColor);
        if (visStart > 0 || visEnd < line.length()) {
            String visibleText = line.substring(visStart, visEnd);
            gc.fillText(visibleText, drawX + visStart * charWidth, y + 2);
        } else {
            gc.fillText(line, drawX, y + 2);
        }

        // 4. Recolor only the level token (subtle severity accent)
        if (levelCode != 0) {
            CharSequence scan = line.length() > 120 ? line.substring(0, 120) : line;
            Matcher lm = LEVEL_PATTERN.matcher(scan);
            if (lm.find()) {
                int s = Math.max(lm.start(), visStart);
                int e = Math.min(lm.end(), visEnd);
                if (s < e) {
                    gc.setFill(levelTextColor(levelCode));
                    gc.fillText(line.substring(s, e), drawX + s * charWidth, y + 2);
                }
            }
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
                copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
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
        setFocusTraversable(true);
        canvas.setFocusTraversable(true);

        this.setOnMousePressed(e -> canvas.requestFocus());
        vScrollBar.setFocusTraversable(false);
        hScrollBar.setFocusTraversable(false);

        javafx.event.EventHandler<KeyEvent> keyHandler = this::handleKeyNavigation;
        canvas.setOnKeyPressed(keyHandler);
        this.setOnKeyPressed(keyHandler);
    }

    public void handleKeyNavigation(KeyEvent e) {
        if (e == null || e.isConsumed()) {
            return;
        }

        if (reader == null && tailBuffer.isEmpty()) {
            return;
        }

        if (e.getCode() == KeyCode.C && e.isControlDown()) {
            copySelectedLines();
            e.consume();
            return;
        }

        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
        if (effectiveLines <= 0) return;
        long maxTop = Math.max(0, effectiveLines - visibleLineCount);

        if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP) {
            if (e.getCode() == KeyCode.UP && followTail) {
                followTail = false;
                if (onFollowTailChanged != null) {
                    onFollowTailChanged.accept(false);
                }
            }

            long newSel;
            if (selectedLine < 0) {
                newSel = Math.max(0, Math.min(currentTopLine, effectiveLines - 1));
            } else {
                if (e.getCode() == KeyCode.DOWN) {
                    newSel = Math.min(selectedLine + 1, effectiveLines - 1);
                } else {
                    newSel = Math.max(selectedLine - 1, 0);
                }
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

        if (e.getCode() == KeyCode.LEFT) {
            hScrollBar.setValue(Math.max(0, hScrollBar.getValue() - 40));
            e.consume();
            return;
        } else if (e.getCode() == KeyCode.RIGHT) {
            hScrollBar.setValue(Math.min(hScrollBar.getMax(), hScrollBar.getValue() + 40));
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
    }

    public void handleArrowKey(KeyCode code) {
        KeyEvent dummyEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
        handleKeyNavigation(dummyEvent);
    }

    public boolean hasSelection() {
        return !selectedLineIndexes.isEmpty() || selectedLine >= 0;
    }

    public void copySelectedLines() {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(this::copySelectedLines);
            return;
        }

        if (selectedLineIndexes.isEmpty()) {
            if (selectedLine >= 0) {
                long idx = (filteredIndexes != null && selectedLine < filteredIndexes.size())
                        ? filteredIndexes.get((int) selectedLine) : selectedLine;
                selectedLineIndexes.add(idx);
            } else {
                return;
            }
        }

        StringBuilder sb = new StringBuilder();
        List<Long> sortedIndices = new ArrayList<>(selectedLineIndexes);
        Collections.sort(sortedIndices);

        for (int i = 0; i < sortedIndices.size(); i++) {
            long globalIndex = sortedIndices.get(i);
            String line = getLineContent(globalIndex);
            if (line != null) {
                sb.append(line);
                if (i < sortedIndices.size() - 1) {
                    sb.append(System.lineSeparator());
                }
            }
        }

        if (sb.length() > 0) {
            ClipboardContent content = new ClipboardContent();
            content.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(content);
            logger.info("Copied {} lines to clipboard", sortedIndices.size());
        }
    }

    private void setupScrollBarListener() {
        vScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressScrollBarSync) return;
            long newTop = newVal.longValue();
            long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
            long maxTop = Math.max(0, effectiveLines - visibleLineCount);
            if (newTop < maxTop && followTail) {
                setFollowTail(false);
            }
            if (newTop != currentTopLine) {
                currentTopLine = newTop;
                // Sync smooth scroll state when scrollbar is dragged directly
                smoothScrollY = newTop;
                targetScrollY = newTop;
                scrollOffsetY = 0;
                render();
                // Update status bar
                fireStatusUpdate(effectiveLines);
            }
        });

        hScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressScrollBarSync) return;
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
        // Ensure visibleAmount provides a comfortable, draggable thumb proportion (minimum 12%)
        double minVisibleProportion = 0.12;
        double safeVisibleAmount = Math.max(visibleLineCount, effectiveLines * minVisibleProportion);
        vScrollBar.setVisibleAmount(safeVisibleAmount);
        vScrollBar.setBlockIncrement(Math.max(1, visibleLineCount - 1));

        if (followTail) {
            currentTopLine = max;
            smoothScrollY = max;
            targetScrollY = max;
            scrollOffsetY = 0;
            suppressScrollBarSync = true;
            vScrollBar.setValue(max);
            suppressScrollBarSync = false;
        } else if (currentTopLine > max) {
            currentTopLine = max;
            smoothScrollY = max;
            targetScrollY = max;
            scrollOffsetY = 0;
            suppressScrollBarSync = true;
            vScrollBar.setValue(max);
            suppressScrollBarSync = false;
        }

        double visibleWidth = Math.max(1, canvas.getWidth() - leftMargin);
        double maxLineWidth = 30000;
        double maxScroll = Math.max(0, maxLineWidth - visibleWidth);
        hScrollBar.setMax(maxScroll);
        hScrollBar.setVisibleAmount(visibleWidth);
        hScrollBar.setBlockIncrement(visibleWidth / 2);

        // Fire status update
        fireStatusUpdate(effectiveLines);
    }

    public ScrollBar getVScrollBar() {
        return vScrollBar;
    }

    public ScrollBar getHScrollBar() {
        return hScrollBar;
    }

    public long getEffectiveLineCount() {
        return (filteredIndexes != null) ? filteredCount : totalLines;
    }

    private void fireStatusUpdate(long effectiveLines) {
        if (onStatusUpdate == null)
            return;
        if (effectiveLines <= 0) {
            onStatusUpdate.onStatusUpdate(0, 0, 0.0);
            return;
        }
        long currentLine = Math.min(currentTopLine + 1, effectiveLines); // 1-indexed for display
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

    /** Returns total lines (file indexed lines + tail buffer lines). */
    public long getTotalLines() {
        return totalLines;
    }

    /** Requests a redraw of the canvas. */
    public void redraw() {
        render();
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
        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
        if (effectiveLines <= 0) {
            return;
        }

        long targetViewIndex = line;
        long targetGlobalLine = line;

        if (filteredIndexes != null) {
            if (line >= 0 && line < filteredCount) {
                targetViewIndex = line;
                targetGlobalLine = filteredIndexes.get((int) line);
            } else {
                int foundIdx = filteredIndexes.indexOf((int) line);
                if (foundIdx >= 0) {
                    targetViewIndex = foundIdx;
                    targetGlobalLine = line;
                } else {
                    return;
                }
            }
        }

        if (targetViewIndex >= 0 && targetViewIndex < effectiveLines) {
            selectedLine = targetViewIndex;
            selectedLineIndexes.clear();
            selectedLineIndexes.add(targetGlobalLine);
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
