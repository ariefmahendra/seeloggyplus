package com.seeloggyplus.features.files.domain;

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

    private String locationId;
}
