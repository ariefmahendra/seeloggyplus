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
    private Color highlightBg;
    private Color hoverColor;
    private AppTheme.Theme paletteTheme = AppTheme.Theme.GRAPHITE;

    private final Label headerLabel;
    private final Canvas canvas;
    private final GraphicsContext gc;
    private final ScrollBar vScrollBar;
    private final ScrollBar hScrollBar;
    private final double charWidth;

    private IntArrayList matchedLines;
    private String[] lineCache;
    private int itemCount = 0;
    private long currentTopLine = 0;
    private double currentScrollX = 0;
    private int visibleLineCount = 30;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private int leftMargin = 60;

    private Pattern searchPattern;

    private SearchHitCallback onLineSelected;
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
        setPadding(new Insets(2));
        setMinWidth(120);
        setPrefWidth(250);
        setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(headerLabel, canvasGrid);

        setVisible(false);
        setManaged(false);

        canvas.widthProperty().addListener((obs, o, n) -> render());
        canvas.heightProperty().addListener((obs, o, n) -> {
            visibleLineCount = Math.max(1, (int) (n.doubleValue() / LINE_HEIGHT));
            updateScrollBar();
            render();
        });

        setupMouseHandlers();

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
    }

    private void applyPalette(AppTheme.Theme theme) {
        this.paletteTheme = theme;
        bgColor = ThemePalette.color("-sl-surface", theme);
        textColor = ThemePalette.color("-sl-text", theme);
        lineNumColor = ThemePalette.color("-sl-canvas-line-number", theme);
        lineNumBg = ThemePalette.color("-sl-canvas-line-number-bg", theme);
        selectionColor = SelectionColors.background(theme == AppTheme.Theme.DARK);
        highlightBg = ThemePalette.color("-sl-canvas-highlight", theme);
        hoverColor = ThemePalette.color("-sl-canvas-hover", theme);
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

    public void trimFromStart(int count) {
        if (matchedLines == null || count <= 0) return;
        int removeCount = Math.min(count, itemCount);

        IntArrayList trimmed = new IntArrayList();
        for (int i = removeCount; i < itemCount; i++) {
            trimmed.add(matchedLines.get(i));
        }
        matchedLines = trimmed;
        itemCount = matchedLines.size();

        String[] newCache = new String[itemCount];
        if (lineCache != null && lineCache.length > removeCount) {
            System.arraycopy(lineCache, removeCount, newCache, 0,
                    Math.min(lineCache.length - removeCount, itemCount));
        }
        lineCache = newCache;

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
        vScrollBar.setMax(max);
        double minVisibleProportion = 0.12;
        double safeVisibleAmount = Math.max(visibleLineCount, itemCount * minVisibleProportion);
        vScrollBar.setVisibleAmount(safeVisibleAmount);
        vScrollBar.setBlockIncrement(Math.max(1, visibleLineCount - 1));

        double visibleWidth = canvas.getWidth() - leftMargin;
        double maxLineWidth = 500 * charWidth;
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
                    lineCache[matchIndex] = full.length() > 500 ? full.substring(0, 500) : full;
                    return lineCache[matchIndex];
                }
            } catch (Exception e) {
                logger.debug("Could not resolve the full line for a search match", e);
            }
        }
        lineCache[matchIndex] = "";
        return "";
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

            if (matchIdx == hoveredIndex && matchIdx != selectedIndex) {
                gc.setFill(hoverColor);
                gc.fillRect(leftMargin, y, width - leftMargin, LINE_HEIGHT);
            }

            if (matchIdx == selectedIndex) {
                gc.setFill(selectionColor);
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
            String content = getLineContent(index);
            if (onLineSelected != null) {
                onLineSelected.onSelected(index, globalLine, content);
            }
        }
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
