package com.seeloggyplus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Updates the status over the JVM's animated splash; native GIF playback owns the bar. */
public final class StartupSplash {

    private static final Logger logger = LoggerFactory.getLogger(StartupSplash.class);
    private static Properties palette;

    private StartupSplash() {
    }

    public static synchronized void showStatus(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            SplashScreen splash = SplashScreen.getSplashScreen();
            if (splash == null || !splash.isVisible()) {
                return;
            }
            Graphics2D graphics = splash.createGraphics();
            try {
                paintStatus(graphics, message);
            } finally {
                graphics.dispose();
            }
            splash.update();
        } catch (RuntimeException e) {
            // Splash support is optional and must never prevent startup.
            logger.debug("Could not update startup splash", e);
        }
    }

    public static synchronized void close() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            SplashScreen splash = SplashScreen.getSplashScreen();
            if (splash != null && splash.isVisible()) {
                splash.close();
            }
        } catch (RuntimeException e) {
            logger.debug("Could not close startup splash", e);
        }
    }

    static void paintStatus(Graphics2D graphics, String message) {
        graphics.setColor(color("-sl-chrome"));
        graphics.fillRect(32, 153, 416, 25);
        graphics.setColor(color("-sl-chrome-text"));
        graphics.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.drawString(message, 32, 171);
    }

    private static synchronized Color color(String token) {
        if (palette == null) {
            Properties colors = new Properties();
            try (var input = StartupSplash.class.getResourceAsStream("/images/startup-splash.properties")) {
                if (input == null) {
                    throw new IllegalStateException("Missing startup splash palette");
                }
                colors.load(input);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            palette = colors;
        }
        return Color.decode(palette.getProperty(token));
    }
}
