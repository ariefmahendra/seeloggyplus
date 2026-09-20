package com.seeloggyplus.update;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.List;

/** Builds the {@code update-manifest.json} published by CI. */
public final class UpdateManifestWriter {

    public record AssetInfo(String key, String url, long size, String sha256) {
    }

    private UpdateManifestWriter() {
    }

    public static String build(String channel, String latest, String minSupported,
            String releaseNotesUrl, String publishedAt, List<AssetInfo> assets) {
        JsonObject root = new JsonObject();
        root.addProperty("channel", orDefault(channel, "stable"));
        root.addProperty("latest", latest);
        if (notBlank(minSupported)) {
            root.addProperty("minSupported", minSupported);
        }
        if (notBlank(releaseNotesUrl)) {
            root.addProperty("releaseNotesUrl", releaseNotesUrl);
        }
        if (notBlank(publishedAt)) {
            root.addProperty("publishedAt", publishedAt);
        }

        JsonObject assetsJson = new JsonObject();
        if (assets != null) {
            for (AssetInfo asset : assets) {
                JsonObject entry = new JsonObject();
                entry.addProperty("url", asset.url());
                entry.addProperty("size", asset.size());
                entry.addProperty("sha256", asset.sha256());
                assetsJson.add(asset.key(), entry);
            }
        }
        root.add("assets", assetsJson);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static String orDefault(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
