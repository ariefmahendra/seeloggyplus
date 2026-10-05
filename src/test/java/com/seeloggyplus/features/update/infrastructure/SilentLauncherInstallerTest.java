package com.seeloggyplus.features.update.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The silent Windows launcher is recreated by the app instead of shipped in
 * update packages (older installers reject unknown package entries, which broke
 * self-update from older releases).
 */
class SilentLauncherInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("creates SeeLoggyPlus.vbs in the installation root when missing")
    void createsLauncherWhenMissing() throws Exception {
        Path root = tempDir.resolve("install");

        Path created = SilentLauncherInstaller.ensure(root);

        assertEquals(root.resolve("SeeLoggyPlus.vbs"), created);
        assertTrue(Files.isRegularFile(created));
        String content = Files.readString(created);
        assertTrue(content.contains("WScript.Shell"), "must start the app hidden");
        assertTrue(content.contains("launcher.bat"), "must delegate to launcher.bat");
        assertTrue(content.contains("shell.Run command, 0, False"), "window style 0 = hidden");
        assertTrue(content.contains("\r\n"), "Windows script should use CRLF");
    }

    @Test
    @DisplayName("never overwrites an existing (possibly customised) launcher")
    void keepsExistingLauncher() throws Exception {
        Path root = tempDir.resolve("install");
        Files.createDirectories(root);
        Path existing = root.resolve("SeeLoggyPlus.vbs");
        Files.writeString(existing, "custom");

        Path result = SilentLauncherInstaller.ensure(root);

        assertEquals(existing, result);
        assertEquals("custom", Files.readString(existing));
    }
}
