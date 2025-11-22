package com.seeloggyplus.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Preference {
    private String id;
    private String code;
    private String value;

    public Preference(String code, String value) {
        this.code = code;
        this.value = value;
    }
}
