package com.seeloggyplus.features.update.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Recreates the Windows silent entry point ({@code SeeLoggyPlus.vbs}) in the
 * installation root.
 * <p>
 * The file must <b>not</b> be part of portable/update packages: older installers
 * validate packages against a content whitelist and reject unknown entries, which
 * would break self-update from those releases. The new app heals the file on
 * startup instead.
 */
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

    /**
     * Creates the silent launcher next to {@code root} when missing. An existing
     * file (for example a user-customised one) is never overwritten.
     *
     * @return the path of the (existing or created) launcher script
     */
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
