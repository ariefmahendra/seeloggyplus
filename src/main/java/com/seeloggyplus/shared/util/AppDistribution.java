package com.seeloggyplus.shared.util;

public final class AppDistribution {

    private AppDistribution() {
    }

    public static boolean isStore() {
        return "store".equals(System.getProperty("seeloggyplus.distribution"));
    }
}
