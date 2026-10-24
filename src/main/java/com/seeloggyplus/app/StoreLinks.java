package com.seeloggyplus.app;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

final class StoreLinks {

    @FunctionalInterface
    interface Opener {
        void open(URI uri) throws Exception;
    }

    private StoreLinks() {
    }

    static String configuredProductId() throws IOException {
        String override = System.getProperty("seeloggyplus.storeProductId");
        if (override != null) return override;
        Properties properties = new Properties();
        try (var input = StoreLinks.class.getResourceAsStream("/store.properties")) {
            if (input != null) properties.load(input);
        }
        return properties.getProperty("store.productId", "");
    }

    static void open(String productId, Opener opener) throws Exception {
        String id = productId == null ? "" : productId.trim();
        boolean known = id.matches("[A-Za-z0-9]{12}");
        URI store = URI.create(known ? "ms-windows-store://pdp/?ProductId=" + id
                : "ms-windows-store://search/?query=SeeLoggyPlus");
        try {
            opener.open(store);
        } catch (Exception storeFailure) {
            URI website = URI.create(known ? "https://apps.microsoft.com/detail/" + id
                    : "https://apps.microsoft.com/search?query=SeeLoggyPlus");
            try {
                opener.open(website);
            } catch (Exception browserFailure) {
                browserFailure.addSuppressed(storeFailure);
                throw browserFailure;
            }
        }
    }
}
