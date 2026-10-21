package com.seeloggyplus.shared.ui.canvas;

import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.SelectionColors;
import javafx.event.Event;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class CanvasSelectionRenderingRegressionTest {
    private Stage stage;
    private CanvasLogViewer viewer;
    private StackPane root;
    private Canvas canvas;
    private final String stack = "\tat java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]";
    private final String nextLine = "    at org.example.api.RequestHandler.handle(RequestHandler.java:89)";

    @Start void start(Stage stage) {
        this.stage = stage;
        viewer = new CanvasLogViewer();
        root = new StackPane(viewer);
        stage.setScene(AppTheme.scene(root));
        stage.setWidth(1100);
        stage.setHeight(330);
        stage.show();
        root.applyCss();
        root.layout();
        viewer.setTailBuffer(new ArrayList<>(List.of(
                new LogEntry(1, "2026-10-20 ERROR synthetic request failed"),
                new LogEntry(2, "java.lang.RuntimeException: synthetic timeout"),
                new LogEntry(3, stack),
                new LogEntry(4, nextLine),
                new LogEntry(5, "Caused by: synthetic connection timeout"))));
        viewer.setFollowTail(false);
        viewer.getVScrollBar().setValue(0);
        canvas = (Canvas) field(viewer, "canvas");
        viewer.requestCanvasFocus();
    }

    @AfterEach void cleanup() throws Exception {
        fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT));
    }

    @Test void selectedStackTraceMatchesOneTextPass() throws Exception {
        var differences = new java.util.LinkedHashMap<AppTheme.Theme, Integer>();
        var copies = new java.util.LinkedHashMap<AppTheme.Theme, String>();
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> {
                AppTheme.setTheme(theme);
                root.applyCss();
                root.layout();
                drag(2, 5, 2, 54);
                viewer.setSearchHighlight("reflect", false, false);
                viewer.redraw();
                snapshot("stack-trace-" + theme.name().toLowerCase());
                int changed = differentPixels(canvas.snapshot(null, null), reference(stack, 2, 5, 54, "reflect", false), 2);
                System.out.println("SELECTION_GLYPH_PIXEL_DIFFERENCES_" + theme + "=" + changed);
                differences.put(theme, changed);
                viewer.copySelectedLines();
                copies.put(theme, Clipboard.getSystemClipboard().getString());
            });
        }
        assertAll(differences.entrySet().stream().map(entry -> (org.junit.jupiter.api.function.Executable)
                () -> assertAll(() -> assertEquals(0, entry.getValue(), "selection glyphs in " + entry.getKey()),
                        () -> assertEquals(stack.substring(5, 54), copies.get(entry.getKey()), "copied raw range " + entry.getKey()))));
    }

    @Test void redrawAndHorizontalScrollDoNotAccumulateGlyphs() throws Exception {
        fx(() -> {
            viewer.getHScrollBar().setValue(38.5);
            drag(2, 12, 2, 44);
            viewer.setSearchHighlight("Method", false, false);
            viewer.redraw();
            assertEquals(0, differentPixels(canvas.snapshot(null, null), reference(stack, 2, 12, 44, "Method", false), 2));
            WritableImage before = canvas.snapshot(null, null);
            for (int i = 0; i < 12; i++) viewer.redraw();
            assertEquals(0, differentPixels(canvas.snapshot(null, null), before, 2), "redraw must not thicken glyphs");
        });
    }

    @Test void reverseAndMultilineDragKeepRawTabsAndNewlines() throws Exception {
        fx(() -> {
            drag(3, 17, 2, 0);
            viewer.copySelectedLines();
            assertEquals(stack + System.lineSeparator() + nextLine.substring(0, 17), Clipboard.getSystemClipboard().getString());
            assertTrue(Clipboard.getSystemClipboard().getString().startsWith("\t"));
            assertEquals(0, differentPixels(canvas.snapshot(null, null), reference(stack, 2, 0, stack.length(), null, true), 2));
        });
    }

    @Test void wholeLineSelectionUsesReadableTextWithNoSecondGlyphLayer() throws Exception {
        fx(() -> {
            viewer.selectLine(0);
            viewer.requestCanvasFocus();
            viewer.redraw();
            String line = "2026-10-20 ERROR synthetic request failed";
            assertEquals(0, differentPixels(canvas.snapshot(null, null), reference(line, 0, 0, line.length(), null, true), 0));
        });
    }

    @Test void draggingOverCachedLinesDoesNotReadTheSourceAgain() throws Exception {
        fx(() -> {
            var reads = new java.util.concurrent.atomic.AtomicInteger();
            var entries = new ArrayList<LogEntry>();
            entries.add(new LogEntry(1, stack) {
                @Override public String getRawLog() {
                    reads.incrementAndGet();
                    return super.getRawLog();
                }
            });
            viewer.setTailBuffer(entries);
            viewer.refreshTail();
            viewer.setOnSelectionChanged(summary -> {});
            fire(MouseEvent.MOUSE_PRESSED, 0, 5, true);
            int warmReads = reads.get();
            for (int i = 6; i < 40; i++) {
                double x = number("leftMargin") + caret(stack, i);
                event(MouseEvent.MOUSE_DRAGGED, x, 12, true);
            }
            System.out.println("SOURCE_READS_DURING_CACHED_DRAG=" + (reads.get() - warmReads));
            assertEquals(warmReads, reads.get(), "dragging within the cached viewport must not reread source lines");
        });
    }

    @Test void selectionStaysAlignedAfterFocusResizeFractionalScrollAndTailAppend() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> {
                AppTheme.setTheme(theme);
                root.applyCss();
                root.layout();
                drag(2, 5, 2, 54);
                root.setFocusTraversable(true);
                root.requestFocus();
                viewer.redraw();
                assertEquals(0, differentPixels(canvas.snapshot(null, null), reference(stack, 2, 5, 54, null, false), 2), "inactive selection " + theme);
                viewer.requestCanvasFocus();
                stage.setWidth(720);
                stage.setHeight(280);
                root.applyCss();
                root.layout();
                setField("smoothScrollY", .25);
                try {
                    var method = CanvasLogViewer.class.getDeclaredMethod("applySmoothScroll");
                    method.setAccessible(true);
                    method.invoke(viewer);
                } catch (Exception error) { throw new AssertionError(error); }
                viewer.redraw();
                assertEquals(0, differentPixels(canvas.snapshot(null, null), reference(stack, 2, 5, 54, null, false), 2), "fractional scroll after resize " + theme);
                entries().add(new LogEntry(entries().size() + 1, "INFO appended synthetic line"));
                viewer.refreshTail();
                assertEquals(0, differentPixels(canvas.snapshot(null, null), reference(stack, 2, 5, 54, null, false), 2), "Tail append " + theme);
                viewer.copySelectedLines();
                assertEquals(stack.substring(5, 54), Clipboard.getSystemClipboard().getString());
            });
        }
    }

    @Test void unicodeAndTabSelectionUsesTheOriginalGlyphPositions() throws Exception {
        String raw = "\tat com.example.Caf\u00e9.\u5904\u7406\u8bf7\u6c42(Caf\u00e9.java:42) ~[synthetic.jar:1.0]";
        fx(() -> {
            viewer.setTailBuffer(new ArrayList<>(List.of(new LogEntry(1, raw))));
            viewer.refreshTail();
            drag(0, 5, 0, raw.length() - 5);
            assertEquals(0, differentPixels(canvas.snapshot(null, null), reference(raw, 0, 5, raw.length() - 5, null, false), 0));
            viewer.copySelectedLines();
            assertEquals(raw.substring(5, raw.length() - 5), Clipboard.getSystemClipboard().getString());
        });
    }

    @Test void selectedTextContrastAndSizeMeetBothThemeRequirements() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> {
                AppTheme.setTheme(theme);
                Color foreground = (Color) field(viewer, "textColor");
                for (boolean active : new boolean[]{true, false}) {
                    Color background = SelectionColors.background(theme, active);
                    double a = luminance(foreground), b = luminance(background);
                    assertTrue((Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5, "selected text contrast " + theme + " active=" + active);
                }
                assertTrue(((Font) field(viewer, "MONO_FONT")).getSize() >= 13);
            });
        }
    }

    private WritableImage reference(String raw, int row, int from, int to, String search, boolean rowEnd) {
        Canvas ref = new Canvas(canvas.getWidth(), canvas.getHeight());
        var gc = ref.getGraphicsContext2D();
        double x = number("leftMargin") - number("currentScrollX"), y = 4 + row * 18 - number("scrollOffsetY");
        gc.setFill((Color) field(viewer, "bgColor"));
        gc.fillRect(0, 0, ref.getWidth(), ref.getHeight());
        gc.save();
        gc.beginPath();
        gc.rect(number("leftMargin"), 0, ref.getWidth() - number("leftMargin"), ref.getHeight());
        gc.clip();
        gc.setFill(SelectionColors.background(AppTheme.getTheme(), SelectionColors.active(viewer)));
        double start = caret(raw, from), end = caret(raw, to);
        gc.fillRect(x + start, y, rowEnd ? ref.getWidth() - x - start : end - start, 18);
        if (search != null) {
            gc.setFill((Color) field(viewer, "highlightBg"));
            for (int position = raw.indexOf(search); position >= 0; position = raw.indexOf(search, position + search.length())) {
                gc.fillRect(x + caret(raw, position), y, caret(raw, position + search.length()) - caret(raw, position), 18);
            }
        }
        gc.setFont((Font) field(viewer, "MONO_FONT"));
        gc.setTextBaseline(VPos.TOP);
        gc.setFill((Color) field(viewer, "textColor"));
        gc.fillText(raw, x, y + 2);
        gc.restore();
        return ref.snapshot(null, null);
    }

    private int differentPixels(WritableImage actual, WritableImage expected, int row) {
        int different = 0;
        int top = (int) (4 + row * 18 - number("scrollOffsetY"));
        for (int y = Math.max(0, top); y < Math.min(actual.getHeight(), top + 18); y++) {
            for (int x = (int) number("leftMargin"); x < (int) actual.getWidth(); x++) {
                if (actual.getPixelReader().getArgb(x, y) != expected.getPixelReader().getArgb(x, y)) different++;
            }
        }
        return different;
    }

    private void drag(int startRow, int startChar, int endRow, int endChar) {
        canvas.requestFocus();
        fire(MouseEvent.MOUSE_PRESSED, startRow, startChar, true);
        fire(MouseEvent.MOUSE_DRAGGED, endRow, endChar, true);
        fire(MouseEvent.MOUSE_RELEASED, endRow, endChar, false);
    }

    private void fire(javafx.event.EventType<MouseEvent> type, int row, int character, boolean down) {
        double x = number("leftMargin") + caret(entries().get(row).getRawLog(), character) - number("currentScrollX");
        double y = 4 + row * 18 + 8 - number("scrollOffsetY");
        event(type, x, y, down);
    }

    private void event(javafx.event.EventType<MouseEvent> type, double x, double y, boolean down) {
        var point = canvas.localToScene(x, y);
        Event.fireEvent(canvas, new MouseEvent(type, point.getX(), point.getY(), point.getX(), point.getY(), MouseButton.PRIMARY, 1,
                false, false, false, false, down, false, false, true, false, false, null));
    }

    private void snapshot(String name) {
        try {
            String phase = System.getenv().getOrDefault("SEELOGGY_SELECTION_REPORT_PHASE", "after");
            Path directory = System.getenv().containsKey("SEELOGGY_SELECTION_REPORT_PHASE")
                    ? Path.of("docs/reports/assets/canvas-selection-fix") : Path.of("build/canvas-selection-review");
            Files.createDirectories(directory);
            WritableImage full = root.snapshot(null, null);
            write(full, directory.resolve(phase + "-" + name + ".png"));
            int top = (int) canvas.localToScene(0, 4 + 2 * 18 - 3).getY();
            int width = (int) Math.min(full.getWidth(), 950);
            WritableImage crop = new WritableImage(full.getPixelReader(), 0, Math.max(0, top), width, 27);
            write(crop, directory.resolve(phase + "-" + name + "-crop.png"));
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private static void write(WritableImage image, Path path) throws Exception {
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, width, height, pixels, 0, width);
        javax.imageio.ImageIO.write(png, "png", path.toFile());
    }

    private double caret(String line, int index) {
        var text = new Text(line);
        text.setFont((Font) field(viewer, "MONO_FONT"));
        for (var element : text.caretShape(index, true)) {
            if (element instanceof javafx.scene.shape.MoveTo move) return move.getX();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<LogEntry> entries() { return (List<LogEntry>) field(viewer, "tailBuffer"); }
    private double number(String name) { return ((Number) field(viewer, name)).doubleValue(); }
    private void setField(String name, Object value) {
        try { var f = CanvasLogViewer.class.getDeclaredField(name); f.setAccessible(true); f.set(viewer, value); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static Object field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static double luminance(Color c) { return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue()); }
    private static double linear(double v) { return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4); }
    private static void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(15, TimeUnit.SECONDS); WaitForAsyncUtils.waitForFxEvents(); }
}
