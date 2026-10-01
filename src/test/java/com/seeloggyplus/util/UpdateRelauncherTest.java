package com.seeloggyplus.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The restart after a self-update must not create a console window. On Windows
 * the application is started directly with {@code javaw} (the staged version jar
 * and the launcher.properties memory setting are reused), never through the old
 * foreground launcher script.
 */
class UpdateRelauncherTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Windows restart uses javaw, the active version jar and the configured memory")
    void windowsCommandUsesJavawAndActiveVersion() throws Exception {
        Path root = tempDir.resolve("install");
        Path versionDir = root.resolve("versions/1.2.3");
        Files.createDirectories(versionDir);
        Files.writeString(root.resolve("current"), "1.2.3");
        Files.writeString(versionDir.resolve("seeloggyplus.jar"), "jar");
        Path jreBin = root.resolve("jre/bin");
        Files.createDirectories(jreBin);
        Files.writeString(jreBin.resolve("javaw.exe"), "");
        Files.writeString(jreBin.resolve("java.exe"), "");
        Files.writeString(root.resolve("launcher.properties"), "max.memory.gb=6\n");

        List<String> command = UpdateRelauncher
                .windowsCommand(root, root.resolve("jre").toString())
                .orElseThrow();

        assertTrue(command.get(0).endsWith("javaw.exe"), "must start javaw, not java.exe");
        assertEquals("-Xmx6g", command.get(1));
        assertEquals("-jar", command.get(2));
        assertEquals(versionDir.resolve("seeloggyplus.jar").toString(), command.get(3));
    }

    @Test
    @DisplayName("falls back to the root jar when no version is active")
    void windowsCommandFallsBackToRootJar() throws Exception {
        Path root = tempDir.resolve("install");
        Files.createDirectories(root.resolve("jre/bin"));
        Files.writeString(root.resolve("jre/bin/javaw.exe"), "");
        Files.writeString(root.resolve("seeloggyplus.jar"), "jar");

        List<String> command = UpdateRelauncher
                .windowsCommand(root, root.resolve("jre").toString())
                .orElseThrow();

        assertEquals(root.resolve("seeloggyplus.jar").toString(), command.get(3));
    }

    @Test
    @DisplayName("no command is built when the jar or JVM is missing")
    void windowsCommandRequiresJarAndJvm() throws Exception {
        Path root = tempDir.resolve("empty");
        Files.createDirectories(root.resolve("jre/bin"));
        Files.writeString(root.resolve("jre/bin/javaw.exe"), "");

        assertTrue(UpdateRelauncher.windowsCommand(root, root.resolve("jre").toString()).isEmpty());

        Files.writeString(root.resolve("seeloggyplus.jar"), "jar");
        assertTrue(UpdateRelauncher.windowsCommand(root, root.resolve("nowhere").toString()).isEmpty());
    }

    @Test
    @DisplayName("active jar prefers the staged current version over the root jar")
    void activeJarPrefersStagedVersion() throws Exception {
        Path root = tempDir.resolve("install");
        Path versionDir = root.resolve("versions/2.0.0");
        Files.createDirectories(versionDir);
        Files.writeString(root.resolve("current"), "2.0.0");
        Files.writeString(versionDir.resolve("seeloggyplus.jar"), "new");
        Files.writeString(root.resolve("seeloggyplus.jar"), "old");

        assertEquals(versionDir.resolve("seeloggyplus.jar"), UpdateRelauncher.activeJar(root));
    }

    @Test
    @DisplayName("javaw is preferred but java.exe is an acceptable fallback")
    void resolveJavawFallsBackToJava() throws Exception {
        Path bin = tempDir.resolve("jvm/bin");
        Files.createDirectories(bin);
        Files.writeString(bin.resolve("java.exe"), "");

        assertEquals(bin.resolve("java.exe"), UpdateRelauncher.resolveJavaw(tempDir.resolve("jvm").toString()));

        Files.writeString(bin.resolve("javaw.exe"), "");
        assertEquals(bin.resolve("javaw.exe"), UpdateRelauncher.resolveJavaw(tempDir.resolve("jvm").toString()));

        assertNull(UpdateRelauncher.resolveJavaw(tempDir.resolve("missing").toString()));
        assertNull(UpdateRelauncher.resolveJavaw(null));
    }

    @Test
    @DisplayName("memory comes from launcher.properties with a safe default")
    void maxMemoryReadsLauncherProperties() throws Exception {
        Path root = tempDir.resolve("install");
        Files.createDirectories(root);

        assertEquals(4, UpdateRelauncher.maxMemoryGb(root), "missing file uses the packaged default");

        Files.writeString(root.resolve("launcher.properties"), "max.memory.gb=8\n");
        assertEquals(8, UpdateRelauncher.maxMemoryGb(root));

        Files.writeString(root.resolve("launcher.properties"), "max.memory.gb=not-a-number\n");
        assertEquals(4, UpdateRelauncher.maxMemoryGb(root));

        Files.writeString(root.resolve("launcher.properties"), "max.memory.gb=0\n");
        assertEquals(4, UpdateRelauncher.maxMemoryGb(root));
    }

    @Test
    @DisplayName("active jar is null when neither a staged version nor a root jar exists")
    void activeJarNullWithoutJars() throws Exception {
        Path root = tempDir.resolve("empty");
        Files.createDirectories(root);

        assertNull(UpdateRelauncher.activeJar(root));
    }

    @Test
    @DisplayName("windowsJavawPath returns the sibling javaw for a java.exe home")
    void windowsCommandWithJavaHome() throws Exception {
        Path root = tempDir.resolve("install");
        Path versionDir = root.resolve("versions/3.0.0");
        Files.createDirectories(versionDir);
        Files.writeString(root.resolve("current"), "3.0.0");
        Files.writeString(versionDir.resolve("seeloggyplus.jar"), "jar");
        Path home = root.resolve("jdk-21");
        Path bin = home.resolve("bin");
        Files.createDirectories(bin);
        Files.writeString(bin.resolve("java.exe"), "");
        Files.writeString(bin.resolve("javaw.exe"), "");

        Optional<List<String>> command = UpdateRelauncher.windowsCommand(root, home.toString());

        assertTrue(command.isPresent());
        assertEquals(bin.resolve("javaw.exe").toString(), command.orElseThrow().get(0));
    }
}
