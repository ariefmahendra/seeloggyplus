package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import com.seeloggyplus.features.detail.presentation.LogDetailPaneController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class WorkbenchChromeRevisionTest {
    private Parent root;
    private Stage stage;
    @Start void start(Stage stage) throws Exception {
        this.stage=stage;
        root=FXMLLoader.load(getClass().getResource("/fxml/MainView.fxml"));
        stage.setScene(AppTheme.scene(root)); stage.setWidth(1024); stage.setHeight(700); stage.show();
    }
    @AfterEach void reset() throws Exception { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }

    @Test void panelIconsBelongToFooterAndToolbarContainsOnlyThreeGroups() throws Exception {
        fx(() -> {
            var bar=(ToolBar)root.lookup("#workbenchToolbar");
            assertEquals(2,bar.getItems().stream().filter(Separator.class::isInstance).count());
            for(String id:new String[]{"toggleLeftPanelButton","toggleBottomPanelButton"}) {
                var button=(ToggleButton)root.lookup("#"+id);
                assertTrue(button.getText().isEmpty());
                assertNotNull(button.getTooltip());
                assertTrue(button.getParent().getStyleClass().contains("status-bar"));
                assertInstanceOf(SVGPath.class,button.getGraphic());
            }
            assertNull(root.lookup("#refreshButton")); assertNull(root.lookup("#clearLogButton"));
            assertTrue(bar.getItems().indexOf(root.lookup("#serversButton"))<bar.getItems().indexOf(root.lookup("#searchField")));
        });
    }

    @Test void footerIconsRemainReadableFlatAndInsideAtSmallSizes() throws Exception {
        for(var theme:AppTheme.Theme.values()) for(double width:new double[]{600,800,1024}) {
            fx(() -> {AppTheme.setTheme(theme);stage.setWidth(width);});
            fx(() -> {
                root.applyCss();root.layout();
                var bar=(Region)root.lookup(".status-bar");
                assertTrue(bar.getHeight()<=28);
                for(String id:new String[]{"toggleLeftPanelButton","toggleBottomPanelButton"}) {
                    var button=(ToggleButton)root.lookup("#"+id);
                    for(boolean selected:new boolean[]{false,true}) {
                        button.setSelected(selected);root.applyCss();
                        var shape=(SVGPath)button.getGraphic();
                        assertTrue(contrast((Color)shape.getStroke(),RenderedColors.background(button))>=3);
                        assertTrue(button.getWidth()>=24 && button.getWidth()<=32);
                        assertTrue(button.getHeight()>=24 && button.getHeight()<=26);
                    }
                    var bounds=root.sceneToLocal(button.localToScene(button.getBoundsInLocal()));
                    assertTrue(bounds.getMinX()>=0 && bounds.getMaxX()<=root.getLayoutBounds().getWidth());
                }
            });
        }
    }

    @Test void narrowToolbarUsesCompleteLabelsOrIntentionalIcons() throws Exception {
        fx(() -> {
            ((com.seeloggyplus.features.search.presentation.SearchNavigator)root.lookup("#searchNavigator")).setMatchCount(50);
            stage.setWidth(800);
        });
        fx(() -> {
            root.applyCss();root.layout();
            var bar=(ToolBar)root.lookup("#workbenchToolbar");
            for(var node:bar.getItems()) if(node instanceof ButtonBase button && !button.getText().isEmpty()
                    && button.getScene()==root.getScene() && button.getContentDisplay()!=ContentDisplay.GRAPHIC_ONLY) {
                assertTrue(button.getWidth()+.1>=button.prefWidth(-1),button.getId()+" must not truncate its action label");
                var caption=(javafx.scene.text.Text)button.lookup(".text");
                if(caption!=null) assertEquals(button.getText(),caption.getText(),button.getId()+" caption must be complete");
            }
        });
    }

    @Test void resourceCaptionFitsAndPanelIconsStayOutlineDuringThemeSwitches() throws Exception {
        for(var theme:AppTheme.Theme.values()) for(double width:new double[]{600,800,1024}) {
            fx(() -> {AppTheme.setTheme(theme);stage.setWidth(width);});
            fx(() -> {
                ((Label)root.lookup("#memoryStatusLabel")).setText("Heap: 1,024 / 2,048 MB");
                root.applyCss();root.layout();
                var resource=(MenuButton)root.lookup("#resourceButton");
                var caption=(Label)resource.lookup(".label");
                assertTrue(caption.getHeight()+.1>=caption.prefHeight(-1),"Resources caption must fit vertically");
                assertTrue(resource.getWidth()+.1>=resource.prefWidth(-1),"Resources must retain its complete caption");
                var arrow=resource.lookup(".arrow-button");
                assertTrue(caption.localToScene(caption.getBoundsInLocal()).getMaxX()<=arrow.localToScene(arrow.getBoundsInLocal()).getMinX()+.1,"Resources caption must not overlap its arrow");
                for(String id:new String[]{"toggleLeftPanelButton","toggleBottomPanelButton"}) {
                    var button=(ToggleButton)root.lookup("#"+id);
                    var shape=(SVGPath)button.getGraphic();
                    button.setSelected(false);root.applyCss();
                    var inactive=RenderedColors.background(button);
                    for(boolean selected:new boolean[]{false,true}) {
                        button.setSelected(selected);root.applyCss();
                        if(selected) assertNotEquals(inactive,RenderedColors.background(button),"Active panel must have a themed selected background");
                        assertEquals(0,((Color)shape.getFill()).getOpacity(),"Panel icon must have a transparent interior");
                        assertTrue(contrast((Color)shape.getStroke(),RenderedColors.background(button))>=3);
                    }
                }
            });
        }
    }

    @Test void formattedDetectsJsonAndXmlWithoutPrerequisiteToggles() throws Exception {
        fx(() -> {
            try {
                var loader=new FXMLLoader(getClass().getResource("/fxml/LogDetailPane.fxml"));Parent detail=loader.load();
                LogDetailPaneController controller=loader.getController();
                assertNull(detail.lookup("#prettifyJsonButton"));assertNull(detail.lookup("#prettifyXmlButton"));
                for(String raw:new String[]{"prefix {\"a\":1,\"b\":2} suffix","prefix <r><v>1</v></r> suffix"}) {
                    controller.show(0,raw);
                    ((ToggleButton)detail.lookup("#formattedModeButton")).fire();
                    assertTrue(controller.getCodeArea().getText().contains("\n"));
                    assertTrue(controller.getCodeArea().getText().startsWith("prefix "));
                    assertTrue(controller.getCodeArea().getText().endsWith(" suffix"));
                    ((ToggleButton)detail.lookup("#rawModeButton")).fire();
                    assertEquals(raw,controller.getCodeArea().getText());
                }
            } catch(Exception e){throw new AssertionError(e);}
        });
    }

    @Test void unsupportedAndClearedDetailsDisableFormattedWithExplanation() throws Exception {
        fx(() -> {
            try {
                var loader=new FXMLLoader(getClass().getResource("/fxml/LogDetailPane.fxml"));Parent detail=loader.load();
                LogDetailPaneController controller=loader.getController();
                var formatted=(ToggleButton)detail.lookup("#formattedModeButton");
                for(String raw:new String[]{"plain stack trace","broken {json:","<not-closed>"}) {
                    controller.show(0,raw);
                    assertTrue(formatted.isDisabled());assertNotNull(formatted.getTooltip());
                    assertEquals(raw,controller.getCodeArea().getText());
                }
                controller.show(0,"{\"ok\":true}");assertFalse(formatted.isDisabled());
                controller.clear();assertTrue(formatted.isDisabled());
            } catch(Exception e){throw new AssertionError(e);}
        });
    }
    @Test void tabMenuClearsOnlyItsTargetAndRetainsReloadAccess() throws Exception {
        fx(() -> {
            var target=new com.seeloggyplus.shared.session.LogSession("target",com.seeloggyplus.shared.session.LogSession.SessionType.LOCAL);
            var keeper=new com.seeloggyplus.shared.session.LogSession("keeper",com.seeloggyplus.shared.session.LogSession.SessionType.LOCAL);
            try {
                var loader=new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));loader.load();MainController controller=loader.getController();
                target.getLiveTailList().add(new com.seeloggyplus.shared.model.LogEntry(0,"clear"));
                keeper.getLiveTailList().add(new com.seeloggyplus.shared.model.LogEntry(0,"keep"));
                var sessions=MainController.class.getDeclaredField("tabSessionManager");sessions.setAccessible(true);
                var manager=(com.seeloggyplus.shared.session.TabSessionManager)sessions.get(controller);manager.setActive(keeper);
                var create=MainController.class.getDeclaredMethod("createTabContextMenu",com.seeloggyplus.shared.session.LogSession.class);create.setAccessible(true);
                var menu=(ContextMenu)create.invoke(controller,target);
                assertNotNull(menu.getItems().stream().filter(item -> "Reload".equals(item.getText())).findFirst().orElseThrow().getOnAction());
                menu.getItems().stream().filter(item -> "Clear Log View".equals(item.getText())).findFirst().orElseThrow().fire();
                assertEquals(0,target.getLiveTailList().size());assertEquals(1,keeper.getLiveTailList().size());assertSame(keeper,manager.getActive());
            } catch(Exception error){throw new AssertionError(error);} finally {target.close();keeper.close();}
        });
    }

    @Test void legacyFormattingPreferencesAreReadAndExplicitNewPreferenceTakesPriority() throws Exception {
        fx(() -> {
            try {
                var loader=new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));loader.load();
                MainController controller=loader.getController();
                var values=new java.util.HashMap<String,String>();values.put("main_auto_prettify_xml","true");
                var prefs=MainController.class.getDeclaredField("preferenceService");prefs.setAccessible(true);
                Object original=prefs.get(controller);
                var service=java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{com.seeloggyplus.shared.settings.PreferenceService.class},
                        (proxy,method,args) -> method.getName().equals("getPreferencesByCode") ? java.util.Optional.ofNullable(values.get(args[0])) : null);
                var load=MainController.class.getDeclaredMethod("loadPreferences");load.setAccessible(true);
                var detail=MainController.class.getDeclaredField("detailPaneController");detail.setAccessible(true);
                var pane=(LogDetailPaneController)detail.get(controller);
                try {
                    prefs.set(controller,service);load.invoke(controller);
                    pane.show(0,"{\"a\":1}");assertTrue(pane.getCodeArea().getText().contains("\n"));
                    values.put("main_detail_formatted","false");load.invoke(controller);
                    assertEquals("{\"a\":1}",pane.getCodeArea().getText());
                } finally {prefs.set(controller,original);}
            } catch(Exception error){throw new AssertionError(error);}
        });
    }
    private static void fx(Runnable r)throws Exception{WaitForAsyncUtils.asyncFx(r).get(15,TimeUnit.SECONDS);WaitForAsyncUtils.waitForFxEvents();}
    private static double contrast(Color a,Color b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static double luminance(Color c){return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue());}
    private static double linear(double c){return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);}
}
