package com.seeloggyplus.features.search.presentation;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.SelectionColors;
import com.seeloggyplus.shared.util.IntArrayList;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class SearchResultPanelBehaviorTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    @Test
    @DisplayName("empty results show the placeholder and clearing resets the panel")
    void emptyResultsShowPlaceholder() throws Exception {
        SearchResultPanel panel = new SearchResultPanel();
        Label header = header(panel);

        panel.showResults(null, 0);
        assertEquals("No results", header.getText());
        assertTrue(panel.isVisible());
        assertTrue(panel.isManaged());
        assertEquals(0, panel.getItemCount());
        assertEquals(-1, privateField(panel, "selectedIndex"));

        panel.clear();
        assertEquals("Search Results", header.getText());
        assertFalse(panel.isVisible());
        assertFalse(panel.isManaged());

        panel.showResults(new IntArrayList(), 0);
        assertEquals("No results", header.getText());
        assertEquals(0, panel.getItemCount());
    }

    @Test
    @DisplayName("results update the header, count and selection state")
    void resultsExposeHeaderAndCount() throws Exception {
        SearchResultPanel panel = new SearchResultPanel();
        panel.showResults(list(9, 10, 11), 12);

        assertEquals("3 results", header(panel).getText());
        assertEquals(3, panel.getItemCount());
        assertTrue(panel.isVisible());
        assertTrue(panel.isManaged());
        assertEquals(-1, privateField(panel, "selectedIndex"));
        assertTrue(((Integer) privateField(panel, "leftMargin")) > 0);
    }

    @Test
    @DisplayName("selecting a match reports its index, global line and content")
    void selectionCallbackReportsMatch() throws Exception {
        SearchResultPanel panel = new SearchResultPanel();
        panel.showResults(list(4, 9, 14), 15);
        panel.setLineContentResolver(line -> "line-" + line);
        List<String> events = new ArrayList<>();
        panel.setOnLineSelected((index, globalLine, content) -> events.add(index + ":" + globalLine + ":" + content));

        panel.selectAndTriggerLine(1);
        assertEquals(List.of("1:9:line-9"), events);

        panel.selectAndTriggerLine(-1);
        panel.selectAndTriggerLine(3);
        assertEquals(1, events.size());
        assertEquals(1, privateField(panel, "selectedIndex"));
    }

    @Test
    @DisplayName("missing, null and failing resolvers render empty content")
    void lineContentFallsBackToEmpty() {
        SearchResultPanel panel = new SearchResultPanel();
        panel.showResults(list(0, 1, 2), 3);
        List<String> contents = new ArrayList<>();
        panel.setOnLineSelected((index, globalLine, content) -> contents.add(content));

        panel.selectAndTriggerLine(0);
        panel.setLineContentResolver(line -> null);
        panel.selectAndTriggerLine(1);
        panel.setLineContentResolver(line -> {
            throw new IllegalStateException("resolver failure");
        });
        panel.selectAndTriggerLine(2);

        assertEquals(List.of("", "", ""), contents);
    }

    @Test
    @DisplayName("long lines are truncated to five hundred characters")
    void longLineIsTruncated() {
        SearchResultPanel panel = new SearchResultPanel();
        panel.showResults(list(0), 1);
        panel.setLineContentResolver(line -> "x".repeat(600));
        List<String> contents = new ArrayList<>();
        panel.setOnLineSelected((index, globalLine, content) -> contents.add(content));

        panel.selectAndTriggerLine(0);

        assertEquals(1, contents.size());
        assertEquals(500, contents.get(0).length());
    }

    @Test
    @DisplayName("trimming drops leading matches and shifts cached content")
    void trimFromStartShiftsMatches() throws Exception {
        SearchResultPanel panel = new SearchResultPanel();
        panel.showResults(list(0, 1, 2, 3), 4);
        AtomicInteger resolutions = new AtomicInteger();
        panel.setLineContentResolver(line -> {
            resolutions.incrementAndGet();
            return "line-" + line;
        });
        List<String> events = new ArrayList<>();
        panel.setOnLineSelected((index, globalLine, content) -> events.add(index + ":" + globalLine + ":" + content));
        for (int i = 0; i < 4; i++) {
            panel.selectAndTriggerLine(i);
        }

        panel.trimFromStart(2);

        assertEquals(2, panel.getItemCount());
        assertEquals("2 results", header(panel).getText());
        assertEquals(1, privateField(panel, "selectedIndex"));
        events.clear();
        panel.selectAndTriggerLine(0);
        assertEquals(List.of("0:2:line-2"), events);
        assertEquals(4, resolutions.get(), "trimmed content must come from the shifted cache");
    }

    @Test
    @DisplayName("trimming ignores null state and non-positive counts")
    void trimFromStartGuards() throws Exception {
        SearchResultPanel panel = new SearchResultPanel();

        panel.trimFromStart(3);
        assertEquals(0, panel.getItemCount());

        panel.showResults(list(1, 2), 3);
        panel.trimFromStart(0);
        assertEquals(2, panel.getItemCount());
        panel.trimFromStart(-4);
        assertEquals(2, panel.getItemCount());
        panel.trimFromStart(9);
        assertEquals(0, panel.getItemCount());
        assertEquals("0 results", header(panel).getText());
    }

    @Test
    @DisplayName("syncing follows growth and shrinkage of the underlying matches")
    void syncItemCountFollowsListChanges() throws Exception {
        SearchResultPanel panel = new SearchResultPanel();
        IntArrayList matches = list(2, 5);
        panel.showResults(matches, 8);
        panel.setLineContentResolver(line -> "line-" + line);
        List<String> events = new ArrayList<>();
        panel.setOnLineSelected((index, globalLine, content) -> events.add(index + ":" + globalLine + ":" + content));

        panel.selectAndTriggerLine(1);
        matches.add(7);
        matches.add(9);
        panel.syncItemCount();

        assertEquals(4, panel.getItemCount());
        assertEquals("4 results", header(panel).getText());
        panel.selectAndTriggerLine(3);

        matches.clear();
        matches.add(2);
        matches.add(5);
        matches.add(7);
        panel.syncItemCount();
        panel.syncItemCount();

        assertEquals(3, panel.getItemCount());
        assertEquals(List.of("1:5:line-5", "3:9:line-9"), events);
    }

    @Test
    @DisplayName("selecting an off-screen match scrolls the viewport to it")
    void selectIndexScrollsIntoView() throws Exception {
        SearchResultPanel panel = new SearchResultPanel();
        IntArrayList matches = new IntArrayList();
        for (int i = 0; i < 100; i++) {
            matches.add(i * 2);
        }
        panel.showResults(matches, 200);

        panel.selectIndex(50);
        assertEquals(21L, privateField(panel, "currentTopLine"));
        assertEquals(21.0, ((ScrollBar) privateField(panel, "vScrollBar")).getValue(), 0.0001);
        assertEquals(50, privateField(panel, "selectedIndex"));

        panel.selectIndex(2);
        assertEquals(2L, privateField(panel, "currentTopLine"));

        panel.selectIndex(-1);
        panel.selectIndex(100);
        assertEquals(2L, privateField(panel, "currentTopLine"));
        assertEquals(2, privateField(panel, "selectedIndex"));
    }

    @Test
    @DisplayName("scrollbar changes move the viewport offsets")
    void scrollBarsMoveViewport() throws Exception {
        SearchResultPanel panel = showPanel(400, 320);
        onFxThread(() -> panel.showResults(sequence(100), 100));
        ScrollBar v = (ScrollBar) privateField(panel, "vScrollBar");
        ScrollBar h = (ScrollBar) privateField(panel, "hScrollBar");

        onFxThread(() -> v.setValue(7));
        assertEquals(7L, privateField(panel, "currentTopLine"));

        onFxThread(() -> v.setValue(7));
        assertEquals(7L, privateField(panel, "currentTopLine"));

        onFxThread(() -> h.setValue(25));
        assertEquals(25.0, (Double) privateField(panel, "currentScrollX"), 0.0001);
    }

    @Test
    @DisplayName("wheel scrolling moves lines vertically and horizontally")
    void scrollGesturesMoveViewport() throws Exception {
        SearchResultPanel panel = showPanel(400, 320);
        onFxThread(() -> panel.showResults(sequence(100), 100));
        Canvas canvas = canvas(panel);

        onFxThread(() -> Event.fireEvent(canvas, scrollEvent(0, -120, false)));
        assertEquals(9L, privateField(panel, "currentTopLine"));

        onFxThread(() -> Event.fireEvent(canvas, scrollEvent(0, 1000, false)));
        assertEquals(0L, privateField(panel, "currentTopLine"));

        onFxThread(() -> Event.fireEvent(canvas, scrollEvent(-10, 0, false)));
        assertEquals(10.0, (Double) privateField(panel, "currentScrollX"), 0.0001);

        onFxThread(() -> Event.fireEvent(canvas, scrollEvent(0, 5, true)));
        assertEquals(5.0, (Double) privateField(panel, "currentScrollX"), 0.0001);
    }

    @Test
    @DisplayName("wheel scrolling without results is ignored")
    void scrollWithoutResultsIsIgnored() throws Exception {
        SearchResultPanel panel = showPanel(400, 320);
        onFxThread(() -> panel.showResults(null, 0));
        Canvas canvas = canvas(panel);

        onFxThread(() -> Event.fireEvent(canvas, scrollEvent(0, -120, false)));

        assertEquals(0L, privateField(panel, "currentTopLine"));
        assertEquals(0.0, (Double) privateField(panel, "currentScrollX"), 0.0001);
    }

    @Test
    @DisplayName("hovering highlights the row under the pointer and clears on exit")
    void hoverTracksPointer() throws Exception {
        SearchResultPanel panel = showPanel(400, 320);
        onFxThread(() -> panel.showResults(list(0, 1, 2), 3));
        Canvas canvas = canvas(panel);

        onFxThread(() -> Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_MOVED, 13)));
        assertEquals(0, privateField(panel, "hoveredIndex"));

        onFxThread(() -> panel.selectIndex(0));
        onFxThread(() -> Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_EXITED, 13)));
        onFxThread(() -> Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_MOVED, 13)));
        assertEquals(0, privateField(panel, "hoveredIndex"));

        onFxThread(() -> Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_MOVED, 49)));
        assertEquals(2, privateField(panel, "hoveredIndex"));

        onFxThread(() -> Event.fireEvent(canvas, mouseEvent(canvas, MouseEvent.MOUSE_EXITED, 0)));
        assertEquals(-1, privateField(panel, "hoveredIndex"));
    }

    @Test
    @DisplayName("primary clicks select a match while other buttons and empty rows are ignored")
    void mouseClicksSelectMatches() throws Exception {
        SearchResultPanel panel = showPanel(400, 320);
        onFxThread(() -> panel.showResults(list(0, 1, 2), 3));
        Canvas canvas = canvas(panel);
        List<String> events = new ArrayList<>();
        panel.setOnLineSelected((index, globalLine, content) -> events.add(index + ":" + globalLine));

        onFxThread(() -> Event.fireEvent(canvas, clickEvent(canvas, 31, MouseButton.SECONDARY)));
        assertEquals(List.of(), events);

        onFxThread(() -> Event.fireEvent(canvas, clickEvent(canvas, 31, MouseButton.PRIMARY)));
        assertEquals(List.of("1:1"), events);

        onFxThread(() -> Event.fireEvent(canvas, clickEvent(canvas, 301, MouseButton.PRIMARY)));
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("the palette switches with dark mode and selection follows the shared colors")
    void paletteSwitchesWithDarkMode() throws Exception {
        SearchResultPanel panel = new SearchResultPanel();
        assertEquals(Color.WHITE, backgroundFill(panel));
        assertEquals(SelectionColors.background(false), privateField(panel, "selectionColor"));

        onFxThread(() -> {
            panel.setDarkMode(true);
            panel.setDarkMode(true);
        });
        assertEquals(Color.web("#1e2226"), backgroundFill(panel));
        assertEquals(SelectionColors.background(true), privateField(panel, "selectionColor"));

        onFxThread(() -> panel.setDarkMode(false));
        assertEquals(Color.WHITE, backgroundFill(panel));
        assertEquals(SelectionColors.background(false), privateField(panel, "selectionColor"));
    }

    @Test
    @DisplayName("theme style classes on the scene root toggle the dark palette")
    void sceneThemeClassTogglesPalette() {
        onFxThread(() -> {
            SearchResultPanel panel = new SearchResultPanel();
            StackPane root = new StackPane(panel);
            stage.setScene(AppTheme.scene(root));
            stage.setWidth(300);
            stage.setHeight(220);
            stage.show();
            root.getStyleClass().removeAll("theme-dark", "theme-light");

            assertEquals(Color.WHITE, backgroundFill(panel));

            root.getStyleClass().add("theme-dark");
            assertNotNull(panel.getScene());
            assertEquals(Color.web("#1e2226"), backgroundFill(panel));

            root.getStyleClass().remove("theme-dark");
            assertEquals(Color.WHITE, backgroundFill(panel));
        });
    }

    @Test
    @DisplayName("search patterns highlight matches and draw their text")
    void patternMatchesRenderHighlightAndText() throws Exception {
        SearchResultPanel panel = showPanel(400, 320);
        onFxThread(() -> {
            panel.setLineContentResolver(line -> "ERROR line " + line);
            panel.showResults(list(10, 11), 12);
            panel.setSearchPattern(Pattern.compile("line"));
        });

        int margin = (Integer) privateField(panel, "leftMargin");
        AtomicBoolean painted = new AtomicBoolean(false);
        onFxThread(() -> {
            WritableImage image = canvas(panel).snapshot(null, null);
            PixelReader reader = image.getPixelReader();
            for (int y = 6; y < 20 && !painted.get(); y++) {
                for (int x = margin; x < Math.min((int) image.getWidth(), margin + 120); x++) {
                    if (reader.getArgb(x, y) != 0xFFFFFFFF) {
                        painted.set(true);
                        break;
                    }
                }
            }
        });

        assertTrue(painted.get(), "match text or highlight must be painted on the canvas");
    }

    private SearchResultPanel showPanel(int width, int height) {
        AtomicReference<SearchResultPanel> ref = new AtomicReference<>();
        onFxThread(() -> {
            SearchResultPanel panel = new SearchResultPanel();
            StackPane root = new StackPane(panel);
            stage.setScene(AppTheme.scene(root));
            stage.setWidth(width);
            stage.setHeight(height);
            stage.show();
            root.applyCss();
            root.layout();
            ref.set(panel);
        });
        SearchResultPanel panel = ref.get();
        assertTrue(canvas(panel).getWidth() > 0, "the canvas must be laid out for rendering tests");
        return panel;
    }

    private static IntArrayList list(int... values) {
        IntArrayList list = new IntArrayList();
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    private static IntArrayList sequence(int count) {
        IntArrayList list = new IntArrayList();
        for (int i = 0; i < count; i++) {
            list.add(i);
        }
        return list;
    }

    private static Label header(SearchResultPanel panel) throws Exception {
        return (Label) privateField(panel, "headerLabel");
    }

    private static Canvas canvas(SearchResultPanel panel) {
        try {
            return (Canvas) privateField(panel, "canvas");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object privateField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Color backgroundFill(Region region) {
        Background background = region.getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return null;
        }
        Paint paint = background.getFills().get(0).getFill();
        return paint instanceof Color color ? color : null;
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

    private static MouseEvent mouseEvent(Canvas canvas, javafx.event.EventType<MouseEvent> type, double localY) {
        double sceneX = canvas.localToScene(0, 0).getX();
        double sceneY = canvas.localToScene(0, localY).getY();
        return new MouseEvent(type, sceneX, sceneY, sceneX, sceneY, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, true, false, false, null);
    }

    private static MouseEvent clickEvent(Canvas canvas, double localY, MouseButton button) {
        double sceneX = canvas.localToScene(0, 0).getX();
        double sceneY = canvas.localToScene(0, localY).getY();
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, sceneX, sceneY, sceneX, sceneY, button, 1,
                false, false, false, false, true, false, false, true, false, false, null);
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
