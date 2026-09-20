package com.seeloggyplus.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;

/**
 * Default {@link UpdateService}. The manifest is fetched over HTTPS and parsed.
 * The fetcher is injectable so tests can run fully offline.
 */
public class UpdateServiceImpl implements UpdateService {

    public static final String DEFAULT_MANIFEST_URL =
            "https://raw.githubusercontent.com/ariefmahendra/seeloggyplus/gh-pages/update-manifest.json";

    private final String manifestUrl;
    private final Function<String, String> manifestFetcher;

    public UpdateServiceImpl() {
        this(DEFAULT_MANIFEST_URL);
    }

    public UpdateServiceImpl(String manifestUrl) {
        this(manifestUrl, UpdateServiceImpl::httpGet);
    }

    UpdateServiceImpl(String manifestUrl, Function<String, String> manifestFetcher) {
        this.manifestUrl = manifestUrl;
        this.manifestFetcher = manifestFetcher;
    }

    @Override
    public UpdateCheckResult check(String channel) {
        String current = AppVersion.current();
        try {
            String json = manifestFetcher.apply(manifestUrl);
            UpdateManifest manifest = UpdateManifest.parse(json);
            if (manifest.isForcedFor(current)) {
                return UpdateCheckResult.forced(current, manifest);
            }
            if (manifest.isNewerThan(current)) {
                return UpdateCheckResult.updateAvailable(current, manifest);
            }
            return UpdateCheckResult.upToDate(current, manifest);
        } catch (Exception e) {
            return UpdateCheckResult.error(current, e.getMessage() == null ? "Update check failed" : e.getMessage());
        }
    }

    private static String httpGet(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Update check interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Update check failed: " + e.getMessage(), e);
        }
    }
}
