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

    /** File was last opened as a normal, non-streaming view (download for remote files). */
    public static final String MODE_OPEN = "OPEN";

    /** File was last opened in live tail / streaming mode. */
    public static final String MODE_TAIL = "TAIL";

    private String id;
    private String fileId;
    private LocalDateTime lastOpened;
    private String mode;
}
