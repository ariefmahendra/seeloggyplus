package com.seeloggyplus.features.update.infrastructure;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowsLauncherTest {

    @Test void distributionIncludesHiddenEntryPointAndExplicitDiagnostics() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));

        assertFalse(build.contains("from('SeeLoggyPlus.vbs')"),
                "SeeLoggyPlus.vbs must not be part of portable/update packages");
        assertTrue(Files.readString(Path.of("src/main/java/com/seeloggyplus/app/Main.java"))
                .contains("SilentLauncherInstaller.ensure"),
                "startup must self-heal the silent launcher");

        String installer = Files.readString(
                Path.of("src/main/java/com/seeloggyplus/features/update/infrastructure/SilentLauncherInstaller.java"));
        assertTrue(installer.contains("WScript.Shell"));
        assertTrue(installer.contains("shell.Run command, 0, False"));
        assertTrue(installer.contains("ScriptFullName"));

        String entry = Files.readString(Path.of("SeeLoggyPlus.vbs"));
        assertTrue(entry.contains("shell.Run command, 0, False"));
        assertTrue(entry.contains("WScript.ScriptFullName"));
        String launcher = Files.readString(Path.of("launcher.bat"));
        assertTrue(launcher.contains("--console"));
        assertTrue(launcher.contains("javaw.exe"));
        assertTrue(launcher.contains("--apply-update"));
        assertTrue(launcher.contains("versions"));
    }
}
