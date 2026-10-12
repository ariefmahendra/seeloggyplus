package com.seeloggyplus.shared.dto;

import lombok.Data;

@Data
public class RemoteFileInfo implements Comparable<RemoteFileInfo> {

    private String name;
    private String path;
    private long size;
    private boolean isDirectory;
    private long modifiedTime;
    private String permissions;
    private String owner;

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
