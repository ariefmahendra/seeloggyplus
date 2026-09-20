package com.seeloggyplus.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges per-platform manifests into a single manifest containing every asset.
 *
 * <pre>
 * --out update-manifest.json --in update-manifest-win.json --in update-manifest-linux.json
 * </pre>
 */
public final class ManifestMergeCli {

    private ManifestMergeCli() {
    }

    public static void main(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        Path out = Path.of(options.require("out"));
        List<String> inputs = options.all("in");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("At least one --in manifest is required");
        }

        String channel = "stable";
        String latest = null;
        String minSupported = "";
        String notes = "";
        List<UpdateManifestWriter.AssetInfo> assets = new ArrayList<>();

        for (String input : inputs) {
            UpdateManifest manifest = UpdateManifest.parse(Files.readString(Path.of(input)));
            if (latest == null || VersionComparator.compare(manifest.latest(), latest) > 0) {
                latest = manifest.latest();
                if (!manifest.channel().isBlank()) {
                    channel = manifest.channel();
                }
                if (manifest.minSupported() != null && !manifest.minSupported().isBlank()) {
                    minSupported = manifest.minSupported();
                }
                if (manifest.releaseNotesUrl() != null && !manifest.releaseNotesUrl().isBlank()) {
                    notes = manifest.releaseNotesUrl();
                }
            }
            manifest.assets().forEach((key, asset) ->
                    assets.add(new UpdateManifestWriter.AssetInfo(key, asset.url(), asset.size(), asset.sha256())));
        }

        String merged = UpdateManifestWriter.build(channel, latest, minSupported, notes, "", assets);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, merged);
        System.out.println("Merged " + inputs.size() + " manifest(s) into " + out + " (" + assets.size() + " assets)");
    }
}
