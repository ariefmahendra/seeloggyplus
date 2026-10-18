package com.seeloggyplus.features.update.infrastructure;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherScriptsTest {
    @org.junit.jupiter.api.io.TempDir Path temporary;

    @Test void installedLauncherHandlesEmptyAndSpacedJavaPathsFromAnotherDirectory() throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        Path install = Files.createDirectory(temporary.resolve("portable app with spaces"));
        String script = windows ? "launcher.bat" : "launcher.sh";
        Files.copy(Path.of(script), install.resolve(script));
        var manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", Probe.class.getName());
        String entry = Probe.class.getName().replace('.', '/') + ".class";
        try (var jar = new java.util.jar.JarOutputStream(Files.newOutputStream(install.resolve("seeloggyplus.jar")), manifest);
             var input = getClass().getResourceAsStream("/" + entry)) {
            org.junit.jupiter.api.Assertions.assertNotNull(input);
            jar.putNextEntry(new java.util.jar.JarEntry(entry)); input.transferTo(jar); jar.closeEntry();
        }
        for (String home : new String[] {"", System.getProperty("java.home")}) {
            Files.writeString(install.resolve("launcher.properties"), "max.memory.gb= 1 \njava.home=" + home + "\n");
            ProcessBuilder command = windows
                    ? new ProcessBuilder("cmd.exe", "/c", "call", install.resolve(script).toString(), "--console")
                    : new ProcessBuilder("bash", install.resolve(script).toString());
            command.directory(temporary.toFile()).redirectErrorStream(true);
            command.environment().put("JAVA_HOME", System.getProperty("java.home"));
            Process process = command.start();
            try {
                assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS), "launcher process must finish");
                String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                org.junit.jupiter.api.Assertions.assertEquals(0, process.exitValue(), output);
                assertTrue(output.contains("PROBE:" + install.toAbsolutePath()), output);
            } finally { if (process.isAlive()) process.destroyForcibly().waitFor(); }
        }
    }

    public static class Probe {
        public static void main(String[] args) {
            System.out.println("PROBE:" + Path.of("").toAbsolutePath());
        }
    }

    @Test void unixLauncherResolvesDataAndConfigurationFromItsInstallation() throws Exception {
        String content = Files.readString(Path.of("launcher.sh"));
        int directory = content.indexOf("cd \"$SCRIPT_DIR\"");
        assertTrue(directory >= 0, "launcher must change directory before looking up relative data");
        assertTrue(directory < content.indexOf("CONFIG_FILE="));
    }

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
    void bundledJreIncludesHttpClientModule() throws Exception {
        Path buildFile = Path.of("build.gradle");
        assumeTrue(Files.exists(buildFile), "build.gradle not present in working dir");
        String content = Files.readString(buildFile);
        assertTrue(content.contains("java.net.http"),
                "jlink module list must include java.net.http so the updater works with the bundled JRE");
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
