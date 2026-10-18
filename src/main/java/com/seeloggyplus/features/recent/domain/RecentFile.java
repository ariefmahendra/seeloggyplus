package com.seeloggyplus.features.recent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RecentFile {

    public static final String MODE_OPEN = "OPEN";

    public static final String MODE_TAIL = "TAIL";

    private String id;
    private String fileId;
    private LocalDateTime lastOpened;
    private String mode;
}
