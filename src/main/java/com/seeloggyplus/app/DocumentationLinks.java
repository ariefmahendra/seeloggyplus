package com.seeloggyplus.app;

import java.net.URI;

final class DocumentationLinks {
    static final URI HOME = URI.create("https://seeloggyplus-doc.vercel.app/");

    @FunctionalInterface
    interface Browser {
        void open(URI uri) throws Exception;
    }

    private DocumentationLinks() { }

    static void open(Browser browser) throws Exception {
        browser.open(HOME);
    }
}
