package com.seeloggyplus.features.logs.infrastructure.parsing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GrokLogFormatDetectorTest {

    private final GrokLogFormatDetector detector = new GrokLogFormatDetector();

    @Test
    void emptySamplesHaveNoDetectedFormat() {
        assertNull(detector.detect(null));
        assertNull(detector.detect(List.of()));
        assertNull(detector.detect(List.of(" ", "")));
    }

    @Test
    void isoLogRetainsDetectedRegexAndTimestampFormat() {
        var config = detector.detect(List.of("2026-10-12 10:20:30 INFO Application started"));
        assertEquals("Auto-Detected (ISO8601 Standard)", config.getName());
        assertEquals("yyyy-MM-dd HH:mm:ss", config.getTimestampFormat());
        assertDoesNotThrow(config::validatePattern);
    }

    @Test
    void jsonAndUnstructuredLogsKeepTheirFallbacks() {
        assertEquals("JSON Log", detector.detect(List.of("{\"message\":\"hello\"}")).getName());
        assertEquals("Generic Log", detector.detect(List.of("hello world")).getName());
    }
}
