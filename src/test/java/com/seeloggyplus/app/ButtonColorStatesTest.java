package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
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
class ButtonColorStatesTest {
    private Stage stage;
    @Start void start(Stage stage) { this.stage = stage; }
    @AfterEach void reset() throws Exception { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }

    @Test void interactiveButtonsUseBlueIconsAndNeutralBackgroundsAcrossThemes() throws Exception {
        fx(() -> {
            var root = new VBox(4);
            stage.setScene(AppTheme.scene(root));
            stage.show();
            for (var theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                for (String context : new String[]{"", "app-toolbar", "unified-file-manager-dialog", "server-management-dialog", "server-edit-dialog", "status-bar"}) {
                    var primary=new Button("Save");primary.getStyleClass().add("btn-primary");primary.setDefaultButton(true);
                    var danger=new Button("Delete");danger.getStyleClass().add("btn-danger-outline");
                    for (ButtonBase button : new ButtonBase[]{new Button("Open"), primary, danger, new ToggleButton("Tail"), new MenuButton("Actions"), new SplitMenuButton()}) {
                        var icon = new FontAwesomeIconView(FontAwesomeIcon.SEARCH);
                        button.setGraphic(icon);
                        var container = new VBox(button);
                        if (!context.isEmpty()) container.getStyleClass().add(context);
                        root.getChildren().setAll(container);
                        root.requestFocus();
                        root.applyCss();root.layout();
                        double width=button.getWidth(), height=button.getHeight();
                        for (String state : new String[]{"hover", "pressed", "armed", "showing", "focused", "selected"}) {
                            if (state.equals("showing") && !(button instanceof MenuButton)) continue;
                            if (state.equals("selected") && !(button instanceof ToggleButton)) continue;
                            var pseudo=PseudoClass.getPseudoClass(state);
                            button.pseudoClassStateChanged(pseudo,true);
                            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-focus"),state.equals("focused"));
                            root.applyCss();root.layout();
                            String label=theme+" "+context+" "+button.getClass().getSimpleName()+" "+state;
                            assertBlue((Color)icon.getFill(),label);
                            var background=RenderedColors.background(button);
                            assertTrue(Math.max(background.getRed(),Math.max(background.getGreen(),background.getBlue()))-Math.min(background.getRed(),Math.min(background.getGreen(),background.getBlue()))<.06,label+" must use a gray background: "+background);
                            assertTrue(contrast((Color)icon.getFill(),background)>=3,label+" icon contrast");
                            assertTrue(contrast((Color)button.getTextFill(),background)>=4.5,label+" text contrast");
                            assertEquals(width,button.getWidth(),.01,label+" stable width");
                            assertEquals(height,button.getHeight(),.01,label+" stable height");
                            button.pseudoClassStateChanged(pseudo,false);
                            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-focus"),false);
                        }
                        button.setDisable(true);
                        button.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),true);
                        root.applyCss();
                        assertNotEquals(theme==AppTheme.Theme.LIGHT?Color.web("#2f5eb9"):Color.web("#71a1fe"),icon.getFill(),"Disabled icon must stay muted");
                    }
                }
            }
        });
    }

    @Test void tabCloseIconTurnsBlueDuringInteraction() throws Exception {
        fx(() -> {
            var tabs=new TabPane(new Tab("Log"));tabs.getStyleClass().add("log-tab-pane");
            var root=new VBox(tabs);root.getStyleClass().add("main-view");
            stage.setScene(AppTheme.scene(root));stage.show();root.applyCss();root.layout();
            var close=(javafx.scene.layout.Region)tabs.lookup(".tab-close-button");
            assertNotNull(close);
            for(var theme:AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                close.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),true);
                root.applyCss();
                assertBlue((Color)close.getBackground().getFills().getFirst().getFill(),theme+" tab close");
                close.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),false);
            }
        });
    }

    @Test void footerOutlineIconsFollowTheSameActivePalette() throws Exception {
        fx(() -> {
            var icon=new SVGPath();icon.setContent("M 2 2 H 16 V 14 H 2 Z M 2 10 H 16");icon.getStyleClass().add("panel-layout-icon");
            var button=new ToggleButton();button.setGraphic(icon);button.getStyleClass().add("panel-toggle");
            var bar=new VBox(button);bar.getStyleClass().add("status-bar");
            var root=new VBox(bar);root.getStyleClass().add("main-view");
            stage.setScene(AppTheme.scene(root));stage.show();
            for(var theme:AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                for(boolean selected:new boolean[]{false,true}) for(String state:new String[]{"hover","pressed","focused"}) {
                    button.setSelected(selected);
                    button.pseudoClassStateChanged(PseudoClass.getPseudoClass(state),true);
                    button.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-focus"),state.equals("focused"));
                    root.applyCss();root.layout();
                    assertBlue((Color)icon.getStroke(),theme+" footer "+state);
                    assertTrue(contrast((Color)icon.getStroke(),RenderedColors.background(button))>=3);
                    assertEquals(0,((Color)icon.getFill()).getOpacity());
                    button.pseudoClassStateChanged(PseudoClass.getPseudoClass(state),false);
                    button.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-focus"),false);
                }
            }
        });
    }
    private static void assertBlue(Color color,String label) { assertTrue(color.getBlue()>color.getRed()+.15 && color.getBlue()>color.getGreen()+.08,label+" icon must turn blue: "+color); }
    private static double contrast(Color a,Color b) { double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05); }
    private static double luminance(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private static double linear(double c) { return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4); }
    private static void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(45,TimeUnit.SECONDS); }
}
