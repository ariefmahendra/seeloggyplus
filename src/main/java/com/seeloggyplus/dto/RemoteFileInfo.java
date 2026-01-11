package com.seeloggyplus.dto;

import lombok.Data;

/**
 * DTO representing a remote file or directory info.
 */
@Data
public class RemoteFileInfo implements Comparable<RemoteFileInfo> {
    private String name;
    private String path;
    private long size;
    private boolean isDirectory;
    private long modifiedTime;
    private String permissions;

    @Override
    public int compareTo(RemoteFileInfo o) {
        if (this.isDirectory && !o.isDirectory) {
            return -1;
        }
        if (!this.isDirectory && o.isDirectory) {
            return 1;
        }
        return this.name.compareToIgnoreCase(o.name);
    }
}
