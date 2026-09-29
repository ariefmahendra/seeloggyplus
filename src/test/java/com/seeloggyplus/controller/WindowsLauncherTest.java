package com.seeloggyplus.controller;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WindowsLauncherTest {
    @Test void distributionIncludesHiddenEntryPointAndExplicitDiagnostics() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("from('SeeLoggyPlus.vbs')"));
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
