package com.seeloggyplus.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XmlPrettifyTest {

    @Test
    @DisplayName("Should prettify pure XML with 2-space indentation")
    void testPrettifyPureXml() {
        String input = "<root><item id=\"1\"><name>SeeLoggyPlus</name></item></root>";
        String result = XmlPrettify.prettify(input);

        assertNotNull(result);
        assertTrue(result.contains("\n"));
        assertTrue(result.contains("  <item id=\"1\">"));
        assertTrue(result.contains("    <name>SeeLoggyPlus</name>"));
    }

    @Test
    @DisplayName("Should preserve XML declaration if present in input")
    void testPrettifyWithXmlDeclaration() {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><status>OK</status></root>";
        String result = XmlPrettify.prettify(input);

        assertNotNull(result);
        assertTrue(result.contains("<?xml"));
        assertTrue(result.contains("<root>"));
        assertTrue(result.contains("  <status>OK</status>"));
    }

    @Test
    @DisplayName("Should not add XML declaration if input did not have one")
    void testPrettifyWithoutXmlDeclaration() {
        String input = "<Order><id>12345</id><amount>99.9</amount></Order>";
        String result = XmlPrettify.prettify(input);

        assertNotNull(result);
        assertFalse(result.startsWith("<?xml"));
        assertTrue(result.contains("<Order>"));
        assertTrue(result.contains("  <id>12345</id>"));
    }

    @Test
    @DisplayName("Should handle namespaced XML and SOAP Envelope tags")
    void testPrettifyNamespacedXml() {
        String input = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<soapenv:Body><m:GetPrice><m:Symbol>ABC</m:Symbol></m:GetPrice></soapenv:Body></soapenv:Envelope>";
        String result = XmlPrettify.prettify(input);

        assertNotNull(result);
        assertTrue(result.contains("<soapenv:Envelope"));
        assertTrue(result.contains("<soapenv:Body>"));
        assertTrue(result.contains("<m:GetPrice>"));
        assertTrue(result.contains("<m:Symbol>ABC</m:Symbol>"));
    }

    @Test
    @DisplayName("Should extract and prettify XML from log line with prefix and suffix")
    void testPrettifyFromLogWithPrefix() {
        String log = "2026-09-05 10:00:00 [INFO] Response XML: <Response><status>SUCCESS</status><code>200</code></Response> - latency: 15ms";
        String result = XmlPrettify.prettifyFromLog(log);

        assertNotNull(result);
        assertTrue(result.startsWith("2026-09-05 10:00:00 [INFO] Response XML: "));
        assertTrue(result.endsWith(" - latency: 15ms"));
        assertTrue(result.contains("<Response>"));
        assertTrue(result.contains("  <status>SUCCESS</status>"));
        assertTrue(result.contains("  <code>200</code>"));
    }

    @Test
    @DisplayName("Should minify XML string")
    void testMinifyXml() {
        String input = "<root>\n  <item>\n    <name>Test</name>\n  </item>\n</root>";
        String result = XmlPrettify.minify(input);

        assertNotNull(result);
        assertFalse(result.contains("\n"));
        assertEquals("<root><item><name>Test</name></item></root>", result);
    }

    @Test
    @DisplayName("Should correctly validate valid and invalid XML")
    void testIsValidXml() {
        assertTrue(XmlPrettify.isValidXml("<root><child/></root>"));
        assertTrue(XmlPrettify.isValidXml("<?xml version=\"1.0\"?><data>1</data>"));

        assertFalse(XmlPrettify.isValidXml(null));
        assertFalse(XmlPrettify.isValidXml(""));
        assertFalse(XmlPrettify.isValidXml("   "));
        assertFalse(XmlPrettify.isValidXml("<unclosedTag>"));
        assertFalse(XmlPrettify.isValidXml("Just plain text with < and >"));
    }

    @Test
    @DisplayName("Should return original text if XML is malformed without throwing exception")
    void testMalformedXmlHandling() {
        String malformed = "<broken>xml without close";
        String result = XmlPrettify.prettify(malformed);
        assertEquals(malformed, result);

        String logWithBroken = "2026-09-05 [ERROR] Received: <open>tag mismatch</different> end";
        String logResult = XmlPrettify.prettifyFromLog(logWithBroken);
        assertEquals(logWithBroken, logResult);
    }
}
