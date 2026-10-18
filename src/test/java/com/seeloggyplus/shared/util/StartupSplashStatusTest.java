package com.seeloggyplus.shared.util;

import org.junit.jupiter.api.Test;
import com.seeloggyplus.shared.ui.AppTheme;
import java.awt.Color;

import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

class StartupSplashStatusTest {

    @Test
    void replacingStatusClearsOldTextAndPreservesBrandingAndAnimatedBar() throws Exception {
        BufferedImage original = splashFrame();
        BufferedImage updated = copy(original);
        paint(updated, "Preparing workspace...");
        paint(updated, "Loading preferences...");
        paint(updated, "Loading interface...");

        BufferedImage direct = copy(original);
        paint(direct, "Loading interface...");
        assertArrayEquals(pixels(direct, 0, 0, 480, 230), pixels(updated, 0, 0, 480, 230),
                "Changing phases must completely erase the preceding status");
        assertFalse(Arrays.equals(pixels(original, 32, 153, 416, 25),
                pixels(updated, 32, 153, 416, 25)), "The current phase must be visible");
        assertArrayEquals(pixels(original, 0, 0, 480, 153), pixels(updated, 0, 0, 480, 153));
        assertArrayEquals(pixels(original, 0, 178, 480, 52), pixels(updated, 0, 178, 480, 52),
                "Status overlays must not obscure the GIF's progress bar or version");
    }

    @Test
    void contentUsesTheSelectedPaletteAndFitsTheSplash() {
        for (var theme : AppTheme.Theme.values()) {
            var content = StartupSplash.createContent(theme, "Loading interface...", StartupSplash.loadFrames(theme).getFirst());
            assertEquals(480, content.getWidth());
            assertEquals(230, content.getHeight());
            var image = new BufferedImage(480, 230, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
            content.paint(graphics);
            } finally {
                graphics.dispose();
            }
            Color expected = Color.decode(theme == AppTheme.Theme.DARK ? "#26282c" : "#e9eaee");
            assertEquals(expected.getRGB(), image.getRGB(0, 10));
            assertEquals(expected.getRGB(), image.getRGB(32, 153));
        }
    }

    @Test
    void missingThemeFallsBackToLight() {
        var content = StartupSplash.createContent(null, "Starting...", StartupSplash.loadFrames(null).getFirst());
        var image = new BufferedImage(480, 230, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            content.paint(graphics);
        } finally {
            graphics.dispose();
        }
        assertEquals(Color.decode("#e9eaee").getRGB(), image.getRGB(0, 10));
    }

    @Test
    void statusAndRepeatedCloseAreSafeDuringHeadlessStartup() {
        assertTrue(GraphicsEnvironment.isHeadless(), "Startup tests must remain headless");
        assertDoesNotThrow(() -> {
            StartupSplash.show(AppTheme.Theme.DARK);
            StartupSplash.showStatus("Preparing workspace...");
            StartupSplash.showStatus("Loading interface...");
            StartupSplash.close();
            StartupSplash.showStatus("Opening main window...");
            StartupSplash.close();
        });
    }

    private static BufferedImage splashFrame() throws Exception {
        try (var resource = StartupSplashStatusTest.class.getResourceAsStream("/images/startup-splash-light.gif")) {
            assertNotNull(resource);
            return ImageIO.read(resource);
        }
    }

    private static BufferedImage copy(BufferedImage image) {
        var copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = copy.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static void paint(BufferedImage image, String status) {
        var graphics = image.createGraphics();
        try {
            var content = StartupSplash.createContent(AppTheme.Theme.LIGHT, status, StartupSplash.loadFrames(AppTheme.Theme.LIGHT).getFirst());
            content.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static int[] pixels(BufferedImage image, int x, int y, int width, int height) {
        return image.getRGB(x, y, width, height, null, 0, width);
    }
}
