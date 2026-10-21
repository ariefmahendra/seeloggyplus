package com.seeloggyplus.app;

import com.seeloggyplus.features.search.presentation.SearchResultPanel;
import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.util.IntArrayList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SearchResultsRegressionTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path spoolRoot;

    private Stage stage;
    private Parent root;
    private MainController controller;
    private final CountDownLatch release = new CountDownLatch(1);

    @Start void start(Stage stage) throws Exception {
        this.stage = stage;
        var loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.setWidth(1024);
        stage.setHeight(700);
        stage.show();
    }

    @AfterEach void cleanup() throws Exception {
        release.countDown();
        fx(() -> { invoke("clearSearch"); AppTheme.setTheme(AppTheme.Theme.LIGHT); });
    }

    @Test void latestPostSearchWinsOverAnOlderTailSearch() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        var session = new LogSession("synthetic-access.log (Demo SSH)", LogSession.SessionType.REMOTE);
        session.setTailModeEnabled(true);
        session.getLiveTailList().add(new LogEntry(1, "GET /health 200") {
            @Override public String getRawLog() {
                if (!javafx.application.Platform.isFxApplicationThread() && first.compareAndSet(true, false)) {
                    entered.countDown();
                    try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
                return super.getRawLog();
            }
        });
        session.getLiveTailList().add(new LogEntry(2, "POST /api/login 200"));
        session.getLiveTailList().add(new LogEntry(3, "POST /api/logout 401"));
        fx(() -> { activate(session); search("GET"); });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        fx(() -> search("POST"));
        await(() -> panel().getItemCount() == 2);
        release.countDown();
        assertTrue(((ForkJoinPool) field(controller, "SEARCH_POOL")).awaitQuiescence(5, TimeUnit.SECONDS));
        fx(() -> {});
        fx(() -> {
            snapshot("post-search");
            System.out.println("POST_QUERY_RESULTS=" + panel().getItemCount());
            assertEquals("POST", ((TextField) root.lookup("#searchField")).getText());
            assertEquals(2, panel().getItemCount(), "an old GET search must not overwrite POST results");
            assertEquals(3, session.getCanvasLogViewer().getItemCount());
            assertTrue(session.getCanvasLogViewer().hasSearchHighlight());
        });
    }

    @Test void clearDuringTailSearchDoesNotRestoreResults() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        var session = new LogSession("clear.log", LogSession.SessionType.REMOTE);
        session.setTailModeEnabled(true);
        session.getLiveTailList().add(new LogEntry(1, "POST /api/login") {
            @Override public String getRawLog() {
                if (!javafx.application.Platform.isFxApplicationThread()) {
                    entered.countDown();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
                return super.getRawLog();
            }
        });
        fx(() -> { activate(session); search("POST"); });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        fx(() -> invoke("clearSearch"));
        release.countDown();
        assertTrue(((ForkJoinPool) field(controller, "SEARCH_POOL")).awaitQuiescence(5, TimeUnit.SECONDS));
        fx(() -> {
            assertNull(session.getFilteredIndexes(), "clear must invalidate in-flight Tail searches");
            assertFalse(panel().isVisible());
            assertFalse(session.getCanvasLogViewer().hasSearchHighlight());
        });
    }

    @Test void resultScrollbarsFitAndLongMatchesRemainReachable() throws Exception {
        String longLine = "POST /api/example 200 " + "payload-".repeat(100) + "END_MARKER";
        fx(() -> {
            var session = new LogSession("long-lines.log (Demo SSH)", LogSession.SessionType.REMOTE);
            session.setTailModeEnabled(true);
            session.getLiveTailList().add(new LogEntry(1, longLine));
            activate(session);
            IntArrayList matches = new IntArrayList();
            for (int i = 0; i < 403; i++) matches.add(i);
            invoke("showSearchResultPanelForTail", matches, java.util.regex.Pattern.compile("END_MARKER"));
            panel().setLineContentResolver(index -> longLine);
        });
        fx(() -> {});
        fx(() -> {
            var horizontal = (ScrollBar) field(panel(), "hScrollBar");
            horizontal.setValue(horizontal.getMax());
            snapshot("long-results");
            System.out.println("RESULT_BOUNDS=" + panel().getBoundsInParent());
            System.out.println("HORIZONTAL_MAX=" + horizontal.getMax());
            AtomicReference<String> selected = new AtomicReference<>();
            panel().setOnLineSelected((index, line, content) -> selected.set(content));
            panel().selectAndTriggerLine(0);
            assertEquals(longLine, selected.get(), "selection must retain the suffix beyond character 500");
            double charWidth = (double) field(panel(), "charWidth");
            Canvas canvas = (Canvas) field(panel(), "canvas");
            int margin = (int) field(panel(), "leftMargin");
            assertTrue(horizontal.getMax() >= longLine.length() * charWidth - (canvas.getWidth() - margin), "scroll range must reach the final character");
            var pixels = canvas.snapshot(null,null).getPixelReader();
            var highlight = (javafx.scene.paint.Color) field(panel(), "highlightBg");
            boolean highlighted = false;
            for (int x=margin;x<(int)canvas.getWidth();x++) {
                if (highlight.equals(pixels.getColor(x,5))) highlighted = true;
            }
            assertTrue(highlighted, "the match beyond character 500 must be painted at the horizontal endpoint");
        });
        for (var theme : AppTheme.Theme.values()) {
            for (double[] size : new double[][]{{800,600},{1024,700},{1600,900}}) {
                fx(() -> { AppTheme.setTheme(theme); stage.setWidth(size[0]); stage.setHeight(size[1]); });
                fx(() -> {
                    root.applyCss(); root.layout();
                    assertInside(root, panel());
                    assertInside(panel(), (Node) field(panel(), "vScrollBar"));
                    assertInside(panel(), (Node) field(panel(), "hScrollBar"));
                    var split = (SplitPane) field(controller, "searchSplitPane");
                    split.setDividerPositions(.5);
                });
            }
        }
    }

    @Test void resultControlsStayInsideRoundedClipAfterResize() throws Exception {
        fx(() -> {
            var session = new LogSession("layout.log", LogSession.SessionType.REMOTE);
            session.setTailModeEnabled(true);
            session.getLiveTailList().add(new LogEntry(1, "POST /api/example"));
            activate(session);
            IntArrayList matches = new IntArrayList();
            matches.add(0);
            invoke("showSearchResultPanelForTail", matches, java.util.regex.Pattern.compile("POST"));
        });
        for (var theme : AppTheme.Theme.values()) {
            for (double[] size : new double[][]{{800,600},{1024,700},{1600,900}}) {
                fx(() -> { AppTheme.setTheme(theme); stage.setWidth(size[0]); stage.setHeight(size[1]); });
                fx(() -> {
                    root.applyCss(); root.layout();
                    assertInside(root, panel());
                    var header = (Label) field(panel(), "headerLabel");
                    var foreground = (javafx.scene.paint.Color) header.getTextFill();
                    var background = com.seeloggyplus.shared.ui.RenderedColors.background(header);
                    double foregroundLuminance = luminance(foreground), backgroundLuminance = luminance(background);
                    assertTrue((Math.max(foregroundLuminance,backgroundLuminance)+.05)/(Math.min(foregroundLuminance,backgroundLuminance)+.05) >= 4.5, "result header contrast " + theme);
                    assertTrue(header.getFont().getSize() >= 11, "result count must remain readable");
                    for (String field : new String[]{"vScrollBar", "hScrollBar"}) {
                        Node bar = (Node) field(panel(), field);
                        assertInside(panel(), bar);
                        var b = panel().sceneToLocal(bar.localToScene(bar.getBoundsInLocal()));
                        var clip = panel().getClip();
                        for (double x : new double[]{b.getMinX()+.5,b.getMaxX()-.5}) {
                            for (double y : new double[]{b.getMinY()+.5,b.getMaxY()-.5}) {
                                assertTrue(clip.contains(x,y), field + " is cut by the rounded panel at " + x + "," + y);
                            }
                        }
                    }
                });
            }
        }
    }

    @Test void postSearchSupportsTypingEnterAndNoMatches() throws Exception {
        fx(() -> {
            var session = new LogSession("typing.log", LogSession.SessionType.REMOTE);
            session.setTailModeEnabled(true);
            session.getLiveTailList().add(new LogEntry(1, "GET /health"));
            session.getLiveTailList().add(new LogEntry(2, "POST /login"));
            activate(session);
            ((TextField) root.lookup("#searchField")).setText("POST");
        });
        await(() -> panel().getItemCount() == 1);
        fx(() -> {
            var input = (TextField) root.lookup("#searchField");
            input.fireEvent(new javafx.event.ActionEvent());
            assertTrue(panel().isVisible());
            search("ABSENT_MARKER");
        });
        await(() -> ((com.seeloggyplus.features.search.presentation.SearchNavigator) field(controller, "searchNavigator")).isVisible()
                && ((Label) field(field(controller, "searchNavigator"), "statusLabel")).getText().equals("No matches"));
    }

    @Test void invalidRegexHasVisibleFeedback() throws Exception {
        fx(() -> {
            var session = new LogSession("regex.log", LogSession.SessionType.REMOTE);
            session.setTailModeEnabled(true);
            session.getLiveTailList().add(new LogEntry(1, "POST /login"));
            activate(session);
            ((ToggleButton) root.lookup("#regexCheckBox")).setSelected(true);
            search("[");
        });
        fx(() -> {
            var navigator = (com.seeloggyplus.features.search.presentation.SearchNavigator) field(controller, "searchNavigator");
            var status = (Label) field(navigator, "statusLabel");
            assertTrue(navigator.isVisible(), "invalid regex must not silently disappear");
            assertTrue(status.getText().contains("Invalid"), status.getText());
        });
    }

    @Test void switchingTabsDiscardsPendingTailResults() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        var old = new LogSession("old.log", LogSession.SessionType.REMOTE);
        old.setTailModeEnabled(true);
        old.getLiveTailList().add(new LogEntry(1, "POST /old") {
            @Override public String getRawLog() {
                if (!javafx.application.Platform.isFxApplicationThread()) {
                    entered.countDown();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
                return super.getRawLog();
            }
        });
        fx(() -> { activate(old); search("POST"); });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var next = new LogSession("new.log", LogSession.SessionType.REMOTE);
        next.setTailModeEnabled(true);
        next.getLiveTailList().add(new LogEntry(1, "GET /new"));
        fx(() -> activate(next));
        release.countDown();
        assertTrue(((ForkJoinPool) field(controller, "SEARCH_POOL")).awaitQuiescence(5, TimeUnit.SECONDS));
        fx(() -> {
            assertNull(next.getFilteredIndexes(), "old results must not be assigned to the new tab");
            assertFalse(panel().isVisible());
            assertFalse(next.getCanvasLogViewer().hasSearchHighlight());
        });
    }

    @Test void tailAppendUpdatesAPreviouslyEmptyResultPanel() throws Exception {
        var session = new LogSession("append.log", LogSession.SessionType.REMOTE);
        session.setTailModeEnabled(true);
        session.getLiveTailList().add(new LogEntry(1, "GET /health"));
        fx(() -> { activate(session); search("POST"); });
        await(() -> session.getFilteredIndexes() != null && session.getFilteredIndexes().isEmpty());
        fx(() -> {
            session.getTailBuffer().add(new LogEntry(2, "POST /new"));
            invoke("scheduleTailFlush", session);
        });
        fx(() -> {
            assertEquals(1, session.getFilteredIndexes().size());
            assertEquals(1, panel().getItemCount(), "new Tail matches must populate a previously empty result panel");
            assertTrue(panel().isVisible());
        });
    }

    @Test void returningToATabResumesItsUnfinishedQuery() throws Exception {
        var old = new LogSession("resume.log", LogSession.SessionType.REMOTE);
        old.setTailModeEnabled(true);
        old.getLiveTailList().add(new LogEntry(1, "POST /resume"));
        var next = new LogSession("other.log", LogSession.SessionType.REMOTE);
        next.setTailModeEnabled(true);
        fx(() -> {
            activate(old);
            ((TextField) root.lookup("#searchField")).setText("POST");
            activate(next);
            invoke("onActiveSessionChanged", old);
        });
        await(() -> panel().isVisible() && panel().getItemCount() == 1);
        fx(() -> assertTrue(old.getCanvasLogViewer().hasSearchHighlight()));
    }

    @Test void localSearchFindsLongLinesAndKeepsFullDetail() throws Exception {
        Path fixtureDirectory = Path.of("build/test-data/search-results");
        Files.createDirectories(fixtureDirectory);
        Path file = Files.createTempFile(fixtureDirectory, "synthetic", ".log");
        String longLine = "POST /example " + "payload-".repeat(100) + "END_MARKER";
        Files.writeString(file, "GET /health\n" + longLine + "\npost /lower\n");
        var index = new com.seeloggyplus.shared.util.LineOffsetIndex();
        try (var input = new java.io.RandomAccessFile(file.toFile(), "r")) { index.buildIndex(input, null); }
        var reader = new com.seeloggyplus.shared.util.MappedFileReader(file.toFile());
        var session = new LogSession("local-synthetic.log", LogSession.SessionType.LOCAL);
        session.setLocalFile(file.toFile()); session.setReader(reader); session.setIndex(index);
        try {
            fx(() -> { activate(session); session.getCanvasLogViewer().loadFile(reader,index); search("POST"); });
            await(() -> panel().getItemCount() == 2);
            fx(() -> {
                assertEquals(3, session.getCanvasLogViewer().getItemCount());
                assertTrue(session.getCanvasLogViewer().hasSearchHighlight());
                panel().selectAndTriggerLine(0);
            });
            await(() -> ((com.seeloggyplus.features.detail.presentation.LogDetailPaneController) field(controller,"detailPaneController"))
                    .getCodeArea().getText().equals(longLine));
            fx(() -> ((ToggleButton) root.lookup("#caseSensitiveCheckBox")).setSelected(true));
            await(() -> panel().getItemCount() == 1);
            fx(() -> { ((ToggleButton) root.lookup("#regexCheckBox")).setSelected(true); search("^POST"); });
            await(() -> panel().getItemCount() == 1 && "^POST".equals(field(controller,"lastAppliedSearchQuery")));
        } finally {
            fx(() -> invoke("clearSearch"));
            reader.close();
        }
    }

    @Test void tailSearchRebasesResultsAfterRowsAreTrimmedDuringTheScan() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean();
        var session = new LogSession("trimmed-search.log", LogSession.SessionType.REMOTE);
        session.setTailModeEnabled(true);
        session.getLiveTailList().add(new LogEntry(0, "GET old") {
            @Override public String getRawLog() {
                if (!javafx.application.Platform.isFxApplicationThread() && once.compareAndSet(false, true)) {
                    entered.countDown();
                    try {release.await(10, TimeUnit.SECONDS);} catch (InterruptedException error) {Thread.currentThread().interrupt();}
                }
                return super.getRawLog();
            }
        });
        session.getLiveTailList().add(new LogEntry(1, "POST target-one"));
        session.getLiveTailList().add(new LogEntry(2, "POST target-two"));
        try {
            fx(() -> {activate(session); search("POST");});
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            fx(() -> {session.getLiveTailList().subList(0, 1).clear(); session.getCanvasLogViewer().refreshTail();});
            release.countDown();
            await(() -> panel().getItemCount() == 2);
            fx(() -> panel().selectAndTriggerLine(0));
            await(() -> ((com.seeloggyplus.features.detail.presentation.LogDetailPaneController) field(controller, "detailPaneController"))
                    .getCodeArea().getText().equals("POST target-one"));
        } finally {release.countDown(); fx(session::close);}
    }

    @Test void tailIncrementalSearchDoesNotReadColdDiskRowsOnTheFxThread() throws Exception {
        var history = new com.seeloggyplus.features.logs.infrastructure.io.SpoolingTailHistory(
                spoolRoot, 1024);
        history.add(new LogEntry(0, "GET " + "x".repeat(2000)));
        var guarded = (com.seeloggyplus.shared.tail.TailHistory) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.seeloggyplus.shared.tail.TailHistory.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("get") && javafx.application.Platform.isFxApplicationThread())
                        throw new AssertionError("cold history read on FX thread");
                    try {return method.invoke(history, arguments);}
                    catch (java.lang.reflect.InvocationTargetException error) {throw error.getCause();}
                });
        var session = new LogSession("cold-search.log", LogSession.SessionType.REMOTE);
        var sourceField = LogSession.class.getDeclaredField("liveTailList"); sourceField.setAccessible(true);
        ((com.seeloggyplus.shared.tail.TailHistory) sourceField.get(session)).close(); sourceField.set(session, guarded);
        session.setTailModeEnabled(true);
        try {
            fx(() -> {activate(session); search("POST");});
            await(() -> "POST".equals(field(controller, "lastAppliedSearchQuery")));
            invoke("handleTailLineBackground", session, "POST " + "y".repeat(2000));
            await(() -> panel().getItemCount() == 1);
        } finally {fx(session::close);}
    }

    @Test void tailProducerWaitsForCapacityWithoutDroppingEarlierLines() throws Exception {
        var session = new LogSession("pressure.log", LogSession.SessionType.REMOTE);
        session.setTailModeEnabled(true);
        fx(() -> activate(session));
        CountDownLatch blockedFx = new CountDownLatch(1);
        CountDownLatch resumeFx = new CountDownLatch(1);
        javafx.application.Platform.runLater(() -> {
            blockedFx.countDown();
            try {resumeFx.await(10, TimeUnit.SECONDS);} catch (InterruptedException error) {Thread.currentThread().interrupt();}
        });
        assertTrue(blockedFx.await(5, TimeUnit.SECONDS));
        var producer = Executors.newSingleThreadExecutor();
        try {
            Future<?> submitted = producer.submit(() -> {
                for (int i = 0; i < 5100; i++) invoke("handleTailLineBackground", session, "row-" + i);
            });
            Thread.sleep(500);
            assertFalse(submitted.isDone(), "producer must wait instead of discarding old rows");
            resumeFx.countDown();
            submitted.get(15, TimeUnit.SECONDS);
            await(() -> session.getLiveTailList().size() == 5100);
            assertEquals("row-0", session.getLiveTailList().getFirst().getRawLog());
            assertEquals("row-5099", session.getLiveTailList().getLast().getRawLog());
        } finally {
            resumeFx.countDown();
            producer.shutdownNow();
            fx(session::close);
        }
    }

    private static double luminance(javafx.scene.paint.Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }

    private static double linear(double value) {
        return value <= .04045 ? value / 12.92 : Math.pow((value+.055)/1.055,2.4);
    }

    private void activate(LogSession session) {
        var tab = (Tab) invoke("createTabForSession", session);
        var tabs = (TabPane) root.lookup("#logTabPane");
        ((com.seeloggyplus.shared.session.TabSessionManager) field(controller, "tabSessionManager")).register(tab, session);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
        invoke("onActiveSessionChanged", session);
    }

    private void search(String query) {
        ((TextField) root.lookup("#searchField")).setText(query);
        ((Button) root.lookup("#searchButton")).fire();
        ((javafx.animation.PauseTransition) field(controller, "searchDebounce")).stop();
    }

    private SearchResultPanel panel() { return (SearchResultPanel) field(controller, "searchResultPanel"); }

    private Object invoke(String name, Object... args) {
        try {
            var method = java.util.Arrays.stream(MainController.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name) && m.getParameterCount() == args.length).findFirst().orElseThrow();
            method.setAccessible(true);
            return method.invoke(controller, args);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private static Object field(Object target, String name) {
        try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception error) { throw new AssertionError(error); }
    }

    private void await(Callable<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
            AtomicBoolean ready = new AtomicBoolean();
            fx(() -> { try { ready.set(condition.call()); } catch (Exception error) { throw new AssertionError(error); } });
            return ready.get();
        });
    }

    private static void assertInside(Node parent, Node child) {
        var bounds = parent.sceneToLocal(child.localToScene(child.getBoundsInLocal()));
        assertTrue(bounds.getMinX() >= -.5 && bounds.getMaxX() <= parent.getLayoutBounds().getWidth() + .5, child + " horizontal overflow " + bounds);
        assertTrue(bounds.getMinY() >= -.5 && bounds.getMaxY() <= parent.getLayoutBounds().getHeight() + .5, child + " vertical overflow " + bounds);
    }

    private void snapshot(String name) {
        try {
            root.applyCss(); root.layout();
            String phase = System.getenv().getOrDefault("SEELOGGY_SEARCH_REPORT_PHASE", "after");
            Path directory = System.getenv().containsKey("SEELOGGY_SEARCH_REPORT_PHASE")
                    ? Path.of(".local-docs/reports/assets/search-results-fix") : Path.of("build/search-results-review");
            Path path = directory.resolve(phase + "-" + name + ".png");
            Files.createDirectories(path.getParent());
            var image = root.snapshot(null, null);
            int w = (int) image.getWidth(), h = (int) image.getHeight();
            int[] pixels = new int[w*h];
            image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
            var png = new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0,0,w,h,pixels,0,w);
            javax.imageio.ImageIO.write(png,"png",path.toFile());
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private static void fx(Runnable action) throws Exception {
        WaitForAsyncUtils.asyncFx(action).get(15, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
