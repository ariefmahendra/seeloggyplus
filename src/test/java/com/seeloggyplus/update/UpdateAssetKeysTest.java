package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateAssetKeysTest {

    @Test
    void mapsPlatformAndJreToAssetKey() {
        assertEquals("windows-nojre", UpdateAssetKeys.forPlatform(true, false));
        assertEquals("windows-jre", UpdateAssetKeys.forPlatform(true, true));
        assertEquals("linux-nojre", UpdateAssetKeys.forPlatform(false, false));
        assertEquals("linux-jre", UpdateAssetKeys.forPlatform(false, true));
    }
}
