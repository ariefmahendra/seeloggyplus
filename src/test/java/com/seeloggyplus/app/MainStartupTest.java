package com.seeloggyplus.app;

import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;

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

        var field = Main.class.getDeclaredField("preferenceService");
        field.setAccessible(true);
        var preparedService = new PreferenceServiceImpl() {
            @Override
            public Optional<String> getPreferencesByCode(String code) {
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

    @Test
    void readsNumericPreferencesDefensively(FxRobot robot) throws Exception {
        Main app = new Main();
        Map<String, String> preferences = startupPreferences(app);
        Method method = Main.class.getDeclaredMethod("getPreferenceAsDouble", String.class);
        method.setAccessible(true);

        preferences.put("probe", "12.5");
        assertEquals(Optional.of(12.5), method.invoke(app, "probe"));

        preferences.put("probe", "not-a-number");
        assertEquals(Optional.empty(), method.invoke(app, "probe"));

        preferences.put("probe", "   ");
        assertEquals(Optional.empty(), method.invoke(app, "probe"));

        preferences.remove("probe");
        assertEquals(Optional.empty(), method.invoke(app, "probe"));
    }

    @Test
    void checksWindowBoundsAgainstTheAvailableScreens(FxRobot robot) throws Exception {
        Main app = new Main();
        Method method = Main.class.getDeclaredMethod("isBoundsVisibleOnScreen",
                double.class, double.class, double.class, double.class);
        method.setAccessible(true);

        boolean expectedVisible = Screen.getScreens().stream()
                .anyMatch(screen -> screen.getVisualBounds().intersects(new Rectangle2D(100, 100, 500, 400)));
        assertEquals(expectedVisible, method.invoke(app, 100.0, 100.0, 500.0, 400.0));

        assertEquals(false, method.invoke(app, -1_000_000.0, -1_000_000.0, 10.0, 10.0));
    }

    @Test
    void restoresSavedWindowBoundsAndMaximizedState(FxRobot robot) throws Exception {
        Main app = new Main();
        Map<String, String> preferences = startupPreferences(app);
        preferences.put("window_x", "100");
        preferences.put("window_y", "100");
        preferences.put("window_width", "640");
        preferences.put("window_height", "480");
        preferences.put("window_maximized", "true");
        Method restore = Main.class.getDeclaredMethod("restoreWindowPreferences", Stage.class);
        restore.setAccessible(true);

        robot.interact(() -> {
            try {
                restore.invoke(app, stage);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });

        assertEquals(640, stage.getWidth(), 1);
        assertEquals(480, stage.getHeight(), 1);
        assertTrue(stage.isMaximized());
    }

    @Test
    void restoresOffScreenWindowByCenteringIt(FxRobot robot) throws Exception {
        Main app = new Main();
        Map<String, String> preferences = startupPreferences(app);
        preferences.put("window_x", "-1000000");
        preferences.put("window_y", "-1000000");
        preferences.put("window_width", "320");
        preferences.put("window_height", "240");
        preferences.put("window_maximized", "false");
        Method restore = Main.class.getDeclaredMethod("restoreWindowPreferences", Stage.class);
        restore.setAccessible(true);

        robot.interact(() -> {
            try {
                restore.invoke(app, stage);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });

        assertEquals(320, stage.getWidth(), 1);
        assertEquals(240, stage.getHeight(), 1);
        assertFalse(stage.isMaximized());
    }

    @Test
    void savesWindowStateOnCloseRequestAndCleansUp(FxRobot robot) throws Exception {
        Main app = new Main();
        var service = new PreferenceServiceImpl();
        Field serviceField = Main.class.getDeclaredField("preferenceService");
        serviceField.setAccessible(true);
        serviceField.set(app, service);
        Field stageField = Main.class.getDeclaredField("primaryStage");
        stageField.setAccessible(true);
        stageField.set(app, stage);
        Method save = Main.class.getDeclaredMethod("saveWindowPreferences");
        save.setAccessible(true);
        Method cleanup = Main.class.getDeclaredMethod("cleanup");
        cleanup.setAccessible(true);

        savePreference("window_width", "");
        savePreference("window_height", "");
        savePreference("window_x", "");
        savePreference("window_y", "");
        savePreference("window_maximized", "");

        robot.interact(() -> {
            try {
                stage.show();
                save.invoke(app);
                cleanup.invoke(app);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });

        assertEquals(String.valueOf(stage.getWidth()), service.getPreferencesByCode("window_width").orElseThrow());
        assertEquals(String.valueOf(stage.getHeight()), service.getPreferencesByCode("window_height").orElseThrow());
        assertEquals(String.valueOf(stage.getX()), service.getPreferencesByCode("window_x").orElseThrow());
        assertEquals(String.valueOf(stage.getY()), service.getPreferencesByCode("window_y").orElseThrow());
        assertEquals(String.valueOf(stage.isMaximized()),
                service.getPreferencesByCode("window_maximized").orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> startupPreferences(Main app) throws Exception {
        Field field = Main.class.getDeclaredField("startupPreferences");
        field.setAccessible(true);
        return (Map<String, String>) field.get(app);
    }

    private void savePreference(String code, String value) {
        var service = new PreferenceServiceImpl();
        originalPreferences.putIfAbsent(code, service.getPreferencesByCode(code).orElse(""));
        service.saveOrUpdatePreferences(new Preference(code, value));
    }
}
