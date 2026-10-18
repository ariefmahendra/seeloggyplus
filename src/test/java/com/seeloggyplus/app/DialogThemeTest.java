package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class DialogThemeTest {
    private Stage stage;
    private Parent root;
    @Start void start(Stage stage) { this.stage=stage; }
    @AfterEach void reset() throws Exception { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }

    @Test void aboutDialogTextFollowsTheme() throws Exception {
        load("AboutDialog");
        for(var theme:AppTheme.Theme.values()) fx(() -> {
            AppTheme.setTheme(theme); apply();
            for(String id:new String[]{"#versionLabel",".about-copyright"}) {
                var label=(Label)root.lookup(id); readable((Color)label.getTextFill(),RenderedColors.background(label));
            }
            var description=(Text)root.lookup(".about-description"); readable((Color)description.getFill(),RenderedColors.background(description));
        });
    }
    @Test void updateHeaderUsesFlatSurfaceInEveryTheme() throws Exception {
        load("UpdateDialog");
        for(var theme:AppTheme.Theme.values()) fx(() -> {
            AppTheme.setTheme(theme); apply();
            var header=(Region)root.lookup(".update-dialog-header");
            assertTrue(header.getBackground().getFills().getFirst().getFill() instanceof Color);
            assertEquals(RenderedColors.background(root),RenderedColors.background(header));
            var title=(Label)root.lookup("#titleLabel"); readable((Color)title.getTextFill(),RenderedColors.background(title));
        });
    }
    @Test void primaryButtonKeepsDistinctNeutralFeedback() throws Exception {
        load("UpdateDialog");
        for(var theme:AppTheme.Theme.values()) fx(() -> {
            AppTheme.setTheme(theme); apply(); var button=(Button)root.lookup("#downloadButton");
            Color idle=RenderedColors.background(button);
            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),true); apply(); Color hover=RenderedColors.background(button);
            assertNotEquals(idle,hover); readable((Color)button.getTextFill(),hover);
            button.arm(); apply(); assertNotEquals(hover,RenderedColors.background(button)); readable((Color)button.getTextFill(),RenderedColors.background(button));
            button.disarm(); button.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"),false);
        });
    }
    private void load(String view) throws Exception {
        fx(() -> { try { root=new FXMLLoader(getClass().getResource("/fxml/"+view+".fxml")).load(); } catch(Exception error) { throw new AssertionError(error); } stage.setScene(AppTheme.scene(root)); stage.show(); apply(); });
    }
    private void apply() { root.applyCss(); root.layout(); }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
    private void readable(Color text,Color bg) { assertTrue((Math.max(lum(text),lum(bg))+.05)/(Math.min(lum(text),lum(bg))+.05)>=4.5,text+" on "+bg); }
    private double lum(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private double linear(double v) { return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4); }
}
