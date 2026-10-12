package com.seeloggyplus.features.update.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class SilentLauncherInstaller {

    public static final String FILE_NAME = "SeeLoggyPlus.vbs";

    static final String SCRIPT = """
            Option Explicit
            Dim shell, fs, folder, command
            Set shell = CreateObject("WScript.Shell")
            Set fs = CreateObject("Scripting.FileSystemObject")
            folder = fs.GetParentFolderName(WScript.ScriptFullName)
            shell.CurrentDirectory = folder
            command = Chr(34) & folder & "\\launcher.bat" & Chr(34)
            shell.Run command, 0, False
            """;

    private SilentLauncherInstaller() {
    }

    public static Path ensure(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        Path target = root.resolve(FILE_NAME);
        if (Files.isRegularFile(target)) {
            return target;
        }
        Files.createDirectories(root);
        Files.writeString(target, SCRIPT.replace("\n", "\r\n"));
        return target;
    }

    static String scriptContent() {
        return SCRIPT.replace("\n", "\r\n");
    }
}
