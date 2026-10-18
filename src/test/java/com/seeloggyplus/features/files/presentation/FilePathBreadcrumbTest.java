package com.seeloggyplus.features.files.presentation;

import com.seeloggyplus.shared.ui.AppTheme;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class FilePathBreadcrumbTest {
    private Stage stage;
    @Start void start(Stage stage) { this.stage = stage; }

    @Test void foldersNavigateAndArrowListsOnlyRequestedChildren() throws Exception {
        AtomicReference<String> destination = new AtomicReference<>();
        WaitForAsyncUtils.asyncFx(() -> {
            TextField field = new TextField("C:\\Users\\Alice");
            FilePathBreadcrumb bar = new FilePathBreadcrumb(field, destination::set,
                    path -> CompletableFuture.completedFuture(List.of(new FilePathBreadcrumb.Folder("Documents", path + "\\Documents"))));
            stage.setScene(AppTheme.scene(new VBox(bar)));
            stage.show();
            Button users = (Button) bar.lookupAll(".path-segment").stream()
                    .filter(node -> ((Button) node).getText().equals("Users")).findFirst().orElseThrow();
            users.fire();
            assertEquals("C:\\Users", destination.get());
            MenuButton arrow = (MenuButton) bar.lookupAll(".path-children").iterator().next();
            arrow.show();
            assertEquals(90, arrow.getGraphic().getRotate());
            return bar;
        }).get();
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.asyncFx(() -> {
            MenuButton arrow = (MenuButton) stage.getScene().lookup(".path-children");
            assertEquals("Documents", arrow.getItems().getFirst().getText());
            arrow.getItems().getFirst().fire();
            assertTrue(destination.get().endsWith("\\Documents"));
            arrow.hide();
            assertEquals(0, arrow.getGraphic().getRotate());
        }).get();
    }

    @Test void remotePathsAndThemesRemainReadableAndSized() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            TextField field = new TextField("/var/log");
            AtomicReference<String> destination = new AtomicReference<>();
            FilePathBreadcrumb bar = new FilePathBreadcrumb(field, destination::set,
                    path -> CompletableFuture.completedFuture(List.of()));
            VBox root = new VBox(bar);
            root.getStyleClass().add("app-toolbar");
            stage.setScene(AppTheme.scene(root));
            stage.show();
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                root.applyCss(); root.layout();
                for (var node : bar.lookupAll(".path-segment")) {
                    Button button = (Button) node;
                    assertTrue(button.getHeight() >= 28);
                    var fg = (javafx.scene.paint.Color) button.getTextFill();
                    var bg = com.seeloggyplus.shared.ui.RenderedColors.background(bar);
                    assertTrue(contrast(fg, bg) >= 4.5, theme.name());
                    assertNotNull(bar.getBorder());
                    var border = (javafx.scene.paint.Color) bar.getBorder().getStrokes().getFirst().getTopStroke();
                    border=com.seeloggyplus.shared.ui.RenderedColors.over(border,bg);
                    assertTrue(contrast(border, bg) < 2.0, theme.name() + ": border should blend with the container");
                    button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                    root.applyCss();
                    var hover = com.seeloggyplus.shared.ui.RenderedColors.background(button);
                    assertTrue(contrast((javafx.scene.paint.Color) button.getTextFill(), hover) >= 4.5, theme.name());
                    assertTrue(contrast(bg, hover) < 1.5, theme.name() + ": hover should be subtle");
                    button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
                    root.applyCss();
                    button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("pressed"), true);
                    root.applyCss();
                    var pressed = com.seeloggyplus.shared.ui.RenderedColors.background(button);
                    assertTrue(contrast((javafx.scene.paint.Color) button.getTextFill(), pressed) >= 4.5, theme.name());
                    button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("pressed"), false);
                    root.applyCss();
                    assertFalse(bar.getBorder().getStrokes().isEmpty());
                    assertTrue(bar.getBorder().getStrokes().getFirst().getWidths().getTop() >= 1);
                    if (button.getText().equals("var")) { button.fire(); assertEquals("/var", destination.get()); }
                }
            }
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
        }).get();
    }
    @Test void manualEditorCanBeOpenedAndDismissed() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            TextField field = new TextField("/var/log");
            FilePathBreadcrumb bar = new FilePathBreadcrumb(field, path -> {},
                    path -> CompletableFuture.completedFuture(List.of()));
            stage.setScene(AppTheme.scene(new VBox(bar)));
            stage.show();
            assertFalse(field.isVisible());
            bar.edit();
            assertTrue(field.isVisible());
            assertEquals("/var/log", field.getSelectedText());
            bar.finishEditing();
            assertFalse(field.isVisible());
        }).get();
    }

    @Test void arrowOpeningDoesNotMoveOrResizeBreadcrumbs() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            FilePathBreadcrumb bar = new FilePathBreadcrumb(new TextField("/var/log"), path -> {},
                    path -> CompletableFuture.completedFuture(List.of()));
            VBox root = new VBox(bar);
            stage.setScene(AppTheme.scene(root)); stage.show(); root.applyCss(); root.layout();
            MenuButton arrow = (MenuButton) bar.lookup(".path-children");
            Button last = (Button) bar.lookupAll(".path-segment").stream()
                    .filter(node -> ((Button) node).getText().equals("log")).findFirst().orElseThrow();
            double width = arrow.getWidth(), height = arrow.getHeight(), x = last.localToScene(0, 0).getX();
            arrow.show(); root.applyCss(); root.layout();
            assertEquals(width, arrow.getWidth(), 0.01);
            assertEquals(height, arrow.getHeight(), 0.01);
            assertEquals(x, last.localToScene(0, 0).getX(), 0.01);
            arrow.hide();
        }).get();
    }

    @Test void overflowMenuNavigatesHiddenAncestorsAndRestoresOnResize() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            AtomicReference<String> destination = new AtomicReference<>();
            FilePathBreadcrumb bar = new FilePathBreadcrumb(new TextField("/workspace/projects/product/screenshots"), destination::set,
                    path -> CompletableFuture.completedFuture(List.of()));
            bar.setMaxWidth(220);
            VBox root = new VBox(bar);
            stage.setScene(AppTheme.scene(root)); stage.show(); root.applyCss(); root.layout();
            MenuButton overflow = (MenuButton) bar.lookup(".path-overflow");
            assertNotNull(overflow);
            assertTrue(overflow.isVisible());
            overflow.show();
            MenuItem workspace = overflow.getItems().stream().filter(item -> item.getText().equals("workspace")).findFirst().orElseThrow();
            workspace.fire();
            assertEquals("/workspace", destination.get());
            overflow.hide();
            Button last = (Button) bar.lookupAll(".path-segment").stream()
                    .filter(node -> ((Button) node).getText().equals("screenshots")).findFirst().orElseThrow();
            assertTrue(last.isVisible());
            assertTrue(last.localToScene(last.getBoundsInLocal()).getMaxX()
                    <= bar.localToScene(bar.getBoundsInLocal()).getMaxX());
            bar.setMaxWidth(1200); root.resize(1200, root.getHeight()); root.requestLayout(); root.layout();
            assertFalse(overflow.isVisible());
            assertTrue(bar.lookupAll(".path-segment").stream().allMatch(javafx.scene.Node::isVisible));
        }).get();
    }

    @Test void arrowsRenderVisiblePixelsInBothDirectionsAcrossThemes() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            FilePathBreadcrumb bar = new FilePathBreadcrumb(new TextField("/var/log"), path -> {},
                    path -> CompletableFuture.completedFuture(List.of()));
            VBox root = new VBox(bar);
            stage.setScene(AppTheme.scene(root)); stage.show();
            try {
                for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                    AppTheme.setTheme(theme);
                    root.applyCss(); root.layout();
                    MenuButton arrow = (MenuButton) bar.lookup(".path-children");
                    for (boolean open : new boolean[] { false, true }) {
                        if (open) arrow.show();
                        root.applyCss(); root.layout();
                        var image = arrow.snapshot(null, null);
                        var background = com.seeloggyplus.shared.ui.RenderedColors.background(arrow);
                        int visiblePixels = 0;
                        for (int y = 5; y < image.getHeight() - 5; y++) {
                            for (int x = 5; x < image.getWidth() - 5; x++) {
                                if (contrast(image.getPixelReader().getColor(x, y), background) >= 3.0) visiblePixels++;
                            }
                        }
                        assertTrue(visiblePixels >= 10, theme + " open=" + open + " visible pixels=" + visiblePixels);
                        arrow.hide();
                    }
                }
            } finally { AppTheme.setTheme(AppTheme.Theme.LIGHT); }
        }).get();
    }

    @Test void overflowIconRendersVisibleDotsAcrossThemes() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            FilePathBreadcrumb bar = new FilePathBreadcrumb(new TextField("/workspace/projects/product/screenshots"), path -> {},
                    path -> CompletableFuture.completedFuture(List.of()));
            bar.setMaxWidth(220);
            VBox root = new VBox(bar);
            stage.setScene(AppTheme.scene(root)); stage.show();
            try {
                for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                    AppTheme.setTheme(theme); root.applyCss(); root.layout();
                    MenuButton overflow = (MenuButton) bar.lookup(".path-overflow");
                    assertTrue(overflow.isVisible());
                    var image = overflow.snapshot(null, null);
                    var background = (javafx.scene.paint.Color) overflow.getBackground().getFills().getFirst().getFill();
                    int visiblePixels = 0;
                    for (int y = 5; y < image.getHeight() - 5; y++) {
                        for (int x = 5; x < image.getWidth() - 5; x++) {
                            if (contrast(image.getPixelReader().getColor(x, y), background) >= 3.0) visiblePixels++;
                        }
                    }
                    assertTrue(visiblePixels >= 10, theme + " visible pixels=" + visiblePixels);
                    assertTrue(overflow.getWidth() >= 28);
                    overflow.show(); root.applyCss(); root.layout();
                    assertTrue(overflow.getItems().stream().anyMatch(item -> item.getText().equals("workspace")));
                    overflow.hide();
                }
            } finally { AppTheme.setTheme(AppTheme.Theme.LIGHT); }
        }).get();
    }

    @Test void focusAndPressKeepBreadcrumbBoundsAndBackgroundInsideContainer() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            TextField editor = new TextField("/workspace/projects/product/screenshots");
            FilePathBreadcrumb bar = new FilePathBreadcrumb(editor, path -> {},
                    path -> CompletableFuture.completedFuture(List.of()));
            bar.setMaxWidth(220);
            VBox root = new VBox(bar, new Button("Other"));
            stage.setScene(AppTheme.scene(root)); stage.show();
            try {
                for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                    AppTheme.setTheme(theme); root.applyCss(); root.layout();
                    double height = bar.getHeight(), width = bar.getWidth();
                    for (String selector : List.of(".path-segment", ".path-children", ".path-overflow")) {
                        Labeled control = (Labeled) bar.lookupAll(selector).stream().filter(javafx.scene.Node::isVisible).findFirst().orElseThrow();
                        double beforeWidth = control.getWidth(), beforeHeight = control.getHeight();
                        for (String state : List.of("focused", "armed", "showing")) {
                            control.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass(state), true);
                            root.applyCss(); root.layout();
                            assertEquals(beforeWidth, control.getWidth(), 0.01, selector + state);
                            assertEquals(beforeHeight, control.getHeight(), 0.01, selector + state);
                            for (var fill : control.getBackground().getFills()) {
                                assertTrue(fill.getInsets().getTop() >= 0, theme + selector + state + ": focus paint must stay inside");
                                assertTrue(fill.getInsets().getLeft() >= 0);
                            }
                            control.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass(state), false);
                        }
                    }
                    bar.edit(); root.applyCss(); root.layout();
                    assertEquals(height, bar.getHeight(), 0.01, theme + ": editing height");
                    assertEquals(width, bar.getWidth(), 0.01);
                    bar.finishEditing(); root.applyCss(); root.layout();
                }
            } finally { AppTheme.setTheme(AppTheme.Theme.LIGHT); }
        }).get();
    }

    @Test void folderPopupUsesThemeAndReadableCompactRows() throws Exception {
        try {
            for (var theme : AppTheme.Theme.values()) {
                WaitForAsyncUtils.asyncFx(() -> {
                    AppTheme.setTheme(theme);
                    var folders = java.util.stream.IntStream.range(0, 80)
                            .mapToObj(i -> new FilePathBreadcrumb.Folder("Folder " + i, "/folder" + i)).toList();
                    var bar = new FilePathBreadcrumb(new TextField("/var/log"), path -> {},
                            path -> CompletableFuture.completedFuture(folders));
                    stage.setScene(AppTheme.scene(new VBox(bar))); stage.show();
                    stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                    ((MenuButton) bar.lookup(".path-children")).show();
                }).get();
                WaitForAsyncUtils.waitForFxEvents();
                WaitForAsyncUtils.asyncFx(() -> {
                    var button = (MenuButton) stage.getScene().lookup(".path-children");
                    var popup = button.getItems().getFirst().getParentPopup();
                    var root = popup.getScene().getRoot(); root.applyCss(); root.layout();
                    var menu = (javafx.scene.layout.Region) popup.getSkin().getNode();
                    var background = com.seeloggyplus.shared.ui.RenderedColors.background(menu);
                    assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-popup-bg", theme), background);
                    assertTrue(menu.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius() >= 6);
                    for (var node : root.lookupAll(".menu-item")) {
                        var row = (javafx.scene.layout.Region) node;
                        var label = (Label) row.lookup(".label");
                        assertTrue(row.getHeight() >= 28);
                        assertTrue(contrast((javafx.scene.paint.Color) label.getTextFill(), background) >= 4.5);
                        row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
                        root.applyCss();
                        var selected = com.seeloggyplus.shared.ui.RenderedColors.background(row);
                        assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-selection-bg", theme), selected);
                        assertTrue(contrast((javafx.scene.paint.Color) label.getTextFill(), selected) >= 4.5);
                        row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), false);
                    }
                    button.hide();
                }).get();
            }
        } finally { WaitForAsyncUtils.asyncFx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)).get(); }
    }

    private static double contrast(javafx.scene.paint.Color a, javafx.scene.paint.Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);
    }
    private static double luminance(javafx.scene.paint.Color c) {
        return 0.2126*linear(c.getRed())+0.7152*linear(c.getGreen())+0.0722*linear(c.getBlue());
    }
    private static double linear(double v) { return v <= 0.04045 ? v/12.92 : Math.pow((v+0.055)/1.055,2.4); }
}
