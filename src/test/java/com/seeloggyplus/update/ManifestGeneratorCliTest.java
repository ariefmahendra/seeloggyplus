package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestGeneratorCliTest {

    @Test
    void generatesManifestFromZip() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-cli");
        try {
            Path zip = work.resolve("SeeloggyPlus-Portable.zip");
            byte[] content = "zip-payload".getBytes();
            Files.write(zip, content);
            Path out = work.resolve("out/update-manifest.json");

            ManifestGeneratorCli.main(new String[] {
                    "--zip", zip.toString(),
                    "--version", "0.4.0",
                    "--url", "https://github.com/example/releases/download/v0.4.0/SeeloggyPlus-Portable.zip",
                    "--channel", "stable",
                    "--min", "0.3.0",
                    "--notes", "https://example.com/notes",
                    "--asset-key", "portable-nojre",
                    "--out", out.toString()
            });

            assertTrue(Files.exists(out));
            UpdateManifest manifest = UpdateManifest.parse(Files.readString(out));
            assertEquals("0.4.0", manifest.latest());
            assertEquals("0.3.0", manifest.minSupported());
            assertEquals(content.length, manifest.assetFor("portable-nojre").size());
            assertEquals(Hashing.sha256(content), manifest.assetFor("portable-nojre").sha256());
        } finally {
            UpdateLayout.deleteRecursively(work);
        }
    }
}
