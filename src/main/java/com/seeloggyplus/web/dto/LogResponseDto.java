package com.seeloggyplus.web.dto;

import com.seeloggyplus.model.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogResponseDto {
    private String fileId;
    private String fileName;
    private String filePath;
    private String source;
    private long totalLines;
    private long filteredLines;
    private List<LogEntry> entries;
}
