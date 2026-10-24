package com.seeloggyplus.app;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StoreLinksTest {

    @Test
    void bundledConfigurationOpensTheRegisteredSeeLoggyPlusListing() throws Exception {
        String previous = System.getProperty("seeloggyplus.storeProductId");
        System.clearProperty("seeloggyplus.storeProductId");
        try {
            var opened = new ArrayList<URI>();
            StoreLinks.open(StoreLinks.configuredProductId(), opened::add);
            assertEquals(List.of(URI.create("ms-windows-store://pdp/?ProductId=9PDJ4N28SNT7")), opened);
        } finally {
            if (previous == null) System.clearProperty("seeloggyplus.storeProductId");
            else System.setProperty("seeloggyplus.storeProductId", previous);
        }
    }

    @Test
    void opensStoreSearchWhileProductIdIsUnavailable() throws Exception {
        var opened = new ArrayList<URI>();
        StoreLinks.open(null, opened::add);
        assertEquals(List.of(URI.create("ms-windows-store://search/?query=SeeLoggyPlus")), opened);
    }

    @Test
    void opensRegisteredProductWhenItsIdIsConfigured() throws Exception {
        var opened = new ArrayList<URI>();
        StoreLinks.open("9N1234567890", opened::add);
        assertEquals(List.of(URI.create("ms-windows-store://pdp/?ProductId=9N1234567890")), opened);
    }

    @Test
    void fallsBackToTheWebListingIfStoreProtocolIsUnavailable() throws Exception {
        var attempts = new ArrayList<URI>();
        StoreLinks.open("9N1234567890", uri -> {
            attempts.add(uri);
            if (uri.getScheme().equals("ms-windows-store")) throw new IOException("Store unavailable");
        });
        assertEquals(List.of(URI.create("ms-windows-store://pdp/?ProductId=9N1234567890"),
                URI.create("https://apps.microsoft.com/detail/9N1234567890")), attempts);
    }

    @Test
    void reportsFailureIfNeitherStoreNorBrowserCanOpen() {
        assertThrows(IOException.class, () -> StoreLinks.open(null, uri -> { throw new IOException("No handler"); }));
    }

    @Test
    void malformedProductIdCannotChangeTheDestination() throws Exception {
        var opened = new ArrayList<URI>();
        StoreLinks.open("9N123&other=value", opened::add);
        assertEquals(List.of(URI.create("ms-windows-store://search/?query=SeeLoggyPlus")), opened);
    }
}
