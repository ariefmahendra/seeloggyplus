package com.seeloggyplus.shared.ui.canvas;

import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.util.IntArrayList;
import com.seeloggyplus.shared.util.LineOffsetIndex;
import com.seeloggyplus.shared.util.MappedFileReader;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class CanvasLogViewerStateBehaviorTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @Test
    void resetViewClearsBuffersFilterAndFollowTailState() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(420, 240);
            AtomicReference<Boolean> follow = new AtomicReference<>();
            AtomicReference<double[]> status = new AtomicReference<>();
            viewer.setOnFollowTailChanged(follow::set);
            viewer.setOnStatusUpdate((current, total, percentage) -> status.set(new double[] { current, total, percentage }));
            viewer.setTailBuffer(tailLines(40));
            viewer.setFollowTail(false);
            viewer.setFilteredIndexes(filter(0, 2, 4));
            viewer.selectLine(0);

            viewer.resetView();

            assertEquals(0, viewer.getTotalLines());
            assertEquals(0, viewer.getEffectiveLineCount());
            assertEquals(0, viewer.getItemCount());
            assertEquals(-1, viewer.getSelectedIndex());
            assertFalse(viewer.hasSelection());
            assertNull(viewer.getFilteredIndexes());
            assertTrue(viewer.isFollowTail());
            assertEquals(Boolean.TRUE, follow.get());
            assertEquals(0, viewer.getCurrentTopLine());
            assertEquals(0.0, viewer.getVScrollBar().getValue(), 1e-9);
            assertEquals(0.0, viewer.getVScrollBar().getMax(), 1e-9);
            assertEquals(0.0, viewer.getHScrollBar().getValue(), 1e-9);
            assertArrayEquals(new double[] { 0, 0, 0.0 }, status.get());
        });
    }

    @Test
    void setTailBufferAcceptsNullAndAppendToFilterRequiresActiveFilter() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.setTailBuffer(null);
            assertEquals(0, viewer.getTotalLines());
            assertEquals(0, viewer.getItemCount());
            assertTrue(viewer.isFollowTail());

            viewer.setTailBuffer(tailLines(3));
            assertEquals(3, viewer.getTotalLines());
            viewer.appendToFilter(1);
            assertEquals(3, viewer.getItemCount());
            assertNull(viewer.getFilteredIndexes());

            viewer.setFilteredIndexes(filter(0));
            viewer.appendToFilter(2);
            assertEquals(2, viewer.getItemCount());
            assertEquals(2, viewer.getFilteredIndexes().size());
            assertEquals(2, viewer.getFilteredIndexes().get(1));

            viewer.clearFilter();
            assertEquals(3, viewer.getItemCount());
            assertNull(viewer.getFilteredIndexes());
        });
    }

    @Test
    void refreshTailClampsViewportWhenNotFollowingTail() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(420, 240);
            List<LogEntry> lines = tailLines(100);
            viewer.setTailBuffer(lines);
            viewer.setFollowTail(false);
            viewer.getVScrollBar().setValue(80);
            assertEquals(80, viewer.getCurrentTopLine());

            lines.subList(0, 90).clear();
            viewer.refreshTail();

            int expectedTop = Math.max(0, 10 - privateInt(viewer, "visibleLineCount"));
            assertEquals(10, viewer.getTotalLines());
            assertEquals(10, viewer.getEffectiveLineCount());
            assertEquals(expectedTop, viewer.getCurrentTopLine());
            assertEquals((double) expectedTop, viewer.getVScrollBar().getValue(), 1e-9);
        });
    }

    @Test
    void refreshTailKeepsBottomWhenFollowingTail() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(420, 240);
            AtomicReference<Long> reportedTotal = new AtomicReference<>();
            viewer.setOnStatusUpdate((current, total, percentage) -> reportedTotal.set(total));
            List<LogEntry> lines = tailLines(100);
            viewer.setTailBuffer(lines);
            assertTrue(viewer.isFollowTail());

            lines.subList(0, 95).clear();
            viewer.refreshTail();

            assertEquals(5, viewer.getTotalLines());
            assertEquals(5L, reportedTotal.get());
            assertEquals(0, viewer.getCurrentTopLine());
            assertEquals(0.0, viewer.getVScrollBar().getValue(), 1e-9);
        });
    }

    @Test
    void exitTailModeClearsFilterAndClampsViewport() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(420, 240);
            viewer.setTailBuffer(tailLines(20));
            AtomicReference<Boolean> follow = new AtomicReference<>();
            viewer.setOnFollowTailChanged(follow::set);
            IntArrayList wide = new IntArrayList();
            for (int i = 0; i < 40; i++) {
                wide.add(i);
            }
            viewer.setFilteredIndexes(wide);
            int visible = privateInt(viewer, "visibleLineCount");
            assertEquals(Math.max(0, 40 - visible), viewer.getCurrentTopLine());

            viewer.exitTailMode();

            int expectedTop = Math.max(0, 20 - visible);
            assertNull(viewer.getFilteredIndexes());
            assertEquals(20, viewer.getTotalLines());
            assertEquals(20, viewer.getEffectiveLineCount());
            assertEquals(20, viewer.getItemCount());
            assertEquals(-1, viewer.getSelectedIndex());
            assertFalse(viewer.hasSelection());
            assertFalse(viewer.isFollowTail());
            assertEquals(Boolean.FALSE, follow.get());
            assertEquals(expectedTop, viewer.getCurrentTopLine());
            assertEquals((double) expectedTop, viewer.getVScrollBar().getValue(), 1e-9);
        });
    }

    @Test
    void jumpToLineMapsFilteredIndexesAndFallsBackToFirstMatch() {
        onFxThread(() -> {
            CanvasLogViewer empty = new CanvasLogViewer();
            empty.jumpToLine(0);
            assertEquals(0, empty.getTotalLines());
            assertEquals(-1, empty.getSelectedIndex());

            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.setTailBuffer(tailLines(10));
            assertTrue(viewer.isFollowTail());
            viewer.jumpToLine(0);
            assertFalse(viewer.isFollowTail());
            assertEquals(0, viewer.getSelectedIndex());

            viewer.setFilteredIndexes(filter(3, 7, 9));
            viewer.jumpToLine(1);
            assertEquals(1, viewer.getSelectedIndex());
            viewer.jumpToLine(7);
            assertEquals(1, viewer.getSelectedIndex());
            viewer.jumpToLine(8);
            assertEquals(0, viewer.getSelectedIndex());
            viewer.jumpToLine(-5);
            assertEquals(0, viewer.getSelectedIndex());
        });
    }

    @Test
    void selectLineMapsFilteredIndexesAndRejectsUnknownGlobalLines() {
        onFxThread(() -> {
            CanvasLogViewer empty = new CanvasLogViewer();
            empty.selectLine(0);
            assertEquals(-1, empty.getSelectedIndex());

            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.setTailBuffer(tailLines(10));
            viewer.selectLine(4);
            assertEquals(4, viewer.getSelectedIndex());
            viewer.selectLine(99);
            assertEquals(4, viewer.getSelectedIndex());

            viewer.setFilteredIndexes(filter(2, 5));
            viewer.selectLine(1);
            assertEquals(1, viewer.getSelectedIndex());
            viewer.selectLine(2);
            assertEquals(0, viewer.getSelectedIndex());
            viewer.selectLine(-2);
            assertEquals(0, viewer.getSelectedIndex());
        });
    }

    @Test
    void searchHighlightSupportsLiteralRegexCaseAndClear() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.setTailBuffer(tailLines(
                    "ERROR first",
                    "error second",
                    "contains a.b literal",
                    "contains axb other"));
            assertFalse(viewer.hasSearchHighlight());
            assertEquals(0, viewer.countMatches());

            viewer.setSearchHighlight("", false, false);
            assertFalse(viewer.hasSearchHighlight());
            viewer.setSearchHighlight(null, false, false);
            assertFalse(viewer.hasSearchHighlight());

            viewer.setSearchHighlight("a.b", false, false);
            assertTrue(viewer.hasSearchHighlight());
            assertEquals(1, viewer.countMatches());

            viewer.setSearchHighlight("a.b", true, false);
            assertEquals(2, viewer.countMatches());

            viewer.setSearchHighlight("ERROR", false, true);
            assertEquals(1, viewer.countMatches());
            viewer.setSearchHighlight("ERROR", false, false);
            assertEquals(2, viewer.countMatches());

            viewer.setSearchHighlight("(", true, false);
            assertFalse(viewer.hasSearchHighlight());
            assertEquals(0, viewer.countMatches());

            viewer.setSearchHighlight("ERROR", false, false);
            viewer.clearSearchHighlight();
            assertFalse(viewer.hasSearchHighlight());
            assertEquals(0, viewer.countMatches());
        });
    }

    @Test
    void countMatchesHonoursFilteredIndexes() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.setTailBuffer(tailLines(
                    "ERROR zero",
                    "ok one",
                    "ERROR two",
                    "ERROR three"));
            viewer.setSearchHighlight("ERROR", false, false);
            assertEquals(3, viewer.countMatches());

            viewer.setFilteredIndexes(filter(0, 3));
            viewer.setSearchHighlight("ERROR", false, false);
            assertEquals(2, viewer.countMatches());

            viewer.clearFilter();
            assertEquals(3, viewer.countMatches());
        });
    }

    @Test
    void statusUpdatesReportTopLineAndPercentage() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(420, 240);
            AtomicReference<String> latest = new AtomicReference<>();
            viewer.setOnStatusUpdate((current, total, percentage) ->
                    latest.set(current + "/" + total + "/" + percentage));
            viewer.setTailBuffer(tailLines(100));
            int maxTop = 100 - privateInt(viewer, "visibleLineCount");
            assertEquals((maxTop + 1) + "/100/" + maxTop + ".0", latest.get());

            viewer.getVScrollBar().setValue(0);
            assertEquals("1/100/0.0", latest.get());
        });
    }

    @Test
    void verticalScrollBarMovesViewportAndDisablesFollowTail() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(420, 240);
            AtomicReference<Boolean> follow = new AtomicReference<>();
            viewer.setOnFollowTailChanged(follow::set);
            viewer.setTailBuffer(tailLines(100));
            int maxTop = 100 - privateInt(viewer, "visibleLineCount");
            assertTrue(viewer.isFollowTail());
            assertEquals(maxTop, viewer.getCurrentTopLine());

            viewer.getVScrollBar().setValue(10);

            assertFalse(viewer.isFollowTail());
            assertEquals(Boolean.FALSE, follow.get());
            assertEquals(10, viewer.getCurrentTopLine());

            viewer.getVScrollBar().setValue(maxTop);
            assertEquals(maxTop, viewer.getCurrentTopLine());
            assertFalse(viewer.isFollowTail());
        });
    }

    @Test
    void horizontalScrollBarTracksScrollPositionAndMetrics() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(420, 240);
            viewer.setTailBuffer(tailLines(3));

            viewer.getHScrollBar().setValue(150);

            Canvas canvas = (Canvas) privateField(viewer, "canvas");
            double visibleWidth = Math.max(1, canvas.getWidth() - privateInt(viewer, "leftMargin"));
            assertEquals(150.0, privateDouble(viewer, "currentScrollX"), 1e-9);
            assertEquals(30000 - visibleWidth, viewer.getHScrollBar().getMax(), 1e-9);
            assertEquals(visibleWidth, viewer.getHScrollBar().getVisibleAmount(), 1e-9);
            assertEquals(visibleWidth / 2, viewer.getHScrollBar().getBlockIncrement(), 1e-9);
        });
    }

    @Test
    void scrollBarMetricsFollowViewportAndFilteredCount() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(420, 240);
            viewer.setTailBuffer(tailLines(200));

            int visible = privateInt(viewer, "visibleLineCount");
            assertEquals(200.0 - visible, viewer.getVScrollBar().getMax(), 1e-9);
            assertEquals(Math.max(visible, 200 * 0.12), viewer.getVScrollBar().getVisibleAmount(), 1e-9);
            assertEquals(Math.max(1, visible - 1), viewer.getVScrollBar().getBlockIncrement(), 1e-9);
            assertEquals(200 - visible, viewer.getCurrentTopLine());

            IntArrayList filtered = new IntArrayList();
            for (int i = 0; i < 50; i++) {
                filtered.add(i);
            }
            viewer.setFilteredIndexes(filtered);

            assertEquals(50, viewer.getItemCount());
            assertEquals(Math.max(0, 50 - visible), viewer.getVScrollBar().getMax(), 1e-9);
            assertEquals(Math.max(0, 50 - visible), viewer.getCurrentTopLine());
        });
    }

    @Test
    void resizeRecomputesCanvasSizeAndVisibleLineCount() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(600, 300);
            Canvas canvas = (Canvas) privateField(viewer, "canvas");
            assertEquals(600.0, canvas.getWidth()
                    + resolved(viewer.getVScrollBar().getWidth(), viewer.getVScrollBar().getPrefWidth()), 1e-9);
            assertEquals(300.0, canvas.getHeight()
                    + resolved(viewer.getHScrollBar().getHeight(), viewer.getHScrollBar().getPrefHeight()), 1e-9);
            assertEquals(Math.max(1, (int) (canvas.getHeight() / 18)), privateInt(viewer, "visibleLineCount"));

            viewer.resize(10, 10);
            assertEquals(100.0, canvas.getWidth(), 1e-9);
            assertEquals(100.0, canvas.getHeight(), 1e-9);
            assertEquals(5, privateInt(viewer, "visibleLineCount"));
        });
    }

    @Test
    void smoothScrollAnimatorConvergesAndSnapsToTarget() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.resize(420, 240);
            viewer.setTailBuffer(tailLines(100));
            viewer.setFollowTail(false);
            setPrivateField(viewer, "currentTopLine", 0L);
            setPrivateField(viewer, "smoothScrollY", 0.0);
            setPrivateField(viewer, "targetScrollY", 10.0);

            AnimationTimer animator = (AnimationTimer) privateField(viewer, "scrollAnimator");
            for (int i = 0; i < 40; i++) {
                animator.handle(i);
            }

            assertEquals(10, viewer.getCurrentTopLine());
            assertEquals(10.0, privateDouble(viewer, "smoothScrollY"), 1e-9);
            assertEquals(0.0, privateDouble(viewer, "scrollOffsetY"), 1e-9);
            assertEquals(10.0, viewer.getVScrollBar().getValue(), 1e-9);
            assertFalse((boolean) privateField(viewer, "scrollAnimating"));

            animator.handle(41);
            assertEquals(10, viewer.getCurrentTopLine());
            assertFalse((boolean) privateField(viewer, "scrollAnimating"));
        });
    }

    @Test
    void levelHelpersClassifySeverityKeywords() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();

            assertEquals(1, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class }, "FATAL boom"));
            assertEquals(2, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class }, "x ERROR y"));
            assertEquals(3, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class }, "Warning: slow"));
            assertEquals(4, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class }, "INFO start"));
            assertEquals(5, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class }, "debug detail"));
            assertEquals(6, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class }, "trace detail"));
            assertEquals(0, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class }, "plain line"));
            assertEquals(0, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class }, ""));
            assertEquals(0, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class }, (Object) null));
            assertEquals(0, (int) invoke(viewer, "detectLevelCode", new Class<?>[] { String.class },
                    "a".repeat(130) + " ERROR"));

            assertEquals(privateField(viewer, "fatalText"),
                    invoke(viewer, "levelTextColor", new Class<?>[] { int.class }, 1));
            assertEquals(privateField(viewer, "errorText"),
                    invoke(viewer, "levelTextColor", new Class<?>[] { int.class }, 2));
            assertEquals(privateField(viewer, "warnText"),
                    invoke(viewer, "levelTextColor", new Class<?>[] { int.class }, 3));
            assertEquals(privateField(viewer, "infoText"),
                    invoke(viewer, "levelTextColor", new Class<?>[] { int.class }, 4));
            assertEquals(privateField(viewer, "debugText"),
                    invoke(viewer, "levelTextColor", new Class<?>[] { int.class }, 5));
            assertEquals(privateField(viewer, "traceText"),
                    invoke(viewer, "levelTextColor", new Class<?>[] { int.class }, 6));
            assertEquals(privateField(viewer, "textColor"),
                    invoke(viewer, "levelTextColor", new Class<?>[] { int.class }, 9));

            assertEquals(privateField(viewer, "fatalBar"),
                    invoke(viewer, "levelBarColor", new Class<?>[] { int.class }, 1));
            assertEquals(privateField(viewer, "errorBar"),
                    invoke(viewer, "levelBarColor", new Class<?>[] { int.class }, 2));
            assertEquals(privateField(viewer, "warnBar"),
                    invoke(viewer, "levelBarColor", new Class<?>[] { int.class }, 3));
            assertNull(invoke(viewer, "levelBarColor", new Class<?>[] { int.class }, 4));

            assertEquals(privateField(viewer, "fatalTint"),
                    invoke(viewer, "levelTintColor", new Class<?>[] { int.class }, 1));
            assertEquals(privateField(viewer, "errorTint"),
                    invoke(viewer, "levelTintColor", new Class<?>[] { int.class }, 2));
            assertEquals(privateField(viewer, "warnTint"),
                    invoke(viewer, "levelTintColor", new Class<?>[] { int.class }, 3));
            assertNull(invoke(viewer, "levelTintColor", new Class<?>[] { int.class }, 4));
        });
    }

    @Test
    void loadFileUsesIndexedReaderAndGutterWidth() throws Exception {
        File temp = File.createTempFile("canvas-viewer", ".log");
        temp.deleteOnExit();
        Files.writeString(temp.toPath(), "alpha\nbeta\ncharlie\n");
        LineOffsetIndex index = new LineOffsetIndex();
        try (RandomAccessFile raf = new RandomAccessFile(temp, "r")) {
            index.buildIndex(raf, null);
        }
        MappedFileReader reader = new MappedFileReader(temp);
        try {
            onFxThread(() -> {
                CanvasLogViewer viewer = new CanvasLogViewer();
                AtomicReference<Boolean> follow = new AtomicReference<>();
                viewer.setOnFollowTailChanged(follow::set);

                viewer.loadFile(reader, index);

                assertEquals(3, viewer.getFileLineCount());
                assertEquals(3, viewer.getTotalLines());
                assertEquals(3, viewer.getEffectiveLineCount());
                assertFalse(viewer.isFollowTail());
                assertEquals(Boolean.FALSE, follow.get());
                assertEquals(-1, viewer.getSelectedIndex());
                assertEquals(0.0, viewer.getVScrollBar().getValue(), 1e-9);
                double charWidth = privateDouble(viewer, "charWidth");
                assertEquals((int) ((String.valueOf(3L).length() + 1) * charWidth + 10),
                        privateInt(viewer, "leftMargin"));

                viewer.setTailBuffer(tailLines(2));
                assertEquals(5, viewer.getTotalLines());

                viewer.selectLine(1);
                viewer.copySelectedLines();
                assertEquals("beta", Clipboard.getSystemClipboard().getString());

                viewer.selectLine(4);
                viewer.copySelectedLines();
                assertEquals("tail-2", Clipboard.getSystemClipboard().getString());
            });
        } finally {
            reader.close();
        }
    }

    @Test
    void setDarkModeIsIdempotent() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.setDarkMode(false);
            viewer.setDarkMode(false);
            assertEquals(Color.web("#ffffff"), backgroundFill(viewer));

            viewer.setDarkMode(true);
            viewer.setDarkMode(true);
            assertEquals(Color.web("#1e2226"), backgroundFill(viewer));

            viewer.setDarkMode(false);
            assertEquals(Color.web("#ffffff"), backgroundFill(viewer));
        });
    }

    @Test
    void renderPaintsSearchHighlightAndClearsIt() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            stage.setScene(AppTheme.scene(viewer));
            stage.show();
            viewer.resize(420, 226);
            Canvas canvas = (Canvas) privateField(viewer, "canvas");

            viewer.setTailBuffer(tailLines("ERROR one"));
            viewer.setSearchHighlight("ERROR", false, false);
            assertTrue(hasYellowPixels(canvas.snapshot(null, null)));

            viewer.clearSearchHighlight();
            assertFalse(hasYellowPixels(canvas.snapshot(null, null)));

            viewer.setTailBuffer(tailLines(
                    "ERROR start abcdefghijklmnopqrstuvwxyz0123456789",
                    "x".repeat(100) + "ERROR far"));
            viewer.getVScrollBar().setValue(0);
            viewer.getHScrollBar().setValue(120);
            viewer.setSearchHighlight("ERROR", false, false);
            assertFalse(hasYellowPixels(canvas.snapshot(null, null)));

            viewer.getHScrollBar().setValue(viewer.getHScrollBar().getMax());
            viewer.redraw();
            viewer.getHScrollBar().setValue(0);
            viewer.requestCanvasFocus();
            viewer.redraw();
            assertEquals(0.0, viewer.getHScrollBar().getValue(), 1e-9);
        });
    }

    private static List<LogEntry> tailLines(int count) {
        List<LogEntry> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            lines.add(new LogEntry(i + 1L, "tail-" + (i + 1)));
        }
        return lines;
    }

    private static List<LogEntry> tailLines(String... rawLines) {
        List<LogEntry> lines = new ArrayList<>();
        long number = 1;
        for (String raw : rawLines) {
            lines.add(new LogEntry(number++, raw));
        }
        return lines;
    }

    private static IntArrayList filter(int... values) {
        IntArrayList list = new IntArrayList();
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    private static boolean hasYellowPixels(WritableImage image) {
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                Color color = reader.getColor(x, y);
                if (color.getRed() > 0.8 && color.getGreen() > 0.8 && color.getBlue() < 0.6) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Color backgroundFill(Region region) {
        Background background = region.getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return null;
        }
        Paint paint = background.getFills().get(0).getFill();
        return paint instanceof Color color ? color : null;
    }

    private static double resolved(double actual, double fallback) {
        return actual > 0 ? actual : fallback;
    }

    private static Object privateField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setPrivateField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static int privateInt(Object target, String name) {
        return ((Number) privateField(target, name)).intValue();
    }

    private static double privateDouble(Object target, String name) {
        return ((Number) privateField(target, name)).doubleValue();
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void onFxThread(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }
}
