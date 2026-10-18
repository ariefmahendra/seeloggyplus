package com.seeloggyplus.app;

import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.features.recent.presentation.RecentFileTreeCell;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import com.seeloggyplus.shared.ui.RoundedPanelSupport;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
class RecentFileHighlightThemeTest {
    private StackPane root;
    private TreeView<RecentFileTreeCell.RecentNode> tree;

    @Start void start(Stage stage) throws Exception {
        Parent panel=new FXMLLoader(getClass().getResource("/fxml/RecentFilesPanel.fxml")).load();
        tree=(TreeView<RecentFileTreeCell.RecentNode>)panel.lookup("#recentFilesTreeView");
        tree.setCellFactory(view -> new RecentFileTreeCell(() -> null));
        var items=new TreeItem<>(RecentFileTreeCell.RecentNode.root()); items.setExpanded(true);
        for(String server:new String[]{"Production","Local"}) {
            var group=new TreeItem<>(RecentFileTreeCell.RecentNode.server(server)); group.setExpanded(true);
            var file=new LogFile(); file.setName("application.log"); file.setFilePath("/var/log/application.log"); file.setSize("1.2 MB");
            group.getChildren().add(new TreeItem<>(RecentFileTreeCell.RecentNode.file(new RecentFilesDto(file,null,server,"OPEN")))); items.getChildren().add(group);
        }
        tree.setRoot(items); tree.setShowRoot(false); RoundedPanelSupport.apply((Region)panel);
        root=new StackPane(panel); root.getStyleClass().add("main-view"); root.setFocusTraversable(true);
        stage.setScene(AppTheme.scene(root)); stage.setX(40); stage.setY(40); stage.setWidth(330); stage.setHeight(420); stage.show(); root.requestFocus(); apply();
    }
    @AfterEach void reset() throws Exception { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }

    @Test void hoverHighlightsAreRoundedForFilesAndGroupsWithoutResizing() throws Exception {
        for(var theme:AppTheme.Theme.values()) fx(() -> {
            AppTheme.setTheme(theme); tree.getSelectionModel().clearSelection(); root.requestFocus(); apply();
            for(var node:tree.lookupAll(".tree-cell")) if(node instanceof TreeCell<?> cell && !cell.isEmpty()) {
                double height=cell.getHeight(); Color idle=RenderedColors.background(cell);
                cell.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),true); apply();
                assertNotEquals(idle,RenderedColors.background(cell)); rounded(cell); readable(cell);
                assertEquals(height,cell.getHeight(),.1);
                cell.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),false); apply();
            }
            for(var node:tree.lookupAll(".tree-cell")) if(node instanceof TreeCell<?> cell && cell.isEmpty()) {
                cell.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),true); apply();
                assertEquals(RenderedColors.background(tree),RenderedColors.background(cell));
            }
        });
    }

    @Test void selectedFileAndGroupKeepRoundedCornersAndReadableTextAcrossFocusStates() throws Exception {
        for(var theme:AppTheme.Theme.values()) for(int index:new int[]{0,1}) for(boolean focused:new boolean[]{false,true}) {
            fx(() -> { AppTheme.setTheme(theme); tree.getSelectionModel().select(index); if(focused) tree.requestFocus(); else root.requestFocus(); apply(); });
            fx(() -> {
                var cell=selected(); rounded(cell); readable(cell);
                cell.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),true); apply(); rounded(cell); readable(cell);
                if(focused && index==1) snapshot(theme);
                cell.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),false);
            });
        }
    }

    @Test void keyboardNavigationRetainsRoundedSelection(FxRobot robot) throws Exception {
        fx(() -> { tree.requestFocus(); tree.getSelectionModel().select(0); });
        robot.press(KeyCode.DOWN).release(KeyCode.DOWN);
        fx(() -> { apply(); assertEquals(1,tree.getSelectionModel().getSelectedIndex()); rounded(selected()); readable(selected()); });
    }
    private TreeCell<?> selected() { return (TreeCell<?>)tree.lookupAll(".tree-cell").stream().filter(node -> node instanceof TreeCell<?> cell && !cell.isEmpty() && cell.isSelected()).findFirst().orElseThrow(); }
    private void rounded(TreeCell<?> cell) {
        var fill=cell.getBackground().getFills().getLast();
        assertTrue(fill.getRadii().getTopLeftHorizontalRadius()>=6);
        assertTrue(fill.getInsets().getLeft()>=2 && fill.getInsets().getRight()>=2);
        assertTrue(fill.getInsets().getTop()>=1 && fill.getInsets().getBottom()>=1);
        var parameters=new javafx.scene.SnapshotParameters(); parameters.setFill(Color.TRANSPARENT);
        var image=cell.snapshot(parameters,null); assertTrue(image.getPixelReader().getColor(0,0).getOpacity()<.1);
    }
    private void readable(TreeCell<?> cell) {
        Color bg=RenderedColors.background(cell);
        if(cell.getText()!=null) contrast((Color)cell.getTextFill(),bg,4.5);
        for(var node:cell.lookupAll(".label")) if(node instanceof Label label) contrast((Color)label.getTextFill(),bg,4.5);
        var arrow=(Region)cell.lookup(".tree-disclosure-node > .arrow");
        if(arrow!=null && cell.getTreeItem()!=null && !cell.getTreeItem().isLeaf()) contrast(RenderedColors.background(arrow),bg,3);
    }
    private void contrast(Color text,Color bg,double minimum) { assertTrue((Math.max(lum(text),lum(bg))+.05)/(Math.min(lum(text),lum(bg))+.05)>=minimum,text+" on "+bg); }
    private double lum(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private double linear(double v) { return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4); }
    private void apply() { root.applyCss(); root.layout(); root.applyCss(); }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
    private void snapshot(AppTheme.Theme theme) {
        try {
            var image=root.snapshot(null,null); int width=(int)image.getWidth(),height=(int)image.getHeight(); int[] pixels=new int[width*height];
            image.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
            var png=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB); png.setRGB(0,0,width,height,pixels,0,width);
            var path=java.nio.file.Path.of("build","recent-highlight-review",theme.name().toLowerCase()+".png"); java.nio.file.Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(png,"png",path.toFile());
        } catch(Exception error) { throw new AssertionError(error); }
    }
}
