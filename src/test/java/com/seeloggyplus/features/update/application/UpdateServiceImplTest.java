package com.seeloggyplus.features.update.application;



import com.seeloggyplus.features.update.domain.UpdateCheckResult;
import com.seeloggyplus.features.update.domain.AppVersion;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceImplTest {

    @Test
    void defaultManifestUrlUsesTheUncachedLatestReleaseEndpoint() {
        // raw.githubusercontent.com serves the manifest with Cache-Control: max-age=300,
        // so a release published moments ago stayed invisible to update checks. The
        // latest-release download endpoint is not cached and always resolves to the
        // manifest attached to the newest release.
        String url = UpdateServiceImpl.DEFAULT_MANIFEST_URL;
        assertTrue(url.startsWith("https://github.com/"), url);
        assertTrue(url.contains("/releases/latest/download/"), url);
        assertTrue(url.endsWith("/update-manifest.json"), url);
        assertFalse(url.contains("raw.githubusercontent.com"), url);
    }

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
