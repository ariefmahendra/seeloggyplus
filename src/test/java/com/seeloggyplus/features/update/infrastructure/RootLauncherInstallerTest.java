package com.seeloggyplus.features.update.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RootLauncherInstallerTest {

    private static final String LEGACY_LAUNCHER = """
            @echo off
            "!JAVA_CMD!" -Xmx%MAX_MEM%g -jar "!APP_JAR!"
            endlocal
            """;

    private static final String JAVAW_LAUNCHER = """
            @echo off
            set "JAVA_CMD=!JAVA_CMD:java.exe=javaw.exe!"
            if "!JAVA_CMD!"=="java" set "JAVA_CMD=javaw"
            start "" "!JAVA_CMD!" -Xmx%MAX_MEM%g -jar "!APP_JAR!"
            endlocal
            """;

    @TempDir
    Path tempDir;

    @Test void repairsKnownLegacyParserWithoutChangingUserConfiguration() throws Exception {
        String packaged = Files.readString(Path.of("launcher.bat"));
        Path root = installWithStagedLauncher(packaged);
        Files.writeString(root.resolve("launcher.bat"), JAVAW_LAUNCHER + "set VALUE=!VALUE: =!\n");
        Files.writeString(root.resolve("launcher.properties"), "max.memory.gb=2\njava.home=C:\\Program Files\\Java\n");
        assertTrue(RootLauncherInstaller.heal(root));
        assertEquals(packaged, Files.readString(root.resolve("launcher.bat")));
        assertEquals("max.memory.gb=2\njava.home=C:\\Program Files\\Java\n", Files.readString(root.resolve("launcher.properties")));
        assertFalse(RootLauncherInstaller.heal(root));
    }

    @Test void repairsKnownUnixLauncherAndPreservesCustomScripts() throws Exception {
        Path root = installWithStagedLauncher(JAVAW_LAUNCHER);
        String packaged = Files.readString(Path.of("launcher.sh"));
        Files.writeString(root.resolve("versions/1.0.0/launcher.sh"), packaged);
        Files.writeString(root.resolve("launcher.sh"), "SCRIPT_DIR=old\nAPP_JAR=old\n");
        assertTrue(RootLauncherInstaller.healUnix(root));
        assertEquals(packaged, Files.readString(root.resolve("launcher.sh")));
        assertFalse(RootLauncherInstaller.healUnix(root));
        Files.writeString(root.resolve("launcher.sh"), "exec custom-launcher\n");
        assertFalse(RootLauncherInstaller.healUnix(root));
        assertEquals("exec custom-launcher\n", Files.readString(root.resolve("launcher.sh")));
    }

    @Test
    @DisplayName("replaces a legacy foreground launcher with the staged javaw launcher")
    void healsLegacyLauncher() throws Exception {
        Path root = installWithStagedLauncher(JAVAW_LAUNCHER);
        Path target = root.resolve("launcher.bat");
        Files.writeString(target, LEGACY_LAUNCHER);

        assertTrue(RootLauncherInstaller.heal(root));

        assertEquals(JAVAW_LAUNCHER, Files.readString(target));
    }

    @Test
    @DisplayName("keeps a launcher that already starts the app with javaw")
    void keepsConsoleFreeLauncher() throws Exception {
        Path root = installWithStagedLauncher(JAVAW_LAUNCHER);
        Path target = root.resolve("launcher.bat");
        String custom = "@echo off\r\nREM customised\r\nstart \"\" javaw -jar app.jar\r\n";
        Files.writeString(target, custom);

        assertFalse(RootLauncherInstaller.heal(root));

        assertEquals(custom, Files.readString(target));
    }

    @Test
    @DisplayName("creates the root launcher when it is missing")
    void createsMissingLauncher() throws Exception {
        Path root = installWithStagedLauncher(JAVAW_LAUNCHER);

        assertTrue(RootLauncherInstaller.heal(root));

        assertEquals(JAVAW_LAUNCHER, Files.readString(root.resolve("launcher.bat")));
    }

    @Test
    @DisplayName("does nothing when the active version has no staged launcher")
    void noOpWithoutStagedLauncher() throws Exception {
        Path root = tempDir.resolve("install");
        Files.createDirectories(root.resolve("versions/1.0.0"));
        Files.writeString(root.resolve("current"), "1.0.0");
        Files.writeString(root.resolve("launcher.bat"), LEGACY_LAUNCHER);

        assertFalse(RootLauncherInstaller.heal(root));

        assertEquals(LEGACY_LAUNCHER, Files.readString(root.resolve("launcher.bat")));
    }

    @Test
    @DisplayName("never copies a staged launcher that still uses java.exe in the foreground")
    void ignoresLegacyStagedLauncher() throws Exception {
        Path root = installWithStagedLauncher(LEGACY_LAUNCHER);
        Path target = root.resolve("launcher.bat");
        Files.writeString(target, LEGACY_LAUNCHER);

        assertFalse(RootLauncherInstaller.heal(root));

        assertEquals(LEGACY_LAUNCHER, Files.readString(target));
    }

    private Path installWithStagedLauncher(String launcherContent) throws Exception {
        Path root = tempDir.resolve("install");
        Path version = root.resolve("versions/1.0.0");
        Files.createDirectories(version);
        Files.writeString(root.resolve("current"), "1.0.0");
        Files.writeString(version.resolve("launcher.bat"), launcherContent);
        return root;
    }
}
