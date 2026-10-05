package com.seeloggyplus.features.update.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateAwarenessTest {

    private static final long HOUR = 60L * 60 * 1000;

    @Test
    @DisplayName("a check is due only after the interval and when not snoozed")
    void checkDueRules() {
        // Comfortably after the interval, so "last = 0" means "never checked".
        long now = 1_000_000_000L;

        assertFalse(UpdateAwareness.isCheckDue(now, now - HOUR, 0, UpdateAwareness.CHECK_INTERVAL_MS),
                "recent check must not trigger another one");
        assertTrue(UpdateAwareness.isCheckDue(now, now - UpdateAwareness.CHECK_INTERVAL_MS, 0,
                UpdateAwareness.CHECK_INTERVAL_MS));
        assertFalse(UpdateAwareness.isCheckDue(now, 0, now + HOUR, UpdateAwareness.CHECK_INTERVAL_MS),
                "a snooze must suppress checks until it expires");
        assertTrue(UpdateAwareness.isCheckDue(now, 0, now - 1, UpdateAwareness.CHECK_INTERVAL_MS));
    }

    @Test
    @DisplayName("the dialog pops up unless the version was skipped or snoozed")
    void popupRules() {
        long now = 1_000_000_000L;

        assertTrue(UpdateAwareness.shouldPopup(now, 0, "0.7.0", null));
        assertTrue(UpdateAwareness.shouldPopup(now, now - 1, "0.7.0", "0.6.9"));
        assertFalse(UpdateAwareness.shouldPopup(now, 0, "0.7.0", "0.7.0"),
                "a skipped version must not pop up again");
        assertFalse(UpdateAwareness.shouldPopup(now, now + HOUR, "0.7.0", null),
                "snoozed reminders must not pop up");
        assertFalse(UpdateAwareness.shouldPopup(now, 0, null, null));
    }

    @Test
    @DisplayName("the status-bar indicator text describes each state")
    void indicatorTexts() {
        assertTrue(UpdateAwareness.indicatorText(UpdateAwareness.IndicatorState.AVAILABLE, "0.7.0", 0)
                .contains("Update 0.7.0 available"));
        assertTrue(UpdateAwareness.indicatorText(UpdateAwareness.IndicatorState.DOWNLOADING, "0.7.0", 42)
                .contains("42%"));
        assertTrue(UpdateAwareness.indicatorText(UpdateAwareness.IndicatorState.READY, "0.7.0", 100)
                .toLowerCase().contains("restart"));
        assertTrue(UpdateAwareness.indicatorText(UpdateAwareness.IndicatorState.FAILED, "0.7.0", 0)
                .toLowerCase().contains("failed"));
        assertEquals("", UpdateAwareness.indicatorText(UpdateAwareness.IndicatorState.NONE, "0.7.0", 0));
        assertEquals("", UpdateAwareness.indicatorText(UpdateAwareness.IndicatorState.AVAILABLE, null, 0));
        assertTrue(UpdateAwareness.indicatorText(UpdateAwareness.IndicatorState.DOWNLOADING, "0.7.0", 250)
                .contains("100%"), "percent must be clamped");
    }

    @Test
    @DisplayName("snooze durations match the offered choices")
    void snoozeDurations() {
        assertEquals(HOUR, UpdateAwareness.snoozeMillis(UpdateAwareness.Snooze.ONE_HOUR));
        assertEquals(8 * HOUR, UpdateAwareness.snoozeMillis(UpdateAwareness.Snooze.EIGHT_HOURS));
        assertEquals(24 * HOUR, UpdateAwareness.snoozeMillis(UpdateAwareness.Snooze.TOMORROW));
        assertEquals(0, UpdateAwareness.snoozeMillis(null));
    }
}
