package com.seeloggyplus.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileExplorerTest {

    @TempDir
    Path directory;

    @Test
    void usesAnAbsoluteExecutableAndSeparateArgumentsForUnusualFileNames() throws Exception {
        Path windows = Files.createDirectories(directory.resolve("Windows folder"));
        Path executable = Files.writeString(windows.resolve("explorer.exe"), "test executable");
        Path log = Files.writeString(directory.resolve("log ü & $ report.txt"), "log");
        assertEquals(List.of(executable.toString(), "/select,", log.toString()),
                FileExplorer.selectionCommand(windows, log.toFile()));
    }

    @Test
    void refusesMissingOrRelativeWindowsDirectoriesInsteadOfSearchingPath() {
        var file = directory.resolve("log.txt").toFile();
        assertThrows(java.io.IOException.class, () -> FileExplorer.selectionCommand(directory, file));
        assertThrows(java.io.IOException.class, () -> FileExplorer.selectionCommand(Path.of("Windows"), file));
    }

    @Test
    void resolvesTheOperatingSystemKnownFolderOnWindows() throws Exception {
        if (!com.sun.jna.Platform.isWindows()) return;
        Path folder = FileExplorer.windowsDirectory();
        assertTrue(folder.isAbsolute());
        assertTrue(Files.isRegularFile(folder.resolve("explorer.exe")));
        assertEquals(folder.resolve("explorer.exe").toString(),
                FileExplorer.selectionCommand(folder, directory.resolve("log.txt").toFile()).getFirst());
    }
}
