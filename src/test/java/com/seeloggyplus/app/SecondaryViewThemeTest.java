package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import com.seeloggyplus.shared.ui.ThemePalette;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SecondaryViewThemeTest {
    private Stage stage;
    private Parent root;

    @Start void start(Stage stage) { this.stage=stage; stage.setX(40); stage.setY(40); }
    @AfterEach void reset() throws Exception { fx(() -> { stage.hide(); AppTheme.setTheme(AppTheme.Theme.LIGHT); }); }

    @Test void aboutIsFlatAndReadable() throws Exception { checkDialog("AboutDialog"); }
    @Test void fileManagerIsFlatAndReadable() throws Exception { checkDialog("UnifiedFileManagerDialog"); }
    @Test void serverManagementIsFlatAndReadable() throws Exception { checkDialog("ServerManagementDialog"); }
    @Test void serverEditorIsFlatAndReadable() throws Exception { checkDialog("ServerEditDialog"); }
    @Test void preferencesAreFlatAndReadable() throws Exception { checkDialog("PreferencesDialog"); }
    @Test void searchIsFlatAndReadable() throws Exception { checkDialog("RemoteLogSearchDialog"); }
    @Test void previewIsFlatAndReadable() throws Exception { checkDialog("LogPreviewDialog"); }

    private void checkDialog(String view) throws Exception {
        load(view);
        for(var theme:AppTheme.Theme.values()) {
            fx(() -> { AppTheme.setTheme(theme); stage.setWidth(width(view)); stage.setHeight(height(view)); });
            fx(() -> {
                root.applyCss(); root.layout(); root.applyCss();
                flat((Region)root,theme);
                for(String selector:sections(view)) {
                    var panel=(Region)root.lookup(selector); assertNotNull(panel,view+" "+selector); flat(panel,theme);
                }
                for(var node:root.lookupAll(".label")) if(node instanceof Labeled label && label.isVisible() && !label.isDisabled() && label.getText()!=null && !label.getText().isBlank()) {
                    readable((Color)label.getTextFill(),RenderedColors.background(label),label.getText()+" "+theme);
                }
                for(var node:root.lookupAll(".button")) if(node instanceof Button button && visible(button)) {
                    assertEquals(28,button.getHeight(),.1,view+" "+button.getText());
                    if(inScrollPane(button)) continue;
                    var bounds=button.localToScene(button.getBoundsInLocal());
                    assertTrue(bounds.getMinX()>=0 && bounds.getMaxX()<=stage.getScene().getWidth()+.5,button.getText());
                    assertTrue(bounds.getMinY()>=0 && bounds.getMaxY()<=stage.getScene().getHeight()+.5,button.getText());
                }
                snapshot(view,theme);
            });
        }
    }
    private boolean visible(javafx.scene.Node node) {
        for(var current=node;current!=null;current=current.getParent()) if(!current.isVisible() || !current.isManaged()) return false;
        return true;
    }
    private boolean inScrollPane(javafx.scene.Node node) {
        for(var current=node.getParent();current!=null;current=current.getParent()) if(current instanceof javafx.scene.control.ScrollPane) return true;
        return false;
    }

    @Test void recentFilesAreFlatOutsideMainView() throws Exception { checkEmbedded("RecentFilesPanel"); }
    @Test void logDetailIsFlatOutsideMainView() throws Exception { checkEmbedded("LogDetailPane"); }

    private void checkEmbedded(String view) throws Exception {
        load(view);
        for(var theme:AppTheme.Theme.values()) {
            fx(() -> { AppTheme.setTheme(theme); stage.setWidth(700); stage.setHeight(320); });
            fx(() -> {
                root.applyCss(); root.layout(); flat((Region)root,theme);
                var detail=(Region)root.lookup(".detail-container");
                if(detail!=null && detail.getBorder()!=null) for(var stroke:detail.getBorder().getStrokes()) assertEquals(0,stroke.getWidths().getTop());
                snapshot(view,theme);
            });
        }
    }

    private String[] sections(String view) {
        return switch(view) {
            case "AboutDialog" -> new String[]{".about-dialog-header",".about-dialog-body",".simple-dialog-footer"};
            case "UnifiedFileManagerDialog" -> new String[]{".file-manager-path-toolbar",".locations-container",".file-manager-footer"};
            case "ServerManagementDialog" -> new String[]{".server-toolbar",".server-group-sidebar",".server-footer",".server-details"};
            case "ServerEditDialog" -> new String[]{".server-edit-footer"};
            case "PreferencesDialog" -> new String[]{".settings-sidebar",".settings-footer"};
            case "RemoteLogSearchDialog" -> new String[]{".search-dialog-form",".search-dialog-section",".simple-dialog-footer"};
            default -> new String[]{".preview-dialog-header",".simple-dialog-footer"};
        };
    }
    private int width(String view) { return switch(view) { case "AboutDialog" -> 420; case "PreferencesDialog" -> 660; case "ServerEditDialog" -> 440; case "LogPreviewDialog" -> 700; default -> 1000; }; }
    private int height(String view) { return switch(view) { case "AboutDialog" -> 320; case "ServerEditDialog","PreferencesDialog" -> 460; case "LogPreviewDialog" -> 500; default -> 640; }; }
    private void load(String view) throws Exception {
        fx(() -> {
            try { root=new FXMLLoader(getClass().getResource("/fxml/"+view+".fxml")).load(); }
            catch(Exception error) { throw new AssertionError(error); }
            stage.setScene(AppTheme.scene(root)); stage.setWidth(width(view)); stage.setHeight(height(view)); stage.show();
        });
    }
    private void flat(Region panel,AppTheme.Theme theme) {
        assertEquals(RenderedColors.over(ThemePalette.color("-sl-surface",theme),Color.WHITE),RenderedColors.background(panel),panel.toString());
        if(panel.getBackground()!=null) for(var fill:panel.getBackground().getFills()) {
            assertTrue(fill.getFill() instanceof Color,"no gradients");
            assertEquals(0,fill.getRadii().getTopLeftHorizontalRadius());
        }
        assertNull(panel.getEffect());
        assertNull(panel.getClip());
    }
    private void readable(Color text,Color bg,String message) { assertTrue((Math.max(lum(text),lum(bg))+.05)/(Math.min(lum(text),lum(bg))+.05)>=4.5,message); }
    private double lum(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private double linear(double v) { return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4); }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
    private void snapshot(String view,AppTheme.Theme theme) {
        try {
            var rendered=root.snapshot(null,null); int width=(int)rendered.getWidth(),height=(int)rendered.getHeight(); int[] pixels=new int[width*height];
            rendered.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
            var png=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB); png.setRGB(0,0,width,height,pixels,0,width);
            var path=java.nio.file.Path.of("build","secondary-view-review",view+"-"+theme.name().toLowerCase()+".png"); java.nio.file.Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(png,"png",path.toFile());
        } catch(Exception error) { throw new AssertionError(error); }
    }
}
