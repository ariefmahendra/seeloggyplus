package com.seeloggyplus.shared.ui;

import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class IslandsSelectionTest {
    private Stage stage;
    private VBox root;
    private TreeView<String> tree;
    private TextField other;

    @Start void start(Stage stage) {
        this.stage = stage;
        tree = new TreeView<>(new TreeItem<>("Selected file"));
        other = new TextField();
        root = new VBox(tree, other);
        stage.setScene(AppTheme.scene(root));
        stage.show();
        tree.getSelectionModel().select(0);
    }

    @AfterEach void reset() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)).get();
    }

    @Test void selectionChangesWithFocusAndTheme() throws Exception {
        for (var theme : new AppTheme.Theme[] {AppTheme.Theme.LIGHT, AppTheme.Theme.DARK}) {
            for (boolean active : new boolean[] {true, false}) {
                WaitForAsyncUtils.asyncFx(() -> {
                    AppTheme.setTheme(theme);
                    if (active) tree.requestFocus(); else other.requestFocus();
                }).get();
                WaitForAsyncUtils.waitForFxEvents();
                WaitForAsyncUtils.asyncFx(() -> {
                    root.applyCss(); root.layout();
                    var cell = root.lookup(".tree-cell:selected");
                    assertNotNull(cell);
                    Color expected = Color.web(theme == AppTheme.Theme.LIGHT
                            ? active ? "#d0dffe" : "#eef0f3"
                            : active ? "#2a4371" : "#33353b");
                    assertEquals(expected, RenderedColors.background(cell));
                    assertEquals(expected, SelectionColors.background(theme, active));
                    assertEquals(active, SelectionColors.active(tree));
                }).get();
            }
        }
    }
}
