package com.seeloggyplus.shared.ui;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IslandsPaletteTest {
    @Test void lightUsesWorkbenchSurfacesAndSelection() {
        var theme = AppTheme.Theme.LIGHT;
        assertEquals(Color.web("#eef0f3"), ThemePalette.color("-sl-chrome", theme));
        assertEquals(Color.WHITE, ThemePalette.color("-sl-surface", theme));
        assertEquals(Color.web("#192330"), ThemePalette.color("-sl-text", theme));
        assertEquals(Color.web("#d0dffe"), ThemePalette.color("-sl-selection-bg", theme));
        assertEquals(Color.web("#e3ebfe"), ThemePalette.color("-sl-tab-selected-bg", theme));
    }

    @Test void darkUsesWorkbenchSurfacesAndSelection() {
        var theme = AppTheme.Theme.DARK;
        assertEquals(Color.web("#20242b"), ThemePalette.color("-sl-chrome", theme));
        assertEquals(Color.web("#181c22"), ThemePalette.color("-sl-surface", theme));
        assertEquals(Color.web("#d1d3d9"), ThemePalette.color("-sl-text", theme));
        assertEquals(Color.web("#2a4371"), ThemePalette.color("-sl-selection-bg", theme));
        assertEquals(Color.web("#233558"), ThemePalette.color("-sl-tab-selected-bg", theme));
    }

    @Test void islandsTextAndIconsRemainReadableOnInteractiveSurfaces() {
        for (var theme : new AppTheme.Theme[] {AppTheme.Theme.LIGHT, AppTheme.Theme.DARK}) {
            for (String background : new String[] {"-sl-surface", "-sl-chrome", "-sl-selection-bg", "-sl-selection-inactive-bg", "-sl-tab-selected-bg", "-sl-toggle-selected-bg"}) {
                Color bg = ThemePalette.color(background, theme);
                assertTrue(contrast(ThemePalette.color("-sl-text", theme), bg) >= 4.5, theme + " " + background);
            }
            assertTrue(contrast(Color.WHITE, ThemePalette.color("-sl-brand", theme)) >= 4.5);
            assertTrue(contrast(ThemePalette.color("-sl-text-secondary", theme), ThemePalette.color("-sl-surface", theme)) >= 4.5);
        }
    }

    private static double contrast(Color a, Color b) {
        return (Math.max(luminance(a), luminance(b)) + .05) / (Math.min(luminance(a), luminance(b)) + .05);
    }
    private static double luminance(Color c) {
        return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue());
    }
    private static double linear(double v) {
        return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }
}
