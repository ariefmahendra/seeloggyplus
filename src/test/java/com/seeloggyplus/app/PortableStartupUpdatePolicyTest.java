package com.seeloggyplus.app;

import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.shared.model.Preference;
import com.sun.net.httpserver.HttpServer;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class PortableStartupUpdatePolicyTest {

    @Test
    void oldAutomaticUpdatePreferencesCannotTriggerAnUpdateRequest() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requested = new CountDownLatch(1);
        server.createContext("/manifest.json", exchange -> {
            requested.countDown();
            byte[] body = "{\"latest\":\"0.0.1\",\"channel\":\"stable\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        var preferences = new PreferenceServiceImpl();
        Map<String, String> originalPreferences = new HashMap<>();
        Map<String, String> originalProperties = new HashMap<>();
        for (String key : List.of("update_auto_check", "update_auto_download", "update_last_check")) {
            originalPreferences.put(key, preferences.getPreferencesByCode(key).orElse(""));
        }
        for (String key : List.of("seeloggyplus.distribution", "seeloggyplus.disableUpdateCheck",
                "seeloggyplus.updateManifestUrl", "seeloggyplus.dev")) originalProperties.put(key, System.getProperty(key));
        try {
            server.start();
            preferences.saveOrUpdatePreferences(new Preference("update_auto_check", "true"));
            preferences.saveOrUpdatePreferences(new Preference("update_auto_download", "true"));
            preferences.saveOrUpdatePreferences(new Preference("update_last_check", "0"));
            System.clearProperty("seeloggyplus.distribution");
            System.clearProperty("seeloggyplus.disableUpdateCheck");
            System.clearProperty("seeloggyplus.dev");
            System.setProperty("seeloggyplus.updateManifestUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/manifest.json");
            WaitForAsyncUtils.asyncFx(() -> {
                try {
                    return new FXMLLoader(getClass().getResource("/fxml/MainView.fxml")).load();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).get();
            WaitForAsyncUtils.waitForFxEvents();
            assertFalse(requested.await(500, TimeUnit.MILLISECONDS), "Portable must never contact the update server");
        } finally {
            server.stop(0);
            originalPreferences.forEach((key, value) -> preferences.saveOrUpdatePreferences(new Preference(key, value)));
            originalProperties.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }
}
