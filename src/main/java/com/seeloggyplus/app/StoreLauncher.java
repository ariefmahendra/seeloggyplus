package com.seeloggyplus.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class StoreLauncher {

    private StoreLauncher() {
    }

    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
        Path data = base.resolve("SeeLoggyPlus/Store").toAbsolutePath();
        Path jar = Path.of(StoreLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.createDirectories(data.resolve("logs"));
        new ProcessBuilder(command(Path.of(System.getProperty("java.home")), jar, data, args))
                .directory(data.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(data.resolve("logs/launcher.log").toFile()))
                .start();
    }

    static List<String> command(Path runtime, Path jar, Path data, String[] args) throws IOException {
        Properties settings = new Properties();
        Path config = data.resolve("launcher.properties");
        if (Files.isRegularFile(config)) {
            try (var input = Files.newInputStream(config)) {
                settings.load(input);
            } catch (IllegalArgumentException e) {
                settings.clear();
            }
        }
        int memory = 4;
        try {
            int requested = Integer.parseInt(settings.getProperty("max.memory.gb", "4").trim());
            if (requested >= 1 && requested <= 16) memory = requested;
        } catch (NumberFormatException ignored) {
        }
        var command = new ArrayList<>(List.of(runtime.resolve("bin/javaw.exe").toString(),
                "-Xmx" + memory + "g", "-Dseeloggyplus.distribution=store",
                "-Dseeloggyplus.dataDir=" + data,
                "-Dseeloggyplus.launcherProperties=" + config,
                "-Dseeloggyplus.logDir=" + data.resolve("logs"),
                "-cp", jar.toString(), "com.seeloggyplus.app.Launcher"));
        command.addAll(Arrays.asList(args));
        return command;
    }
}
