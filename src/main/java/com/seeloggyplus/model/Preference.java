package com.seeloggyplus.model;

public class Preference {
    private String code;
    private String value;

    public Preference(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public Preference() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
