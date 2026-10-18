package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class ThemeAvailabilityTest {
    @Start void start(Stage stage) { }
    @AfterEach void reset() throws Exception { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }
    @Test void onlyLightAndDarkAreSelectable() throws Exception {
        assertArrayEquals(new AppTheme.Theme[]{AppTheme.Theme.LIGHT,AppTheme.Theme.DARK},AppTheme.Theme.values());
        fx(() -> {
            try {
                var loader=new FXMLLoader(getClass().getResource("/fxml/PreferencesDialog.fxml"));
                loader.load();
                var combo=(ComboBox<?>)loader.getNamespace().get("themeComboBox");
                assertEquals(java.util.List.of("Light","Dark"),combo.getItems());
            } catch(Exception error) { throw new AssertionError(error); }
        });
    }
    @Test void missingUnknownAndLegacyValuesResolveToLight() {
        for(String value:new String[]{null,""," ","unknown","graphite"," GRAPHITE "}) assertEquals(AppTheme.Theme.LIGHT,AppTheme.Theme.fromPreference(value));
        assertEquals(AppTheme.Theme.DARK,AppTheme.Theme.fromPreference(" DARK "));
    }
    @Test void booleanAndNullApisAlwaysInstallOneLightPalette() throws Exception {
        fx(() -> {
            AppTheme.setTheme(AppTheme.Theme.DARK); AppTheme.setDark(false); assertEquals(AppTheme.Theme.LIGHT,AppTheme.getTheme());
            AppTheme.setTheme(null); var scene=AppTheme.scene(new StackPane());
            assertEquals(1,scene.getStylesheets().stream().filter(url -> url.endsWith("theme-light.css")).count());
            assertTrue(scene.getRoot().getStyleClass().contains("theme-light"));
        });
    }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
}
