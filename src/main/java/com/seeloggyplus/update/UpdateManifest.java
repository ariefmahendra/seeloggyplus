package com.seeloggyplus.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed update manifest. Example:
 * <pre>
 * {
 *   "channel": "stable",
 *   "latest": "0.3.0",
 *   "minSupported": "0.2.0",
 *   "releaseNotesUrl": "https://...",
 *   "assets": {
 *     "portable-nojre": { "url": "https://...zip", "size": 48123456, "sha256": "ab12..." }
 *   }
 * }
 * </pre>
 */
public record UpdateManifest(
        String channel,
        String latest,
        String minSupported,
        String releaseNotesUrl,
        String publishedAt,
        Map<String, UpdateAsset> assets,
        String signature) {

    public static UpdateManifest parse(String json) throws UpdateException {
        if (json == null || json.isBlank()) {
            throw new UpdateException("Manifest is empty");
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new UpdateException("Manifest is not valid JSON", e);
        }

        String latest = required(root, "latest");
        String channel = optional(root, "channel", "stable");
        String minSupported = optional(root, "minSupported", "");
        String releaseNotesUrl = optional(root, "releaseNotesUrl", "");
        String publishedAt = optional(root, "publishedAt", "");
        String signature = optional(root, "signature", "");

        Map<String, UpdateAsset> assets = new LinkedHashMap<>();
        JsonElement assetsElement = root.get("assets");
        if (assetsElement != null && assetsElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : assetsElement.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject assetJson = entry.getValue().getAsJsonObject();
                String url = required(assetJson, "url");
                requireHttps(url, "asset url");
                long size = assetJson.has("size") && !assetJson.get("size").isJsonNull()
                        ? assetJson.get("size").getAsLong()
                        : -1L;
                String sha256 = optional(assetJson, "sha256", "");
                assets.put(entry.getKey(), new UpdateAsset(url, size, sha256));
            }
        }
        if (!releaseNotesUrl.isBlank()) {
            requireHttps(releaseNotesUrl, "releaseNotesUrl");
        }
        return new UpdateManifest(channel, latest, minSupported, releaseNotesUrl, publishedAt,
                Collections.unmodifiableMap(assets), signature);
    }

    public boolean isNewerThan(String current) {
        return VersionComparator.compare(latest, current) > 0;
    }

    public boolean isForcedFor(String current) {
        if (minSupported == null || minSupported.isBlank()) {
            return false;
        }
        return VersionComparator.compare(current, minSupported) < 0;
    }

    public UpdateAsset assetFor(String preferredKey) {
        if (preferredKey != null && assets.containsKey(preferredKey)) {
            return assets.get(preferredKey);
        }
        return assets.values().stream().findFirst().orElse(null);
    }

    private static void requireHttps(String url, String field) throws UpdateException {
        if (url == null || !isAllowedUrl(url)) {
            throw new UpdateException(field + " must be an https (or localhost) URL: " + url);
        }
    }

    private static boolean isAllowedUrl(String url) {
        return url.regionMatches(true, 0, "https://", 0, 8)
                || url.regionMatches(true, 0, "http://localhost", 0, 16)
                || url.regionMatches(true, 0, "http://127.0.0.1", 0, 16);
    }

    private static String required(JsonObject object, String field) throws UpdateException {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull() || element.getAsString().isBlank()) {
            throw new UpdateException("Missing required field: " + field);
        }
        return element.getAsString();
    }

    private static String optional(JsonObject object, String field, String fallback) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsString();
    }
}
