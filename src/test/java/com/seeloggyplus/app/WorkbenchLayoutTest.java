package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.util.IntArrayList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class WorkbenchLayoutTest {
    private Parent root;
    private MainController controller;
    private Stage stage;

    @Start void start(Stage stage) throws Exception {
        this.stage = stage;
        var loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load(); controller = loader.getController();
        stage.setScene(AppTheme.scene(root)); stage.setWidth(1024); stage.setHeight(700); stage.show();
    }

    @AfterEach void cleanup() throws Exception {
        fx(() -> { invoke("resetWorkbenchLayout"); invoke("hideSearchResultPanel"); AppTheme.setTheme(AppTheme.Theme.LIGHT); });
    }

    @Test void dockingMovesExistingResultsAndKeepsSelectionAndQuery() throws Exception {
        fx(() -> {
            var matches = new IntArrayList(); matches.add(4); matches.add(8);
            invoke("showSearchResultPanel", matches, null);
            ((TextField) root.lookup("#searchField")).setText("ERROR");
            Object results = field("searchResultPanel");
            var panel=(com.seeloggyplus.features.search.presentation.SearchResultPanel) results;
            panel.selectAndTriggerLine(1);
            ((javafx.animation.PauseTransition) field("searchDebounce")).stop();
            invoke("setResultsPosition", "right");
            assertTrue(((SplitPane) field("searchSplitPane")).getItems().contains(results));
            invoke("setResultsPosition", "bottom");
            var dock = (TabPane) root.lookup("#workbenchDock");
            assertNotNull(dock);
            assertSame(results, dock.getTabs().getFirst().getContent());
            assertEquals(2, dock.getTabs().size());
            invoke("setResultsPosition", "right");
            assertSame(results, field("searchResultPanel"));
            try {
                var selection=results.getClass().getDeclaredField("selectedIndex"); selection.setAccessible(true);
                assertEquals(1,selection.get(results));
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            assertEquals("ERROR", ((TextField) root.lookup("#searchField")).getText());
        });
    }

    @Test void autoDockUsesAvailableWorkspaceAndDetailCanCloseIndependently() throws Exception {
        fx(() -> { invoke("setResultsPosition", "auto"); var matches = new IntArrayList(); matches.add(0); invoke("showSearchResultPanel", matches, null); });
        for (double[] size : new double[][]{{800,600},{1024,700},{1366,768},{1920,1080}}) {
            fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); });
            fx(() -> {
                root.applyCss(); root.layout();
                var right = ((SplitPane) field("searchSplitPane")).getItems().contains(field("searchResultPanel"));
                assertEquals(((SplitPane) root.lookup("#verticalSplitPane")).getWidth() >= 960, right);
                var log = root.lookup("#logContainer").localToScene(root.lookup("#logContainer").getBoundsInLocal());
                assertTrue(log.getWidth() >= 200); assertTrue(log.getHeight() >= 120);
            });
        }
        fx(() -> {
            invoke("setResultsPosition", "bottom");
            if (((ToggleButton) root.lookup("#toggleBottomPanelButton")).isSelected()) invoke("toggleBottomPanel");
            var dock = (TabPane) root.lookup("#workbenchDock");
            assertEquals(1, dock.getTabs().size());
            assertSame(field("searchResultPanel"), dock.getTabs().getFirst().getContent());
        });
    }

    @Test void positionPersistsAndResetRestoresAutoWithoutChangingTheme() throws Exception {
        fx(() -> {
            AppTheme.setTheme(AppTheme.Theme.DARK);
            invoke("setResultsPosition", "bottom");
            var preferences = (com.seeloggyplus.shared.settings.PreferenceService) field("preferenceService");
            assertEquals("bottom", preferences.getPreferencesByCode("workbench_results_position").orElseThrow());
            invoke("resetWorkbenchLayout");
            assertEquals("auto", preferences.getPreferencesByCode("workbench_results_position").orElseThrow());
            assertEquals(AppTheme.Theme.DARK, AppTheme.getTheme());
        });
    }

    @Test void chromeHasExplicitActionsAndReadableTargetsInBothThemes() throws Exception {
        for (var theme : AppTheme.Theme.values()) fx(() -> {
            AppTheme.setTheme(theme); root.applyCss(); root.layout();
            assertEquals("Open", ((Button) root.lookup("#openFileButton")).getText());
            assertEquals("Follow", ((ToggleButton) root.lookup("#followTailButton")).getText());
            assertNotNull(root.lookup("#tailStatusLabel"));
            assertNotNull(root.lookup("#resourceButton"));
            assertEquals(32, ((Button) root.lookup("#openFileButton")).getHeight(), 1);
            assertFalse(root.lookupAll(".status-bar .button").stream().anyMatch(n -> n instanceof Button b && b.getText().equals("GC")));
        });
    }

    @Test void reloadRestoresPositionAndInvalidDimensionsUseDefaults() throws Exception {
        fx(() -> {
            invoke("setResultsPosition","bottom");
            var preferences=(com.seeloggyplus.shared.settings.PreferenceService) field("preferenceService");
            preferences.saveOrUpdatePreferences(new com.seeloggyplus.shared.model.Preference("workbench_right_divider","NaN"));
            try {
                var loader=new FXMLLoader(getClass().getResource("/fxml/MainView.fxml")); loader.load();
                MainController restored=loader.getController();
                var dock=MainController.class.getDeclaredField("workbenchDock"); dock.setAccessible(true);
                assertEquals("bottom",((WorkbenchDock)dock.get(restored)).position());
                assertEquals(.66,((WorkbenchDock)dock.get(restored)).rightDivider(),.01);
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    @Test void tailStatusSeparatesConnectionFromFollow() throws Exception {
        fx(() -> {
            var sessions=(com.seeloggyplus.shared.session.TabSessionManager) field("tabSessionManager");
            var local=new com.seeloggyplus.shared.session.LogSession("local",com.seeloggyplus.shared.session.LogSession.SessionType.LOCAL);
            var remote=new com.seeloggyplus.shared.session.LogSession("remote",com.seeloggyplus.shared.session.LogSession.SessionType.REMOTE);
            try {
                local.setTailModeEnabled(true); sessions.setActive(local);
                invoke("updateWorkbenchTailStatus");
                assertEquals("Live",((Label)root.lookup("#tailStatusLabel")).getText());
                ((ToggleButton)field("followTailButton")).setSelected(false);
                assertEquals("Live",((Label)root.lookup("#tailStatusLabel")).getText());
                remote.setTailModeEnabled(true); sessions.setActive(remote); invoke("updateWorkbenchTailStatus");
                assertEquals("Disconnected",((Label)root.lookup("#tailStatusLabel")).getText());
                remote.setTailModeEnabled(false); invoke("updateWorkbenchTailStatus");
                assertEquals("Paused",((Label)root.lookup("#tailStatusLabel")).getText());
            } finally {sessions.setActive(null); local.close(); remote.close();}
        });
    }

    @Test void selectedDetailTabSurvivesMovingResultsToRightAndBack() throws Exception {
        fx(() -> {
            invoke("setResultsPosition","bottom");
            var matches=new IntArrayList(); matches.add(0); invoke("showSearchResultPanel",matches,null);
            invoke("revealWorkbenchDetail");
            var dock=(TabPane)root.lookup("#workbenchDock");
            assertEquals("Detail",dock.getSelectionModel().getSelectedItem().getText());
            invoke("toggleBottomPanel"); invoke("toggleBottomPanel");
            assertEquals("Detail",dock.getSelectionModel().getSelectedItem().getText());
            invoke("setResultsPosition","right"); invoke("setResultsPosition","bottom");
            assertEquals("Detail",dock.getSelectionModel().getSelectedItem().getText());
        });
    }

    private Object field(String name) {
        try { var f=MainController.class.getDeclaredField(name); f.setAccessible(true); return f.get(controller); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private Object invoke(String name, Object... args) {
        try { var method=java.util.Arrays.stream(MainController.class.getDeclaredMethods()).filter(m -> m.getName().equals(name) && m.getParameterCount()==args.length).findFirst().orElseThrow(); method.setAccessible(true); return method.invoke(controller,args); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(15, TimeUnit.SECONDS); WaitForAsyncUtils.waitForFxEvents(); }
}
