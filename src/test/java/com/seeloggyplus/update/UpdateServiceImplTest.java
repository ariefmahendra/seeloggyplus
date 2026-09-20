package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UpdateServiceImplTest {

    private static UpdateServiceImpl serviceReturning(String manifestJson) {
        return new UpdateServiceImpl("https://example.com/manifest.json", url -> manifestJson);
    }

    @Test
    void reportsUpdateAvailableForNewerVersion() {
        String current = AppVersion.current();
        String json = "{\"latest\":\"999.0.0\",\"assets\":{\"portable-nojre\":"
                + "{\"url\":\"https://example.com/a.zip\",\"sha256\":\"x\"}}}";
        UpdateCheckResult result = serviceReturning(json).check("stable");

        assertEquals(UpdateCheckResult.Status.UPDATE_AVAILABLE, result.status());
        assertEquals("999.0.0", result.manifest().latest());
        assertEquals(current, result.currentVersion());
    }

    @Test
    void reportsUpToDateWhenSameVersion() {
        String current = AppVersion.current();
        String json = "{\"latest\":\"" + current + "\"}";
        UpdateCheckResult result = serviceReturning(json).check("stable");

        assertEquals(UpdateCheckResult.Status.UP_TO_DATE, result.status());
    }

    @Test
    void reportsForcedWhenBelowMinimumSupported() {
        String json = "{\"latest\":\"999.0.0\",\"minSupported\":\"999.0.0\"}";
        UpdateCheckResult result = serviceReturning(json).check("stable");

        assertEquals(UpdateCheckResult.Status.FORCED, result.status());
    }

    @Test
    void reportsErrorOnFetchFailure() {
        Function<String, String> failing = url -> {
            throw new IllegalStateException("offline");
        };
        UpdateCheckResult result = new UpdateServiceImpl("https://example.com/manifest.json", failing).check("stable");

        assertEquals(UpdateCheckResult.Status.ERROR, result.status());
        assertNull(result.manifest());
        assertNotNull(result.message());
    }

    @Test
    void reportsErrorOnInvalidManifest() {
        UpdateCheckResult result = serviceReturning("not json").check("stable");
        assertEquals(UpdateCheckResult.Status.ERROR, result.status());
    }
}
