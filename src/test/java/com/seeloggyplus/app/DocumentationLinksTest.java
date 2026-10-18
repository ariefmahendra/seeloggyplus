package com.seeloggyplus.app;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentationLinksTest {
    @Test void helpOpensPublishedWebsite() throws Exception {
        var opened = new AtomicReference<URI>();
        DocumentationLinks.open(opened::set);
        assertEquals(URI.create("https://seeloggyplus-doc.vercel.app/"), opened.get());
    }

    @Test void browserFailureIsReportedToCaller() {
        var failure = new java.io.IOException("Browser unavailable");
        assertSame(failure, assertThrows(java.io.IOException.class,
                () -> DocumentationLinks.open(uri -> { throw failure; })));
    }

    @Test void portablePackagesUseOnlineDocumentation() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        assertFalse(build.contains("from('help')"));
        assertFalse(build.contains("from('launcher.properties')"), "Packages must not include developer launcher settings");
        assertFalse(Files.exists(Path.of("help/index.html")));
        String readme = Files.readString(Path.of("README.md"));
        assertTrue(readme.contains("https://seeloggyplus-doc.vercel.app/"));
        assertTrue(readme.contains("| `Ctrl+C` |"));
        assertTrue(readme.contains("| `F5` |"));
    }
}
