package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateManifestWriterTest {

    @Test
    void writesManifestThatParsesBackWithSameValues() throws Exception {
        String json = UpdateManifestWriter.build("stable", "0.3.0", "0.2.0",
                "https://example.com/notes", "2026-09-20T10:00:00Z",
                List.of(new UpdateManifestWriter.AssetInfo("portable-nojre",
                        "https://example.com/app.zip", 48123456L, "abc123")));

        UpdateManifest manifest = UpdateManifest.parse(json);
        assertEquals("stable", manifest.channel());
        assertEquals("0.3.0", manifest.latest());
        assertEquals("0.2.0", manifest.minSupported());
        assertEquals("https://example.com/notes", manifest.releaseNotesUrl());
        assertEquals(48123456L, manifest.assetFor("portable-nojre").size());
        assertEquals("abc123", manifest.assetFor("portable-nojre").sha256());
    }

    @Test
    void defaultsChannelWhenBlank() {
        String json = UpdateManifestWriter.build("", "1.0.0", "", "", "", List.of());
        assertTrue(json.contains("\"channel\": \"stable\""));
    }

}
