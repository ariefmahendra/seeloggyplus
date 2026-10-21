package com.seeloggyplus.shared.ui.canvas;

import com.seeloggyplus.shared.util.LineOffsetIndex;
import com.seeloggyplus.shared.util.MappedFileReader;
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

import com.seeloggyplus.shared.model.LogEntry;
import java.util.List;
import java.util.Collections;
import javafx.scene.input.KeyEvent;
import com.seeloggyplus.shared.ui.SelectionColors;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.ThemePalette;
import com.seeloggyplus.shared.util.IntArrayList;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;

public class CanvasLogViewer extends GridPane {

    private static final Logger logger = LoggerFactory.getLogger(CanvasLogViewer.class);
    private static final int LINE_HEIGHT = 18;
    private static final int PADDING = 4;
    private int leftMargin = 80;
    private final double charWidth;
    private static final Font MONO_FONT = Font.font("Consolas", FontWeight.NORMAL, 13);
    private static final Font LINE_NUM_FONT = Font.font("Consolas", FontWeight.NORMAL, 11);

    private Color bgColor;
    private Color textColor;
    private Color lineNumColor;
    private Color lineNumBg;
    private Color selectionColor;
    private final javafx.beans.value.ChangeListener<javafx.scene.Node> selectionFocusListener = (owner, previous, current) -> render();
    private Color fatalText;
    private Color errorText;
    private Color warnText;
    private Color infoText;
    private Color debugText;
    private Color traceText;
    private Color highlightBg;
    private AppTheme.Theme paletteTheme = AppTheme.getTheme();

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
    private long fileLineCount = 0;
    private int visibleLineCount = 50;

    private static final java.util.concurrent.atomic.AtomicLong COPY_REQUESTS = new java.util.concurrent.atomic.AtomicLong();
    private java.util.concurrent.FutureTask<Void> copyTask;
    private java.util.concurrent.FutureTask<Void> countTask;
    private long countGeneration;
    private boolean countDirty;
    private java.util.function.BiConsumer<Integer, Boolean> countCallback;
    private final java.util.Map<Object, String> loadedTailRows = new java.util.LinkedHashMap<>(64, .75f, true);
    private List<LogEntry> tailBuffer = Collections.emptyList();

    private IntArrayList filteredIndexes = null;
    private long filteredCount = 0;

    private Pattern searchPattern = null;

    private long selectedLine = -1;
    private long selectionStartViewLine = -1;
    private int selectionStartChar = 0;
    private long selectionEndViewLine = -1;
    private int selectionEndChar = 0;
    private boolean selectionWholeLine = false;
    private boolean isDragging = false;
    private long dragStartViewLine = -1;
    private long dragEndViewLine = -1;
    private int dragStartChar = 0;
    private int dragEndChar = 0;

    private ContextMenu currentContextMenu;

    private double smoothScrollY = 0;
    private double targetScrollY = 0;
    private double scrollOffsetY = 0;
    private boolean renderDirty = false;
    private boolean suppressScrollBarSync = false;
    private static final double SCROLL_LERP = 0.55;
    private static final double SCROLL_SNAP_THRESHOLD = 0.3;
    public static final int MATCH_SCAN_LIMIT = 50000;
    public static final int SELECTION_CHAR_SCAN_LIMIT = 100;
    private AnimationTimer scrollAnimator;

    @Setter
    private LineClickHandler onLineClick;
    @Setter
    private LineDoubleClickHandler onLineDoubleClick;

    @FunctionalInterface
    public interface StatusUpdateHandler {
        void onStatusUpdate(long currentLine, long totalLines, double percentage);
    }

    @Setter
    private StatusUpdateHandler onStatusUpdate;

    @Setter
    private Consumer<String> onSelectionChanged;

    public String getSelectionSummary() {
        if (!hasSelectionRange()) {
            if (selectedLine < 0) {
                return null;
            }
            return "1 line";
        }

        long first = selectionStartViewLine;
        long last = selectionEndViewLine;
        long lines = last - first + 1;
        int scanned = (int) Math.min(lines, SELECTION_CHAR_SCAN_LIMIT);
        long chars = 0;
        for (long view = first; view < first + scanned; view++) {
            int length = lineLengthAt(view);
            int from = Math.min(selectionStartCharAt(view), length);
            int to = Math.max(from, selectionEndCharAt(view, length));
            chars += (to - from);
        }

        StringBuilder summary = new StringBuilder();
        summary.append(lines).append(lines == 1 ? " line" : " lines");
        if (lines <= SELECTION_CHAR_SCAN_LIMIT) {
            summary.append(", ").append(chars).append(chars == 1 ? " char" : " chars");
        }
        return summary.toString();
    }

    private void fireSelectionChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(getSelectionSummary());
        }
    }

    public CanvasLogViewer() {
        Text text = new Text("A");
        text.setFont(MONO_FONT);
        this.charWidth = text.getLayoutBounds().getWidth();

        canvas = new Canvas(800, 600);
        gc = canvas.getGraphicsContext2D();
        gc.setTextBaseline(VPos.TOP);

        vScrollBar = new ScrollBar();
        vScrollBar.setOrientation(Orientation.VERTICAL);
        vScrollBar.setVisibleAmount(50);
        vScrollBar.setUnitIncrement(1);

        vScrollBar.setPrefWidth(14);
        vScrollBar.setMinWidth(14);
        vScrollBar.setMaxWidth(14);
        vScrollBar.getStyleClass().add("log-scroll-bar");

        hScrollBar = new ScrollBar();
        hScrollBar.setOrientation(Orientation.HORIZONTAL);
        hScrollBar.setMin(0);
        hScrollBar.setMax(2000);
        hScrollBar.setVisibleAmount(800);
        hScrollBar.setUnitIncrement(20);

        hScrollBar.setPrefHeight(10);
        hScrollBar.setMinHeight(10);
        hScrollBar.setMaxHeight(10);

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

        add(canvas, 0, 0);
        add(vScrollBar, 1, 0);
        add(hScrollBar, 0, 1);

        setupScrollBarListener();
        setupMouseHandlers();
        setupKeyboardHandlers();
        setupResizeHandler();
        setupSmoothScrollAnimator();

        setMinSize(0, 0);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        applyPalette(AppTheme.getTheme());

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) oldScene.focusOwnerProperty().removeListener(selectionFocusListener);
            if (newScene != null) {
                newScene.focusOwnerProperty().addListener(selectionFocusListener);
                Parent themeRoot = newScene.getRoot();
                if (themeRoot != null) {
                    themeRoot.getStyleClass().addListener(
                            (ListChangeListener<String>) change -> updateThemeFromScene());
                }
                updateThemeFromScene();
            }
        });

        render();
    }

    private void clearSelectionRange() {
        selectionStartViewLine = -1;
        selectionStartChar = 0;
        selectionEndViewLine = -1;
        selectionEndChar = 0;
        selectionWholeLine = false;
        fireSelectionChanged();
    }

    private void clearSelectionState() {
        selectedLine = -1;
        clearSelectionRange();
        isDragging = false;
        dragStartViewLine = -1;
        dragEndViewLine = -1;
        dragStartChar = 0;
        dragEndChar = 0;
    }

    private boolean hasSelectionRange() {
        return selectionStartViewLine >= 0 && selectionEndViewLine >= 0;
    }

    private long toGlobalLine(long viewIndex) {
        if (filteredIndexes != null && viewIndex >= 0 && viewIndex < filteredIndexes.size()) {
            return filteredIndexes.get((int) viewIndex);
        }
        return viewIndex;
    }

    private int lineLengthAt(long viewIndex) {
        long global = toGlobalLine(viewIndex);
        if (global >= fileLineCount && tailBuffer instanceof com.seeloggyplus.shared.tail.TailHistory history) {
            int row = (int) (global - fileLineCount);
            return row >= 0 && row < history.size() ? history.lineLength(row) : 0;
        }
        String line = viewIndex < 0 ? null : lineAtView(viewIndex);
        return line == null ? 0 : line.length();
    }

    private void selectWholeLine(long viewIndex) {
        if (viewIndex < 0) {
            clearSelectionRange();
            return;
        }
        selectionStartViewLine = viewIndex;
        selectionStartChar = 0;
        selectionEndViewLine = viewIndex;
        selectionEndChar = lineLengthAt(viewIndex);
        selectionWholeLine = true;
        fireSelectionChanged();
    }

    private void selectWholeLineRange(long anchorLine, long caretLine) {
        if (anchorLine < 0 || caretLine < 0) {
            clearSelectionRange();
            return;
        }
        selectionStartViewLine = Math.min(anchorLine, caretLine);
        selectionStartChar = 0;
        selectionEndViewLine = Math.max(anchorLine, caretLine);
        selectionEndChar = lineLengthAt(selectionEndViewLine);
        selectionWholeLine = true;
        fireSelectionChanged();
    }

    private void setCharSelection(long anchorLine, int anchorChar, long caretLine, int caretChar) {
        boolean anchorFirst = anchorLine < caretLine || (anchorLine == caretLine && anchorChar <= caretChar);
        if (anchorFirst) {
            selectionStartViewLine = anchorLine;
            selectionStartChar = anchorChar;
            selectionEndViewLine = caretLine;
            selectionEndChar = caretChar;
        } else {
            selectionStartViewLine = caretLine;
            selectionStartChar = caretChar;
            selectionEndViewLine = anchorLine;
            selectionEndChar = anchorChar;
        }
        selectionWholeLine = false;
        fireSelectionChanged();
    }

    private boolean isViewLineSelected(long viewIndex) {
        return hasSelectionRange() && viewIndex >= selectionStartViewLine && viewIndex <= selectionEndViewLine;
    }

    private int selectionStartCharAt(long viewIndex) {
        return viewIndex == selectionStartViewLine ? selectionStartChar : 0;
    }

    private int selectionEndCharAt(long viewIndex, int lineLength) {
        return viewIndex == selectionEndViewLine ? Math.min(selectionEndChar, lineLength) : lineLength;
    }

    private String lineAtView(long viewIndex) {
        long row = viewIndex - lineCacheStartLine;
        if (lineCacheStartLine >= 0 && row >= 0 && row < lineCacheRows && row < lineCache.length) {
            return lineCache[(int) row];
        }
        return getLineContent(toGlobalLine(viewIndex));
    }

    private Text layoutAtView(long viewIndex) {
        long row = viewIndex - lineCacheStartLine;
        if (lineCacheStartLine >= 0 && row >= 0 && row < lineCacheRows && row < lineLayoutCache.length
                && lineLayoutCache[(int) row] != null) {
            return lineLayoutCache[(int) row];
        }
        return textLayout(lineAtView(viewIndex));
    }

    private static Text textLayout(String line) {
        Text layout = new Text(line == null ? "" : line);
        layout.setFont(MONO_FONT);
        return layout;
    }

    private static double caretX(Text layout, int index) {
        int safeIndex = Math.max(0, Math.min(index, layout.getText().length()));
        for (var element : layout.caretShape(safeIndex, true)) {
            if (element instanceof javafx.scene.shape.MoveTo move) return move.getX();
        }
        return 0;
    }

    private int charIndexAt(double localX, long viewIndex) {
        Text layout = layoutAtView(viewIndex);
        double textX = localX - leftMargin + currentScrollX;
        int index = layout.hitTest(new javafx.geometry.Point2D(textX, 0)).getInsertionIndex();
        return Math.max(0, Math.min(index, layout.getText().length()));
    }

    private String buildSelectedText() {
        if (!hasSelectionRange()) {
            if (selectedLine < 0) {
                return null;
            }
            String line = getLineContent(toGlobalLine(selectedLine));
            return line == null ? null : line;
        }

        StringBuilder sb = new StringBuilder();
        for (long view = selectionStartViewLine; view <= selectionEndViewLine; view++) {
            String line = getLineContent(toGlobalLine(view));
            if (line == null) {
                line = "";
            }
            int from = Math.min(selectionStartCharAt(view), line.length());
            int to = Math.max(from, selectionEndCharAt(view, line.length()));
            if (to > from) {
                sb.append(line, from, to);
            }
            if (view < selectionEndViewLine) {
                sb.append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    public void resetView() {
        cancelAsyncWork();
        this.reader = null;
        this.index = null;
        this.fileLineCount = 0;
        this.tailBuffer = Collections.emptyList();
        loadedTailRows.clear();
        lineCache = new String[0];
        lineLayoutCache = new Text[0];
        lineLevelCache = new int[0];
        lineCacheRows = 0;
        this.totalLines = 0;
        this.currentTopLine = 0;
        this.smoothScrollY = 0;
        this.targetScrollY = 0;
        this.scrollOffsetY = 0;
        clearSelectionState();
        this.filteredIndexes = null;
        this.filteredCount = 0;
        this.lineCacheStartLine = -1;
        this.followTail = true;
        if (onFollowTailChanged != null) {
            onFollowTailChanged.accept(true);
        }

        leftMargin = 80;
        updateScrollBar();
        suppressScrollBarSync = true;
        vScrollBar.setValue(0);
        hScrollBar.setValue(0);
        suppressScrollBarSync = false;
        render();
        fireStatusUpdate(getEffectiveLineCount());
        logger.info("CanvasLogViewer reset.");
    }

    public void loadFile(MappedFileReader reader, LineOffsetIndex index) {
        this.reader = reader;
        this.index = index;
        this.fileLineCount = index.getLineCount();
        this.totalLines = fileLineCount + tailBuffer.size();
        this.currentTopLine = 0;
        this.smoothScrollY = 0;
        this.targetScrollY = 0;
        this.scrollOffsetY = 0;
        clearSelectionState();
        this.lineCacheStartLine = -1;

        int maxDigits = String.valueOf(totalLines).length();
        leftMargin = (int) ((maxDigits + 1) * charWidth + 10);

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

    public void setFilteredIndexes(IntArrayList indexes) {
        this.filteredIndexes = indexes;
        this.filteredCount = (indexes != null) ? indexes.size() : totalLines;
        this.currentTopLine = 0;
        this.smoothScrollY = 0;
        this.targetScrollY = 0;
        this.scrollOffsetY = 0;
        clearSelectionState();
        this.lineCacheStartLine = -1;

        updateScrollBar();
        render();

        logger.info("Filter applied: showing {} of {} lines", filteredCount, totalLines);
    }

    public void clearFilter() {
        setFilteredIndexes(null);
    }

    public IntArrayList getFilteredIndexes() {
        return filteredIndexes;
    }

    public void appendToFilter(long globalIndex) {
        if (filteredIndexes != null) {
            filteredIndexes.add((int) globalIndex);
            filteredCount++;
        }
    }

    private boolean followTail = true;

    @Setter
    private Consumer<Boolean> onFollowTailChanged;

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

    public void setTailBuffer(List<LogEntry> tailBuffer) {
        lineCacheStartLine = -1;
        loadedTailRows.clear();
        this.tailBuffer = (tailBuffer != null) ? tailBuffer : Collections.emptyList();
        this.totalLines = fileLineCount + this.tailBuffer.size();

        if (followTail) {
            scrollToBottom();
        } else {
            updateScrollBar();
        }
    }

    public void refreshTail() {
        this.totalLines = fileLineCount + tailBuffer.size();
        lineCacheStartLine = -1;

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

    public void exitTailMode() {
        this.followTail = false;
        if (onFollowTailChanged != null) {
            onFollowTailChanged.accept(false);
        }

        this.totalLines = fileLineCount + tailBuffer.size();

        this.filteredIndexes = null;
        this.filteredCount = 0;

        this.lineCacheStartLine = -1;

        long effectiveLines = totalLines;
        long maxTop = Math.max(0, effectiveLines - visibleLineCount);
        if (currentTopLine > maxTop) {
            currentTopLine = maxTop;
        }

        smoothScrollY = currentTopLine;
        targetScrollY = currentTopLine;
        scrollOffsetY = 0;

        clearSelectionState();

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

    public void clearSearchHighlight() {
        this.searchPattern = null;
        render();
    }

    public void jumpToLine(long line) {
        if (followTail) {
            setFollowTail(false);
        }
        long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
        if (effectiveLines <= 0) {
            return;
        }

        long targetViewIndex = line;

        if (filteredIndexes != null) {
            if (line >= 0 && line < filteredCount) {
                targetViewIndex = line;
            } else {
                int foundIdx = filteredIndexes.indexOf((int) line);
                if (foundIdx >= 0) {
                    targetViewIndex = foundIdx;
                } else {
                    targetViewIndex = 0;
                }
            }
        }

        long maxTop = Math.max(0, effectiveLines - visibleLineCount);
        currentTopLine = Math.max(0, Math.min(targetViewIndex, maxTop));
        selectedLine = targetViewIndex;
        selectWholeLine(targetViewIndex);
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

    private String[] lineCache = new String[0];
    private Text[] lineLayoutCache = new Text[0];
    private int[] lineLevelCache = new int[0];
    private long lineCacheStartLine = -1;
    private int lineCacheRows = 0;

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

    private void applyPalette(AppTheme.Theme theme) {
        this.paletteTheme = theme;
        bgColor = ThemePalette.color("-sl-surface", theme);
        textColor = ThemePalette.color("-sl-log-text", theme);
        lineNumColor = ThemePalette.color("-sl-canvas-line-number", theme);
        lineNumBg = ThemePalette.color("-sl-canvas-line-number-bg", theme);
        selectionColor = SelectionColors.background(theme);
        fatalText = ThemePalette.color("-sl-level-fatal-fg", theme);
        errorText = ThemePalette.color("-sl-level-error-fg", theme);
        warnText = ThemePalette.color("-sl-level-warn-fg", theme);
        infoText = ThemePalette.color("-sl-level-info-fg", theme);
        debugText = ThemePalette.color("-sl-level-debug-fg", theme);
        traceText = ThemePalette.color("-sl-level-trace-fg", theme);
        highlightBg = ThemePalette.color("-sl-canvas-highlight", theme);
        setBackground(new Background(new BackgroundFill(bgColor, CornerRadii.EMPTY, Insets.EMPTY)));
        render();
    }

    public void setDarkMode(boolean dark) {
        setTheme(dark ? AppTheme.Theme.DARK : AppTheme.Theme.LIGHT);
    }

    public void setTheme(AppTheme.Theme theme) {
        AppTheme.Theme target = theme == null ? AppTheme.Theme.LIGHT : theme;
        if (this.paletteTheme == target) {
            return;
        }
        applyPalette(target);
    }

    private void updateThemeFromScene() {
        Scene scene = getScene();
        Parent themeRoot = scene == null ? null : scene.getRoot();
        if (themeRoot == null) {
            setTheme(AppTheme.Theme.LIGHT);
        } else if (themeRoot.getStyleClass().contains("theme-dark")) {
            setTheme(AppTheme.Theme.DARK);
        } else if (themeRoot.getStyleClass().contains("theme-light")) {
            setTheme(AppTheme.Theme.LIGHT);
        } else {
            setTheme(AppTheme.Theme.LIGHT);
        }
    }

    private void render() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        gc.setFill(bgColor);
        gc.fillRect(0, 0, width, height);

        if (reader == null && tailBuffer.isEmpty()) {
            return;
        }

        long effectiveLineCount = (filteredIndexes != null) ? filteredCount : totalLines;

        int linesToRender = visibleLineCount + 1;

        if (lineCache.length < linesToRender) {
            lineCache = new String[linesToRender + 10];
            lineLayoutCache = new Text[linesToRender + 10];
            lineLevelCache = new int[linesToRender + 10];
            lineCacheStartLine = -1;
        }
        if (lineCacheStartLine != currentTopLine || lineCacheRows < linesToRender) {
            long previousStart = lineCacheStartLine;
            int previousRows = lineCacheRows;
            long shift = currentTopLine - previousStart;
            if (previousStart >= 0 && Math.abs(shift) < previousRows) {
                int from = (int) Math.max(0, shift);
                int to = (int) Math.max(0, -shift);
                int count = Math.min(previousRows - from, lineCache.length - to);
                System.arraycopy(lineCache, from, lineCache, to, count);
                System.arraycopy(lineLayoutCache, from, lineLayoutCache, to, count);
                System.arraycopy(lineLevelCache, from, lineLevelCache, to, count);
            }
            lineCacheStartLine = currentTopLine;
            lineCacheRows = linesToRender;
            for (int i = 0; i < linesToRender; i++) {
                long viewIndex = currentTopLine + i;
                if (viewIndex >= effectiveLineCount) {
                    lineCache[i] = null;
                    lineLayoutCache[i] = null;
                    lineLevelCache[i] = 0;
                    continue;
                }
                long previousRow = viewIndex - previousStart;
                if (previousStart >= 0 && previousRow >= 0 && previousRow < previousRows) continue;
                long actualLineIndex = (filteredIndexes != null)
                        ? filteredIndexes.get((int) viewIndex) : viewIndex;
                String content = getRenderLineContent(actualLineIndex);
                if (!java.util.Objects.equals(lineCache[i], content) || lineLayoutCache[i] == null) {
                    lineLayoutCache[i] = textLayout(content);
                    lineLevelCache[i] = detectLevelCode(content);
                }
                lineCache[i] = content;
            }
        }

        gc.save();
        gc.beginPath();
        gc.rect(leftMargin, 0, Math.max(0, width - leftMargin), height);
        gc.clip();

        gc.setFont(MONO_FONT);

        for (int i = 0; i < linesToRender; i++) {
            long viewIndex = currentTopLine + i;
            if (viewIndex >= effectiveLineCount) {
                break;
            }

            long actualLineIndex = (filteredIndexes != null)
                    ? filteredIndexes.get((int) viewIndex) : viewIndex;

            double y = PADDING + i * LINE_HEIGHT - scrollOffsetY;
            if (y + LINE_HEIGHT < 0 || y > height) continue;

            String line = lineCache[i];
            if (line == null) line = "";
            int level = lineLevelCache[i];
            renderLineContent(line, lineLayoutCache[i], level, leftMargin, y, viewIndex);
        }
        gc.restore();

        gc.setFill(lineNumBg);
        gc.fillRect(0, 0, leftMargin - 5, height);

        gc.setFont(LINE_NUM_FONT);

        for (int i = 0; i < linesToRender; i++) {
            long viewIndex = currentTopLine + i;
            if (viewIndex >= effectiveLineCount)
                break;

            long actualLineIndex = (filteredIndexes != null)
                    ? filteredIndexes.get((int) viewIndex) : viewIndex;

            double y = PADDING + i * LINE_HEIGHT - scrollOffsetY;
            if (y + LINE_HEIGHT < 0 || y > height) continue;

            gc.setFill(lineNumColor);
            String lineNumStr = String.valueOf(actualLineIndex + 1);
            double numX = leftMargin - 10 - lineNumStr.length() * charWidth;
            gc.fillText(lineNumStr, numX, y + 2);
        }
    }

    private void renderLineContent(String line, Text layout, int levelCode, double x, double y, long viewIndex) {
        if (layout == null) return;
        double drawX = x - currentScrollX;
        double canvasWidth = canvas.getWidth();
        if (drawX + layout.getLayoutBounds().getWidth() < leftMargin || drawX > canvasWidth) return;

        boolean isSelected = isViewLineSelected(viewIndex);
        double selectedStart = drawX;
        double selectedEnd = drawX;
        boolean wholeTextSelected = false;
        if (isSelected) {
            int start = selectionStartCharAt(viewIndex);
            int end = selectionEndCharAt(viewIndex, line.length());
            wholeTextSelected = start == 0 && end == line.length();
            selectedStart = drawX + caretX(layout, start);
            selectedEnd = drawX + caretX(layout, end);
            boolean toRowEnd = selectionWholeLine || viewIndex < selectionEndViewLine;
            double selectionWidth = toRowEnd ? canvasWidth - selectedStart : selectedEnd - selectedStart;
            if (selectionWidth > 0) {
                gc.setFill(SelectionColors.active(this) ? selectionColor : SelectionColors.background(paletteTheme, false));
                gc.fillRect(selectedStart, y, selectionWidth, LINE_HEIGHT);
            }
        }

        if (searchPattern != null) {
            Matcher matcher = searchPattern.matcher(line);
            gc.setFill(highlightBg);
            while (matcher.find()) {
                double start = drawX + caretX(layout, matcher.start());
                double end = drawX + caretX(layout, matcher.end());
                if (end < leftMargin) continue;
                if (start > canvasWidth) break;
                gc.fillRect(start, y, end - start, LINE_HEIGHT);
            }
        }

        if (wholeTextSelected) {
            gc.setFill(textColor);
            gc.fillText(line, drawX, y + 2);
            return;
        }
        Color normal = levelTextColor(levelCode);
        if (!isSelected || selectedStart >= selectedEnd || normal.equals(textColor)) {
            gc.setFill(normal);
            gc.fillText(line, drawX, y + 2);
            return;
        }
        renderTextSegment(line, drawX, y, leftMargin, selectedStart, normal);
        renderTextSegment(line, drawX, y, selectedStart, selectedEnd, textColor);
        renderTextSegment(line, drawX, y, selectedEnd, canvasWidth, normal);
    }

    private void renderTextSegment(String line, double drawX, double y, double left, double right, Color color) {
        left = Math.max(leftMargin, left);
        right = Math.min(canvas.getWidth(), right);
        if (right <= left) return;
        gc.save();
        try {
            gc.beginPath();
            gc.rect(left, y, right - left, LINE_HEIGHT);
            gc.clip();
            gc.setFill(color);
            gc.fillText(line, drawX, y + 2);
        } finally {
            gc.restore();
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

                long linesToScroll = Math.round(-deltaY / 40.0) * 3;
                if (linesToScroll == 0 && deltaY != 0) {
                    linesToScroll = deltaY > 0 ? -1 : 1;
                }

                if (linesToScroll < 0 && followTail) {
                    followTail = false;
                    if (onFollowTailChanged != null)
                        onFollowTailChanged.accept(false);
                }

                long effectiveLines = (filteredIndexes != null) ? filteredCount : totalLines;
                long maxScroll = Math.max(0, effectiveLines - visibleLineCount);

                long newTop = Math.max(0, Math.min(maxScroll, currentTopLine + linesToScroll));

                if (newTop >= maxScroll) {
                    newTop = maxScroll;
                    if (!followTail) {
                        followTail = true;
                        if (onFollowTailChanged != null)
                            onFollowTailChanged.accept(true);
                    }
                }

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

            canvas.requestFocus();

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
                if (!isViewLineSelected(listIndex)) {
                    selectWholeLine(listIndex);
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
                boolean inRange = listIndex >= 0 && listIndex < effective;
                int clickChar = charIndexAt(e.getX(), listIndex);

                if (e.isShiftDown() && selectedLine >= 0) {
                    long caretLine = inRange ? listIndex : Math.max(0, effective - 1);
                    selectWholeLineRange(selectedLine, caretLine);
                    selectedLine = caretLine;
                    render();
                    handleMouseClick(e);
                } else {
                    isDragging = true;
                    dragStartViewLine = viewLine;
                    dragEndViewLine = viewLine;
                    dragStartChar = clickChar;
                    dragEndChar = clickChar;
                    if (inRange) {
                        selectedLine = listIndex;
                        selectWholeLine(listIndex);
                    } else {
                        clearSelectionRange();
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
            viewLine = Math.max(0, Math.min(viewLine, visibleLineCount));

            long effective = (filteredIndexes != null) ? filteredCount : totalLines;
            long lastIndex = Math.max(0, effective - 1);
            long anchorLine = Math.min(currentTopLine + dragStartViewLine, lastIndex);
            long caretLine = Math.min(currentTopLine + viewLine, lastIndex);
            int caretChar = charIndexAt(e.getX(), caretLine);

            if (viewLine != dragEndViewLine || caretChar != dragEndChar) {
                dragEndViewLine = viewLine;
                dragEndChar = caretChar;
                setCharSelection(anchorLine, dragStartChar, caretLine, caretChar);
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

            if (e.getClickCount() == 1 || e.getClickCount() == 2) notifyLineSelected(actualLineIndex, e.getClickCount() == 2);
        }
    }

    private void setupKeyboardHandlers() {
        setFocusTraversable(true);
        canvas.setFocusTraversable(true);

        this.setOnMousePressed(e -> canvas.requestFocus());
        vScrollBar.setFocusTraversable(false);
        hScrollBar.setFocusTraversable(false);

        EventHandler<KeyEvent> keyHandler = this::handleKeyNavigation;
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

            selectedLine = newSel;
            selectWholeLine(newSel);

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

            notifyLineSelected(toGlobalLine(newSel), false);

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
        return hasSelectionRange() || selectedLine >= 0;
    }

    public void copySelectedLines() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::copySelectedLines);
            return;
        }

        long request = COPY_REQUESTS.incrementAndGet();
        if (copyTask != null) copyTask.cancel(true);
        if (tailBuffer instanceof com.seeloggyplus.shared.tail.TailHistory && hasSelection()) {
            copyTailSelection(request);
            return;
        }
        putClipboard(buildSelectedText());
    }

    private record SourceSnapshot(MappedFileReader reader, LineOffsetIndex index, long fileLines,
                                  List<LogEntry> tail) implements AutoCloseable {
        String line(long global) {
            if (global < fileLines) return reader.readLine(index, global);
            long row = global - fileLines;
            if (row < 0 || row >= tail.size()) return "";
            LogEntry entry = tail.get((int) row);
            return entry == null ? "" : entry.getRawLog();
        }
        @Override public void close() {
            if (tail instanceof com.seeloggyplus.shared.tail.TailHistory.Snapshot snapshot) snapshot.close();
        }
    }

    private SourceSnapshot captureSource() {
        List<LogEntry> tail = tailBuffer instanceof com.seeloggyplus.shared.tail.TailHistory history
                ? history.snapshot() : new java.util.ArrayList<>(tailBuffer);
        return new SourceSnapshot(reader, index, fileLineCount, tail);
    }

    private void cancelAsyncWork() {
        if (copyTask != null) {copyTask.cancel(true); copyTask = null;}
        countGeneration++;
        countDirty = false;
        if (countTask != null) {countTask.cancel(true); countTask = null;}
    }

    private void putClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        logger.info("Copied {} characters to clipboard", text.length());
    }

    private void copyTailSelection(long request) {
        boolean ranged = hasSelectionRange();
        long first = ranged ? selectionStartViewLine : selectedLine;
        long last = ranged ? selectionEndViewLine : selectedLine;
        int from = selectionStartChar;
        int end = selectionEndChar;
        boolean whole = !ranged || selectionWholeLine;
        long[] globals = new long[(int) (last - first + 1)];
        for (int i = 0; i < globals.length; i++) globals[i] = toGlobalLine(first + i);
        SourceSnapshot source = captureSource();
        copyTask = new java.util.concurrent.FutureTask<>(() -> {
            try {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < globals.length; i++) {
                    if (Thread.currentThread().isInterrupted() || COPY_REQUESTS.get() != request) return null;
                    String line = source.line(globals[i]);
                    if (line == null) line = "";
                    int begin = !whole && i == 0 ? Math.min(from, line.length()) : 0;
                    int finish = !whole && i == globals.length - 1 ? Math.min(end, line.length()) : line.length();
                    if (finish > begin) text.append(line, begin, finish);
                    if (i + 1 < globals.length) text.append(System.lineSeparator());
                }
                String copied = text.toString();
                Platform.runLater(() -> {if (COPY_REQUESTS.get() == request) putClipboard(copied);});
            } catch (RuntimeException error) {logger.debug("Could not copy the tail snapshot", error);}
            return null;
        }) {
            @Override protected void done() {source.close();}
        };
        Thread.ofVirtual().name("tail-copy").start(copyTask);
    }

    private void notifyLineSelected(long global, boolean doubleClick) {
        if (global >= fileLineCount && tailBuffer instanceof com.seeloggyplus.shared.tail.TailHistory history) {
            int row = (int) (global - fileLineCount);
            if (row < 0 || row >= history.size()) return;
            Object key = history.keyAt(row);
            String loaded = loadedTailRows.get(key);
            LogEntry cached = loaded == null ? history.peek(row) : null;
            if (loaded != null || cached != null) {
                fireLineSelected(global, loaded != null ? loaded : cached.getRawLog(), doubleClick);
            } else {
                history.loadAsync(row, entry -> Platform.runLater(() -> {
                    if (tailBuffer == history && row < history.size() && history.keyAt(row) == key
                            && toGlobalLine(selectedLine) == global) {
                        rememberTailLine(key, entry.getRawLog());
                        fireLineSelected(global, entry.getRawLog(), doubleClick);
                    }
                }));
            }
            return;
        }
        fireLineSelected(global, getLineContent(global), doubleClick);
    }

    private void fireLineSelected(long global, String content, boolean doubleClick) {
        if (doubleClick && onLineDoubleClick != null) onLineDoubleClick.handle(global, content);
        if (!doubleClick && onLineClick != null) onLineClick.handle(global, content);
    }

    private void rememberTailLine(Object key, String content) {
        loadedTailRows.put(key, content == null ? "" : content);
        while (loadedTailRows.size() > visibleLineCount + 10) loadedTailRows.remove(loadedTailRows.keySet().iterator().next());
        lineCacheStartLine = -1;
        requestRender();
    }

    public void countMatchesAsync(java.util.function.BiConsumer<Integer, Boolean> ready) {
        countCallback = ready;
        if (countTask != null && !countTask.isDone()) {countDirty = true; return;}
        countDirty = false;
        Pattern pattern = searchPattern;
        if (pattern == null) {ready.accept(0, false); return;}
        List<LogEntry> currentTail = tailBuffer;
        MappedFileReader currentReader = reader;
        int count = getItemCount();
        long[] globals = new long[Math.min(count, MATCH_SCAN_LIMIT)];
        for (int i = 0; i < globals.length; i++) globals[i] = toGlobalLine(i);
        SourceSnapshot source = captureSource();
        long generation = ++countGeneration;
        countTask = new java.util.concurrent.FutureTask<>(() -> {
            int matches = 0;
            try {
                for (long global : globals) {
                    if (Thread.currentThread().isInterrupted()) return null;
                    if (pattern.matcher(source.line(global)).find()) matches++;
                }
                int found = matches;
                Platform.runLater(() -> {
                    if (generation != countGeneration) return;
                    countTask = null;
                    if (currentTail == tailBuffer && currentReader == reader && pattern == searchPattern)
                        ready.accept(found, count > MATCH_SCAN_LIMIT);
                    if (countDirty) countMatchesAsync(countCallback);
                });
            } catch (RuntimeException error) {logger.debug("Could not count snapshot matches", error);}
            return null;
        }) {
            @Override protected void done() {source.close();}
        };
        Thread.ofVirtual().name("tail-match-count").start(countTask);
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

                smoothScrollY = newTop;
                targetScrollY = newTop;
                scrollOffsetY = 0;
                render();

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
        widthProperty().addListener((obs, o, n) -> resizeCanvas());
        heightProperty().addListener((obs, o, n) -> resizeCanvas());

        vScrollBar.widthProperty().addListener((obs, o, n) -> resizeCanvas());
        hScrollBar.heightProperty().addListener((obs, o, n) -> resizeCanvas());

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
                    if (smoothScrollY != targetScrollY) {
                        smoothScrollY = targetScrollY;
                        applySmoothScroll();
                    }
                    if (renderDirty) {
                        renderDirty = false;
                        render();
                    }

                    stop();
                    scrollAnimating = false;
                } else {
                    smoothScrollY += diff * SCROLL_LERP;
                    applySmoothScroll();
                    renderDirty = false;
                    render();
                }
            }
        };

        scrollAnimating = false;
    }

    private void requestRender() {
        renderDirty = true;
        if (!scrollAnimating) {
            scrollAnimating = true;
            scrollAnimator.start();
        }
    }

    private boolean scrollAnimating = false;

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

    private String getRenderLineContent(long globalIndex) {
        if (globalIndex >= fileLineCount && tailBuffer instanceof com.seeloggyplus.shared.tail.TailHistory history) {
            int row = (int) (globalIndex - fileLineCount);
            if (row < 0 || row >= history.size()) return "";
            Object key = history.keyAt(row);
            String loaded = loadedTailRows.get(key);
            if (loaded != null) return loaded;
            LogEntry entry = history.peek(row);
            if (entry != null) return entry.getRawLog();
            history.loadAsync(row, resolved -> javafx.application.Platform.runLater(() -> {
                if (tailBuffer == history && row < history.size() && history.keyAt(row) == key) {
                    rememberTailLine(key, resolved.getRawLog());
                }
            }));
            return "";
        }
        return getLineContent(globalIndex);
    }

    private String getLineContent(long globalIndex) {
        if (globalIndex < fileLineCount) {
            return reader.readLine(index, globalIndex);
        } else {
            int bufferIndex = (int) (globalIndex - fileLineCount);
            if (bufferIndex >= 0 && bufferIndex < tailBuffer.size()) {
                if (Platform.isFxApplicationThread() && tailBuffer instanceof com.seeloggyplus.shared.tail.TailHistory)
                    return getRenderLineContent(globalIndex);
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
        long currentLine = Math.min(currentTopLine + 1, effectiveLines);
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

    public long getFileLineCount() {
        return fileLineCount;
    }

    public long getTotalLines() {
        return totalLines;
    }

    public void redraw() {
        render();
    }

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

        if (filteredIndexes != null) {
            if (line >= 0 && line < filteredCount) {
                targetViewIndex = line;
            } else {
                int foundIdx = filteredIndexes.indexOf((int) line);
                if (foundIdx >= 0) {
                    targetViewIndex = foundIdx;
                } else {
                    return;
                }
            }
        }

        if (targetViewIndex >= 0 && targetViewIndex < effectiveLines) {
            selectedLine = targetViewIndex;
            selectWholeLine(targetViewIndex);
            render();
        }
    }

    public int countMatches() {
        if (searchPattern == null) {
            return 0;
        }

        int count = getItemCount();
        int matches = 0;

        int limit = Math.min(count, MATCH_SCAN_LIMIT);

        for (int i = 0; i < limit; i++) {
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

    public boolean isMatchScanTruncated() {
        return getItemCount() > MATCH_SCAN_LIMIT;
    }
}
