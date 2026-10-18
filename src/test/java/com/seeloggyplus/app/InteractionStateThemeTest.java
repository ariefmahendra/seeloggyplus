package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class InteractionStateThemeTest {
    private Stage stage;
    private VBox root;
    private Button button;
    private ToggleButton toggle;

    @Start void start(Stage stage) {
        this.stage = stage;
        button = new Button("Open"); toggle = new ToggleButton("Tail");
        var toolbar = new HBox(6, button, toggle);
        toolbar.getStyleClass().add("app-toolbar");
        root = new VBox(toolbar, new Button("Next"));
        root.getStyleClass().add("main-view");
        stage.setScene(AppTheme.scene(root)); stage.setWidth(500); stage.setHeight(250); stage.show();
        root.setFocusTraversable(true); root.requestFocus();
    }

    @AfterEach void reset() throws Exception { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }

    @Test void statePriorityIsConsistentAcrossMainViewAndDialogs() throws Exception {
        for (var scope : new String[]{"main-view", "preferences-dialog", "unified-file-manager-dialog", "server-management-dialog", "server-edit-dialog"}) {
            for (var theme : AppTheme.Theme.values()) {
                fx(() -> {
                    root.getStyleClass().removeAll("main-view", "preferences-dialog", "unified-file-manager-dialog", "server-management-dialog", "server-edit-dialog");
                    root.getStyleClass().add(scope); AppTheme.setTheme(theme);
                    button.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true); apply();
                    Color hover = RenderedColors.background(button);
                    button.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                    button.arm(); apply();
                    Color pressed = RenderedColors.background(button);
                    assertNotEquals(hover, pressed, scope + " " + theme + " pressed must differ from hover even with focus");
                    readable(button.getTextFill(), pressed);
                    button.disarm(); button.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), false);
                    button.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), false);
                    toggle.setSelected(true); apply();
                    assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-toggle-selected-bg", theme), toggle.getBackground().getFills().getFirst().getFill());
                    Color selected = RenderedColors.background(toggle);
                    toggle.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true); apply();
                    Color selectedHover = RenderedColors.background(toggle);
                    assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-toggle-selected-hover-bg", theme), toggle.getBackground().getFills().getFirst().getFill());
                    assertNotEquals(selected, selectedHover, scope + " " + theme + " selected hover");
                    toggle.arm(); apply();
                    assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-toggle-selected-pressed-bg", theme), toggle.getBackground().getFills().getFirst().getFill());
                    assertNotEquals(selectedHover, RenderedColors.background(toggle), scope + " " + theme + " selected pressed");
                    readable(toggle.getTextFill(), RenderedColors.background(toggle));
                    toggle.disarm(); toggle.setDisable(true); apply();
                    Color disabled = RenderedColors.background(toggle);
                    toggle.arm(); apply();
                    assertEquals(disabled, RenderedColors.background(toggle), "disabled ignores armed");
                    assertEquals(.45, toggle.getOpacity(), .001);
                    toggle.disarm(); toggle.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), false); apply();
                    assertEquals(disabled, RenderedColors.background(toggle), "disabled ignores hover");
                    toggle.setDisable(false); toggle.setSelected(false);
                });
            }
        }
    }

    @Test void keyboardFocusMovesRingWithoutResizingAndMouseClearsIt(FxRobot robot) throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> { AppTheme.setTheme(theme); root.requestFocus(); apply(); });
            double width = WaitForAsyncUtils.asyncFx(button::getWidth).get();
            double height = WaitForAsyncUtils.asyncFx(button::getHeight).get();
            robot.press(KeyCode.TAB).release(KeyCode.TAB);
            fx(() -> {
                assertTrue(button.isFocused());
                assertTrue(button.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus")));
                apply();
                var ring = button.getBorder().getStrokes().getFirst();
                assertEquals(1, ring.getWidths().getTop());
                assertTrue(contrast((Color)ring.getTopStroke(), RenderedColors.background(button)) >= 3);
                assertEquals(width, button.getWidth(), .01); assertEquals(height, button.getHeight(), .01);
                AppTheme.install(stage.getScene()); AppTheme.install(stage.getScene());
            });
            robot.press(KeyCode.TAB).release(KeyCode.TAB);
            fx(() -> {
                assertTrue(toggle.isFocused());
                assertFalse(button.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus")));
                assertTrue(toggle.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus")));
            });
            robot.press(KeyCode.SHIFT).press(KeyCode.TAB).release(KeyCode.TAB).release(KeyCode.SHIFT);
            fx(() -> assertTrue(button.isFocused()));
            robot.clickOn(button, MouseButton.PRIMARY);
            fx(() -> {
                assertFalse(button.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus")));
                apply(); assertEquals(Color.TRANSPARENT, button.getBorder().getStrokes().getFirst().getTopStroke());
            });
        }
    }

    @Test void keyboardActivationStillFiresOnceAfterRepeatedInstall(FxRobot robot) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        fx(() -> { button.setOnAction(event -> count.incrementAndGet()); button.setDefaultButton(true); AppTheme.install(stage.getScene()); AppTheme.refresh(stage.getScene()); root.requestFocus(); });
        robot.press(KeyCode.TAB).release(KeyCode.TAB);
        robot.press(KeyCode.SPACE).release(KeyCode.SPACE);
        robot.press(KeyCode.ENTER).release(KeyCode.ENTER);
        fx(() -> assertEquals(2, count.get()));
    }

    @Test void themeStateBeforeSceneAttachmentInstallsKeyboardFocus(FxRobot robot) throws Exception {
        fx(() -> {
            root = new VBox(button = new Button("Dialog action"), toggle = new ToggleButton("Next"));
            root.getStylesheets().setAll(AppTheme.sceneStylesheets(AppTheme.Theme.LIGHT));
            AppTheme.applyThemeState(root, AppTheme.Theme.LIGHT);
            stage.setScene(new javafx.scene.Scene(root));
            root.setFocusTraversable(true); root.requestFocus();
        });
        robot.press(KeyCode.TAB).release(KeyCode.TAB);
        fx(() -> assertTrue(button.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus"))));
    }

    @Test void statePreviewsKeepTextAndIconsReadableAcrossThemes() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> {
                root = new VBox(12);
                var controls = new java.util.ArrayList<javafx.scene.control.ButtonBase>();
                for (var scope : new String[]{"main-view", "preferences-dialog", "unified-file-manager-dialog", "server-management-dialog", "server-edit-dialog"}) {
                    var toolbar = new HBox(6); toolbar.getStyleClass().add("app-toolbar");
                    var group = new VBox(4, new javafx.scene.control.Label(scope), toolbar); group.getStyleClass().add(scope);
                    root.getChildren().add(group);
                    for (var state : new String[]{"Normal", "Hover", "Pressed", "Focus", "Focus+press", "Selected", "Sel+hover", "Sel+press", "Disabled"}) {
                        javafx.scene.control.ButtonBase control;
                        if (state.startsWith("Sel") || state.equals("Disabled")) {
                            var selected = new ToggleButton(state); selected.setSelected(true); control = selected;
                        } else control = new Button(state);
                        var icon = new de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView(de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon.FILE_TEXT);
                        icon.setSize("12"); icon.getStyleClass().add("glyph-icon"); control.setGraphic(icon);
                        if (state.contains("hover") || state.equals("Hover")) control.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
                        if (state.contains("press") || state.equals("Pressed")) control.arm();
                        if (state.startsWith("Focus")) {
                            control.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                            control.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-focus"), true);
                        }
                        if (state.equals("Disabled")) control.setDisable(true);
                        toolbar.getChildren().add(control); controls.add(control);
                    }
                }
                stage.setScene(AppTheme.scene(root)); AppTheme.setTheme(theme);
                stage.setWidth(1100); stage.setHeight(500); root.setFocusTraversable(true); root.requestFocus(); apply();
                for (var control : controls) {
                    if (control.isDisabled()) continue;
                    readable(control.getTextFill(), RenderedColors.background(control));
                    var icon = (de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView)control.getGraphic();
                    assertTrue(contrast((Color)icon.getFill(), RenderedColors.background(control)) >= 3, theme + " " + control.getText());
                }
                snapshot(theme);
            });
        }
    }

    private void snapshot(AppTheme.Theme theme) {
        try {
            var rendered = root.snapshot(null, null);
            int width = (int)rendered.getWidth(), height = (int)rendered.getHeight();
            int[] pixels = new int[width*height];
            rendered.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
            var png = new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0,0,width,height,pixels,0,width);
            var path = java.nio.file.Path.of("build","interaction-review","states-"+theme.name().toLowerCase()+".png");
            java.nio.file.Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(png,"png",path.toFile());
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void apply() { root.applyCss(); root.layout(); }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
    private void readable(javafx.scene.paint.Paint text, Color bg) { assertTrue(contrast((Color)text, bg) >= 4.5, "text=" + text + " background=" + bg + " theme=" + AppTheme.getTheme()); }
    private double contrast(Color a, Color b) { return (Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05); }
    private double luminance(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private double linear(double v) { return v<=.04045 ? v/12.92 : Math.pow((v+.055)/1.055,2.4); }
}
