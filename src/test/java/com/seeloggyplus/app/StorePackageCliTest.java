package com.seeloggyplus.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamSource;

import static org.junit.jupiter.api.Assertions.*;

class StorePackageCliTest {

    @TempDir
    Path stage;

    @Test
    void transformerBlocksExternalStylesheetsAndDtds() throws Exception {
        var factory = StorePackageCli.transformerFactory();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertEquals("", factory.getAttribute(XMLConstants.ACCESS_EXTERNAL_DTD));
        assertEquals("", factory.getAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET));
        Path secret = Files.writeString(stage.resolve("external.xml"), "<secret>private</secret>");
        String include = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:include href=\"" + secret.toUri() + "\"/></xsl:stylesheet>";
        assertThrows(TransformerConfigurationException.class,
                () -> factory.newTransformer(new StreamSource(new StringReader(include))));
        String dtd = "<!DOCTYPE xsl:stylesheet SYSTEM \"" + secret.toUri() + "\">"
                + "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>";
        assertThrows(TransformerConfigurationException.class,
                () -> factory.newTransformer(new StreamSource(new StringReader(dtd))));
    }

    @Test
    void createsManifestAndLogosForTheRegisteredStoreIdentity() throws Exception {
        StorePackageCli.main(new String[]{stage.toString(), "0.9.0",
                "AriefMahendra.SeeLoggyPlus", "CN=A19804B0-0953-43F2-B116-C24E0EED56BE", "Arief Mahendra"});

        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var manifest = factory.newDocumentBuilder().parse(stage.resolve("AppxManifest.xml").toFile());
        var identity = (org.w3c.dom.Element) manifest.getElementsByTagName("Identity").item(0);
        assertEquals("AriefMahendra.SeeLoggyPlus", identity.getAttribute("Name"));
        assertEquals("CN=A19804B0-0953-43F2-B116-C24E0EED56BE", identity.getAttribute("Publisher"));
        assertEquals("0.9.0.0", identity.getAttribute("Version"));
        assertEquals("x64", identity.getAttribute("ProcessorArchitecture"));
        assertEquals("Arief Mahendra", manifest.getElementsByTagName("PublisherDisplayName").item(0).getTextContent());
        var application = (org.w3c.dom.Element) manifest.getElementsByTagName("Application").item(0);
        assertEquals("SeeLoggyPlus.exe", application.getAttribute("Executable"));
        assertEquals("Windows.FullTrustApplication", application.getAttribute("EntryPoint"));
        assertEquals("runFullTrust", ((org.w3c.dom.Element) manifest.getElementsByTagNameNS(
                "http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities", "Capability")
                .item(0)).getAttribute("Name"));
        for (int size : new int[]{44, 50, 150}) {
            var logo = ImageIO.read(stage.resolve("Assets/Logo" + size + ".png").toFile());
            assertEquals(size, logo.getWidth());
            assertEquals(size, logo.getHeight());
        }
    }

    @Test
    void nativeLauncherManifestMatchesX64AndDeclaresPerMonitorDpi() throws Exception {
        StorePackageCli.main(new String[]{stage.toString(), "0.9.0",
                "AriefMahendra.SeeLoggyPlus", "CN=A19804B0-0953-43F2-B116-C24E0EED56BE", "Arief Mahendra"});
        Path nativeManifest = stage.resolve("StoreLauncher.manifest");
        assertTrue(Files.isRegularFile(nativeManifest), "Store launcher needs its own native manifest");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(nativeManifest.toFile());
        var identity = (org.w3c.dom.Element) document.getElementsByTagNameNS(
                "urn:schemas-microsoft-com:asm.v1", "assemblyIdentity").item(0);
        assertEquals("amd64", identity.getAttribute("processorArchitecture"));
        assertEquals("SeeLoggyPlus.exe", identity.getAttribute("name"));
        assertEquals("0.9.0.0", identity.getAttribute("version"));
        assertEquals("true/PM", document.getElementsByTagNameNS(
                "http://schemas.microsoft.com/SMI/2005/WindowsSettings", "dpiAware").item(0).getTextContent());
        assertEquals("PerMonitorV2, PerMonitor, system", document.getElementsByTagNameNS(
                "http://schemas.microsoft.com/SMI/2016/WindowsSettings", "dpiAwareness").item(0).getTextContent());
    }

    @Test
    void rejectsVersionsThatCannotBeSubmittedToTheStore() {
        for (String version : new String[]{"DEV", "0.9.0-beta", "1.2", "1.2.3.4", "65536.0.0"}) {
            assertThrows(IllegalArgumentException.class, () -> StorePackageCli.main(new String[]{
                    stage.toString(), version, "Package.Name", "CN=Publisher", "Publisher"}));
        }
        assertFalse(Files.exists(stage.resolve("AppxManifest.xml")));
    }
}
