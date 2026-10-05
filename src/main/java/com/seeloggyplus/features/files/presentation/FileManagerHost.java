package com.seeloggyplus.features.files.presentation;

import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ssh.SSHService;

/**
 * Callbacks the file manager dialog uses to open dialogs owned by the shell.
 * The application layer implements this so the feature does not depend on app code.
 */
public interface FileManagerHost {

    record FindInFilesResult(FileInfo file, int targetLine, int tailWindowLines, int tailJumpIndex,
                             boolean tail, boolean openInstead) { }

    /** Shows the Find-in-Files dialog; returns the chosen result or null when cancelled. */
    FindInFilesResult openFindInFiles(SSHService sshService, SSHServerModel server, String path, int maxTailWindow);

    /** Shows the log preview dialog for the given file. */
    void openPreview(FileInfo file, SSHService sshService);

    /** Shows the server management dialog. */
    void openServerManagement();
}
