package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the launcher scripts against losing blue/green layout support. */
class LauncherScriptsTest {

    @Test
    void windowsLauncherIsVersionAware() throws Exception {
        Path script = Path.of("launcher.bat");
        assumeTrue(Files.exists(script), "launcher.bat not present in working dir");
        String content = Files.readString(script);
        assertTrue(content.contains("versions"), "launcher.bat must look inside versions/");
        assertTrue(content.contains("current"), "launcher.bat must read the current pointer");
        assertTrue(content.contains("--apply-update"), "launcher.bat must support --apply-update");
    }

    @Test
    void unixLauncherIsVersionAware() throws Exception {
        Path script = Path.of("launcher.sh");
        assumeTrue(Files.exists(script), "launcher.sh not present in working dir");
        String content = Files.readString(script);
        assertTrue(content.contains("versions"), "launcher.sh must look inside versions/");
        assertTrue(content.contains("current"), "launcher.sh must read the current pointer");
        assertTrue(content.contains("--apply-update"), "launcher.sh must support --apply-update");
    }
}
