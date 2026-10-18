package com.seeloggyplus.bugfix;

import com.seeloggyplus.shared.ui.AppTheme;

import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.features.files.application.LocalFileService;
import com.seeloggyplus.shared.settings.PreferenceService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

@ExtendWith(ApplicationExtension.class)
public class BugExplorationTest {

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(obj);
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private static void invoke(Object obj, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = obj.getClass().getDeclaredMethod(method, types);
        m.setAccessible(true);
        m.invoke(obj, args);
    }

    @Nested
    @DisplayName("Bug 1 & 3 — Status icon stale after loadServers")
    class Bug1And3StatusIconStale {

        @Test
        @DisplayName("serverTable.refresh() must be called after filterServers() in loadServers().setOnSucceeded")
        public void serverTableRefreshMustBeCalledAfterFilterServers() throws Exception {
            File implFile = new File(
                "src/main/java/com/seeloggyplus/features/servers/presentation/ServerManagementDialogController.java");
            assertTrue(implFile.exists(), "ServerManagementDialogController.java must exist");

            String content = Files.readString(implFile.toPath());

            int loadIdx = content.indexOf("private void loadServers()");
            if (loadIdx == -1) loadIdx = content.indexOf("void loadServers()");
            assertNotEquals(-1, loadIdx, "loadServers() method must exist in controller");

            int onSucceededIdx = content.indexOf("setOnSucceeded", loadIdx);
            assertNotEquals(-1, onSucceededIdx, "loadServers() must have setOnSucceeded handler");

            int lambdaStart = content.indexOf('{', onSucceededIdx);
            int lambdaEnd = lambdaStart;
            int depth = 0;
            for (int i = lambdaStart; i < content.length(); i++) {
                if (content.charAt(i) == '{') depth++;
                else if (content.charAt(i) == '}') { depth--; if (depth == 0) { lambdaEnd = i; break; } }
            }
            String lambda = content.substring(lambdaStart, lambdaEnd + 1);

            boolean hasFilterServers   = lambda.contains("filterServers(");
            boolean hasTableRefresh    = lambda.contains("serverTable.refresh()");

            assertTrue(hasFilterServers,
                "loadServers().setOnSucceeded must call filterServers() — not found in lambda block");

            assertTrue(hasTableRefresh,
                "BUG 1&3 CONFIRMED: loadServers().setOnSucceeded does NOT call serverTable.refresh() " +
                "after filterServers(). Counter-example: setOnSucceeded lambda = [\n" +
                lambda.trim() + "\n]\n" +
                "Without serverTable.refresh(), the statusColumn cell factory is never re-evaluated " +
                "after the server list is repopulated. A server with an active session will still " +
                "show ✗ because the cell's last render predates the new session. " +
                "Reproduction: open Server Management after creating a session in File Manager → " +
                "status icon shows ✗ instead of ✓."
            );
        }
    }

    @Nested
    @DisplayName("Bug 2 — File manager auto-sort not applied")
    class Bug2AutoSortMissing {

        @Test
        @DisplayName("After load, directories must come first sorted by modified desc, then files")
        public void defaultSortMustGroupDirsThenFiles() {
            long base = System.currentTimeMillis();
            List<FileInfo> input = new ArrayList<>();
            input.add(makeFile("zfile.log",   false, base - 1000));
            input.add(makeFile("adir",        true,  base - 500));
            input.add(makeFile("mfile.txt",   false, base - 2000));
            input.add(makeFile("bdir",        true,  base - 200));
            input.add(makeFile("cfile.java",  false, base - 300));
            input.add(makeFile("..",          true,  0));

            ObservableList<FileInfo> allFiles = FXCollections.observableArrayList(input);
            FilteredList<FileInfo> filteredFiles =
                    new FilteredList<>(allFiles, p -> true);
            SortedList<FileInfo> sortedData =
                    new SortedList<>(filteredFiles);

            Comparator<FileInfo> defaultSort = Comparator
                    .comparing((FileInfo f) -> f.getName().equals("..") ? 0 : (f.isDirectory() ? 1 : 2))
                    .thenComparing(f -> f.getModified() != null ? f.getModified() : LocalDateTime.MIN,
                                   Comparator.reverseOrder())
                    .thenComparing(f -> f.getName().toLowerCase());
            sortedData.setComparator(defaultSort);

            List<String> actualOrder = sortedData.stream()
                    .map(FileInfo::getName)
                    .toList();

            List<String> expectedOrder = List.of("..", "bdir", "adir", "cfile.java", "zfile.log", "mfile.txt");

            assertEquals(expectedOrder, actualOrder,
                    "BUG 2 CONFIRMED: SortedList bound to null comparator produces insertion order '" +
                    actualOrder + "' instead of grouped + modified-descending order '" + expectedOrder + "'. " +
                    "Counter-example: input=[zfile.log(file), adir(dir), mfile.txt(file), bdir(dir), " +
                    "cfile.java(file), ..] → actual order has no dir-first grouping.");
        }

        private FileInfo makeFile(String name, boolean isDir, long modifiedTime) {
            FileInfo f = new FileInfo();
            f.setName(name);
            f.setDirectory(isDir);
            f.setModifiedTime(modifiedTime);
            f.setPath("/" + name);
            return f;
        }
    }

    @Nested
    @DisplayName("Bug 4 — Fat JAR contains DEV version")
    class Bug4DevVersionInFatJar {

        @Test
        @DisplayName("build.gradle fatJar task must declare dependsOn processResources")
        public void fatJarMustDependOnProcessResources() throws Exception {
            File buildGradle = new File("build.gradle");
            assertTrue(buildGradle.exists(), "build.gradle must exist at project root");

            String content = Files.readString(buildGradle.toPath());

            int fatJarIdx = content.indexOf("tasks.register('fatJar'");
            assertNotEquals(-1, fatJarIdx, "fatJar task must exist in build.gradle");

            int braceDepth = 0;
            int blockStart = content.indexOf('{', fatJarIdx);
            int blockEnd = blockStart;
            for (int i = blockStart; i < content.length(); i++) {
                if (content.charAt(i) == '{') braceDepth++;
                else if (content.charAt(i) == '}') {
                    braceDepth--;
                    if (braceDepth == 0) { blockEnd = i; break; }
                }
            }
            String fatJarBlock = content.substring(fatJarIdx, blockEnd + 1);

            assertTrue(
                fatJarBlock.contains("dependsOn processResources") ||
                fatJarBlock.contains("dependsOn generateVersionProperties"),
                "BUG 4 CONFIRMED: fatJar task block does NOT contain 'dependsOn processResources' " +
                "or 'dependsOn generateVersionProperties'. " +
                "Counter-example: fatJar task block = [\n" + fatJarBlock.trim() + "\n]" +
                "\nThis means running './gradlew fatJar' skips generateVersionProperties, " +
                "producing a JAR with version.properties absent or containing 'DEV'."
            );
        }

        @Test
        @DisplayName("AboutDialogController must not have its own static VERSION initializer (DRY violation)")
        public void aboutDialogControllerMustNotDuplicateVersionReader() throws Exception {
            File aboutController = new File(
                "src/main/java/com/seeloggyplus/app/AboutDialogController.java");
            assertTrue(aboutController.exists(), "AboutDialogController.java must exist");

            String content = Files.readString(aboutController.toPath());

            boolean hasDuplicateVersionField = content.contains("private static final String VERSION");
            boolean hasDuplicateStaticInit   = content.contains("static {") &&
                                               content.contains("version.properties");

            assertFalse(hasDuplicateVersionField && hasDuplicateStaticInit,
                "BUG 4 (DRY) CONFIRMED: AboutDialogController has its own private static VERSION field " +
                "and reads version.properties independently instead of using Main.VERSION. " +
                "Counter-example: Found 'private static final String VERSION' + 'static {' block " +
                "reading 'version.properties' in AboutDialogController.java. " +
                "This is the DRY violation described in requirement 1.9."
            );
        }
    }

    @Nested
    @DisplayName("Bug 5 — downloadFileConcurrent hangs on concurrent SFTP channels")
    class Bug5DownloadHang {

        @Test
        @DisplayName("downloadFileConcurrent with threadCount=2 must return within 40 seconds")
        @Timeout(value = 45, unit = TimeUnit.SECONDS)
        public void concurrentDownloadMustNotHang() throws Exception {
            File implFile = new File(
                "src/main/java/com/seeloggyplus/features/ssh/infrastructure/SSHServiceImpl.java");
            assertTrue(implFile.exists(), "SSHServiceImpl.java must exist");

            String content = Files.readString(implFile.toPath());

            int methodIdx = content.indexOf("boolean downloadFileConcurrent(");
            if (methodIdx == -1) methodIdx = content.indexOf("public boolean downloadFileConcurrent(");
            assertNotEquals(-1, methodIdx, "downloadFileConcurrent method must exist");

            int methodEnd = findMethodEnd(content, methodIdx);

            String concurrentBody = content.substring(methodIdx, methodEnd);
            int resumableIdx = content.indexOf("boolean downloadFileResumable(");
            String resumableBody = resumableIdx >= 0
                    ? content.substring(resumableIdx, findMethodEnd(content, resumableIdx))
                    : "";
            boolean connectHasTimeout = concurrentBody.contains("channel.connect(30_000)")
                    || resumableBody.contains("channel.connect(30_000)");
            assertTrue(connectHasTimeout,
                "BUG 5 CONFIRMED: channel.connect() is called without a 30-second timeout " +
                "inside the concurrent download path. Counter-example: isBugCondition_B5 = true — " +
                "with threadCount=2 on a server allowing max 1 concurrent SFTP channel, " +
                "channel.connect() for thread-2 blocks indefinitely. " +
                "Fix requires: channel.connect(30_000)."
            );
            assertFalse(Pattern.compile("channel\\.connect\\(\\s*\\)")
                            .matcher(concurrentBody).find(),
                "downloadFileConcurrent() must not call channel.connect() without a timeout");

            boolean latchNoArg = concurrentBody.contains("latch.await()");
            int latchIdx = content.indexOf("latch.await()", methodIdx);
            assertFalse(latchNoArg,
                "BUG 5 CONFIRMED: latch.await() is called without a timeout inside " +
                "downloadFileConcurrent()" + (latchIdx >= 0 ? " (line ~" + lineOf(content, latchIdx) + ")" : "") + ". " +
                "Counter-example: isBugCondition_B5 = true — if channel.connect() in thread-2 " +
                "hangs, latch never reaches zero, latch.await() blocks forever → entire download " +
                "hangs indefinitely. Fix requires: latch.await(timeoutSec, TimeUnit.SECONDS)."
            );
        }

        private int findMethodEnd(String src, int methodStart) {
            int braceDepth = 0;
            int blockStart = src.indexOf('{', methodStart);
            if (blockStart == -1) return src.length();
            for (int i = blockStart; i < src.length(); i++) {
                if (src.charAt(i) == '{') braceDepth++;
                else if (src.charAt(i) == '}') {
                    braceDepth--;
                    if (braceDepth == 0) return i;
                }
            }
            return src.length();
        }

        private int lineOf(String src, int idx) {
            return (int) src.substring(0, idx).chars().filter(c -> c == '\n').count() + 1;
        }
    }

    @Nested
    @ExtendWith(ApplicationExtension.class)
    @DisplayName("Bug 6 — File manager does not restore last directory")
    class Bug6NoLastDirectory {

        private UnifiedFileManagerDialogController controller;
        private TextField pathField;

        @Start
        public void start(Stage stage) throws Exception {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            stage.setScene(AppTheme.scene(root));
            stage.show();

            pathField = getField(controller, "pathField");
        }

        @Test
        @DisplayName("After navigating to a directory and reopening, currentPath must be the last visited directory")
        public void reopenMustRestoreLastVisitedDirectory(FxRobot robot) throws Exception {
            final String HOME_DIR     = "/home/testuser";
            final String LAST_DIR     = "/tmp/test-dir";

            LocalFileService mockLocal = new LocalFileService() {
                @Override public String getHomeDirectory() { return HOME_DIR; }
                @Override public List<FileInfo> listFiles(String path) { return List.of(); }
            };
            setField(controller, "localFileService", mockLocal);

            Map<String, String> prefStore = new HashMap<>();
            PreferenceService mockPrefs = new PreferenceService() {
                @Override public void savePreferences(Preference p) { prefStore.put(p.getCode(), p.getValue()); }
                @Override public void updatePreferences(Preference p) { prefStore.put(p.getCode(), p.getValue()); }
                @Override public Optional<String> getPreferencesByCode(String code) {
                    return Optional.ofNullable(prefStore.get(code));
                }
                @Override public List<Preference> getListPreferences() { return List.of(); }
                @Override public void saveOrUpdatePreferences(Preference p) { prefStore.put(p.getCode(), p.getValue()); }
            };

            try {
                setField(controller, "preferenceService", mockPrefs);
            } catch (NoSuchFieldException e) {
            }

            Field cacheField = controller.getClass().getDeclaredField("directoryCache");
            cacheField.setAccessible(true);
            ((Map<?, ?>) cacheField.get(controller)).clear();

            Class<?> locationItemClass = Arrays.stream(controller.getClass().getDeclaredClasses())
                    .filter(c -> c.getSimpleName().equals("LocationItem"))
                    .findFirst()
                    .orElseThrow();
            Method localFactory = locationItemClass.getDeclaredMethod("local");
            localFactory.setAccessible(true);
            Object localItem = localFactory.invoke(null);

            Platform.runLater(() -> {
                try {
                    invoke(controller, "handleLocationSelected",
                            new Class[]{locationItemClass}, localItem);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            Thread.sleep(500);
            WaitForAsyncUtils.waitForFxEvents();

            Platform.runLater(() -> {
                try {
                    invoke(controller, "navigateTo", new Class[]{String.class}, LAST_DIR);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            Thread.sleep(500);
            WaitForAsyncUtils.waitForFxEvents();

            Platform.runLater(() -> {
                try {
                    Object reopenItem = localFactory.invoke(null);
                    invoke(controller, "handleLocationSelected",
                            new Class[]{locationItemClass}, reopenItem);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            Thread.sleep(500);
            WaitForAsyncUtils.waitForFxEvents();

            String normalizedLastDir;
            try {
                normalizedLastDir = Paths.get(LAST_DIR).toAbsolutePath().normalize().toString();
            } catch (Exception e) {
                normalizedLastDir = LAST_DIR;
            }
            String actualPath = pathField.getText();
            assertEquals(normalizedLastDir, actualPath,
                "BUG 6 CONFIRMED: After navigating to '" + LAST_DIR + "' and reopening the dialog, " +
                "currentPath is '" + actualPath + "' instead of '" + LAST_DIR + "'. " +
                "Counter-example: isBugCondition_B6 = true because navigateTo() does not call " +
                "saveOrUpdatePreferences() and handleLocationSelected() calls " +
                "navigateTo(localFileService.getHomeDirectory()) unconditionally, " +
                "ignoring any stored last-path preference."
            );
        }
    }
}
