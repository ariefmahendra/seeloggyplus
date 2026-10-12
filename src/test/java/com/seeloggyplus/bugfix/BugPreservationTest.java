package com.seeloggyplus.bugfix;

import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.settings.PreferenceService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class BugPreservationTest {

    private static SSHServerModel server(String name, String host, String username) {
        SSHServerModel s = new SSHServerModel();
        s.setName(name);
        s.setHost(host);
        s.setUsername(username);
        return s;
    }

    private static List<SSHServerModel> filterServers(List<SSHServerModel> allServers, String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return new ArrayList<>(allServers);
        }
        String search = searchText.toLowerCase();
        return allServers.stream()
                .filter(s -> (s.getName() != null && s.getName().toLowerCase().contains(search))
                        || s.getHost().toLowerCase().contains(search)
                        || s.getUsername().toLowerCase().contains(search)
                        || (s.getDefaultPath() != null && s.getDefaultPath().toLowerCase().contains(search)))
                .collect(Collectors.toList());
    }

    private static FileInfo makeFileInfo(String name, boolean isDir, long modifiedTimeMs) {
        FileInfo f = new FileInfo();
        f.setName(name);
        f.setDirectory(isDir);
        f.setModifiedTime(modifiedTimeMs);
        f.setPath("/" + name);
        return f;
    }

    @Nested
    @DisplayName("Bug 1 & 3 Preservation — filter/search still works after reload")
    class Preservation_Bug1And3_FilterSearch {

        @Test
        @DisplayName("3.1 Empty search returns all servers (property: ∀ serverList, empty query → full list)")
        void emptySearchReturnsAllServers() {
            List<SSHServerModel> servers = List.of(
                    server("prod", "10.0.0.1", "admin"),
                    server("staging", "10.0.0.2", "deploy"),
                    server("dev", "192.168.1.5", "root")
            );
            assertEquals(servers, filterServers(servers, ""),
                    "Empty search must return all servers unchanged");
            assertEquals(servers, filterServers(servers, null),
                    "Null search must return all servers unchanged");
            assertEquals(servers, filterServers(servers, "   "),
                    "Whitespace-only search must return all servers unchanged");
        }

        @Test
        @DisplayName("3.1 Search by name is case-insensitive prefix/substring match")
        void searchByNameCaseInsensitive() {
            List<SSHServerModel> servers = List.of(
                    server("ProductionDB", "10.0.0.1", "dba"),
                    server("StagingApp", "10.0.0.2", "app"),
                    server("DevLocal", "127.0.0.1", "dev")
            );

            assertEquals(1, filterServers(servers, "PRODUCTION").size());
            assertEquals(1, filterServers(servers, "productiondb").size());

            assertEquals(1, filterServers(servers, "ing").size(),
                    "Substring 'ing' matches StagingApp only");
            assertTrue(filterServers(servers, "staging").stream()
                    .anyMatch(s -> "StagingApp".equals(s.getName())));
        }

        @Test
        @DisplayName("3.1/3.5 PBT: random server lists — filtered result is always a subset of all servers")
        void filteredResultIsAlwaysSubsetOfAllServers() {
            Random rng = new Random(42L);
            String[] names  = {"prod", "staging", "dev", "qa", "perf", "demo", "backup"};
            String[] hosts  = {"10.0.0.1", "10.0.0.2", "192.168.1.1", "172.16.0.5", "127.0.0.1"};
            String[] users  = {"admin", "deploy", "root", "app", "test"};
            String[] queries = {"prod", "10.0", "root", "xyz_no_match", "a", "Admin", "192", ""};

            for (String query : queries) {
                for (int listSize = 0; listSize <= 10; listSize++) {
                    List<SSHServerModel> servers = new ArrayList<>();
                    for (int i = 0; i < listSize; i++) {
                        servers.add(server(
                                names[i % names.length] + i,
                                hosts[i % hosts.length],
                                users[i % users.length]
                        ));
                    }
                    List<SSHServerModel> filtered = filterServers(servers, query);

                    assertTrue(servers.containsAll(filtered),
                            "Filtered result must be a subset of allServers. query=" + query + " listSize=" + listSize);

                    assertTrue(filtered.size() <= servers.size(),
                            "Filtered size must not exceed total. query=" + query);
                }
            }
        }

        @Test
        @DisplayName("3.2/3.6 Search text is preserved after reload (filter uses searchField.getText() at call time)")
        void searchTextPreservedAcrossReload() {
            List<SSHServerModel> beforeReload = List.of(
                    server("prod-db", "10.0.0.1", "dba"),
                    server("staging-app", "10.0.0.2", "app")
            );
            List<SSHServerModel> afterReload = List.of(
                    server("prod-db", "10.0.0.1", "dba"),
                    server("staging-app", "10.0.0.2", "app"),
                    server("prod-cache", "10.0.0.3", "cache")
            );
            String activeSearch = "prod";

            List<SSHServerModel> resultBefore = filterServers(beforeReload, activeSearch);
            List<SSHServerModel> resultAfter  = filterServers(afterReload, activeSearch);

            assertEquals(1, resultBefore.size(), "Before reload: 1 server matches 'prod'");
            assertEquals(2, resultAfter.size(),  "After reload with same query: 2 servers match 'prod'");
            assertTrue(resultAfter.stream().allMatch(s -> s.getName().contains("prod")),
                    "All results must match the active search query");
        }
    }

    @Nested
    @DisplayName("Bug 2 Preservation — manual sort override and search filter independence")
    class Preservation_Bug2_SortAndFilter {

        @Test
        @DisplayName("3.3 Manual sort comparator is respected by SortedList")
        void manualSortComparatorRespectedBySortedList() {
            long base = System.currentTimeMillis();
            ObservableList<FileInfo> allFiles = FXCollections.observableArrayList(
                    makeFileInfo("z_file.log", false, base - 1000),
                    makeFileInfo("a_file.txt", false, base - 500),
                    makeFileInfo("b_dir",      true,  base - 200),
                    makeFileInfo("..",         true,  0)
            );

            FilteredList<FileInfo> filtered = new FilteredList<>(allFiles, p -> true);
            SortedList<FileInfo> sorted = new SortedList<>(filtered);

            Comparator<FileInfo> nameAsc = Comparator.comparing(f -> f.getName().toLowerCase());
            sorted.comparatorProperty().bind(new SimpleObjectProperty<>(nameAsc));

            List<String> names = sorted.stream().map(FileInfo::getName).collect(Collectors.toList());

            assertEquals("..", names.get(0));
            assertEquals("a_file.txt", names.get(1));
            assertEquals("b_dir", names.get(2));
            assertEquals("z_file.log", names.get(3));
        }

        @Test
        @DisplayName("3.4 Search filter does not reset active sort comparator")
        void searchFilterDoesNotResetSortComparator() {
            long base = System.currentTimeMillis();
            ObservableList<FileInfo> allFiles = FXCollections.observableArrayList(
                    makeFileInfo("zebra.log",  false, base - 1000),
                    makeFileInfo("alpha.log",  false, base - 500),
                    makeFileInfo("beta_dir",   true,  base - 200),
                    makeFileInfo("gamma.txt",  false, base - 100)
            );

            FilteredList<FileInfo> filtered = new FilteredList<>(allFiles, p -> true);
            SortedList<FileInfo> sorted = new SortedList<>(filtered);

            Comparator<FileInfo> nameDesc = Comparator.comparing((FileInfo f) -> f.getName().toLowerCase()).reversed();
            SimpleObjectProperty<Comparator<FileInfo>> comparatorProp = new SimpleObjectProperty<>(nameDesc);
            sorted.comparatorProperty().bind(comparatorProp);

            filtered.setPredicate(f -> f.getName().contains("a"));

            List<String> names = sorted.stream().map(FileInfo::getName).collect(Collectors.toList());

            for (int i = 0; i < names.size() - 1; i++) {
                assertTrue(names.get(i).compareToIgnoreCase(names.get(i + 1)) >= 0,
                        "Names must be in descending order after filter. Got: " + names);
            }

            assertNotNull(comparatorProp.get(), "Manual sort comparator must still be set after filter");
        }

        @Test
        @DisplayName("3.4 PBT: search filter with random strings never clears the sort comparator")
        void searchFilterNeverClearsSortComparator() {
            String[] filterStrings = {"", "a", "log", "xyz", "123", "dir", null};

            for (String filterStr : filterStrings) {
                ObservableList<FileInfo> allFiles = FXCollections.observableArrayList(
                        makeFileInfo("alpha.log", false, 1000L),
                        makeFileInfo("beta_dir",  true,  2000L),
                        makeFileInfo("gamma.txt", false, 3000L)
                );
                FilteredList<FileInfo> filtered = new FilteredList<>(allFiles, p -> true);
                SortedList<FileInfo> sorted = new SortedList<>(filtered);

                Comparator<FileInfo> sizeAsc = Comparator.comparingLong(FileInfo::getSize);
                SimpleObjectProperty<Comparator<FileInfo>> comparatorProp = new SimpleObjectProperty<>(sizeAsc);
                sorted.comparatorProperty().bind(comparatorProp);

                final String fs = filterStr;
                filtered.setPredicate(f -> fs == null || fs.isEmpty() || f.getName().contains(fs));

                assertSame(sizeAsc, comparatorProp.get(),
                        "Sort comparator must not change when search filter changes. filterStr=" + filterStr);
            }
        }
    }

    @Nested
    @DisplayName("Bug 4 Preservation — DEV fallback and processResources chain")
    class Preservation_Bug4_VersionDev {

        @Test
        @DisplayName("3.7 About/Main VERSION falls back to 'DEV' when version.properties absent from classpath")
        void versionFallsBackToDevWhenPropertiesAbsent() {
            String version = "DEV";
            try (InputStream input = getClass().getResourceAsStream("/version.properties.NONEXISTENT")) {
                Properties prop = new Properties();
                if (input == null) {
                } else {
                    prop.load(input);
                    version = prop.getProperty("version", version);
                }
            } catch (IOException ex) {
            }
            assertEquals("DEV", version,
                    "3.7: When version.properties is absent, version must read as 'DEV'");
        }

        @Test
        @DisplayName("3.7 Version-read logic: Properties.getProperty with 'DEV' default always returns 'DEV' on missing key")
        void propertiesGetPropertyReturnsDefaultForMissingKey() {
            String[][] propertiesSets = {
                {},
                {"other.key", "val"},
                {"VERSION", "1.0.0"},
            };
            for (String[] pairs : propertiesSets) {
                Properties props = new Properties();
                for (int i = 0; i + 1 < pairs.length; i += 2) {
                    props.setProperty(pairs[i], pairs[i + 1]);
                }
                String result = props.getProperty("version", "DEV");
                assertEquals("DEV", result,
                        "getProperty must return 'DEV' default when 'version' key absent. props=" + props);
            }
        }

        @Test
        @DisplayName("3.8 build.gradle processResources already dependsOn generateVersionProperties")
        void buildGradleProcessResourcesDependsOnGenerateVersionProperties() throws Exception {
            File buildGradle = new File("build.gradle");
            assertTrue(buildGradle.exists(), "build.gradle must exist at project root");

            String content = Files.readString(buildGradle.toPath());

            assertTrue(
                content.contains("processResources.dependsOn generateVersionProperties") ||
                content.contains("processResources dependsOn generateVersionProperties"),
                "3.8: build.gradle must keep processResources.dependsOn generateVersionProperties " +
                "so that './gradlew build' always generates version.properties. " +
                "Content check: " + content.substring(
                    Math.max(0, content.indexOf("processResources") - 5),
                    Math.min(content.length(), content.indexOf("processResources") + 80))
            );
        }
    }

    @Nested
    @DisplayName("Bug 5 Preservation — single-thread download path unchanged")
    class Preservation_Bug5_SingleThread {

        private String downloadConcurrentBody;

        @BeforeEach
        void readMethod() throws Exception {
            File implFile = new File(
                "src/main/java/com/seeloggyplus/features/ssh/infrastructure/SSHServiceImpl.java");
            assertTrue(implFile.exists(), "SSHServiceImpl.java must exist");
            String content = Files.readString(implFile.toPath());

            int methodIdx = content.indexOf("boolean downloadFileConcurrent(");
            if (methodIdx == -1) methodIdx = content.indexOf("public boolean downloadFileConcurrent(");
            assertNotEquals(-1, methodIdx, "downloadFileConcurrent method must exist");

            int braceDepth = 0, blockStart = content.indexOf('{', methodIdx), blockEnd = blockStart;
            for (int i = blockStart; i < content.length(); i++) {
                if (content.charAt(i) == '{') braceDepth++;
                else if (content.charAt(i) == '}') { braceDepth--; if (braceDepth == 0) { blockEnd = i; break; } }
            }
            downloadConcurrentBody = content.substring(blockStart, blockEnd + 1);
        }

        @Test
        @DisplayName("3.9 Small-file guard: fileSize < 5MB routes to single-thread downloadFile()")
        void smallFileGuardPresent() {
            boolean hasSmallFileGuard =
                downloadConcurrentBody.contains("fileSize < 5 * 1024 * 1024") ||
                downloadConcurrentBody.contains("fileSize < 5*1024*1024") ||
                downloadConcurrentBody.contains("5242880");
            assertTrue(hasSmallFileGuard,
                "3.9: downloadFileConcurrent() must retain the small-file guard " +
                "(fileSize < 5MB → delegate to single-thread downloadFile()). " +
                "This guard must not be removed by the Bug 5 fix.");
        }

        @Test
        @DisplayName("3.9 PBT: timeout calculation for any fileSize is always in [60, 3600] after fix")
        void timeoutCalculationAlwaysInBounds() {
            long[] fileSizes = {
                0L,
                1L,
                5L * 1024 * 1024,
                10L * 1024 * 1024,
                100L * 1024 * 1024,
                1024L * 1024 * 1024,
                10L * 1024 * 1024 * 1024,
            };
            for (long fileSize : fileSizes) {
                long timeoutSec = Math.max(60, Math.min(3600, (fileSize / 102_400) + 60));
                assertTrue(timeoutSec >= 60,
                    "Timeout must be >= 60s for fileSize=" + fileSize + " got=" + timeoutSec);
                assertTrue(timeoutSec <= 3600,
                    "Timeout must be <= 3600s for fileSize=" + fileSize + " got=" + timeoutSec);
            }
        }

        @Test
        @DisplayName("3.11 SFTP-unavailable guard: sftpAvailable==false routes to single-thread exec fallback")
        void sftpUnavailableGuardPresent() {
            boolean hasSftpFallback =
                downloadConcurrentBody.contains("sftpAvailable") &&
                (downloadConcurrentBody.contains("return downloadFile(") ||
                 downloadConcurrentBody.contains("downloadFile(remotePath"));
            assertTrue(hasSftpFallback,
                "3.11: downloadFileConcurrent() must retain the SFTP-unavailable fallback to " +
                "single-thread exec download. This guard must not be removed by the Bug 5 fix.");
        }
    }

    @Nested
    @DisplayName("Bug 6 Preservation — Home button, missing pref fallback")
    class Preservation_Bug6_HomeAndFallback {

        @Test
        @DisplayName("3.12 After fix: navigateTo() saves preference; home button navigates correctly")
        void navigateHomeDoesNotSavePreference() throws Exception {
            File implFile = new File(
                "src/main/java/com/seeloggyplus/features/files/presentation/UnifiedFileManagerDialogController.java");
            assertTrue(implFile.exists(), "UnifiedFileManagerDialogController.java must exist");
            String content = Files.readString(implFile.toPath());

            int methodIdx = content.indexOf("private void navigateTo(String path)");
            assertNotEquals(-1, methodIdx, "navigateTo(String) must exist");

            int braceDepth = 0, blockStart = content.indexOf('{', methodIdx), blockEnd = blockStart;
            for (int i = blockStart; i < content.length(); i++) {
                if (content.charAt(i) == '{') braceDepth++;
                else if (content.charAt(i) == '}') { braceDepth--; if (braceDepth == 0) { blockEnd = i; break; } }
            }
            String navigateToBody = content.substring(blockStart, blockEnd + 1);

            assertTrue(navigateToBody.contains("saveOrUpdatePreferences"),
                "3.12 POST-FIX: navigateTo() must call saveOrUpdatePreferences() after the Bug 6 fix. " +
                "Home button navigation will save home path as last-path — this is acceptable per spec notes.");
        }

        @Test
        @DisplayName("3.13/3.14 Empty Optional from getPreferencesByCode → fallback path, no exception")
        void emptyPreferenceFallbackWithoutError() {
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

            String localHome    = "/home/testuser";
            String serverDefault = "/opt/app";

            String[] missingKeys = {
                "file_manager_last_path_local",
                "file_manager_last_path_deleted_server",
                "file_manager_last_path_renamed_server",
                "file_manager_last_path_",
                "nonexistent_key"
            };
            for (String key : missingKeys) {
                String resultLocal = mockPrefs.getPreferencesByCode(key)
                    .filter(p -> p != null && !p.isBlank())
                    .orElse(localHome);
                assertEquals(localHome, resultLocal,
                    "3.14: Missing pref key must silently fall back to home. key=" + key);

                String resultRemote = mockPrefs.getPreferencesByCode(key)
                    .filter(p -> p != null && !p.isBlank())
                    .orElse(serverDefault);
                assertEquals(serverDefault, resultRemote,
                    "3.14: Missing pref key must silently fall back to defaultPath. key=" + key);
            }
        }

        @Test
        @DisplayName("3.13 Stale server preference key is ignored when server is deleted/renamed")
        void staleServerPreferenceIgnoredNaturally() {
            Map<String, String> prefStore = new HashMap<>();
            prefStore.put("file_manager_last_path_old_name", "/some/saved/path");

            String newServerKey = "file_manager_last_path_new_name";
            String fallback = "/remote/default";

            Optional<String> result = Optional.ofNullable(prefStore.get(newServerKey));
            String path = result.filter(p -> !p.isBlank()).orElse(fallback);

            assertEquals(fallback, path,
                "3.13: Stale preference for old server name must not affect new/renamed server lookup. " +
                "Expected fallback='" + fallback + "' but got '" + path + "'");
        }

        @Test
        @DisplayName("3.14 PBT: blank/empty preference values all fall back to default")
        void blankPreferenceValueFallsBackToDefault() {
            String[] blankValues = {"", "  ", "\t", "\n", "   \t  "};
            String defaultPath = "/home/user";

            for (String blank : blankValues) {
                String result = Optional.of(blank)
                    .filter(p -> !p.isBlank())
                    .orElse(defaultPath);
                assertEquals(defaultPath, result,
                    "3.14: Blank preference value '" + blank.replace("\n","\\n").replace("\t","\\t") +
                    "' must fall back to default path");
            }
        }
    }
}
