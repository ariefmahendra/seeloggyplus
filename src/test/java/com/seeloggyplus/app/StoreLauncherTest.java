package com.seeloggyplus.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoreLauncherTest {

    @TempDir
    Path temporary;

    @Test
    void launchesBundledJavaWithWritableDataAndSavedMemoryLimit() throws Exception {
        Path data = temporary.resolve("user data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("launcher.properties"), "max.memory.gb=6\n");
        Path runtime = temporary.resolve("runtime");
        Path jar = temporary.resolve("app/seeloggyplus-all.jar");

        List<String> command = StoreLauncher.command(runtime, jar, data, new String[]{"a log.txt"});

        assertEquals(runtime.resolve("bin/javaw.exe").toString(), command.getFirst());
        assertTrue(command.contains("-Xmx6g"));
        assertTrue(command.contains("-Dseeloggyplus.dataDir=" + data));
        assertTrue(command.contains("-Dseeloggyplus.launcherProperties=" + data.resolve("launcher.properties")));
        assertTrue(command.contains("-Dseeloggyplus.logDir=" + data.resolve("logs")));
        assertTrue(command.contains("-Dseeloggyplus.distribution=store"));
        assertEquals(List.of("-cp", jar.toString(), "com.seeloggyplus.app.Launcher", "a log.txt"),
                command.subList(command.indexOf("-cp"), command.size()));
    }

    @Test
    void missingOrInvalidMemorySettingsUseThePortableDefault() throws Exception {
        Path runtime = temporary.resolve("runtime");
        Path jar = temporary.resolve("app.jar");
        assertTrue(StoreLauncher.command(runtime, jar, temporary, new String[0]).contains("-Xmx4g"));
        for (String value : List.of("0", "17", "-1", "bad", "4 -Dunsafe=true")) {
            Files.writeString(temporary.resolve("launcher.properties"), "max.memory.gb=" + value);
            assertTrue(StoreLauncher.command(runtime, jar, temporary, new String[0]).contains("-Xmx4g"), value);
        }
    }
}
