package com.seeloggyplus.app;

import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

final class FileExplorer {

    private FileExplorer() {
    }

    static Path windowsDirectory() throws IOException {
        try {
            return Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Windows));
        } catch (RuntimeException exception) {
            throw new IOException("Cannot resolve the Windows directory", exception);
        }
    }

    static List<String> selectionCommand(Path windowsDirectory, File file) throws IOException {
        Path explorer = windowsDirectory.resolve("explorer.exe");
        if (!windowsDirectory.isAbsolute() || !Files.isRegularFile(explorer, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Windows Explorer is unavailable in the Windows directory");
        }
        return List.of(explorer.toString(), "/select,", file.getAbsolutePath());
    }
}
