package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.control.skin.ComboBoxListViewSkin;
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
class InputControlThemeTest {
    private VBox root;
    private ComboBox<String> combo;
    private Spinner<Integer> spinner;

    @Start void start(Stage stage) {
        combo=new ComboBox<>(); combo.getItems().addAll("Open/Download", "Tail", "A very long option that must remain inside the popup"); combo.getSelectionModel().selectFirst();
        combo.setPrefWidth(180);
        spinner=new Spinner<>(1,100,12); spinner.setEditable(true); spinner.setPrefWidth(104);
        root=new VBox(12,combo,spinner); root.getStyleClass().add("preferences-dialog"); root.setFocusTraversable(true);
        stage.setScene(AppTheme.scene(root)); stage.setX(40); stage.setY(40); stage.setWidth(500); stage.setHeight(260); stage.show(); root.requestFocus();
    }
    @AfterEach void reset() throws Exception { fx(() -> { combo.hide(); AppTheme.setTheme(AppTheme.Theme.LIGHT); }); }

    @Test void fieldsAreCompactAndFeedbackKeepsTextAndArrowsReadable() throws Exception {
        for(var theme:AppTheme.Theme.values()) fx(() -> {
            AppTheme.setTheme(theme); apply();
            for(Region field:new Region[]{combo,spinner}) {
                assertEquals(28,field.getHeight(),.1);
                assertTrue(field.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius()>=5);
            }
            var value=(ListCell<?>)combo.lookup(".list-cell");
            readable((Color)value.getTextFill(),RenderedColors.background(combo),4.5);
            readable((Color)((javafx.scene.text.Text)spinner.getEditor().lookup(".text")).getFill(),RenderedColors.background(spinner.getEditor()),4.5);
            for(String selector:new String[]{".increment-arrow", ".decrement-arrow"}) {
                var arrow=(Region)spinner.lookup(selector); readable(RenderedColors.background(arrow),RenderedColors.background(arrow.getParent()),3);
            }
            Color idle=RenderedColors.background(combo); state(combo,"hover",true); apply();
            Color hover=RenderedColors.background(combo); assertNotEquals(idle,hover);
            readable((Color)value.getTextFill(),hover,4.5);
            combo.arm(); apply(); assertNotEquals(hover,RenderedColors.background(combo));
            readable((Color)value.getTextFill(),RenderedColors.background(combo),4.5); combo.disarm();
            var comboArrow=(Region)combo.lookup(".arrow"); readable(RenderedColors.background(comboArrow),RenderedColors.background(combo),3);
            combo.setDisable(true); apply(); assertEquals(.45,combo.getOpacity(),.001);
            assertEquals(idle,RenderedColors.background(combo)); combo.setDisable(false); state(combo,"hover",false);
            var arrowButton=(Region)spinner.lookup(".increment-arrow-button");
            Color normal=RenderedColors.background(arrowButton); state(arrowButton,"hover",true); apply();
            Color arrowHover=RenderedColors.background(arrowButton); assertNotEquals(normal,arrowHover);
            state(arrowButton,"pressed",true); apply(); assertNotEquals(arrowHover,RenderedColors.background(arrowButton));
            state(arrowButton,"pressed",false); state(arrowButton,"hover",false);
            spinner.setDisable(true); state(arrowButton,"hover",true); state(arrowButton,"pressed",true); apply();
            assertEquals(.45,spinner.getOpacity(),.001); assertEquals(normal,RenderedColors.background(arrowButton));
            spinner.setDisable(false); state(arrowButton,"hover",false); state(arrowButton,"pressed",false);
        });
    }

    @Test void actualDropdownHasRoundedRowsAndDistinctSelection() throws Exception {
        for(var theme:AppTheme.Theme.values()) {
            fx(() -> { AppTheme.setTheme(theme); combo.show(); apply(); });
            fx(() -> {
                var list=(ListView<?>)((ComboBoxListViewSkin<?>)combo.getSkin()).getPopupContent(); list.applyCss(); list.layout(); list.applyCss();
                assertTrue(list.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius()>=10);
                assertTrue(list.getPadding().getTop()>=6);
                var parameters=new javafx.scene.SnapshotParameters(); parameters.setFill(Color.TRANSPARENT);
                var pixels=list.snapshot(parameters,null).getPixelReader();
                assertTrue(pixels.getColor(0,0).getOpacity()<.1,"popup corner must expose the background");
                for(var node:list.lookupAll(".list-cell")) if(node instanceof ListCell<?> cell && !cell.isEmpty()) {
                    assertTrue(cell.getHeight()>=28); assertTrue(cell.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius()>=6);
                    readable((Color)cell.getTextFill(),RenderedColors.background(cell),4.5);
                    if(!cell.isSelected()) { state(cell,"hover",true); list.applyCss(); assertNotEquals(RenderedColors.background(cell),RenderedColors.background(list.lookup(".list-cell:selected"))); state(cell,"hover",false); }
                }
                snapshot(list,"selection-"+theme.name().toLowerCase());
                combo.hide(); combo.getItems().clear(); combo.show(); assertTrue(combo.isShowing()); combo.hide();
                combo.getItems().addAll("Open/Download", "Tail"); combo.getSelectionModel().selectFirst();
            });
        }
    }

    @Test void keyboardFocusReachesSpinnerEditorWithoutResizing(FxRobot robot) throws Exception {
        for(var theme:AppTheme.Theme.values()) {
            fx(() -> { AppTheme.setTheme(theme); root.requestFocus(); apply(); });
            robot.press(KeyCode.TAB).release(KeyCode.TAB);
            fx(() -> {
                assertTrue(combo.isFocused()); apply();
                assertTrue(combo.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus")));
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-focus-ring",theme),combo.getBorder().getStrokes().getFirst().getTopStroke());
            });
            robot.press(KeyCode.TAB).release(KeyCode.TAB);
            fx(() -> {
                assertTrue(spinner.isFocused() || spinner.getEditor().isFocused()); apply();
                spinner.getEditor().requestFocus(); apply();
                assertTrue(spinner.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus")));
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-focus-ring",theme),spinner.getBorder().getStrokes().getFirst().getTopStroke());
                assertEquals(28,spinner.getHeight(),.1);
                spinner.getEditor().setText("24"); spinner.commitValue(); assertEquals(24,spinner.getValue());
            });
            robot.clickOn(spinner.getEditor());
            fx(() -> assertFalse(spinner.getPseudoClassStates().contains(PseudoClass.getPseudoClass("keyboard-focus"))));
        }
    }
    @Test void dropdownKeyboardSelectionAndScrollingKeepRoundedSurface(FxRobot robot) throws Exception {
        for(var theme:AppTheme.Theme.values()) {
            fx(() -> {
                AppTheme.setTheme(theme); combo.getItems().clear();
                for(int i=0;i<30;i++) combo.getItems().add("Option "+i);
                combo.getSelectionModel().selectFirst(); combo.requestFocus(); combo.show(); apply();
            });
            robot.press(KeyCode.DOWN).release(KeyCode.DOWN).press(KeyCode.ENTER).release(KeyCode.ENTER);
            fx(() -> { assertEquals("Option 1",combo.getValue()); assertFalse(combo.isShowing()); combo.show(); });
            fx(() -> {
                var list=(ListView<?>)((ComboBoxListViewSkin<?>)combo.getSkin()).getPopupContent();
                list.scrollTo(29); list.applyCss(); list.layout(); list.applyCss();
                assertTrue(list.lookupAll(".scroll-bar").stream().anyMatch(javafx.scene.Node::isVisible));
                snapshot(root,"fields-"+theme.name().toLowerCase());
                snapshot(list,"dropdown-"+theme.name().toLowerCase());
            });
            robot.press(KeyCode.ESCAPE).release(KeyCode.ESCAPE);
            fx(() -> assertFalse(combo.isShowing()));
        }
    }
    private void snapshot(Region region,String name) {
        try {
            var rendered=region.snapshot(null,null); int width=(int)rendered.getWidth(),height=(int)rendered.getHeight();
            int[] pixels=new int[width*height]; rendered.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
            var png=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB); png.setRGB(0,0,width,height,pixels,0,width);
            var path=java.nio.file.Path.of("build","input-control-review",name+".png"); java.nio.file.Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(png,"png",path.toFile());
        } catch(Exception error) { throw new AssertionError(error); }
    }
    private void apply() { root.applyCss(); root.layout(); }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
    private void state(javafx.scene.Node node,String name,boolean value) { node.pseudoClassStateChanged(PseudoClass.getPseudoClass(name),value); }
    private void readable(Color text,Color bg,double minimum) { assertTrue((Math.max(lum(text),lum(bg))+.05)/(Math.min(lum(text),lum(bg))+.05)>=minimum,text+" on "+bg); }
    private double lum(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private double linear(double v) { return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4); }
}
