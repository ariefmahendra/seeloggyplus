package com.seeloggyplus.shared.ui.canvas;

import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.util.IntArrayList;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class CanvasLogViewerInteractionTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        AppTheme.setTheme(AppTheme.Theme.LIGHT);
    }

    private static Object readField(Object target, String name) {
        try {Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);}
        catch (ReflectiveOperationException error) {throw new AssertionError(error);}
    }

    @Test
    void coldTailActivationAndMatchCountingCompleteWithoutFxDiskReads() throws Exception {
        var history = new com.seeloggyplus.features.logs.infrastructure.io.SpoolingTailHistory(
                java.nio.file.Path.of("build/performance/test-fixtures"), 1024);
        String raw = "POST activation " + "x".repeat(2000);
        history.add(new LogEntry(0, raw)); history.add(new LogEntry(1, "GET " + "y".repeat(2000)));
        var guarded = (com.seeloggyplus.shared.tail.TailHistory) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.seeloggyplus.shared.tail.TailHistory.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("get") && Platform.isFxApplicationThread())
                        throw new AssertionError("cold history read on FX thread");
                    try {return method.invoke(history, arguments);}
                    catch (java.lang.reflect.InvocationTargetException error) {throw error.getCause();}
                });
        AtomicReference<CanvasLogViewer> reference = new AtomicReference<>();
        AtomicReference<String> activated = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger matches = new java.util.concurrent.atomic.AtomicInteger(-1);
        try {
            onFxThread(() -> {
                var viewer = shownViewer(); reference.set(viewer);
                viewer.setTailBuffer(guarded); viewer.setOnLineClick((row, text) -> activated.set(text));
                Event.fireEvent(canvasOf(viewer), mouseEvent(canvasOf(viewer), MouseEvent.MOUSE_PRESSED,
                        rowY(0), MouseButton.PRIMARY, 1, false, true));
                viewer.setSearchHighlight("POST", false, true);
                viewer.countMatchesAsync((count, truncated) -> {assertTrue(Platform.isFxApplicationThread()); matches.set(count);});
            });
            WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS,
                    () -> raw.equals(activated.get()) && matches.get() == 1);
        } finally {onFxThread(() -> {if (reference.get() != null) reference.get().resetView();}); history.close();}
    }

    @Test
    void copyingColdTailSelectionReadsHistoryAwayFromTheFxThread() throws Exception {
        var history = new com.seeloggyplus.features.logs.infrastructure.io.SpoolingTailHistory(
                java.nio.file.Path.of("build/performance/test-fixtures"), 1024);
        String raw = "POST cold-copy " + "x".repeat(2000);
        history.add(new LogEntry(0, raw));
        var guarded = (com.seeloggyplus.shared.tail.TailHistory) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.seeloggyplus.shared.tail.TailHistory.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("get") && Platform.isFxApplicationThread())
                        throw new AssertionError("cold history read on FX thread");
                    try {return method.invoke(history, arguments);}
                    catch (java.lang.reflect.InvocationTargetException error) {throw error.getCause();}
                });
        AtomicReference<CanvasLogViewer> reference = new AtomicReference<>();
        try {
            onFxThread(() -> {
                ClipboardContent initial = new ClipboardContent(); initial.putString("before copy");
                Clipboard.getSystemClipboard().setContent(initial);
                var viewer = shownViewer(); reference.set(viewer);
                viewer.setTailBuffer(guarded); viewer.selectLine(0); viewer.copySelectedLines();
            });
            WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS,
                    () -> WaitForAsyncUtils.asyncFx(() -> raw.equals(Clipboard.getSystemClipboard().getString())).get());
        } finally {onFxThread(() -> {if (reference.get() != null) reference.get().resetView();}); history.close();}
    }

    @Test
    void resettingTheViewerReleasesDecodedTextAndGlyphLayouts() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(20));
            viewer.redraw();
            assertTrue(((String[]) readField(viewer, "lineCache")).length > 0);
            viewer.resetView();
            assertEquals(0, ((String[]) readField(viewer, "lineCache")).length);
            assertEquals(0, ((javafx.scene.text.Text[]) readField(viewer, "lineLayoutCache")).length);
            assertEquals(0, ((java.util.Map<?, ?>) readField(viewer, "loadedTailRows")).size());
        });
    }

    @Test
    void coldTailViewportLoadsRowsLargerThanTheHotCache() throws Exception {
        var history = new com.seeloggyplus.features.logs.infrastructure.io.SpoolingTailHistory(
                java.nio.file.Path.of("build/performance/test-fixtures"), 1024);
        for (int i = 0; i < 10; i++) history.add(new LogEntry(i, "POST row-" + i + "x".repeat(2000)));
        AtomicReference<CanvasLogViewer> reference = new AtomicReference<>();
        try {
            onFxThread(() -> {
                var viewer = shownViewer(); reference.set(viewer);
                viewer.setFollowTail(false); viewer.setTailBuffer(history); viewer.redraw();
            });
            WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS, () -> {
                java.util.concurrent.atomic.AtomicBoolean complete = new java.util.concurrent.atomic.AtomicBoolean();
                onFxThread(() -> {
                    String[] lines = (String[]) readField(reference.get(), "lineCache");
                    complete.set(lines.length >= 10 && java.util.stream.IntStream.range(0, 10)
                            .allMatch(i -> lines[i] != null && lines[i].contains("POST row-" + i)));
                });
                return complete.get();
            });
        } finally {onFxThread(() -> {if (reference.get() != null) reference.get().resetView();}); history.close();}
    }

    @Test
    void scrollingReusesLayoutsForOverlappingRows() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setFollowTail(false);
            viewer.setTailBuffer(tailLines(200));
            viewer.redraw();
            javafx.scene.text.Text[] before = ((javafx.scene.text.Text[]) readField(viewer, "lineLayoutCache")).clone();
            viewer.getVScrollBar().setValue(1);
            viewer.redraw();
            javafx.scene.text.Text[] after = (javafx.scene.text.Text[]) readField(viewer, "lineLayoutCache");
            assertSame(before[1], after[0], "overlapping text layout should survive a one-row scroll");
        });
    }

    @Test
    void primaryClickSelectsLineAndNotifiesHandlers() {
        onFxThread(() -> {
            CanvasLogViewer empty = new CanvasLogViewer();
            List<String> emptyClicks = new ArrayList<>();
            empty.setOnLineClick((number, content) -> emptyClicks.add(number + ":" + content));
            Event.fireEvent(canvasOf(empty), mouseEvent(canvasOf(empty), MouseEvent.MOUSE_PRESSED,
                    rowY(0), MouseButton.PRIMARY, 1, false, true));
            assertTrue(emptyClicks.isEmpty());
            assertEquals(-1, empty.getSelectedIndex());

            CanvasLogViewer viewer = shownViewer();
            List<String> clicks = new ArrayList<>();
            List<String> doubles = new ArrayList<>();
            viewer.setOnLineClick((number, content) -> clicks.add(number + ":" + content));
            viewer.setOnLineDoubleClick((number, content) -> doubles.add(number + ":" + content));
            viewer.setTailBuffer(tailLines(20));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(0), MouseButton.PRIMARY, 1, false, true));
            assertEquals(List.of("0:tail-1"), clicks);
            assertEquals(0, viewer.getSelectedIndex());
            assertTrue(viewer.hasSelection());

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(2), MouseButton.PRIMARY, 2, false, true));
            assertEquals(List.of("2:tail-3"), doubles);
            assertEquals(2, viewer.getSelectedIndex());

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(40), MouseButton.PRIMARY, 1, false, true));
            assertEquals(1, clicks.size());
            assertEquals(2, viewer.getSelectedIndex());
        });
    }

    @Test
    void shiftClickExtendsSelectionRangeInViewOrder() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(0), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(2), MouseButton.PRIMARY, 1, true, true));
            viewer.copySelectedLines();

            String expected = String.join(System.lineSeparator(), "tail-1", "tail-2", "tail-3");
            assertEquals(expected, Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void filteredShiftClickCopiesMappedLinesInViewOrder() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.setFilteredIndexes(filter(3, 1));
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(0), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(1), MouseButton.PRIMARY, 1, true, true));
            viewer.copySelectedLines();

            String expected = String.join(System.lineSeparator(), "tail-4", "tail-2");
            assertEquals(expected, Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void dragSelectsRangeAndClampsToViewport() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(30));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);
            int visible = privateInt(viewer, "visibleLineCount");

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(1), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    10000, rowY(4), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    10000, -50, MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    10000, 10000, MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    10000, 10000, MouseButton.PRIMARY, 1, false, true));

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    rowY(4), MouseButton.SECONDARY, 1, false, false));
            assertTrue((boolean) privateField(viewer, "isDragging"));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    rowY(4), MouseButton.PRIMARY, 1, false, false));
            assertFalse((boolean) privateField(viewer, "isDragging"));

            viewer.copySelectedLines();

            StringBuilder expected = new StringBuilder();
            for (int i = 1; i <= visible; i++) {
                if (i > 1) {
                    expected.append(System.lineSeparator());
                }
                expected.append("tail-").append(i + 1);
            }
            assertEquals(expected.toString(), Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void dragWithinLineSelectsCharactersAndCopiesSubstring() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 2), rowY(1), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    charX(viewer, 6), rowY(1), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    charX(viewer, 6), rowY(1), MouseButton.PRIMARY, 1, false, false));

            assertTrue(viewer.hasSelection());
            viewer.copySelectedLines();
            assertEquals("il-2", Clipboard.getSystemClipboard().getString());

            KeyEvent ctrlC = keyEvent(KeyCode.C, true);
            viewer.handleKeyNavigation(ctrlC);
            assertTrue(ctrlC.isConsumed());
            assertEquals("il-2", Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void dragAcrossLinesCopiesPartialFirstAndLastLine() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 3), rowY(0), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    charX(viewer, 2), rowY(2), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    charX(viewer, 2), rowY(2), MouseButton.PRIMARY, 1, false, false));

            viewer.copySelectedLines();

            String expected = String.join(System.lineSeparator(), "l-1", "tail-2", "ta");
            assertEquals(expected, Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void dragBackwardsNormalizesSelectionOrder() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 4), rowY(2), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    charX(viewer, 1), rowY(0), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    charX(viewer, 1), rowY(0), MouseButton.PRIMARY, 1, false, false));

            viewer.copySelectedLines();

            String expected = String.join(System.lineSeparator(), "ail-1", "tail-2", "tail");
            assertEquals(expected, Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void dragClampsCharacterIndexToLineLength() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 2), rowY(0), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    10000, rowY(1), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    10000, rowY(1), MouseButton.PRIMARY, 1, false, false));

            viewer.copySelectedLines();

            String expected = String.join(System.lineSeparator(), "il-1", "tail-2");
            assertEquals(expected, Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void contextMenuCopyKeepsCharacterSelection() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 1), rowY(2), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    charX(viewer, 4), rowY(2), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    charX(viewer, 4), rowY(2), MouseButton.PRIMARY, 1, false, false));

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 2), rowY(2), MouseButton.SECONDARY, 1, false, false));

            ContextMenu menu = (ContextMenu) privateField(viewer, "currentContextMenu");
            assertNotNull(menu);
            menu.getItems().get(0).fire();

            assertEquals("ail", Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void contextMenuSelectsClickedLineWhenOutsideSelection() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 1), rowY(0), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    charX(viewer, 4), rowY(0), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    charX(viewer, 4), rowY(0), MouseButton.PRIMARY, 1, false, false));

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 1), rowY(3), MouseButton.SECONDARY, 1, false, false));

            ContextMenu menu = (ContextMenu) privateField(viewer, "currentContextMenu");
            assertNotNull(menu);
            menu.getItems().get(0).fire();

            assertEquals("tail-4", Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void secondaryClickSelectsLineAndContextMenuCopies() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(1), MouseButton.SECONDARY, 1, false, false));
            ContextMenu menu = (ContextMenu) privateField(viewer, "currentContextMenu");
            assertNotNull(menu);
            assertEquals(1, menu.getItems().size());
            MenuItem copyItem = menu.getItems().get(0);
            assertEquals("Copy Selection", copyItem.getText());
            assertTrue(viewer.hasSelection());
            assertEquals(-1, viewer.getSelectedIndex());

            copyItem.fire();
            assertEquals("tail-2", Clipboard.getSystemClipboard().getString());

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(1), MouseButton.SECONDARY, 1, false, false));
            ContextMenu second = (ContextMenu) privateField(viewer, "currentContextMenu");
            assertNotNull(second);
            assertNotSame(menu, second);
            second.hide();

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(3), MouseButton.PRIMARY, 1, false, true));
            assertNull(privateField(viewer, "currentContextMenu"));
            assertEquals(3, viewer.getSelectedIndex());
        });
    }

    @Test
    void pageAndHomeEndKeysMoveViewport() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(100));
            viewer.getVScrollBar().setValue(0);
            int visible = privateInt(viewer, "visibleLineCount");
            int maxTop = 100 - visible;

            KeyEvent pageDown = keyEvent(KeyCode.PAGE_DOWN, false);
            viewer.handleKeyNavigation(pageDown);
            assertTrue(pageDown.isConsumed());
            assertEquals(visible, viewer.getCurrentTopLine());

            viewer.handleKeyNavigation(keyEvent(KeyCode.PAGE_UP, false));
            assertEquals(0, viewer.getCurrentTopLine());

            viewer.getVScrollBar().setValue(maxTop);
            viewer.handleKeyNavigation(keyEvent(KeyCode.PAGE_DOWN, false));
            assertEquals(maxTop, viewer.getCurrentTopLine());

            viewer.getVScrollBar().setValue(0);
            viewer.handleKeyNavigation(keyEvent(KeyCode.END, true));
            assertEquals(maxTop, viewer.getCurrentTopLine());

            viewer.handleKeyNavigation(keyEvent(KeyCode.HOME, true));
            assertEquals(0, viewer.getCurrentTopLine());

            KeyEvent plainHome = keyEvent(KeyCode.HOME, false);
            viewer.handleKeyNavigation(plainHome);
            assertTrue(plainHome.isConsumed());
            assertEquals(0, viewer.getCurrentTopLine());
        });
    }

    @Test
    void leftAndRightKeysScrollHorizontally() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));

            KeyEvent right = keyEvent(KeyCode.RIGHT, false);
            viewer.handleKeyNavigation(right);
            assertTrue(right.isConsumed());
            assertEquals(40.0, viewer.getHScrollBar().getValue(), 1e-9);
            assertEquals(40.0, privateDouble(viewer, "currentScrollX"), 1e-9);

            viewer.handleKeyNavigation(keyEvent(KeyCode.LEFT, false));
            assertEquals(0.0, viewer.getHScrollBar().getValue(), 1e-9);

            viewer.handleKeyNavigation(keyEvent(KeyCode.LEFT, false));
            assertEquals(0.0, viewer.getHScrollBar().getValue(), 1e-9);
        });
    }

    @Test
    void ctrlCCopiesSelectionAndConsumesEvent() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(3));
            viewer.selectLine(1);

            KeyEvent ctrlC = keyEvent(KeyCode.C, true);
            viewer.handleKeyNavigation(ctrlC);

            assertTrue(ctrlC.isConsumed());
            assertEquals("tail-2", Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void copyIsScheduledWhenCalledOutsideFxThread() throws Exception {
        AtomicReference<CanvasLogViewer> ref = new AtomicReference<>();
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.setTailBuffer(tailLines(2));
            viewer.selectLine(0);
            ref.set(viewer);
        });

        ref.get().copySelectedLines();
        WaitForAsyncUtils.waitForFxEvents();

        String copied = WaitForAsyncUtils.asyncFx(() -> Clipboard.getSystemClipboard().getString()).get();
        assertEquals("tail-1", copied);
    }

    @Test
    void copyWithoutSelectionLeavesClipboardUntouchedAndMapsFilteredSelection() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            ClipboardContent sentinel = new ClipboardContent();
            sentinel.putString("sentinel");
            Clipboard.getSystemClipboard().setContent(sentinel);

            viewer.copySelectedLines();
            assertEquals("sentinel", Clipboard.getSystemClipboard().getString());

            viewer.setFilteredIndexes(filter(2, 4));
            setPrivateField(viewer, "selectedLine", 1L);
            viewer.copySelectedLines();
            assertEquals("tail-5", Clipboard.getSystemClipboard().getString());
        });
    }

    @Test
    void arrowKeysClampSelectionAndToggleFollowTail() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            AtomicReference<Boolean> follow = new AtomicReference<>();
            viewer.setTailBuffer(tailLines(100));
            viewer.setOnFollowTailChanged(follow::set);
            int visible = privateInt(viewer, "visibleLineCount");
            int maxTop = 100 - visible;

            viewer.handleKeyNavigation(keyEvent(KeyCode.UP, false));
            assertFalse(viewer.isFollowTail());
            assertEquals(Boolean.FALSE, follow.get());
            assertEquals(maxTop, viewer.getSelectedIndex());

            viewer.handleKeyNavigation(keyEvent(KeyCode.UP, false));
            assertEquals(maxTop - 1, viewer.getSelectedIndex());

            viewer.jumpToLine(50);
            viewer.getVScrollBar().setValue(30);
            viewer.handleKeyNavigation(keyEvent(KeyCode.DOWN, false));
            assertEquals(51 - visible + 1, viewer.getCurrentTopLine());
            assertEquals(51, viewer.getSelectedIndex());

            viewer.getVScrollBar().setValue(60);
            viewer.handleKeyNavigation(keyEvent(KeyCode.UP, false));
            assertEquals(50, viewer.getCurrentTopLine());
            assertEquals(50, viewer.getSelectedIndex());

            CanvasLogViewer fresh = shownViewer();
            fresh.setTailBuffer(tailLines(100));
            int freshMax = 100 - privateInt(fresh, "visibleLineCount");
            fresh.handleKeyNavigation(keyEvent(KeyCode.DOWN, false));
            assertEquals(freshMax, fresh.getSelectedIndex());
            fresh.jumpToLine(99);
            fresh.handleKeyNavigation(keyEvent(KeyCode.DOWN, false));
            assertEquals(99, fresh.getSelectedIndex());
            fresh.handleKeyNavigation(keyEvent(KeyCode.UP, false));
            assertEquals(98, fresh.getSelectedIndex());
        });
    }

    @Test
    void scrollEventsScrollVerticallyAndToggleFollowTail() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            AtomicReference<Boolean> follow = new AtomicReference<>();
            viewer.setTailBuffer(tailLines(100));
            viewer.setOnFollowTailChanged(follow::set);
            Canvas canvas = canvasOf(viewer);
            int maxTop = 100 - privateInt(viewer, "visibleLineCount");

            Event.fireEvent(canvas, scrollEvent(0, 40, false));
            assertFalse(viewer.isFollowTail());
            assertEquals(Boolean.FALSE, follow.get());
            assertEquals(maxTop - 3, viewer.getCurrentTopLine());

            Event.fireEvent(canvas, scrollEvent(0, 1, false));
            assertEquals(maxTop - 4, viewer.getCurrentTopLine());

            Event.fireEvent(canvas, scrollEvent(0, -1000, false));
            assertTrue(viewer.isFollowTail());
            assertEquals(Boolean.TRUE, follow.get());
            assertEquals(maxTop, viewer.getCurrentTopLine());

            Event.fireEvent(canvas, scrollEvent(-100, 0, true));
            assertEquals(100.0, viewer.getHScrollBar().getValue(), 1e-9);
            assertEquals(100.0, privateDouble(viewer, "currentScrollX"), 1e-9);

            CanvasLogViewer empty = new CanvasLogViewer();
            Event.fireEvent(canvasOf(empty), scrollEvent(0, 40, false));
            assertEquals(0, empty.getTotalLines());
        });
    }

    @Test
    void keyboardNavigationIgnoresNullConsumedAndEmptyEvents() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.handleKeyNavigation(null);

            KeyEvent consumed = keyEvent(KeyCode.DOWN, false);
            consumed.consume();
            viewer.handleKeyNavigation(consumed);

            KeyEvent down = keyEvent(KeyCode.DOWN, false);
            viewer.handleKeyNavigation(down);
            assertFalse(down.isConsumed());
            assertEquals(-1, viewer.getSelectedIndex());

            CanvasLogViewer filteredEmpty = new CanvasLogViewer();
            filteredEmpty.setTailBuffer(tailLines(3));
            filteredEmpty.setFilteredIndexes(new IntArrayList());
            KeyEvent pageDown = keyEvent(KeyCode.PAGE_DOWN, false);
            filteredEmpty.handleKeyNavigation(pageDown);
            assertFalse(pageDown.isConsumed());
            assertEquals(0, filteredEmpty.getItemCount());
        });
    }

    @Test
    void handleArrowKeyDelegatesToKeyNavigation() {
        onFxThread(() -> {
            CanvasLogViewer viewer = new CanvasLogViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);

            viewer.handleArrowKey(KeyCode.DOWN);
            assertEquals(0, viewer.getSelectedIndex());
            viewer.handleArrowKey(KeyCode.DOWN);
            assertEquals(1, viewer.getSelectedIndex());
            viewer.handleArrowKey(KeyCode.UP);
            assertEquals(0, viewer.getSelectedIndex());
        });
    }

    @Test
    void dragHighlightCoversOnlyTheSelectedCharacters() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 2), rowY(1), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    charX(viewer, 6), rowY(1), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    charX(viewer, 6), rowY(1), MouseButton.PRIMARY, 1, false, false));

            int sampleY = 4 + 18 + 1;
            WritableImage image = canvas.snapshot(null, null);
            Color inside = image.getPixelReader().getColor((int) charX(viewer, 3), sampleY);
            Color insideSecond = image.getPixelReader().getColor((int) charX(viewer, 5), sampleY);
            Color beforeStart = image.getPixelReader().getColor((int) charX(viewer, 1), sampleY);
            Color afterEnd = image.getPixelReader().getColor((int) charX(viewer, 8), sampleY);

            assertEquals(inside, insideSecond);
            assertTrue(inside.getBrightness() < beforeStart.getBrightness());
            assertEquals(beforeStart, afterEnd);
        });
    }

    @Test
    void clickHighlightSpansTheWholeRow() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(5));
            viewer.getVScrollBar().setValue(0);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 2), rowY(1), MouseButton.PRIMARY, 1, false, true));

            int sampleY = 4 + 18 + 1;
            WritableImage image = canvas.snapshot(null, null);
            Color nearText = image.getPixelReader().getColor((int) charX(viewer, 1), sampleY);
            Color farRight = image.getPixelReader().getColor((int) canvas.getWidth() - 20, sampleY);
            Color otherRow = image.getPixelReader().getColor((int) charX(viewer, 1), 4 + 3 * 18 + 1);

            assertEquals(nearText, farRight);
            assertTrue(nearText.getBrightness() < otherRow.getBrightness());
        });
    }

    @Test
    void selectionSummaryDescribesTheSelectedText() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(10));
            viewer.getVScrollBar().setValue(0);
            AtomicReference<String> latest = new AtomicReference<>();
            viewer.setOnSelectionChanged(latest::set);
            Canvas canvas = canvasOf(viewer);

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    rowY(1), MouseButton.PRIMARY, 1, false, true));
            assertEquals("1 line, 6 chars", latest.get());

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 2), rowY(1), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    charX(viewer, 6), rowY(1), MouseButton.PRIMARY, 1, false, true));
            assertEquals("1 line, 4 chars", latest.get());

            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    charX(viewer, 1), rowY(0), MouseButton.PRIMARY, 1, false, true));
            Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED,
                    charX(viewer, 3), rowY(2), MouseButton.PRIMARY, 1, false, true));
            assertEquals("3 lines, 14 chars", latest.get());

            viewer.resetView();
            assertNull(latest.get());
        });
    }

    @Test
    void selectionSummaryIsNullWithoutAnySelection() {
        onFxThread(() -> {
            CanvasLogViewer viewer = shownViewer();
            viewer.setTailBuffer(tailLines(3));
            assertNull(viewer.getSelectionSummary());
        });
    }

    private CanvasLogViewer shownViewer() {
        CanvasLogViewer viewer = new CanvasLogViewer();
        stage.setScene(AppTheme.scene(viewer));
        stage.show();
        viewer.resize(420, 240);
        return viewer;
    }

    private static Canvas canvasOf(CanvasLogViewer viewer) {
        return (Canvas) privateField(viewer, "canvas");
    }

    private static double rowY(int row) {
        return 4 + row * 18 + 5;
    }

    private static KeyEvent keyEvent(KeyCode code, boolean controlDown) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, controlDown, false, false);
    }

    private static MouseEvent mouseEvent(Canvas canvas, EventType<MouseEvent> type, double localY,
            MouseButton button, int clickCount, boolean shiftDown, boolean primaryDown) {
        return mouseEvent(canvas, type, 0, localY, button, clickCount, shiftDown, primaryDown);
    }

    private static MouseEvent mouseEvent(Canvas canvas, EventType<MouseEvent> type, double localX, double localY,
            MouseButton button, int clickCount, boolean shiftDown, boolean primaryDown) {
        Point2D scenePoint = canvas.localToScene(localX, localY);
        return new MouseEvent(type, scenePoint.getX(), scenePoint.getY(), scenePoint.getX(), scenePoint.getY(),
                button, clickCount, shiftDown, false, false, false,
                primaryDown, false, false, true, false, false, null);
    }

    private static double charX(CanvasLogViewer viewer, int charIndex) {
        return privateInt(viewer, "leftMargin") + charIndex * privateDouble(viewer, "charWidth");
    }

    private static ScrollEvent scrollEvent(double deltaX, double deltaY, boolean shiftDown) {
        return new ScrollEvent(ScrollEvent.SCROLL,
                0, 0, 0, 0,
                shiftDown, false, false, false,
                false, false,
                deltaX, deltaY, deltaX, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, null);
    }

    private static List<LogEntry> tailLines(int count) {
        List<LogEntry> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            lines.add(new LogEntry(i + 1L, "tail-" + (i + 1)));
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

    private static double privateDouble(Object target, String name) {
        return ((Number) privateField(target, name)).doubleValue();
    }

    private static int privateInt(Object target, String name) {
        return ((Number) privateField(target, name)).intValue();
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
