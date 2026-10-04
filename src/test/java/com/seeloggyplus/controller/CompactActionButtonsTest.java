package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class CompactActionButtonsTest {

    private Parent main;
    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        main = FXMLLoader.load(getClass().getResource("/fxml/MainView.fxml"));
        stage.setScene(AppTheme.scene(main));
        stage.setWidth(1100);
        stage.setHeight(720);
        stage.show();
    }

    @AfterEach
    void resetTheme(FxRobot robot) {
        robot.interact(() -> AppTheme.setTheme(AppTheme.Theme.GRAPHITE));
    }

    @Test
    void detailActionsMatchToolbarDensity(FxRobot robot) {
        robot.interact(() -> {
            var panel = main.lookup("#bottomPanel");
            panel.setVisible(true);
            panel.setManaged(true);
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                main.applyCss();
                main.layout();
                ButtonBase reference = button(main, "refreshButton");
                for (String id : new String[]{"prettifyJsonButton", "prettifyXmlButton", "copyButton", "clearDetailButton", "pinBottomPanelButton"}) {
                    ButtonBase action = button(main, id);
                    assertEquals(reference.getPadding(), action.getPadding(), id + " should match toolbar padding");
                    assertTrue(action.getHeight() <= reference.getHeight() + 2, id + " should match toolbar height");
                    assertTrue(action.getWidth() <= reference.getWidth() + 6, id + " should remain an icon-sized control");
                }
            }
        });
    }

    @Test
    void fileManagerActionsMatchToolbarDensityAndRetainTheirText(FxRobot robot) {
        robot.interact(() -> {
            main.applyCss();
            main.layout();
            ButtonBase reference = button(main, "refreshButton");
            Insets padding = reference.getPadding();
            double height = reference.getHeight();
            try {
                Parent manager = FXMLLoader.load(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
                stage.setScene(AppTheme.scene(manager));
                for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                    AppTheme.setTheme(theme);
                    manager.applyCss();
                    manager.layout();
                    for (String id : new String[]{"newGroupButton", "favoriteCurrentButton", "manageServersButton", "cancelButton", "previewButton", "tailButton", "openButton"}) {
                        ButtonBase action = button(manager, id);
                        assertEquals(padding, action.getPadding(), id + " should match toolbar padding");
                        assertTrue(action.getHeight() <= height + 2, id + " should match toolbar height");
                        FontAwesomeIconView icon = assertInstanceOf(FontAwesomeIconView.class, action.getGraphic());
                        assertEquals(12, icon.getFont().getSize(), 0.01, id + " should use compact readable glyphs");
                    }
                    for (String id : new String[]{"cancelButton", "previewButton", "tailButton", "openButton"}) {
                        ButtonBase action = button(manager, id);
                        assertFalse(action.getText().isBlank(), "Footer actions must remain easy to identify");
                        assertTrue(action.getWidth() <= 110, "Short footer actions should not have oversized widths");
                    }
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void recentPanelActionsMatchToolbarDensity(FxRobot robot) {
        robot.interact(() -> {
            var panel = main.lookup("#leftPanel");
            var split = (javafx.scene.control.SplitPane) main.lookup("#horizontalSplitPane");
            if (!split.getItems().contains(panel)) {
                split.getItems().add(0, panel);
            }
            panel.setVisible(true);
            panel.setManaged(true);
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                main.applyCss();
                main.layout();
                ButtonBase reference = button(main, "refreshButton");
                for (String id : new String[]{"pinLeftPanelButton", "clearRecentButton"}) {
                    ButtonBase action = button(main, id);
                    assertEquals(reference.getPadding(), action.getPadding(), id + " should match toolbar padding");
                    assertTrue(action.getHeight() <= reference.getHeight() + 2, id + " should match toolbar height");
                }
                var tree = assertInstanceOf(javafx.scene.layout.Region.class, main.lookup("#recentFilesTreeView"));
                ButtonBase clear = button(main, "clearRecentButton");
                assertTrue(clear.getWidth() <= tree.getWidth() - 10,
                        "Clear Recent Files must hug its label instead of stretching across the panel (clear="
                                + clear.getWidth() + ", tree=" + tree.getWidth() + ")");
            }
        });
    }

    private static ButtonBase button(Parent root, String id) {
        return assertInstanceOf(ButtonBase.class, root.lookup("#" + id));
    }
}
