package com.seeloggyplus.shared.ui;

import com.seeloggyplus.shared.ui.AppTheme.Theme;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThemePaletteTest {

    @Test
    @DisplayName("base tokens resolve for graphite and are overridden per variant")
    void resolvesBaseAndVariantTokens() {
        assertEquals(Color.web("#ffffff"), ThemePalette.color("-sl-surface", Theme.GRAPHITE));
        assertEquals(Color.web("#ffffff"), ThemePalette.color("-sl-surface", Theme.LIGHT));
        assertEquals(Color.web("#1e2226"), ThemePalette.color("-sl-surface", Theme.DARK));

        assertEquals(Color.web("#4a535e"), ThemePalette.color("-sl-accent", Theme.GRAPHITE));
        assertEquals(Color.web("#2563eb"), ThemePalette.color("-sl-accent", Theme.LIGHT));
        assertEquals(Color.web("#434b53"), ThemePalette.color("-sl-accent", Theme.DARK));
    }

    @Test
    @DisplayName("rgba tokens keep their alpha")
    void parsesAlphaTokens() {
        assertEquals(Color.web("rgba(52,58,64,0.18)"),
                ThemePalette.color("-sl-selection-bg", Theme.GRAPHITE));
        assertEquals(Color.web("rgba(110,168,254,0.28)"),
                ThemePalette.color("-sl-selection-bg", Theme.DARK));
    }

    @Test
    @DisplayName("canvas and level-extreme tokens resolve for every theme")
    void resolvesCanvasTokens() {
        assertEquals(Color.web("#6c757d"), ThemePalette.color("-sl-canvas-line-number", Theme.GRAPHITE));
        assertEquals(Color.web("#6c757d"), ThemePalette.color("-sl-canvas-line-number", Theme.LIGHT));
        assertEquals(Color.web("#8b939c"), ThemePalette.color("-sl-canvas-line-number", Theme.DARK));

        assertEquals(Color.web("#ffff00"), ThemePalette.color("-sl-canvas-highlight", Theme.GRAPHITE));
        assertEquals(Color.web("rgba(250,204,21,0.35)"),
                ThemePalette.color("-sl-canvas-highlight", Theme.DARK));

        assertEquals(Color.web("#d97706"), ThemePalette.color("-sl-level-warn-bar", Theme.GRAPHITE));
        assertEquals(Color.web("#f59e0b"), ThemePalette.color("-sl-level-warn-bar", Theme.DARK));
        assertEquals(Color.web("rgba(239,68,68,0.10)"),
                ThemePalette.color("-sl-level-error-tint", Theme.DARK));
    }

    @Test
    @DisplayName("a null theme falls back to graphite")
    void nullThemeFallsBackToGraphite() {
        assertEquals(ThemePalette.color("-sl-surface", Theme.GRAPHITE),
                ThemePalette.color("-sl-surface", null));
    }

    @Test
    @DisplayName("unknown tokens are rejected instead of rendering wrong colours")
    void rejectsUnknownTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> ThemePalette.color("-sl-does-not-exist", Theme.GRAPHITE));
    }
}
