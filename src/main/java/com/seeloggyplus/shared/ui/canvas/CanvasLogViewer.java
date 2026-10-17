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
    private AppTheme.Theme paletteTheme = AppTheme.Theme.GRAPHITE;

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

        applyPalette(AppTheme.Theme.GRAPHITE);

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

        render();
    }

    private void clearSelectionRange() {
        selectionStartViewLine = -1;
        selectionStartChar = 0;
        selectionEndViewLine = -1;
        selectionEndChar = 0;
        selectionWholeLine = false;
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
        String line = viewIndex < 0 ? null : getLineContent(toGlobalLine(viewIndex));
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

    private int charIndexAt(double localX, long viewIndex) {
        int length = lineLengthAt(viewIndex);
        double textX = localX - leftMargin + currentScrollX;
        int index = (int) Math.round(textX / charWidth);
        return Math.max(0, Math.min(index, length));
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
        this.reader = null;
        this.index = null;
        this.fileLineCount = 0;
        this.tailBuffer = Collections.emptyList();
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

    private void applyPalette(AppTheme.Theme theme) {
        this.paletteTheme = theme;
        bgColor = ThemePalette.color("-sl-surface", theme);
        textColor = ThemePalette.color("-sl-text", theme);
        lineNumColor = ThemePalette.color("-sl-canvas-line-number", theme);
        lineNumBg = ThemePalette.color("-sl-canvas-line-number-bg", theme);
        selectionColor = SelectionColors.background(theme == AppTheme.Theme.DARK);
        fatalText = ThemePalette.color("-sl-level-fatal-fg", theme);
        errorText = ThemePalette.color("-sl-level-error-fg", theme);
        warnText = ThemePalette.color("-sl-level-warn-fg", theme);
        infoText = ThemePalette.color("-sl-level-info-fg", theme);
        debugText = ThemePalette.color("-sl-syntax-trace", theme);
        traceText = ThemePalette.color("-sl-syntax-trace", theme);
        fatalBar = ThemePalette.color("-sl-level-fatal-bar", theme);
        errorBar = ThemePalette.color("-sl-level-error-bar", theme);
        warnBar = ThemePalette.color("-sl-level-warn-bar", theme);
        fatalTint = ThemePalette.color("-sl-level-fatal-tint", theme);
        errorTint = ThemePalette.color("-sl-level-error-tint", theme);
        warnTint = ThemePalette.color("-sl-level-warn-tint", theme);
        highlightBg = ThemePalette.color("-sl-canvas-highlight", theme);
        setBackground(new Background(new BackgroundFill(bgColor, CornerRadii.EMPTY, Insets.EMPTY)));
        render();
    }

    public void setDarkMode(boolean dark) {
        setTheme(dark ? AppTheme.Theme.DARK : AppTheme.Theme.GRAPHITE);
    }

    public void setTheme(AppTheme.Theme theme) {
        AppTheme.Theme target = theme == null ? AppTheme.Theme.GRAPHITE : theme;
        if (this.paletteTheme == target) {
            return;
        }
        applyPalette(target);
    }

    private void updateThemeFromScene() {
        Scene scene = getScene();
        Parent themeRoot = scene == null ? null : scene.getRoot();
        if (themeRoot == null) {
            setTheme(AppTheme.Theme.GRAPHITE);
        } else if (themeRoot.getStyleClass().contains("theme-dark")) {
            setTheme(AppTheme.Theme.DARK);
        } else if (themeRoot.getStyleClass().contains("theme-light")) {
            setTheme(AppTheme.Theme.LIGHT);
        } else {
            setTheme(AppTheme.Theme.GRAPHITE);
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
            lineLevelCache = new int[linesToRender + 10];
            lineCacheStartLine = -1;
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
            renderLineContent(line, level, leftMargin, y, viewIndex);
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

    private void renderLineContent(String line, int levelCode, double x, double y, long viewIndex) {
        double drawX = x - currentScrollX;
        double canvasWidth = canvas.getWidth();

        double lineWidth = line.length() * charWidth;
        if (drawX + lineWidth < leftMargin || drawX > canvasWidth) {
            return;
        }

        int visStart = Math.max(0, (int) ((leftMargin - drawX) / charWidth));
        int visEnd = Math.min(line.length(), (int) ((canvasWidth - drawX) / charWidth) + 1);

        boolean isSelected = isViewLineSelected(viewIndex);

        Color tint = levelTintColor(levelCode);
        if (tint != null) {
            gc.setFill(tint);
            gc.fillRect(drawX, y, canvasWidth - leftMargin + currentScrollX, LINE_HEIGHT);
        }

        if (isSelected) {
            int selStart = Math.max(0, Math.min(selectionStartCharAt(viewIndex), line.length()));
            int selEnd = Math.max(selStart, selectionEndCharAt(viewIndex, line.length()));
            boolean toRowEnd = selectionWholeLine || viewIndex < selectionEndViewLine;
            double selX = drawX + selStart * charWidth;
            double selW = toRowEnd ? (canvasWidth - selX) : (selEnd - selStart) * charWidth;
            if (selW > 0) {
                gc.setFill(selectionColor);
                gc.fillRect(selX, y, selW, LINE_HEIGHT);
            }
        }

        if (searchPattern != null) {
            Matcher m = searchPattern.matcher(line);

            gc.setFill(highlightBg);
            while (m.find()) {
                if (m.end() < visStart) continue;
                if (m.start() > visEnd) break;
                double startX = drawX + m.start() * charWidth;
                double highlightWidth = (m.end() - m.start()) * charWidth;
                gc.fillRect(startX, y, highlightWidth, LINE_HEIGHT);
            }
        }

        gc.setFill(textColor);
        if (visStart > 0 || visEnd < line.length()) {
            String visibleText = line.substring(visStart, visEnd);
            gc.fillText(visibleText, drawX + visStart * charWidth, y + 2);
        } else {
            gc.fillText(line, drawX, y + 2);
        }

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

            String content = getLineContent(toGlobalLine(newSel));
            if (onLineClick != null) {
                onLineClick.handle(toGlobalLine(newSel), content);
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
        return hasSelectionRange() || selectedLine >= 0;
    }

    public void copySelectedLines() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::copySelectedLines);
            return;
        }

        String selected = buildSelectedText();
        if (selected == null || selected.isEmpty()) {
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(selected);
        Clipboard.getSystemClipboard().setContent(content);
        logger.info("Copied {} characters to clipboard", selected.length());
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

        int scanLimit = 50000;
        int limit = Math.min(count, scanLimit);

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
}
