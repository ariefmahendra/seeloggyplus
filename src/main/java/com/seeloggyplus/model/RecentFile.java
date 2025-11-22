package com.seeloggyplus.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Model class representing a recently opened file
 * Used to track file history in the application
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RecentFile {
    private String id;
    private String fileId;
    private LocalDateTime lastOpened;
}
