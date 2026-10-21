package com.seeloggyplus.features.search.presentation;

import com.seeloggyplus.shared.util.IntArrayList;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.LongFunction;
import java.util.function.BiConsumer;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javafx.application.Platform;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.seeloggyplus.shared.ui.SelectionColors;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.ThemePalette;
import javafx.geometry.Orientation;
import javafx.scene.layout.Pane;

public class SearchResultPanel extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(SearchResultPanel.class);

    private static final int LINE_HEIGHT = 18;
    private static final int PADDING = 4;
    private static final Font MONO_FONT = Font.font("Consolas", FontWeight.NORMAL, 12);
    private static final Font LINE_NUM_FONT = Font.font("Consolas", FontWeight.NORMAL, 10);

    private Color bgColor;
    private Color textColor;
    private Color lineNumColor;
    private Color lineNumBg;
    private Color selectionColor;
    private final javafx.beans.value.ChangeListener<javafx.scene.Node> selectionFocusListener = (owner, previous, current) -> render();
    private Color highlightBg;
    private Color hoverColor;
    private AppTheme.Theme paletteTheme = AppTheme.getTheme();

    private final Label headerLabel;
    private final Canvas canvas;
    private final GraphicsContext gc;
    private final ScrollBar vScrollBar;
    private final ScrollBar hScrollBar;
    private final double charWidth;

    private IntArrayList matchedLines;
    private final Map<Integer, String> lineCache = new LinkedHashMap<>(256, .75f, true);
    private final Set<Integer> pendingLines = new HashSet<>();
    private Executor contentExecutor;
    private volatile long contentGeneration;
    private int inFlightContent;
    private static final long CACHE_BYTES = 8L * 1024 * 1024;
    private int pendingSelection = -1;
    private int maxLineCharacters;
    private int itemCount = 0;
    private long currentTopLine = 0;
    private double currentScrollX = 0;
    private int visibleLineCount = 30;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private int leftMargin = 60;

    private Pattern searchPattern;

    private SearchHitCallback onLineSelected;
    private BiConsumer<Integer, Long> onLineActivated;
    private LongFunction<String> lineContentResolver;

    @FunctionalInterface
    public interface SearchHitCallback {
        void onSelected(int listIndex, long globalLine, String content);
    }

    public SearchResultPanel() {
        Text measure = new Text("M");
        measure.setFont(MONO_FONT);
        charWidth = measure.getLayoutBounds().getWidth();

        headerLabel = new Label("Search Results");
        headerLabel.getStyleClass().add("search-results-header");

        canvas = new Canvas();
        gc = canvas.getGraphicsContext2D();
        gc.setTextBaseline(VPos.TOP);

        vScrollBar = new ScrollBar();
        vScrollBar.setOrientation(Orientation.VERTICAL);
        vScrollBar.setMin(0);
        vScrollBar.setPrefWidth(10);
        vScrollBar.setMinWidth(10);
        vScrollBar.setMaxWidth(10);
        vScrollBar.valueProperty().addListener((obs, o, n) -> {
            long newTop = Math.round(n.doubleValue());
            if (newTop != currentTopLine) {
                currentTopLine = newTop;
                render();
            }
        });

        hScrollBar = new ScrollBar();
        hScrollBar.setOrientation(Orientation.HORIZONTAL);
        hScrollBar.setMin(0);
        hScrollBar.setPrefHeight(10);
        hScrollBar.setMinHeight(10);
        hScrollBar.setMaxHeight(10);
        hScrollBar.valueProperty().addListener((obs, o, n) -> {
            double newX = n.doubleValue();
            if (newX != currentScrollX) {
                currentScrollX = newX;
                render();
            }
        });

        Pane canvasHolder = new Pane(canvas);
        canvasHolder.setMinSize(0, 0);
        canvasHolder.setPrefSize(0, 0);

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
        setPadding(new Insets(8));
        setMinWidth(120);
        setPrefWidth(250);
        setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(headerLabel, canvasGrid);

        setVisible(false);
        setManaged(false);

        canvas.widthProperty().addListener((obs, o, n) -> { updateScrollBar(); render(); });
        canvas.heightProperty().addListener((obs, o, n) -> {
            visibleLineCount = Math.max(1, (int) ((n.doubleValue() - PADDING * 2) / LINE_HEIGHT));
            updateScrollBar();
            render();
        });

        setupMouseHandlers();

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
    }

    private void applyPalette(AppTheme.Theme theme) {
        this.paletteTheme = theme;
        bgColor = ThemePalette.color("-sl-surface", theme);
        textColor = ThemePalette.color("-sl-log-text", theme);
        lineNumColor = ThemePalette.color("-sl-canvas-line-number", theme);
        lineNumBg = ThemePalette.color("-sl-canvas-line-number-bg", theme);
        selectionColor = SelectionColors.background(theme);
        highlightBg = ThemePalette.color("-sl-canvas-highlight", theme);
        hoverColor = ThemePalette.color("-sl-canvas-hover", theme);
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

    public void setOnLineSelected(SearchHitCallback handler) {
        this.onLineSelected = handler;
    }

    public void setOnLineActivated(BiConsumer<Integer, Long> handler) {
        this.onLineActivated = handler;
    }

    public void setLineContentResolver(LongFunction<String> resolver) {
        setLineContentResolver(resolver, null);
    }

    public void setLineContentResolver(LongFunction<String> resolver, Executor executor) {
        this.lineContentResolver = resolver;
        this.contentExecutor = executor;
        resetContent();
    }

    private void resetContent() {
        contentGeneration++;
        pendingLines.clear();
        lineCache.clear();
        pendingSelection = -1;
        maxLineCharacters = 0;
    }

    public void setSearchPattern(Pattern pattern) {
        this.searchPattern = pattern;
        render();
    }

    public void showResults(IntArrayList matchedIndexes, int totalLines) {
        matchedLines = null;
        itemCount = 0;
        selectedIndex = -1;
        resetContent();
        currentScrollX = 0;
        hScrollBar.setValue(0);
        vScrollBar.setValue(0);
        if (matchedIndexes == null || matchedIndexes.size() == 0) {
            headerLabel.setText("No results");
            this.matchedLines = matchedIndexes;
            this.itemCount = 0;
            this.selectedIndex = -1;
            currentTopLine = 0;
            setVisible(true);
            setManaged(true);
            updateScrollBar();
            render();
            return;
        }

        this.matchedLines = matchedIndexes;
        this.itemCount = matchedIndexes.size();

        this.selectedIndex = -1;
        this.currentTopLine = 0;

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
        resetContent();
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

    public void trimFromStart(int count) {
        if (matchedLines == null || count <= 0) return;
        int removeCount = Math.min(count, itemCount);

        IntArrayList trimmed = new IntArrayList();
        for (int i = removeCount; i < itemCount; i++) {
            trimmed.add(matchedLines.get(i));
        }
        matchedLines = trimmed;
        itemCount = matchedLines.size();

        contentGeneration++;
        pendingLines.clear();
        pendingSelection = -1;
        Map<Integer, String> shiftedCache = new LinkedHashMap<>();
        lineCache.forEach((index, content) -> {
            if (index >= removeCount) shiftedCache.put(index - removeCount, content);
        });
        lineCache.clear();
        lineCache.putAll(shiftedCache);
        maxLineCharacters = lineCache.values().stream().mapToInt(String::length).max().orElse(0);

        if (selectedIndex >= 0) {
            selectedIndex -= removeCount;
            if (selectedIndex < 0) selectedIndex = -1;
        }

        currentTopLine = Math.max(0, currentTopLine - removeCount);

        headerLabel.setText(itemCount + " results");
        updateScrollBar();
        render();
    }

    public void syncItemCount() {
        if (matchedLines == null) return;
        int newCount = matchedLines.size();
        if (newCount == itemCount) return;

        if (newCount < itemCount) {
            contentGeneration++;
            pendingLines.clear();
            pendingSelection = -1;
            lineCache.keySet().removeIf(index -> index >= newCount);
            maxLineCharacters = lineCache.values().stream().mapToInt(String::length).max().orElse(0);
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

    public int getItemCount() {
        return itemCount;
    }

    private void updateScrollBar() {
        long max = Math.max(0, itemCount - visibleLineCount);
        currentTopLine = Math.min(currentTopLine, max);
        vScrollBar.setMax(max);
        vScrollBar.setValue(currentTopLine);
        double minVisibleProportion = 0.12;
        double safeVisibleAmount = Math.max(visibleLineCount, itemCount * minVisibleProportion);
        vScrollBar.setVisibleAmount(safeVisibleAmount);
        vScrollBar.setBlockIncrement(Math.max(1, visibleLineCount - 1));

        double visibleWidth = Math.max(0, canvas.getWidth() - leftMargin);
        double maxLineWidth = maxLineCharacters * charWidth + PADDING;
        double maxScroll = Math.max(0, maxLineWidth - visibleWidth);
        currentScrollX = Math.min(currentScrollX, maxScroll);
        hScrollBar.setMax(maxScroll);
        hScrollBar.setValue(currentScrollX);
        hScrollBar.setVisibleAmount(visibleWidth);
        hScrollBar.setBlockIncrement(visibleWidth / 2);
    }

    private String getLineContent(int matchIndex) {
        if (matchIndex < 0 || matchIndex >= itemCount) return "";
        String cached = lineCache.get(matchIndex);
        if (cached != null) return cached;
        if (lineContentResolver == null || matchedLines == null) return "";
        long globalLine = matchedLines.get(matchIndex);
        LongFunction<String> resolver = lineContentResolver;
        if (contentExecutor == null) {
            String content = resolve(resolver, globalLine);
            cacheContent(matchIndex, content);
            return content;
        }
        if (inFlightContent < 4 && pendingLines.add(matchIndex)) {
            inFlightContent++;
            long generation = contentGeneration;
            contentExecutor.execute(() -> {
                String content = generation == contentGeneration ? resolve(resolver, globalLine) : "";
                Platform.runLater(() -> {
                    inFlightContent--;
                    if (generation != contentGeneration) {
                        render();
                        return;
                    }
                    pendingLines.remove(matchIndex);
                    cacheContent(matchIndex, content);
                    render();
                    if (pendingSelection == matchIndex && selectedIndex == matchIndex) {
                        pendingSelection = -1;
                        fireSelection(matchIndex, globalLine, content);
                    }
                });
            });
        }
        return "";
    }

    private String resolve(LongFunction<String> resolver, long globalLine) {
        try {
            String content = resolver.apply(globalLine);
            return content == null ? "" : content;
        } catch (Exception error) {
            logger.debug("Could not resolve the full line for a search match", error);
            return "";
        }
    }

    private void cacheContent(int index, String content) {
        lineCache.put(index, content);
        long bytes = lineCache.values().stream().mapToLong(value -> 40L + 2L * value.length()).sum();
        while (lineCache.size() > 256 || bytes > CACHE_BYTES) {
            String removed = lineCache.remove(lineCache.keySet().iterator().next());
            bytes -= 40L + 2L * removed.length();
        }
        maxLineCharacters = Math.max(maxLineCharacters, content.length());
        updateScrollBar();
    }

    private void render() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;

        gc.setFill(bgColor);
        gc.fillRect(0, 0, width, height);

        if (matchedLines == null || itemCount == 0) return;

        int linesToRender = visibleLineCount + 1;

        gc.save();
        gc.beginPath();
        gc.rect(leftMargin, 0, Math.max(0, width - leftMargin), height);
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

            if (matchIdx == hoveredIndex && matchIdx != selectedIndex) {
                gc.setFill(hoverColor);
                gc.fillRect(leftMargin, y, width - leftMargin, LINE_HEIGHT);
            }

            if (matchIdx == selectedIndex) {
                gc.setFill(SelectionColors.active(this) ? selectionColor : SelectionColors.background(paletteTheme, false));
                gc.fillRect(leftMargin, y, width - leftMargin, LINE_HEIGHT);
            }

            if (searchPattern != null && !line.isEmpty()) {
                gc.setFill(highlightBg);
                Matcher m = searchPattern.matcher(line);
                while (m.find()) {
                    double startX = drawX + m.start() * charWidth;
                    double hlWidth = (m.end() - m.start()) * charWidth;

                    if (startX + hlWidth < leftMargin) continue;
                    if (startX > width) break;
                    gc.fillRect(startX, y, hlWidth, LINE_HEIGHT);
                }
            }

            int visStart = Math.max(0, (int) ((leftMargin - drawX) / charWidth));
            int visEnd = Math.min(line.length(), (int) ((width - drawX) / charWidth) + 1);
            if (visStart < visEnd && visEnd > 0) {
                gc.setFill(textColor);
                gc.fillText(line.substring(visStart, visEnd), drawX + visStart * charWidth, y + 2);
            }
        }
        gc.restore();

        gc.setFill(lineNumBg);
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
            gc.setFill(lineNumColor);
            gc.fillText(lineNumStr, numX, y + 2);
        }
    }

    public void selectAndTriggerLine(int index) {
        if (index >= 0 && index < itemCount && matchedLines != null) {
            selectIndex(index);
            long globalLine = matchedLines.get(index);
            if (onLineActivated != null) onLineActivated.accept(index, globalLine);
            String content = getLineContent(index);
            if (contentExecutor != null && !lineCache.containsKey(index)) {
                pendingSelection = index;
            } else {
                pendingSelection = -1;
                fireSelection(index, globalLine, content);
            }
        }
    }

    private void fireSelection(int index, long globalLine, String content) {
        if (onLineSelected != null) onLineSelected.onSelected(index, globalLine, content);
    }

    private void setupMouseHandlers() {
        canvas.setOnScroll(e -> {
            if (itemCount == 0) return;

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
            if (idx >= 0 && idx < itemCount) {
                selectAndTriggerLine(idx);
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
