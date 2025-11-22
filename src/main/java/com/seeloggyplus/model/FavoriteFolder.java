package com.seeloggyplus.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteFolder {
    private Integer id;
    private String name;
    private String path;
    /**
     * An identifier for the location. Can be a server ID/name or a special value for local drive.
     */
    private String locationId;
}
