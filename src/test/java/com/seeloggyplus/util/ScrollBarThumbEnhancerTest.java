package com.seeloggyplus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrollBarThumbEnhancerTest {

    @Test
    void growsVisibleAmountToProduceAGrabbableThumb() {
        // max 500, visible 10 -> total 510, 12% = 61.2
        assertEquals(61.2, ScrollBarThumbEnhancer.requiredVisibleAmount(500, 10, 0.12), 0.0001);
    }

    @Test
    void keepsVisibleAmountWhenAlreadyLargeEnough() {
        // max 500, visible 100 -> total 600, 12% = 72 -> keep 100
        assertEquals(100.0, ScrollBarThumbEnhancer.requiredVisibleAmount(500, 100, 0.12), 0.0001);
    }

    @Test
    void handlesDegenerateInputs() {
        assertEquals(10.0, ScrollBarThumbEnhancer.requiredVisibleAmount(0, 10, 0.12), 0.0001);
        assertEquals(0.0, ScrollBarThumbEnhancer.requiredVisibleAmount(500, 0, 0.12), 0.0001);
        assertEquals(10.0, ScrollBarThumbEnhancer.requiredVisibleAmount(500, 10, 0), 0.0001);
    }
}
