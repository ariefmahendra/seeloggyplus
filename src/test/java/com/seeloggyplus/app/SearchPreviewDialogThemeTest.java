package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
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
class SearchPreviewDialogThemeTest {
    private Stage stage;
    private Parent root;
    private FXMLLoader loader;

    @Start void start(Stage stage) { this.stage = stage; stage.setX(40); stage.setY(40); }
    @AfterEach void reset() throws Exception { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }

    @Test void openAndJumpUsesAnOpenActionIcon() throws Exception {
        load("RemoteLogSearchDialog");
        fx(() -> com.seeloggyplus.shared.ui.ActionIconAssertions.verify(root, "openButton", "EXTERNAL_LINK"));
    }

    @Test void searchPanelsAreFlatCompactAndFitEverySize(FxRobot robot) throws Exception {
        load("RemoteLogSearchDialog");
        for (var theme : AppTheme.Theme.values()) {
            for (double[] size : new double[][]{{800,560},{960,640},{1280,800}}) {
                resize(theme,size);
                fx(() -> {
                    for (String id : new String[]{"searchFormPanel","searchResultsPanel","searchPreviewPanel"}) checkPanel(id);
                    for (String id : new String[]{"searchButton","cancelSearchButton","openButton","rootPathField","keywordField","contextSelector"}) {
                        var node = region(id); assertEquals(28,node.getHeight(),.1,id); inside(node);
                    }
                    var split = (SplitPane)root.lookup("#searchResultsSplit");
                    assertEquals(6,split.lookup(".split-pane-divider").getLayoutBounds().getHeight(),.1);
                    assertTrue(region("searchResultsPanel").getHeight() >= 140);
                    assertTrue(region("searchPreviewPanel").getHeight() >= 120);
                    assertEquals(.55,split.getDividerPositions()[0],.04);
                    ((Label)root.lookup("#statusLabel")).setText("Search failed: a long connection status that must wrap without pushing the search controls outside the window.");
                    root.applyCss(); root.layout(); inside(region("searchButton")); inside(region("statusLabel"));
                    assertTrue(((Label)root.lookup("#statusLabel")).isWrapText());
                    checkContrast();
                    if(size[0]==960) snapshot("search",theme);
                    ((Label)root.lookup("#statusLabel")).setText("Searches every file under the folder");
                });
            }
        }
        var divider = WaitForAsyncUtils.asyncFx(() -> root.lookup("#searchResultsSplit").lookup(".split-pane-divider")).get();
        double before = WaitForAsyncUtils.asyncFx(() -> region("searchResultsPanel").getHeight()).get();
        robot.moveTo(divider).press(javafx.scene.input.MouseButton.PRIMARY).moveBy(0,-45).release(javafx.scene.input.MouseButton.PRIMARY);
        fx(() -> assertTrue(region("searchResultsPanel").getHeight() < before-10));
    }

    @Test void previewUsesFlatPanelAndFullPathTooltip() throws Exception {
        load("LogPreviewDialog");
        for(var theme : AppTheme.Theme.values()) {
            for(double[] size : new double[][]{{560,360},{700,500},{1100,700}}) {
                resize(theme,size);
                fx(() -> {
                    checkPanel("previewPanel");
                    assertEquals(28,region("closeButton").getHeight(),.1);
                    var label = (Label)root.lookup("#fileNameLabel");
                    label.setText("/var/log/production/a-very-long-folder-name/".repeat(6)+"application.log");
                    root.applyCss(); root.layout();
                    assertEquals(OverrunStyle.CENTER_ELLIPSIS,label.getTextOverrun());
                    assertEquals(label.getText(),label.getTooltip().getText());
                    inside(label); inside(region("closeButton"));
                    assertTrue(region("progressIndicator").getWidth() <= 24);
                    checkContrast();
                    if(size[0]==700) snapshot("preview",theme);
                });
            }
        }
    }

    @Test void searchPreviewSelectionTracksFocusAndKeepsLineNumbersReadable() throws Exception {
        load("RemoteLogSearchDialog");
        for(var theme : AppTheme.Theme.values()) {
            resize(theme,new double[]{960,640});
            for(boolean active : new boolean[]{false,true}) {
                fx(() -> {
                    @SuppressWarnings("unchecked") var list=(ListView<com.seeloggyplus.shared.model.PreviewLine>)root.lookup("#previewList");
                    list.getItems().setAll(new com.seeloggyplus.shared.model.PreviewLine(42,"INFO application started",true));
                    list.getSelectionModel().select(0);
                    if(active) list.requestFocus(); else root.lookup("#keywordField").requestFocus();
                });
                fx(() -> {
                    root.applyCss(); root.layout();
                    var list=(ListView<?>)root.lookup("#previewList");
                    var cell=(ListCell<?>)list.lookupAll(".list-cell").stream().filter(node -> node instanceof ListCell<?> item && item.isSelected()).findFirst().orElseThrow();
                    assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color(active?"-sl-selection-bg":"-sl-selection-inactive-bg",theme),cell.getBackground().getFills().getFirst().getFill());
                    checkContrast();
                    if(active) snapshot("search-selection",theme);
                });
            }
            fx(() -> {
                ((ListView<?>)root.lookup("#previewList")).getSelectionModel().clearSelection();
                root.applyCss(); root.layout(); checkContrast();
            });
        }
    }

    @Test void windowMinimumsProtectContentAfterDecorations() throws Exception {
        for(String view : new String[]{"RemoteLogSearchDialog","LogPreviewDialog"}) {
            load(view);
            fx(() -> { stage.setWidth(1); stage.setHeight(1); });
            fx(() -> {
                var content=(Region)root;
                assertTrue(stage.isResizable());
                assertTrue(stage.getScene().getWidth()>=content.getMinWidth()-.5);
                assertTrue(stage.getScene().getHeight()>=content.getMinHeight()-.5);
                assertEquals(content.getMinWidth()+stage.getWidth()-stage.getScene().getWidth(),stage.getMinWidth(),.5);
                assertEquals(content.getMinHeight()+stage.getHeight()-stage.getScene().getHeight(),stage.getMinHeight(),.5);
            });
        }
    }

    @Test void populatedTablesAndPreviewListsKeepSelectedTextReadable() throws Exception {
        for(String view : new String[]{"RemoteLogSearchDialog","LogPreviewDialog"}) {
            load(view);
            for(var theme : AppTheme.Theme.values()) {
                resize(theme,view.equals("RemoteLogSearchDialog")?new double[]{960,640}:new double[]{700,500});
                fx(() -> {
                    if(view.equals("RemoteLogSearchDialog")) {
                        @SuppressWarnings("unchecked") var table=(TableView<com.seeloggyplus.features.search.domain.RemoteLogSearchMatch>)root.lookup("#resultTable");
                        table.getItems().setAll(new com.seeloggyplus.features.search.domain.RemoteLogSearchMatch("/var/log/application.log",42,"INFO application started"));
                        table.getSelectionModel().select(0); table.requestFocus();
                    } else {
                        @SuppressWarnings("unchecked") var list=(ListView<com.seeloggyplus.features.preview.presentation.LogPreviewDialogController.LogLine>)root.lookup("#logListView");
                        list.getItems().setAll(new com.seeloggyplus.features.preview.presentation.LogPreviewDialogController.LogLine(1,"INFO application started"));
                        list.getSelectionModel().select(0); list.requestFocus();
                    }
                });
                fx(() -> { root.applyCss(); root.layout(); checkContrast(); snapshot(view.equals("RemoteLogSearchDialog")?"search-results":"preview-lines",theme); });
                fx(() -> {
                    for(var node:root.lookup(view.equals("RemoteLogSearchDialog")?"#previewList":"#logListView").lookupAll(".list-cell")) {
                        var cell=(ListCell<?>)node;
                        if(cell.isEmpty() && cell.getListView()!=null) assertEquals(RenderedColors.background(cell.getListView()),RenderedColors.background(cell),theme+" empty list rows");
                    }
                    for(var node:root.lookupAll(".table-row-cell")) {
                        var row=(TableRow<?>)node;
                        if(row.isEmpty()) assertEquals(RenderedColors.background(row.getTableView()),RenderedColors.background(row),theme+" empty table rows");
                    }
                });
            }
        }
    }

    @Test void dialogButtonsKeepKeyboardFocusAndPressedFeedback(FxRobot robot) throws Exception {
        for(String view : new String[]{"RemoteLogSearchDialog","LogPreviewDialog"}) for(var theme : AppTheme.Theme.values()) {
            load(view);
            fx(() -> { AppTheme.setTheme(theme); root.setFocusTraversable(true); root.requestFocus(); });
            var button=WaitForAsyncUtils.asyncFx(() -> (Button)root.lookup(view.equals("RemoteLogSearchDialog")?"#searchButton":"#closeButton")).get();
            for(int i=0;i<12 && !WaitForAsyncUtils.asyncFx(button::isFocused).get();i++) robot.press(javafx.scene.input.KeyCode.TAB).release(javafx.scene.input.KeyCode.TAB);
            fx(() -> {
                root.applyCss();
                assertTrue(button.isFocused());
                assertTrue(button.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("keyboard-focus")));
                var ring=button.getBorder().getStrokes().getFirst();
                assertEquals(1,ring.getWidths().getTop());
                assertTrue(contrast((Color)ring.getTopStroke(),RenderedColors.background(button))>=3);
            });
            robot.moveTo(button);
            Color hover=WaitForAsyncUtils.asyncFx(() -> { root.applyCss(); return RenderedColors.background(button); }).get();
            robot.press(javafx.scene.input.MouseButton.PRIMARY);
            fx(() -> {
                root.applyCss(); assertNotEquals(hover,RenderedColors.background(button));
                assertFalse(button.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("keyboard-focus")));
            });
            robot.release(javafx.scene.input.MouseButton.PRIMARY);
        }
    }

    private void load(String name) throws Exception {
        fx(() -> {
            stage.hide();
            try { loader = new FXMLLoader(getClass().getResource("/fxml/"+name+".fxml")); root=loader.load(); }
            catch(Exception error) { throw new AssertionError(error); }
            stage.setScene(AppTheme.scene(root));
            var content=(Region)root;
            com.seeloggyplus.shared.ui.DialogWindowSupport.setMinimumContentSize(stage,content.getMinWidth(),content.getMinHeight());
            stage.show();
        });
    }
    private void resize(AppTheme.Theme theme,double[] size) throws Exception {
        fx(() -> { AppTheme.setTheme(theme); stage.setWidth(size[0]+stage.getWidth()-stage.getScene().getWidth()); stage.setHeight(size[1]+stage.getHeight()-stage.getScene().getHeight()); });
        fx(() -> { root.applyCss(); root.layout(); });
    }
    private Region region(String id) { return (Region)root.lookup("#"+id); }
    private void checkPanel(String id) {
        var panel=region(id); assertNotNull(panel,id);
        assertNull(panel.getClip(),id+" must not have a rounded clip");
        assertFalse(panel.getStyleClass().contains("workspace-island"));
        if(panel.getBackground()!=null) for(var fill:panel.getBackground().getFills()) assertEquals(0,fill.getRadii().getTopLeftHorizontalRadius());
        Color backdrop=RenderedColors.background(root);
        assertEquals(backdrop,RenderedColors.background(panel));
        var pixels=root.snapshot(null,null).getPixelReader();
        for(double x:new double[]{1,panel.getWidth()-2}) for(double y:new double[]{1,panel.getHeight()-2}) {
            var p=root.sceneToLocal(panel.localToScene(x,y));
            assertEquals(backdrop,pixels.getColor((int)p.getX(),(int)p.getY()),id+" corner");
        }
    }
    private void inside(javafx.scene.Node node) {
        var b=root.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
        assertTrue(b.getMinX()>=-.5 && b.getMaxX()<=root.getLayoutBounds().getWidth()+.5,node+" horizontal overflow");
        assertTrue(b.getMinY()>=-.5 && b.getMaxY()<=root.getLayoutBounds().getHeight()+.5,node+" vertical overflow");
    }
    private void checkContrast() {
        root.applyCss();
        for(var node:root.lookupAll(".label")) {
            var label=(Label)node;
            if(label.getText().isBlank() || !label.isVisible() || label.isDisabled() || !label.isManaged()) continue;
            assertTrue(contrast((Color)label.getTextFill(),RenderedColors.background(label))>=4.5,label.getText()+" "+AppTheme.getTheme()+" text="+label.getTextFill()+" bg="+RenderedColors.background(label));
        }
        for(String selector:new String[]{".list-cell",".table-cell"}) for(var node:root.lookupAll(selector)) {
            var cell=(Labeled)node;
            if(cell.getText()==null || cell.getText().isBlank() || !cell.isVisible() || cell.isDisabled()) continue;
            assertTrue(contrast((Color)cell.getTextFill(),RenderedColors.background(cell))>=4.5,cell.getText()+" "+AppTheme.getTheme());
        }
        for(var node:root.lookupAll(".glyph-icon")) {
            if(!node.isVisible() || node.isDisabled()) continue;
            if(node instanceof javafx.scene.text.Text text) assertTrue(contrast((Color)text.getFill(),RenderedColors.background(node))>=3);
        }
    }
    private double contrast(Color a,Color b) { return (Math.max(luminance(a),luminance(b))+.05)/(Math.min(luminance(a),luminance(b))+.05); }
    private double luminance(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private double linear(double v) { return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4); }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
    private void snapshot(String kind,AppTheme.Theme theme) {
        try {
            var rendered=root.snapshot(null,null); int width=(int)rendered.getWidth(),height=(int)rendered.getHeight();
            int[] pixels=new int[width*height]; rendered.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
            var png=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB); png.setRGB(0,0,width,height,pixels,0,width);
            var path=java.nio.file.Path.of("build","simple-dialog-review",kind+"-"+theme.name().toLowerCase()+".png");
            java.nio.file.Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(png,"png",path.toFile());
        } catch(Exception error) { throw new AssertionError(error); }
    }
}
