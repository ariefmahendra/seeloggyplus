package com.seeloggyplus;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class StartupSplashTest {

    @Test
    void nativeSplashImageIsEmbeddedAndHasReadableBranding() throws Exception {
        try (var input = getClass().getResourceAsStream("/images/startup-splash.gif")) {
            assertNotNull(input, "The native splash must live inside the JAR for blue/green installations");
            var image = ImageIO.read(input);
            assertNotNull(image);
            assertEquals(480, image.getWidth());
            assertEquals(230, image.getHeight());

            String theme = Files.readString(Path.of("src/main/resources/style/theme.css"));
            Color background = token(theme, "-sl-chrome");
            Color foreground = token(theme, "-sl-chrome-text");
            assertEquals(background.getRGB(), image.getRGB(0, 10));
            assertTrue(contrast(foreground, background) >= 4.5, "Splash text must meet the text contrast threshold");

            int textPixels = 0;
            for (int y = 35; y < 100; y++) {
                for (int x = 120; x < 450; x++) {
                    if (image.getRGB(x, y) == foreground.getRGB()) {
                        textPixels++;
                    }
                }
            }
            assertTrue(textPixels > 100, "The splash must render visible application branding");
            Color segment = new Color(image.getRGB(60, 188));
            Color track = new Color(image.getRGB(400, 188));
            assertEquals(token(theme, "-sl-accent-text"), segment);
            assertEquals(token(theme, "-sl-chrome-hover"), track);
            assertTrue(contrast(segment, track) >= 4.5, "The moving segment must remain clearly visible on its track");
        }
    }

    @Test
    void loadingBarMovesInALoopingAnimationWhileBrandingStaysStable() throws Exception {
        try (var resource = getClass().getResourceAsStream("/images/startup-splash.gif")) {
            assertNotNull(resource);
            try (var input = ImageIO.createImageInputStream(resource)) {
                var reader = ImageIO.getImageReadersByFormatName("gif").next();
                try {
                    reader.setInput(input);
                    int frames = reader.getNumImages(true);
                    assertTrue(frames >= 12, "The native splash needs enough frames for a smooth loading bar");
                    var first = reader.read(0);
                    var middle = reader.read(frames / 2);
                    boolean barMoved = false;
                    for (int y = 184; y < 193; y++) {
                        for (int x = 32; x < 448; x++) {
                            barMoved |= first.getRGB(x, y) != middle.getRGB(x, y);
                        }
                    }
                    assertTrue(barMoved, "The progress segment must move while startup is busy");
                    assertArrayEquals(first.getRGB(0, 0, 480, 180, null, 0, 480),
                            middle.getRGB(0, 0, 480, 180, null, 0, 480),
                            "Animating the bar must not flicker the logo and startup text");

                    int cycleDelay = 0;
                    for (int i = 0; i < frames; i++) {
                        var metadata = (IIOMetadataNode) reader.getImageMetadata(i)
                                .getAsTree("javax_imageio_gif_image_1.0");
                        var control = (IIOMetadataNode) metadata.getElementsByTagName("GraphicControlExtension").item(0);
                        int delay = Integer.parseInt(control.getAttribute("delayTime"));
                        assertTrue(delay >= 4 && delay <= 10, "Animation should run at a modest, smooth frame rate");
                        cycleDelay += delay;
                    }
                    assertTrue(cycleDelay <= 200, "A loading animation cycle should take at most two seconds");

                    var metadata = (IIOMetadataNode) reader.getImageMetadata(0)
                            .getAsTree("javax_imageio_gif_image_1.0");
                    var extension = (IIOMetadataNode) metadata.getElementsByTagName("ApplicationExtension").item(0);
                    assertNotNull(extension, "The animation must continue during a slow startup");
                    assertEquals("NETSCAPE", extension.getAttribute("applicationID"));
                    assertArrayEquals(new byte[]{1, 0, 0}, (byte[]) extension.getUserObject(),
                            "Zero loop count means repeat until the splash closes");
                } finally {
                    reader.dispose();
                }
            }
        }
    }

    @Test
    void splashIsConfiguredBeforeMainForBothJarAndDevLaunches() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        var manifestEntries = Pattern.compile("'SplashScreen-Image'\\s*:\\s*'images/startup-splash.gif'")
                .matcher(build);
        assertEquals(2, manifestEntries.results().count(), "Both jar and fatJar need a native splash manifest entry");
        assertTrue(build.contains("-splash:"), "runDev must show the same early startup feedback");
        assertFalse(build.contains("startup-splash.png"), "The retired static splash must not be generated or launched");
    }

    @Test
    void splashTaglineMatchesTheReadmeHeadline() throws Exception {
        String readme = Files.readString(Path.of("README.md"));
        String headline = readme.lines()
                .filter(line -> line.startsWith("# "))
                .findFirst()
                .orElseThrow(() -> new AssertionError("README.md must start with a level-1 headline"))
                .replaceFirst("^#\\s+", "");
        int dash = headline.indexOf('—');
        String tagline = dash >= 0 ? headline.substring(dash + 1).trim() : headline;
        assertFalse(tagline.isBlank(), "the README headline must carry the product tagline");

        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("graphics.drawString('" + tagline + "'"),
                "The splash tagline must match the README headline exactly, expected: " + tagline);
        assertFalse(build.contains("Local & SSH log viewer"),
                "The old splash tagline must not survive next to the README wording");
    }

    private static Color token(String css, String name) {
        var matcher = Pattern.compile(Pattern.quote(name) + "\\s*:\\s*(#[0-9a-fA-F]{6})").matcher(css);
        assertTrue(matcher.find(), "Missing theme token " + name);
        return Color.decode(matcher.group(1));
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a);
        double second = luminance(b);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
