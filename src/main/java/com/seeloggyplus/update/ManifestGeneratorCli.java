package com.seeloggyplus.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a per-platform manifest. Assets are passed as {@code --asset key=file}
 * and their URL is derived from {@code --base-url}.
 *
 * <pre>
 * --version 0.2.0 --base-url https://.../download/0.2.0 --out partial.json
 * --asset windows-nojre=build/distributions/app-win.zip
 * --asset windows-jre=build/distributions/app-win-jre.zip
 * [--channel stable] [--min 0.1.0] [--notes https://...]
 * </pre>
 */
public final class ManifestGeneratorCli {

    private ManifestGeneratorCli() {
    }

    public static void main(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        Path out = Path.of(options.require("out"));
        String version = options.require("version");
        String channel = options.get("channel", "stable");
        String minSupported = options.get("min", "");
        String notes = options.get("notes", "");
        String baseUrl = options.require("base-url");

        List<UpdateManifestWriter.AssetInfo> assets = new ArrayList<>();
        for (String spec : options.all("asset")) {
            int separator = spec.indexOf('=');
            if (separator <= 0 || separator == spec.length() - 1) {
                throw new IllegalArgumentException("Invalid --asset (expected key=file): " + spec);
            }
            String key = spec.substring(0, separator);
            Path file = Path.of(spec.substring(separator + 1));
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Asset file not found: " + file);
            }
            String url = trimTrailingSlash(baseUrl) + "/" + file.getFileName();
            assets.add(new UpdateManifestWriter.AssetInfo(key, url, Files.size(file), Hashing.sha256(file)));
        }
        if (assets.isEmpty()) {
            throw new IllegalArgumentException("At least one --asset is required");
        }

        String manifest = UpdateManifestWriter.build(channel, version, minSupported, notes,
                OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), assets);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, manifest);
        System.out.println("Wrote update manifest: " + out + " (" + assets.size() + " assets)");
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
