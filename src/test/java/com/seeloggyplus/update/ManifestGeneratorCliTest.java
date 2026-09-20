package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestGeneratorCliTest {

    @Test
    void generatesManifestWithMultipleAssets() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-cli");
        try {
            Path noJre = work.resolve("seeloggyplus-Portable-win.zip");
            Path withJre = work.resolve("seeloggyplus-Portable-win-jre.zip");
            byte[] noJreBytes = "no-jre".getBytes();
            byte[] withJreBytes = "with-jre-bytes".getBytes();
            Files.write(noJre, noJreBytes);
            Files.write(withJre, withJreBytes);
            Path out = work.resolve("out/update-manifest-windows.json");

            ManifestGeneratorCli.main(new String[] {
                    "--version", "0.4.0",
                    "--channel", "stable",
                    "--base-url", "https://github.com/example/releases/download/0.4.0",
                    "--asset", "windows-nojre=" + noJre,
                    "--asset", "windows-jre=" + withJre,
                    "--min", "0.3.0",
                    "--notes", "https://example.com/notes",
                    "--out", out.toString()
            });

            UpdateManifest manifest = UpdateManifest.parse(Files.readString(out));
            assertEquals("0.4.0", manifest.latest());
            assertEquals(2, manifest.assets().size());
            assertEquals("https://github.com/example/releases/download/0.4.0/" + noJre.getFileName(),
                    manifest.assetFor("windows-nojre").url());
            assertEquals(noJreBytes.length, manifest.assetFor("windows-nojre").size());
            assertEquals(Hashing.sha256(noJreBytes), manifest.assetFor("windows-nojre").sha256());
            assertEquals(Hashing.sha256(withJreBytes), manifest.assetFor("windows-jre").sha256());
        } finally {
            UpdateLayout.deleteRecursively(work);
        }
    }

    @Test
    void mergesPlatformManifests() throws Exception {
        Path work = Files.createTempDirectory("seeloggy-merge");
        try {
            Path win = work.resolve("update-manifest-windows.json");
            Path linux = work.resolve("update-manifest-linux.json");
            Files.writeString(win, UpdateManifestWriter.build("stable", "0.4.0", "0.3.0", "", "",
                    List.of(new UpdateManifestWriter.AssetInfo("windows-nojre",
                            "https://example.com/win.zip", 10, "aaa"))));
            Files.writeString(linux, UpdateManifestWriter.build("stable", "0.4.0", "", "", "",
                    List.of(new UpdateManifestWriter.AssetInfo("linux-jre",
                            "https://example.com/linux-jre.zip", 20, "bbb"))));
            Path out = work.resolve("update-manifest.json");

            ManifestMergeCli.main(new String[] {
                    "--out", out.toString(),
                    "--in", win.toString(),
                    "--in", linux.toString()
            });

            UpdateManifest merged = UpdateManifest.parse(Files.readString(out));
            assertEquals("0.4.0", merged.latest());
            assertEquals(2, merged.assets().size());
            assertEquals("0.3.0", merged.minSupported());
            assertTrue(merged.assets().containsKey("windows-nojre"));
            assertTrue(merged.assets().containsKey("linux-jre"));
        } finally {
            UpdateLayout.deleteRecursively(work);
        }
    }

    @Test
    void parseRejectsMissingValueAndUnexpectedArgument() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {"--out"}));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {"unexpected"}));
    }
}
