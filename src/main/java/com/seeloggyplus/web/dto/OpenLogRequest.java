package com.seeloggyplus.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLogRequest {
    private String source; // "LOCAL" or "REMOTE"
    private String path;
    private String serverId;
    private String configId;
    private boolean tailMode;
    @Builder.Default
    private int contextRows = 1000;
}
