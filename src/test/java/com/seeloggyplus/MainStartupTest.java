package com.seeloggyplus;

import com.seeloggyplus.model.Preference;
import com.seeloggyplus.service.impl.PreferenceServiceImpl;
import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class MainStartupTest {

    private Stage stage;
    private final Map<String, String> originalPreferences = new LinkedHashMap<>();

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void cleanup(FxRobot robot) {
        robot.interact(() -> {
            stage.setOnCloseRequest(null);
            stage.hide();
            AppTheme.setTheme(AppTheme.Theme.GRAPHITE);
        });
        var service = new PreferenceServiceImpl();
        originalPreferences.forEach((code, value) ->
                service.saveOrUpdatePreferences(new Preference(code, value)));
    }

    @Test
    void preparesPreferencesInBackgroundAndRestoresThemWithoutStartupQueries(FxRobot robot) throws Exception {
        savePreference("app_theme", "dark");
        savePreference("window_width", "1150");
        savePreference("window_height", "760");
        savePreference("window_x", "");
        savePreference("window_y", "");
        savePreference("window_maximized", "false");
        List<String> phases = new ArrayList<>();
        Main app = new Main() {
            @Override
            void updateStartupStatus(String message) {
                phases.add(message);
                super.updateStartupStatus(message);
            }
        };

        WaitForAsyncUtils.async(() -> {
            assertFalse(Platform.isFxApplicationThread());
            app.init();
            return null;
        }).get(15, TimeUnit.SECONDS);

        assertFalse(stage.isShowing(), "Background preparation must not open JavaFX windows");
        assertEquals(List.of("Preparing workspace...", "Loading preferences..."), phases);
        assertEquals(AppTheme.Theme.GRAPHITE, AppTheme.getTheme(),
                "The theme must be applied on the JavaFX thread, not in init()");

        // A failing query service proves start() consumes the prepared snapshot.
        var field = Main.class.getDeclaredField("preferenceService");
        field.setAccessible(true);
        var preparedService = new PreferenceServiceImpl() {
            @Override
            public java.util.Optional<String> getPreferencesByCode(String code) {
                throw new AssertionError("Startup queried preferences on the UI thread: " + code);
            }
        };
        field.set(app, preparedService);

        robot.interact(() -> app.start(stage));

        assertTrue(stage.isShowing());
        assertNotNull(stage.getScene().getRoot());
        assertEquals("SeeLoggyPlus - Log Viewer v" + Main.VERSION, stage.getTitle());
        assertEquals(AppTheme.Theme.DARK, AppTheme.getTheme());
        assertEquals(1150, stage.getWidth(), 1);
        assertEquals(760, stage.getHeight(), 1);
        assertFalse(stage.isMaximized());
        assertSame(preparedService, field.get(app), "start() must reuse the service prepared in init()");
        assertEquals(List.of("Preparing workspace...", "Loading preferences...", "Loading interface...",
                "Opening main window..."), phases, "Status should follow actual completed startup work");
    }

    private void savePreference(String code, String value) {
        var service = new PreferenceServiceImpl();
        originalPreferences.putIfAbsent(code, service.getPreferencesByCode(code).orElse(""));
        service.saveOrUpdatePreferences(new Preference(code, value));
    }
}
