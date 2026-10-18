package com.seeloggyplus.features.update.presentation;

import com.seeloggyplus.features.update.application.UpdateService;
import com.seeloggyplus.features.update.application.UpdateServiceImpl;
import com.seeloggyplus.features.update.domain.AppVersion;
import com.seeloggyplus.features.update.domain.UpdateAwareness;
import com.seeloggyplus.features.update.domain.UpdateCheckResult;
import com.seeloggyplus.features.update.domain.UpdateManifest;
import com.seeloggyplus.features.update.domain.UpdatePreferences;
import com.seeloggyplus.features.update.domain.UpdateAsset;
import com.seeloggyplus.features.update.infrastructure.UpdateCoordinator;
import com.seeloggyplus.features.update.infrastructure.UpdateDownloader;
import com.seeloggyplus.features.update.infrastructure.UpdateInstaller;
import com.seeloggyplus.features.update.infrastructure.UpdateLayout;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@ExtendWith(ApplicationExtension.class)
class UpdateFeatureBehaviorTest {

    private static final String MANIFEST = """
            {"channel":"stable","latest":"9.9.9","minSupported":"0.0.1",
             "releaseNotesUrl":"https://example.com/notes",
             "assets":{"portable-nojre":{"url":"https://example.com/app.zip","size":100,"sha256":"abc"}}}
            """;

    private static final String ASSETLESS_MANIFEST = "{\"latest\":\"9.9.9\"}";

    private final FakePreferenceService preferences = new FakePreferenceService();
    private UpdateFeature updateFeature;
    private Label updateStatusLabel;

    private void createFeature() {
        onFxThread(() -> {
            updateStatusLabel = new Label();
            updateFeature = new UpdateFeature(preferences);
            updateFeature.initialize(updateStatusLabel, null, null);
            updateFeature.hideUpdateIndicator();
        });
    }

    private static UpdateCheckResult available() throws Exception {
        return UpdateCheckResult.updateAvailable("0.6.3", manifest(MANIFEST));
    }

    private static UpdateManifest manifest(String json) throws Exception {
        return UpdateManifest.parse(json);
    }

    private void runUpdateCheck(boolean interactive, String channel) throws Exception {
        Method method = UpdateFeature.class.getDeclaredMethod("runUpdateCheck", boolean.class, String.class);
        method.setAccessible(true);
        method.invoke(updateFeature, interactive, channel);
    }

    @Test
    @DisplayName("a result without a manifest leaves the indicator hidden")
    void showUpdateIndicatorIgnoresResultsWithoutManifest() {
        createFeature();

        onFxThread(() -> {
            updateFeature.showUpdateIndicator(null);
            updateFeature.showUpdateIndicator(UpdateCheckResult.error("0.6.3", "offline"));
        });

        assertFalse(updateStatusLabel.isVisible());
        assertFalse(updateStatusLabel.isManaged());
        assertTrue(updateStatusLabel.getText() == null || updateStatusLabel.getText().isBlank());
    }

    @Test
    @DisplayName("indicator updates before initialization are ignored")
    void indicatorUpdatesAreIgnoredWithoutLabel() {
        UpdateFeature feature = new UpdateFeature(new FakePreferenceService());

        assertDoesNotThrow(() -> feature.setUpdateIndicator(UpdateAwareness.IndicatorState.AVAILABLE, "9.9.9", 0));
        assertDoesNotThrow(feature::hideUpdateIndicator);
        assertDoesNotThrow(feature::handleUpdateIndicatorClick);
    }

    @Test
    @DisplayName("background update ignores missing, failed, up-to-date and assetless results")
    void backgroundUpdateRequiresAnUpdateWithAnAsset() throws Exception {
        createFeature();
        AtomicInteger coordinatorCalls = new AtomicInteger();
        UpdateCheckResult upToDate = UpdateCheckResult.upToDate("0.6.3", manifest(MANIFEST));
        UpdateCheckResult assetless = UpdateCheckResult.updateAvailable("0.6.3", manifest(ASSETLESS_MANIFEST));

        onFxThread(() -> updateFeature.setUpdateCoordinatorFactory(() -> {
            coordinatorCalls.incrementAndGet();
            return null;
        }));
        onFxThread(() -> {
            updateFeature.startBackgroundUpdate(null);
            updateFeature.startBackgroundUpdate(UpdateCheckResult.error("0.6.3", "offline"));
            updateFeature.startBackgroundUpdate(upToDate);
            updateFeature.startBackgroundUpdate(assetless);
        });

        assertEquals(0, coordinatorCalls.get());
        assertFalse(updateStatusLabel.isVisible());
    }

    @Test
    @DisplayName("a version already staged for restart is not downloaded again")
    void backgroundUpdateSkipsReadyVersion() throws Exception {
        createFeature();
        AtomicInteger coordinatorCalls = new AtomicInteger();
        UpdateCheckResult result = available();

        onFxThread(() -> {
            updateFeature.setUpdateCoordinatorFactory(() -> {
                coordinatorCalls.incrementAndGet();
                return null;
            });
            updateFeature.markUpdateReady("9.9.9");
            updateFeature.startBackgroundUpdate(result);
        });

        assertEquals(0, coordinatorCalls.get());
        assertTrue(updateStatusLabel.isVisible());
        assertTrue(updateStatusLabel.getText().toLowerCase().contains("restart"));
    }

    @Test
    @DisplayName("a failed background download shows the failed state and allows a retry")
    void backgroundUpdateFailureShowsFailedIndicator() throws Exception {
        createFeature();
        UpdateCheckResult result = available();

        onFxThread(() -> {
            updateFeature.setUpdateCoordinatorFactory(() -> new FakeCoordinator(false));
            updateFeature.startBackgroundUpdate(result);
        });
        waitUntil(() -> updateStatusLabel.getText() != null
                && updateStatusLabel.getText().toLowerCase().contains("failed"));
        assertTrue(updateStatusLabel.isVisible());

        onFxThread(() -> updateFeature.setUpdateCoordinatorFactory(() -> new FakeCoordinator(true)));
        onFxThread(() -> updateFeature.startBackgroundUpdate(result));
        waitUntil(() -> updateStatusLabel.getText() != null
                && updateStatusLabel.getText().toLowerCase().contains("restart"));
        assertTrue(updateStatusLabel.isVisible());
    }

    @Test
    @DisplayName("the auto-check menu item reflects and persists the preference")
    void autoCheckMenuTogglePersistsPreference() {
        AtomicReference<CheckMenuItem> itemRef = new AtomicReference<>();
        onFxThread(() -> {
            CheckMenuItem item = new CheckMenuItem("Check for updates");
            itemRef.set(item);
            updateStatusLabel = new Label();
            updateFeature = new UpdateFeature(preferences);
            updateFeature.initialize(updateStatusLabel, item, null);
        });
        CheckMenuItem item = itemRef.get();
        assertTrue(item.isSelected());

        onFxThread(() -> {
            item.setSelected(false);
            item.fire();
        });
        assertEquals("false", preferences.getPreferencesByCode(UpdatePreferences.AUTO_CHECK).orElse(null));

        onFxThread(() -> {
            item.setSelected(true);
            item.fire();
        });
        assertEquals("true", preferences.getPreferencesByCode(UpdatePreferences.AUTO_CHECK).orElse(null));
    }

    @Test
    @DisplayName("a skipped version stays hidden and still records the last check")
    void skippedVersionStaysHidden() throws Exception {
        createFeature();
        preferences.put(UpdatePreferences.skipKey("stable"), "9.9.9");
        StubUpdateService service = new StubUpdateService(available());
        onFxThread(() -> updateFeature.setUpdateServiceFactory(() -> service));

        runUpdateCheck(false, "stable");

        assertTrue(service.awaitCheck(), "the injected update service must be consulted");
        waitUntil(() -> preferences.getPreferencesByCode(UpdatePreferences.LAST_CHECK).isPresent());
        settleFx();
        assertFalse(updateStatusLabel.isVisible());
        assertNoUpdateDialog();
    }

    @Test
    @DisplayName("an available update is advertised while snoozed popups stay closed")
    void availableUpdateShowsIndicatorWithoutPopup() throws Exception {
        createFeature();
        preferences.put(UpdatePreferences.AUTO_DOWNLOAD, "false");
        preferences.put(UpdatePreferences.SNOOZE_UNTIL, String.valueOf(Long.MAX_VALUE));
        StubUpdateService service = new StubUpdateService(available());
        onFxThread(() -> updateFeature.setUpdateServiceFactory(() -> service));

        runUpdateCheck(false, "stable");

        assertTrue(service.awaitCheck());
        waitUntil(() -> updateStatusLabel.isVisible());
        assertTrue(updateStatusLabel.getText().contains("9.9.9"));
        assertNoUpdateDialog();
    }

    @Test
    @DisplayName("an up-to-date result does not clear a ready-to-restart indicator")
    void upToDateKeepsReadyIndicator() throws Exception {
        createFeature();
        StubUpdateService service = new StubUpdateService(UpdateCheckResult.upToDate("0.6.3", manifest(MANIFEST)));
        onFxThread(() -> {
            updateFeature.setUpdateServiceFactory(() -> service);
            updateFeature.markUpdateReady("9.9.9");
        });

        runUpdateCheck(false, "stable");

        assertTrue(service.awaitCheck());
        settleFx();
        assertTrue(updateStatusLabel.isVisible());
        assertTrue(updateStatusLabel.getText().toLowerCase().contains("restart"));
        assertNoUpdateDialog();
    }

    @Test
    @DisplayName("a failed check leaves the last-check timestamp and indicator untouched")
    void failedCheckKeepsState() throws Exception {
        createFeature();
        StubUpdateService service = new StubUpdateService(UpdateCheckResult.error("0.6.3", "offline"));
        onFxThread(() -> updateFeature.setUpdateServiceFactory(() -> service));

        runUpdateCheck(false, "stable");

        assertTrue(service.awaitCheck());
        settleFx();
        assertNull(preferences.getPreferencesByCode(UpdatePreferences.LAST_CHECK).orElse(null));
        assertFalse(updateStatusLabel.isVisible());
        assertNoUpdateDialog();
    }

    @Test
    @DisplayName("an update service failure is reported as an error without touching the indicator")
    void serviceFailureIsTolerated() throws Exception {
        createFeature();
        ExplodingUpdateService service = new ExplodingUpdateService();
        onFxThread(() -> updateFeature.setUpdateServiceFactory(() -> service));

        runUpdateCheck(false, "stable");

        assertTrue(service.awaitCheck());
        settleFx();
        assertNull(preferences.getPreferencesByCode(UpdatePreferences.LAST_CHECK).orElse(null));
        assertFalse(updateStatusLabel.isVisible());
        assertNoUpdateDialog();
    }

    @Test
    @DisplayName("the auto check is skipped when disabled or unreadable")
    void autoCheckGuardsAreSwallowed() {
        UpdateFeature disabled = new UpdateFeature(preferences);
        preferences.put(UpdatePreferences.AUTO_CHECK, "false");
        assertDoesNotThrow(() -> {
            invokePrivate(disabled, "maybeAutoUpdateCheck");
        });

        assertDoesNotThrow(() -> {
            invokePrivate(new UpdateFeature(failingPreferences()), "maybeAutoUpdateCheck");
        });
    }

    @Test
    @DisplayName("unreadable skip preferences are tolerated and never trigger a popup")
    void unreadableSkipPreferenceIsTolerated() throws Exception {
        createFeature();
        preferences.failRead(UpdatePreferences.skipKey("stable"));
        preferences.put(UpdatePreferences.AUTO_DOWNLOAD, "false");
        preferences.put(UpdatePreferences.SNOOZE_UNTIL, String.valueOf(Long.MAX_VALUE));
        StubUpdateService service = new StubUpdateService(available());
        onFxThread(() -> updateFeature.setUpdateServiceFactory(() -> service));

        runUpdateCheck(false, "stable");

        assertTrue(service.awaitCheck());
        waitUntil(() -> updateStatusLabel.isVisible());
        assertTrue(updateStatusLabel.getText().contains("9.9.9"));
        assertNoUpdateDialog();
    }

    @Test
    @DisplayName("an unwritable last-check preference does not break the check")
    void lastCheckWriteFailureIsTolerated() throws Exception {
        createFeature();
        preferences.failWrite(UpdatePreferences.LAST_CHECK);
        StubUpdateService service = new StubUpdateService(UpdateCheckResult.upToDate("0.6.3", manifest(MANIFEST)));
        onFxThread(() -> updateFeature.setUpdateServiceFactory(() -> service));

        runUpdateCheck(false, "stable");

        assertTrue(service.awaitCheck());
        settleFx();
        assertFalse(updateStatusLabel.isVisible());
        assertNoUpdateDialog();
    }

    @Test
    @DisplayName("auto-download installs in the background and reaches the restart state")
    void autoDownloadReachesReady() throws Exception {
        createFeature();
        preferences.put(UpdatePreferences.AUTO_DOWNLOAD, "true");
        preferences.put(UpdatePreferences.SNOOZE_UNTIL, String.valueOf(Long.MAX_VALUE));
        StubUpdateService service = new StubUpdateService(available());
        onFxThread(() -> {
            updateFeature.setUpdateServiceFactory(() -> service);
            updateFeature.setUpdateCoordinatorFactory(() -> new FakeCoordinator(true));
        });

        runUpdateCheck(false, "stable");

        assertTrue(service.awaitCheck());
        waitUntil(() -> updateStatusLabel.getText() != null
                && updateStatusLabel.getText().toLowerCase().contains("restart"));
        assertTrue(updateStatusLabel.isVisible());
        assertNoUpdateDialog();
    }

    @Test
    @DisplayName("the scheduled check runs when due and uses the configured channel")
    void scheduledCheckRunsWhenDue() throws Exception {
        assumeFalse("DEV".equalsIgnoreCase(AppVersion.current()));
        createFeature();
        preferences.put(UpdatePreferences.AUTO_CHECK, "true");
        preferences.put(UpdatePreferences.LAST_CHECK, "0");
        preferences.put(UpdatePreferences.SNOOZE_UNTIL, "0");
        preferences.put(UpdatePreferences.CHANNEL, "beta");
        StubUpdateService service = new StubUpdateService(UpdateCheckResult.upToDate("0.6.3", manifest(MANIFEST)));
        onFxThread(() -> updateFeature.setUpdateServiceFactory(() -> service));

        String disabled = System.getProperty("seeloggyplus.disableUpdateCheck");
        String channelOverride = System.getProperty("seeloggyplus.updateChannel");
        System.clearProperty("seeloggyplus.disableUpdateCheck");
        System.clearProperty("seeloggyplus.updateChannel");
        try {
            onFxThread(updateFeature::scheduleAutoUpdateCheck);
            assertTrue(service.awaitCheck(), "the scheduled check must consult the injected service");
            waitUntil(() -> preferences.getPreferencesByCode(UpdatePreferences.LAST_CHECK).isPresent());
            settleFx();
            assertEquals(List.of("beta"), service.channels());
            assertNotNull(privateField(updateFeature, "updateCheckTimeline"));
        } finally {
            restoreProperty("seeloggyplus.disableUpdateCheck", disabled);
            restoreProperty("seeloggyplus.updateChannel", channelOverride);
            stopTimeline();
        }
    }

    @Test
    @DisplayName("the update channel prefers the system override, then preferences, then the default")
    void channelResolutionOrder() throws Exception {
        createFeature();

        String previous = System.getProperty("seeloggyplus.updateChannel");
        System.setProperty("seeloggyplus.updateChannel", "beta");
        try {
            assertEquals("beta", invokePrivate(updateFeature, "currentUpdateChannel"));
        } finally {
            restoreProperty("seeloggyplus.updateChannel", previous);
        }

        preferences.put(UpdatePreferences.CHANNEL, "canary");
        assertEquals("canary", invokePrivate(updateFeature, "currentUpdateChannel"));

        preferences.put(UpdatePreferences.CHANNEL, "   ");
        assertEquals(UpdatePreferences.DEFAULT_CHANNEL, invokePrivate(updateFeature, "currentUpdateChannel"));

        assertEquals(UpdatePreferences.DEFAULT_CHANNEL,
                invokePrivate(new UpdateFeature(failingPreferences()), "currentUpdateChannel"));
    }

    @Test
    @DisplayName("the manifest url prefers the system override, then preferences, then the release default")
    void manifestUrlResolutionOrder() throws Exception {
        createFeature();

        String previous = System.getProperty("seeloggyplus.updateManifestUrl");
        System.setProperty("seeloggyplus.updateManifestUrl", "https://example.com/custom.json");
        try {
            assertEquals("https://example.com/custom.json", invokePrivate(updateFeature, "updateManifestUrl"));
        } finally {
            restoreProperty("seeloggyplus.updateManifestUrl", previous);
        }

        preferences.put(UpdatePreferences.MANIFEST_URL, "https://example.com/from-pref.json");
        assertEquals("https://example.com/from-pref.json", invokePrivate(updateFeature, "updateManifestUrl"));

        preferences.put(UpdatePreferences.MANIFEST_URL, " ");
        assertEquals(UpdateServiceImpl.DEFAULT_MANIFEST_URL, invokePrivate(updateFeature, "updateManifestUrl"));

        assertEquals(UpdateServiceImpl.DEFAULT_MANIFEST_URL,
                invokePrivate(new UpdateFeature(failingPreferences()), "updateManifestUrl"));
    }

    @Test
    @DisplayName("long preferences fall back when missing, invalid or unreadable")
    void longPreferenceFallback() throws Exception {
        UpdateFeature feature = new UpdateFeature(preferences);

        assertEquals(11L, invokePrivate(feature, "readLongPreference", UpdatePreferences.SNOOZE_UNTIL, 11L));

        preferences.put(UpdatePreferences.SNOOZE_UNTIL, "not-a-number");
        assertEquals(11L, invokePrivate(feature, "readLongPreference", UpdatePreferences.SNOOZE_UNTIL, 11L));

        preferences.put(UpdatePreferences.SNOOZE_UNTIL, "42");
        assertEquals(42L, invokePrivate(feature, "readLongPreference", UpdatePreferences.SNOOZE_UNTIL, 11L));

        UpdateFeature failing = new UpdateFeature(failingPreferences());
        assertEquals(11L, invokePrivate(failing, "readLongPreference", UpdatePreferences.SNOOZE_UNTIL, 11L));
    }

    @Test
    @DisplayName("auto-download defaults to enabled and survives unreadable preferences")
    void autoDownloadDefaultsToEnabled() throws Exception {
        UpdateFeature feature = new UpdateFeature(preferences);
        assertEquals(Boolean.TRUE, invokePrivate(feature, "isAutoDownloadEnabled"));

        preferences.put(UpdatePreferences.AUTO_DOWNLOAD, "FALSE");
        assertEquals(Boolean.FALSE, invokePrivate(feature, "isAutoDownloadEnabled"));

        UpdateFeature failing = new UpdateFeature(failingPreferences());
        assertEquals(Boolean.TRUE, invokePrivate(failing, "isAutoDownloadEnabled"));
    }

    @Test
    @DisplayName("startup reconciliation rolls a failed activation back to the previous version")
    void startupReconciliationRollsBack() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-update-feature-rollback");
        String installRoot = System.getProperty("seeloggyplus.installRoot");
        String disabled = System.getProperty("seeloggyplus.disableUpdateCheck");
        try {
            Path previousJar = root.resolve("versions").resolve("0.9.0").resolve("seeloggyplus.jar");
            Files.createDirectories(previousJar.getParent());
            Files.writeString(previousJar, "jar");
            Files.writeString(root.resolve("current"), "1.0.0");
            Files.writeString(root.resolve("current.previous"), "0.9.0");
            System.setProperty("seeloggyplus.installRoot", root.toString());
            System.clearProperty("seeloggyplus.disableUpdateCheck");

            new UpdateFeature(preferences).reconcileUpdateStartup();

            assertEquals(Optional.of("0.9.0"), new UpdateLayout(root).currentVersion());
        } finally {
            restoreProperty("seeloggyplus.installRoot", installRoot);
            restoreProperty("seeloggyplus.disableUpdateCheck", disabled);
            deleteRecursively(root);
        }
    }

    @Test
    @DisplayName("startup reconciliation leaves a fresh installation untouched")
    void startupReconciliationWithoutInstallation() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-update-feature-fresh");
        String installRoot = System.getProperty("seeloggyplus.installRoot");
        String disabled = System.getProperty("seeloggyplus.disableUpdateCheck");
        try {
            System.setProperty("seeloggyplus.installRoot", root.toString());
            System.clearProperty("seeloggyplus.disableUpdateCheck");

            new UpdateFeature(preferences).reconcileUpdateStartup();

            assertTrue(new UpdateLayout(root).currentVersion().isEmpty());
        } finally {
            restoreProperty("seeloggyplus.installRoot", installRoot);
            restoreProperty("seeloggyplus.disableUpdateCheck", disabled);
            deleteRecursively(root);
        }
    }

    @Test
    @DisplayName("the default coordinator factory targets the installation root and staging directory")
    void defaultCoordinatorFactoryTargetsInstallLayout() throws Exception {
        UpdateFeature feature = new UpdateFeature(preferences);
        Supplier<?> factory = (Supplier<?>) privateField(feature, "updateCoordinatorFactory");

        UpdateCoordinator coordinator = (UpdateCoordinator) factory.get();

        assertNotNull(coordinator);
        UpdateLayout layout = (UpdateLayout) privateField(coordinator, "layout");
        assertEquals(UpdateLayout.installationRoot(), layout.root());
        assertEquals(UpdateDialogController.stagingDirectory(), privateField(coordinator, "stagingDir"));
    }

    @Test
    @DisplayName("What's New is only considered for unguarded, unseen versions")
    void whatsNewSkipsGuardedAndSeenVersions() {
        UpdateFeature feature = new UpdateFeature(preferences);

        String disabled = System.getProperty("seeloggyplus.disableUpdateCheck");
        String dev = System.getProperty("seeloggyplus.dev");
        System.setProperty("seeloggyplus.disableUpdateCheck", "true");
        System.setProperty("seeloggyplus.dev", "true");
        try {
            feature.showWhatsNewIfNeeded();
        } finally {
            restoreProperty("seeloggyplus.disableUpdateCheck", disabled);
            restoreProperty("seeloggyplus.dev", dev);
        }
        assertNull(preferences.getPreferencesByCode(UpdatePreferences.SEEN_VERSION).orElse(null));

        String seen = AppVersion.current();
        preferences.put(UpdatePreferences.SEEN_VERSION, seen);
        System.clearProperty("seeloggyplus.disableUpdateCheck");
        System.clearProperty("seeloggyplus.dev");
        try {
            feature.showWhatsNewIfNeeded();
        } finally {
            restoreProperty("seeloggyplus.disableUpdateCheck", disabled);
            restoreProperty("seeloggyplus.dev", dev);
        }
        assertEquals(seen, preferences.getPreferencesByCode(UpdatePreferences.SEEN_VERSION).orElse(null));
        assertNoDialog("What's New");
    }

    private FakePreferenceService failingPreferences() {
        FakePreferenceService failing = new FakePreferenceService();
        failing.failEverything();
        return failing;
    }

    private void stopTimeline() {
        try {
            Object timeline = privateField(updateFeature, "updateCheckTimeline");
            if (timeline instanceof Timeline animation) {
                onFxThread(animation::stop);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object privateField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invokePrivate(Object target, String name, Object... args) throws Exception {
        Class<?>[] types = args.length == 2 && args[0] instanceof String && args[1] instanceof Long
                ? new Class<?>[] { String.class, long.class }
                : new Class<?>[0];
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static void assertNoUpdateDialog() {
        assertNoDialog("Software Update");
    }

    private static void assertNoDialog(String title) {
        AtomicBoolean showing = new AtomicBoolean(false);
        onFxThread(() -> showing.set(Window.getWindows().stream()
                .anyMatch(window -> window instanceof Stage stage && title.equals(stage.getTitle()) && stage.isShowing())));
        assertFalse(showing.get(), "no dialog titled '" + title + "' must be opened");
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            WaitForAsyncUtils.waitForFxEvents();
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(40);
        }
        fail("Condition was not met within timeout");
    }

    private static void settleFx() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void onFxThread(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }

    static class StubUpdateService implements UpdateService {

        private final UpdateCheckResult result;
        private final CountDownLatch checked = new CountDownLatch(1);
        private final List<String> channels = new CopyOnWriteArrayList<>();

        StubUpdateService(UpdateCheckResult result) {
            this.result = result;
        }

        @Override
        public UpdateCheckResult check(String channel) {
            channels.add(channel);
            checked.countDown();
            return result;
        }

        boolean awaitCheck() throws InterruptedException {
            return checked.await(5, TimeUnit.SECONDS);
        }

        List<String> channels() {
            return List.copyOf(channels);
        }
    }

    static class ExplodingUpdateService implements UpdateService {

        private final CountDownLatch checked = new CountDownLatch(1);

        @Override
        public UpdateCheckResult check(String channel) {
            checked.countDown();
            throw new IllegalStateException("update service offline");
        }

        boolean awaitCheck() throws InterruptedException {
            return checked.await(5, TimeUnit.SECONDS);
        }
    }

    static class FakeCoordinator extends UpdateCoordinator {

        private final boolean success;

        FakeCoordinator(boolean success) {
            super(new UpdateLayout(Path.of("build", "test-data", "update-feature-fixture")),
                    Path.of("build", "test-data", "update-feature-staging"),
                    new UpdateDownloader((url, offset) -> {
                        throw new IOException("offline test");
                    }),
                    new UpdateInstaller());
            this.success = success;
        }

        @Override
        public InstallResult install(UpdateAsset asset, String version, ProgressListener progress,
                BooleanSupplier cancelled) {
            if (progress != null) {
                progress.onProgress("Downloading", 50, 100);
                progress.onProgress("Installing", 0, 0);
            }
            if (cancelled != null) {
                cancelled.getAsBoolean();
            }
            return success
                    ? new InstallResult(true, false, version, "ok")
                    : new InstallResult(false, false, version, "boom");
        }
    }

    static class FakePreferenceService implements PreferenceService {

        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final List<String> failingReads = new CopyOnWriteArrayList<>();
        private final List<String> failingWrites = new CopyOnWriteArrayList<>();
        private volatile boolean failAll;

        void put(String code, String value) {
            values.put(code, value);
        }

        void failEverything() {
            failAll = true;
        }

        void failRead(String code) {
            failingReads.add(code);
        }

        void failWrite(String code) {
            failingWrites.add(code);
        }

        private void guardRead(String code) {
            if (failAll || failingReads.contains(code)) {
                throw new IllegalStateException("unreadable preference: " + code);
            }
        }

        private void guardWrite(String code) {
            if (failAll || failingWrites.contains(code)) {
                throw new IllegalStateException("unwritable preference: " + code);
            }
        }

        @Override
        public void savePreferences(Preference preference) {
            guardWrite(preference.getCode());
            values.put(preference.getCode(), preference.getValue());
        }

        @Override
        public void updatePreferences(Preference preference) {
            guardWrite(preference.getCode());
            values.put(preference.getCode(), preference.getValue());
        }

        @Override
        public Optional<String> getPreferencesByCode(String code) {
            guardRead(code);
            return Optional.ofNullable(values.get(code));
        }

        @Override
        public List<Preference> getListPreferences() {
            return values.entrySet().stream()
                    .map(entry -> new Preference(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public void saveOrUpdatePreferences(Preference preference) {
            guardWrite(preference.getCode());
            values.put(preference.getCode(), preference.getValue());
        }
    }
}
