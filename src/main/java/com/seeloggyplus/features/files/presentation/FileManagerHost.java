package com.seeloggyplus.features.files.presentation;

import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ssh.SSHService;

public interface FileManagerHost {

    record FindInFilesResult(FileInfo file, int targetLine, int tailWindowLines, int tailJumpIndex,
                             boolean tail, boolean openInstead) { }

    FindInFilesResult openFindInFiles(SSHService sshService, SSHServerModel server, String path, int maxTailWindow);

    void openPreview(FileInfo file, SSHService sshService);

    void openServerManagement();
}
