package com.seeloggyplus.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonPrettifyTest {

    @Test
    @DisplayName("Should prettify pure JSON object with 2-space indent")
    void testPrettifyPureJsonObject() {
        String input = "{\"name\":\"SeeLoggyPlus\",\"active\":true,\"version\":2}";
        String result = JsonPrettify.prettify(input);

        assertNotNull(result);
        assertTrue(result.contains("\n"));
        assertTrue(result.contains("\"name\" : \"SeeLoggyPlus\""));
        assertTrue(result.contains("\"active\" : true"));
    }

    @Test
    @DisplayName("Should prettify pure JSON array")
    void testPrettifyPureJsonArray() {
        String input = "[\"apple\",\"banana\",\"cherry\"]";
        String result = JsonPrettify.prettify(input);

        assertNotNull(result);
        assertTrue(result.contains("\n"));
        assertTrue(result.contains("\"banana\""));
    }

    @Test
    @DisplayName("Should format JSON with custom indentation (4 spaces)")
    void testPrettifyWithCustomIndent() {
        String input = "{\"key\":\"value\"}";
        String result = JsonPrettify.prettify(input, 4);

        assertNotNull(result);
        assertTrue(result.contains("    \"key\" : \"value\""));
    }

    @Test
    @DisplayName("Should correctly extract and prettify JSON from log with prefix containing curly braces")
    void testPrettifyFromLogWithPrefixAndCurlyBraces() {
        String log = "2026-09-05 10:00:00 [INFO] {worker-1} - Received payload: {\"userId\":101,\"name\":\"Alice\"} successfully.";
        String result = JsonPrettify.prettifyFromLog(log);

        assertNotNull(result);
        // Prefix and suffix must remain intact
        assertTrue(result.startsWith("2026-09-05 10:00:00 [INFO] {worker-1} - Received payload: "));
        assertTrue(result.endsWith(" successfully."));
        // JSON inside must be formatted
        assertTrue(result.contains("\"userId\" : 101"));
        assertTrue(result.contains("\"name\" : \"Alice\""));
    }

    @Test
    @DisplayName("Should correctly extract and prettify JSON array when log prefix contains bracket like [INFO]")
    void testPrettifyFromLogWithLogLevelBrackets() {
        String log = "2026-09-05 10:00:00 [INFO] [Thread-1] Items: [1,2,3] processed.";
        String result = JsonPrettify.prettifyFromLog(log);

        assertNotNull(result);
        assertTrue(result.startsWith("2026-09-05 10:00:00 [INFO] [Thread-1] Items: "));
        assertTrue(result.endsWith(" processed."));
        assertTrue(result.contains("1"));
        assertTrue(result.contains("2"));
        assertTrue(result.contains("3"));
    }

    @Test
    @DisplayName("Should handle JSON string values containing curly braces and escaped quotes")
    void testPrettifyJsonWithCurlysInsideString() {
        String input = "{\"pattern\":\"{id:[0-9]+}\",\"message\":\"Hello \\\"World\\\" {user}\"}";
        String result = JsonPrettify.prettify(input);

        assertNotNull(result);
        assertTrue(result.contains("\"{id:[0-9]+}\""));
        assertTrue(result.contains("\"Hello \\\"World\\\" {user}\""));
    }

    @Test
    @DisplayName("Should minify JSON string")
    void testMinifyJson() {
        String input = "{\n  \"name\" : \"Test\",\n  \"count\" : 42\n}";
        String result = JsonPrettify.minify(input);

        assertNotNull(result);
        assertFalse(result.contains("\n"));
        assertEquals("{\"name\":\"Test\",\"count\":42}", result);
    }

    @Test
    @DisplayName("Should correctly validate valid and invalid JSON")
    void testIsValidJson() {
        assertTrue(JsonPrettify.isValidJson("{\"ok\":true}"));
        assertTrue(JsonPrettify.isValidJson("[1, 2, 3]"));

        assertFalse(JsonPrettify.isValidJson(null));
        assertFalse(JsonPrettify.isValidJson(""));
        assertFalse(JsonPrettify.isValidJson("   "));
        assertFalse(JsonPrettify.isValidJson("{invalid json}"));
        assertFalse(JsonPrettify.isValidJson("[INFO]"));
        assertFalse(JsonPrettify.isValidJson("{worker-1}"));
    }

    @Test
    @DisplayName("Should return original text if input is malformed and not throw exception")
    void testMalformedJsonHandling() {
        String malformed = "{unclosed json: 123";
        String result = JsonPrettify.prettify(malformed);
        assertEquals(malformed, result);

        String logWithMalformed = "2026-09-05 [ERROR] Bad json: {bad:value without quotes} end";
        String logResult = JsonPrettify.prettifyFromLog(logWithMalformed);
        assertEquals(logWithMalformed, logResult);
    }
}
