package com.seeloggyplus.shared.ui;

import com.seeloggyplus.shared.ui.AppTheme.Theme;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThemePaletteTest {

    @Test
    @DisplayName("tokens resolve independently for Light and Dark")
    void resolvesEachThemeTokenSet() {
        assertEquals(Color.web("#ffffff"), ThemePalette.color("-sl-surface", Theme.LIGHT));
        assertEquals(Color.web("#191a1c"), ThemePalette.color("-sl-surface", Theme.DARK));

        assertEquals(Color.web("#3871e1"), ThemePalette.color("-sl-accent", Theme.LIGHT));
        assertEquals(Color.web("#3871e1"), ThemePalette.color("-sl-accent", Theme.DARK));
    }

    @Test
    @DisplayName("rgba tokens keep their alpha")
    void parsesAlphaTokens() {
        assertEquals(Color.web("rgba(0,0,0,0.070588)"),
                ThemePalette.color("-sl-button-hover", Theme.LIGHT));
        assertEquals(Color.web("#2a4371"),
                ThemePalette.color("-sl-selection-bg", Theme.DARK));
    }

    @Test
    @DisplayName("canvas and level-extreme tokens resolve for every theme")
    void resolvesCanvasTokens() {
        assertEquals(Color.web("#5f6269"), ThemePalette.color("-sl-canvas-line-number", Theme.LIGHT));
        assertEquals(Color.web("#9fa2a8"), ThemePalette.color("-sl-canvas-line-number", Theme.DARK));

        assertEquals(Color.web("#ffff00"), ThemePalette.color("-sl-canvas-highlight", Theme.LIGHT));
        assertEquals(Color.web("rgba(250,204,21,0.35)"),
                ThemePalette.color("-sl-canvas-highlight", Theme.DARK));

        assertEquals(Color.web("#d97706"), ThemePalette.color("-sl-level-warn-bar", Theme.LIGHT));
        assertEquals(Color.web("#f59e0b"), ThemePalette.color("-sl-level-warn-bar", Theme.DARK));
        assertEquals(Color.web("rgba(239,68,68,0.10)"),
                ThemePalette.color("-sl-level-error-tint", Theme.DARK));
    }

    @Test
    @DisplayName("a null theme falls back to Light")
    void nullThemeFallsBackToLight() {
        assertEquals(ThemePalette.color("-sl-surface", Theme.LIGHT),
                ThemePalette.color("-sl-surface", null));
    }

    @Test
    @DisplayName("unknown tokens are rejected instead of rendering wrong colours")
    void rejectsUnknownTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> ThemePalette.color("-sl-does-not-exist", Theme.LIGHT));
    }
}
