package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import javafx.css.PseudoClass;
import javafx.scene.control.CheckBox;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
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
class CheckboxThemeTest {
    private Stage stage;
    private VBox root;
    private CheckBox check;

    @Start void start(Stage stage) {
        this.stage=stage; stage.setX(40); stage.setY(40);
        check=new CheckBox("Regex");
        root=new VBox(8,check,new CheckBox("Case sensitive"));
        root.getStyleClass().add("remote-log-search-dialog");
        root.setFocusTraversable(true);
        stage.setScene(AppTheme.scene(root)); stage.setWidth(500); stage.setHeight(200); stage.show(); root.requestFocus();
    }
    @AfterEach void reset() throws Exception { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }

    @Test void checkboxesUseFlatBoxesAndDistinctStatesAcrossThemes() throws Exception {
        for(var theme:AppTheme.Theme.values()) fx(() -> {
            AppTheme.setTheme(theme); apply();
            var box=(Region)check.lookup(".box"); var mark=(Region)box.lookup(".mark");
            assertEquals(16,box.getWidth(),.1); assertEquals(16,box.getHeight(),.1);
            assertEquals(28,check.getHeight(),.1);
            assertTrue(box.getBackground().getFills().getFirst().getFill() instanceof Color,"no Modena gradient");
            assertTrue(box.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius()>=3);
            assertTrue(contrast((Color)box.getBorder().getStrokes().getFirst().getTopStroke(),RenderedColors.background(box))>=3);
            assertTrue(contrast((Color)check.getTextFill(),RenderedColors.background(check))>=4.5);
            Color idle=RenderedColors.background(box);
            check.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),true); apply();
            Color hover=RenderedColors.background(box); assertNotEquals(idle,hover);
            check.arm(); apply(); assertNotEquals(hover,RenderedColors.background(box)); check.disarm();
            check.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),false);
            check.setSelected(true); apply();
            Color selected=RenderedColors.background(box);
            assertTrue(contrast((Color)mark.getBackground().getFills().getFirst().getFill(),selected)>=3);
            check.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),true); apply();
            Color selectedHover=RenderedColors.background(box); assertNotEquals(selected,selectedHover);
            check.arm(); apply(); assertNotEquals(selectedHover,RenderedColors.background(box)); check.disarm();
            check.setDisable(true); apply();
            Color disabled=RenderedColors.background(box); assertEquals(.45,check.getOpacity(),.001);
            check.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),false); check.arm(); apply();
            assertEquals(disabled,RenderedColors.background(box)); check.disarm(); check.setDisable(false); check.setSelected(false);
            check.setIndeterminate(true); apply();
            assertTrue(contrast((Color)mark.getBackground().getFills().getFirst().getFill(),RenderedColors.background(box))>=3);
            assertEquals(2,mark.getHeight(),.1); check.setIndeterminate(false);
        });
    }

    @Test void keyboardFocusDoesNotResizeAndSpaceTogglesTheCheckbox(FxRobot robot) throws Exception {
        for(var theme:AppTheme.Theme.values()) {
            fx(() -> { AppTheme.setTheme(theme); root.requestFocus(); apply(); });
            double width=WaitForAsyncUtils.asyncFx(check::getWidth).get();
            robot.press(KeyCode.TAB).release(KeyCode.TAB);
            fx(() -> {
                apply(); assertTrue(check.isFocused());
                var box=(Region)check.lookup(".box");
                assertTrue(check.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus")));
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-focus-ring",theme),box.getBorder().getStrokes().getFirst().getTopStroke());
                assertEquals(width,check.getWidth(),.1);
            });
            robot.press(KeyCode.SPACE).release(KeyCode.SPACE);
            fx(() -> assertTrue(check.isSelected()));
            robot.clickOn(check);
            fx(() -> { assertFalse(check.isSelected()); assertFalse(check.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus"))); });
        }
    }

    @Test void checkboxStatesRenderConsistently() throws Exception {
        for(var theme:AppTheme.Theme.values()) fx(() -> {
            var row=new javafx.scene.layout.HBox(14);
            for(String state:new String[]{"Normal","Hover","Pressed","Selected","Focus","Mixed","Disabled"}) {
                var item=new CheckBox(state);
                if(state.equals("Selected") || state.equals("Focus") || state.equals("Disabled")) item.setSelected(true);
                if(state.equals("Mixed")) item.setIndeterminate(true);
                if(state.equals("Hover")) item.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),true);
                if(state.equals("Pressed")) item.arm();
                if(state.equals("Focus")) {
                    item.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"),true);
                    item.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-focus"),true);
                }
                item.setDisable(state.equals("Disabled")); row.getChildren().add(item);
            }
            root.getChildren().setAll(row); AppTheme.setTheme(theme); stage.setWidth(760); stage.setHeight(90); apply();
            try {
                var rendered=root.snapshot(null,null); int width=(int)rendered.getWidth(),height=(int)rendered.getHeight();
                assertTrue(width > 0 && height > 0);
                int[] pixels=new int[width*height]; rendered.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
                var png=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB); png.setRGB(0,0,width,height,pixels,0,width);
                var path=java.nio.file.Path.of("build","checkbox-review","checkboxes-"+theme.name().toLowerCase()+".png");
                java.nio.file.Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(png,"png",path.toFile());
            } catch(Exception error) { throw new AssertionError(error); }
        });
    }

    private void apply() { root.applyCss(); root.layout(); }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
    private double contrast(Color a,Color b) { return (Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05); }
    private double luminance(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private double linear(double v) { return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4); }
}
