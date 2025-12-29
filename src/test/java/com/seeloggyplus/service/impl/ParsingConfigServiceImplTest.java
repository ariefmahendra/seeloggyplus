package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.service.ParsingConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParsingConfigServiceImplTest {

    private ParsingConfigService service;

    @BeforeEach
    public void setUp() {
        service = new ParsingConfigServiceImpl(null);
    }

    // --- 1. ISO 8601 Extended ---

    @Test
    public void testDetect_ISO_Basic() {
        verifyLog("2025-01-01T10:15:30Z INFO Msg", "2025-01-01T10:15:30Z", "yyyy-MM-dd'T'HH:mm:ssXXX");
        verifyLog("2025-01-01T10:15:30+07:00 INFO Msg", "2025-01-01T10:15:30+07:00", "yyyy-MM-dd'T'HH:mm:ssXXX");
    }

    @Test
    public void testDetect_ISO_Fractional() {
        verifyLog("2025-01-01T10:15:30.123Z INFO Msg", "2025-01-01T10:15:30.123Z", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        verifyLog("2025-01-01T10:15:30.123456Z INFO Msg", "2025-01-01T10:15:30.123456Z",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    }

    @Test
    public void testDetect_ISO_SpaceSeparator() {
        verifyLog("2025-01-01 10:15:30Z INFO Msg", "2025-01-01 10:15:30Z", "yyyy-MM-dd HH:mm:ssXXX");
        verifyLog("2025-01-01 10:15:30+0700 INFO Msg", "2025-01-01 10:15:30+0700", "yyyy-MM-dd HH:mm:ssXXX");
    }

    // --- 2. Unix / Epoch ---

    @Test
    public void testDetect_Epoch_Seconds() {
        verifyLog("1735726530 INFO Msg", "1735726530", "Epoch (Unix Timestamp)");
    }

    @Test
    public void testDetect_Epoch_Millis() {
        verifyLog("1735726530123 INFO Msg", "1735726530123", "Epoch (Unix Timestamp)");
    }

    @Test
    public void testDetect_Epoch_Decimal() {
        verifyLog("1735726530.123 INFO Msg", "1735726530.123", "Epoch (Unix Timestamp)");
    }

    // --- 3. Syslog & Unix Family ---

    @Test
    public void testDetect_Syslog_RFC3164() {
        verifyLog("Jan  1 10:15:30 myhost app: msg", "Jan  1 10:15:30", "MMM dd HH:mm:ss");
    }

    @Test
    public void testDetect_Syslog_BSD() {
        verifyLog("Tue Jan  1 10:15:30 2025 myhost app: msg", "Tue Jan  1 10:15:30 2025", "EEE MMM dd HH:mm:ss yyyy");
    }

    // --- 4. Web Server & Proxy ---

    @Test
    public void testDetect_Apache_Access() {
        verifyLog("127.0.0.1 - - [01/Jan/2025:10:15:30 +0700] \"GET / HTTP/1.1\"", "01/Jan/2025:10:15:30 +0700",
                "dd/MMM/yyyy:HH:mm:ss Z");
    }

    @Test
    public void testDetect_Apache_Error() {
        // [Wed Jan 01 10:15:30.123456 2025] [error] Msg
        verifyLog("[Wed Jan 01 10:15:30.123456 2025] [error] Msg", "Wed Jan 01 10:15:30.123456 2025",
                "EEE MMM dd HH:mm:ss yyyy");
    }

    // --- 5. Java / JVM ---

    @Test
    public void testDetect_Log4j_Comma() {
        verifyLog("2025-01-01 10:15:30,123 INFO Msg", "2025-01-01 10:15:30,123", "yyyy-MM-dd HH:mm:ss.SSS");
    }

    // --- 6. Database Logs ---

    @Test
    public void testDetect_Postgres() {
        // Validation note: The parser separates "UTC" into the timezone group.
        // So the timestamp group will only contain the date/time part.
        verifyLog("2025-01-01 10:15:30.123 UTC LOG:  msg", "2025-01-01 10:15:30.123", "yyyy-MM-dd HH:mm:ss.SSS");
    }

    @Test
    public void testDetect_Oracle() {
        verifyLog("01-JAN-2025 22:15:30 Agent started", "01-JAN-2025 22:15:30", "dd-MMM-yyyy HH:mm:ss");
    }

    // --- 7. Cloud / Container ---

    @Test
    public void testDetect_Docker_Stdout() {
        verifyLog("2025-01-01T10:15:30.123456789Z stdout F Msg", "2025-01-01T10:15:30.123456789Z",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    }

    // --- 8. Region / Locale ---

    @Test
    public void testDetect_US_Date() {
        verifyLog("01/01/2025 10:15:30 AM INFO Msg", "01/01/2025 10:15:30 AM", "MM/dd/yyyy HH:mm:ss");
    }

    @Test
    public void testDetect_EU_Date() {
        verifyLog("01.01.2025 10:15:30 INFO Msg", "01.01.2025 10:15:30", "dd.MM.yyyy HH:mm:ss");
    }

    // --- 9. Weird / Real World ---

    @Test
    public void testDetect_TimeOnly() {
        verifyLog("10:15:30,123 INFO Msg", "10:15:30,123", "HH:mm:ss.SSS");
    }

    @Test
    public void testDetect_Logback_Pipe() {
        String log = "2025-01-01 10:15:30,123 |-INFO in MyContext - msg";
        verifyLog(log, "2025-01-01 10:15:30,123", "yyyy-MM-dd HH:mm:ss.SSS");

        // Detailed check for context
        ParsingConfig config = service.detectLogFormat(Collections.singletonList(log));
        Matcher m = Pattern.compile(config.getRegexPattern()).matcher(log);
        Assertions.assertTrue(m.find());
        Assertions.assertEquals("INFO", m.group("level"));
        Assertions.assertEquals("MyContext", m.group("context"));
    }

    @Test
    public void testDetect_CompactFormat() {
        // 14 digits starting with 20
        verifyLog("20250101101530 INFO Msg", "20250101101530", "yyyyMMddHHmmss");
    }

    // --- Helper ---

    private void verifyLog(String log, String expectedTimestamp, String expectedFormat) {
        try {
            ParsingConfig config = service.detectLogFormat(Collections.singletonList(log));
            Assertions.assertNotNull(config, "Failed to detect config for: " + log);

            if (expectedFormat != null) {
                Assertions.assertEquals(expectedFormat, config.getTimestampFormat(),
                        "Wrong format detected for: " + log);
            }

            Pattern p = Pattern.compile(config.getRegexPattern());
            Matcher m = p.matcher(log);
            Assertions.assertTrue(m.find(), "Regex " + config.getRegexPattern() + " failed to match: " + log);
            if (expectedTimestamp != null) {
                Assertions.assertEquals(expectedTimestamp, m.group("timestamp"));
            }
        } catch (AssertionError e) {
            System.err.println("TEST FAILURE for log: " + log);
            System.err.println("Error: " + e.getMessage());
            throw e;
        }
    }
}
