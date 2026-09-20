package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateManifestTest {

    private static final String VALID = """
            {
              "channel": "stable",
              "latest": "0.3.0",
              "minSupported": "0.2.0",
              "releaseNotesUrl": "https://example.com/notes",
              "publishedAt": "2026-09-20T10:00:00Z",
              "assets": {
                "portable-nojre": {
                  "url": "https://example.com/SeeloggyPlus-0.3.0.zip",
                  "size": 48123456,
                  "sha256": "abc123"
                },
                "portable-withjre": {
                  "url": "https://example.com/SeeloggyPlus-0.3.0-jre.zip",
                  "size": 90000000,
                  "sha256": "def456"
                }
              }
            }
            """;

    @Test
    void parsesValidManifest() throws Exception {
        UpdateManifest manifest = UpdateManifest.parse(VALID);
        assertEquals("stable", manifest.channel());
        assertEquals("0.3.0", manifest.latest());
        assertEquals("0.2.0", manifest.minSupported());
        assertEquals(2, manifest.assets().size());
        assertEquals("abc123", manifest.assetFor("portable-nojre").sha256());
        assertEquals(48123456L, manifest.assetFor("portable-nojre").size());
        assertNotNull(manifest.assetFor("unknown-key")); // falls back to first
    }

    @Test
    void detectsNewerAndForced() throws Exception {
        UpdateManifest manifest = UpdateManifest.parse(VALID);
        assertTrue(manifest.isNewerThan("0.2.0"));
        assertFalse(manifest.isNewerThan("0.3.0"));
        assertTrue(manifest.isForcedFor("0.1.0"));
        assertFalse(manifest.isForcedFor("0.2.0"));
    }

    @Test
    void rejectsMissingLatest() {
        assertThrows(UpdateException.class, () -> UpdateManifest.parse("{\"channel\":\"stable\"}"));
    }

    @Test
    void acceptsLocalhostUrlsForLocalSimulation() throws Exception {
        String json = """
                {"latest":"1.0.0",
                 "assets":{"portable-nojre":{"url":"http://localhost:8000/app.zip"}},
                 "releaseNotesUrl":"http://127.0.0.1:8000/notes.html"}
                """;
        UpdateManifest manifest = UpdateManifest.parse(json);
        assertEquals("http://localhost:8000/app.zip", manifest.assetFor("portable-nojre").url());
    }

    @Test
    void rejectsNonHttpsAssetUrl() {
        String json = """
                {"latest":"1.0.0","assets":{"a":{"url":"http://example.com/a.zip"}}}
                """;
        assertThrows(UpdateException.class, () -> UpdateManifest.parse(json));
    }

    @Test
    void rejectsNonHttpsReleaseNotes() {
        String json = """
                {"latest":"1.0.0","releaseNotesUrl":"http://example.com/notes"}
                """;
        assertThrows(UpdateException.class, () -> UpdateManifest.parse(json));
    }

    @Test
    void rejectsMalformedJsonAndEmpty() {
        assertThrows(UpdateException.class, () -> UpdateManifest.parse("not json"));
        assertThrows(UpdateException.class, () -> UpdateManifest.parse(""));
        assertThrows(UpdateException.class, () -> UpdateManifest.parse(null));
    }

    @Test
    void manifestWithoutMinSupportedIsNeverForced() throws Exception {
        UpdateManifest manifest = UpdateManifest.parse("{\"latest\":\"1.0.0\"}");
        assertFalse(manifest.isForcedFor("0.0.1"));
        assertNull(manifest.assetFor("anything"));
    }
}
