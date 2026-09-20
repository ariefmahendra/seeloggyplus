package com.seeloggyplus.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI used by the Gradle {@code generateUpdateManifest} task.
 *
 * Usage:
 * <pre>
 * --zip &lt;portable.zip&gt; --version &lt;x.y.z&gt; --url &lt;downloadUrl&gt; --out &lt;manifest.json&gt;
 * [--channel stable] [--min &lt;minSupported&gt;] [--notes &lt;releaseNotesUrl&gt;] [--asset-key portable-nojre]
 * </pre>
 */
public final class ManifestGeneratorCli {

    private ManifestGeneratorCli() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        Path zip = Path.of(require(options, "zip"));
        Path out = Path.of(require(options, "out"));
        String version = require(options, "version");
        String url = require(options, "url");
        String channel = options.getOrDefault("channel", "stable");
        String minSupported = options.getOrDefault("min", "");
        String notes = options.getOrDefault("notes", "");
        String assetKey = options.getOrDefault("asset-key", "portable-nojre");

        long size = Files.size(zip);
        String sha256 = Hashing.sha256(zip);
        String manifest = UpdateManifestWriter.build(channel, version, minSupported, notes,
                OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                List.of(new UpdateManifestWriter.AssetInfo(assetKey, url, size, sha256)));

        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, manifest);
        System.out.println("Wrote update manifest: " + out);
    }

    static Map<String, String> parse(String[] args) throws IllegalArgumentException {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + key);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            options.put(key.substring(2), args[++i]);
        }
        return options;
    }

    private static String require(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + key);
        }
        return value;
    }
}
