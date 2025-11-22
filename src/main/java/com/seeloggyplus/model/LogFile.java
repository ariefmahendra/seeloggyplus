package com.seeloggyplus.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LogFile {
    private String id;
    private String name;
    private String filePath;
    private String size;
    private String modified;
    private boolean isRemote;
    private String sshServerID;
    private String parsingConfigurationID;
}
