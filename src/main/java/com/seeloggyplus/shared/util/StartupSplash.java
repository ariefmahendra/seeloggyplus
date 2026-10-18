package com.seeloggyplus.shared.util;

import com.seeloggyplus.shared.ui.AppTheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.Timer;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;

public final class StartupSplash {

    private static final Logger logger = LoggerFactory.getLogger(StartupSplash.class);
    private static volatile String status = "Starting SeeLoggyPlus...";
    private static JWindow window;
    private static JLabel content;
    private static Timer animation;

    private StartupSplash() {
    }

    public static void show(AppTheme.Theme theme) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                disposeWindow();
                var frames = loadFrames(theme);
                content = createContent(theme, status, frames.getFirst());
                window = new JWindow();
                window.setFocusableWindowState(false);
                window.setContentPane(content);
                window.pack();
                window.setLocationRelativeTo(null);
                window.setVisible(true);
                int[] frame = {0};
                animation = new Timer(50, event -> {
                    frame[0] = (frame[0] + 1) % frames.size();
                    content.setIcon(frames.get(frame[0]));
                });
                animation.start();
            } catch (RuntimeException e) {
                logger.debug("Could not show startup splash", e);
                disposeWindow();
            }
        });
    }

    public static void showStatus(String message) {
        status = message;
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        EventQueue.invokeLater(() -> {
            if (content != null) {
                content.putClientProperty("startup.status", message);
                content.repaint();
            }
        });
    }

    public static void close() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        EventQueue.invokeLater(StartupSplash::disposeWindow);
    }

    private static void disposeWindow() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
        if (window != null) {
            window.dispose();
            window = null;
        }
        content = null;
    }

    static List<ImageIcon> loadFrames(AppTheme.Theme theme) {
        String name = (theme == null ? AppTheme.Theme.LIGHT : theme).preferenceValue();
        var resource = Objects.requireNonNull(StartupSplash.class.getResource("/images/startup-splash-" + name + ".gif"));
        var reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (var stream = resource.openStream(); var input = ImageIO.createImageInputStream(stream)) {
            reader.setInput(input);
            List<ImageIcon> frames = new ArrayList<>();
            for (int index = 0; index < reader.getNumImages(true); index++) {
                frames.add(new ImageIcon(reader.read(index)));
            }
            return frames;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            reader.dispose();
        }
    }

    static JLabel createContent(AppTheme.Theme theme, String message, ImageIcon image) {
        String name = (theme == null ? AppTheme.Theme.LIGHT : theme).preferenceValue();
        String resource = "/images/startup-splash-" + name;
        Properties palette = new Properties();
        try (var input = StartupSplash.class.getResourceAsStream(resource + ".properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing startup splash palette: " + name);
            }
            palette.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Color background = Color.decode(palette.getProperty("-sl-chrome"));
        Color foreground = Color.decode(palette.getProperty("-sl-chrome-text"));
        JLabel label = new JLabel(image) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D overlay = (Graphics2D) graphics.create();
                try {
                    overlay.setColor(background);
                    overlay.fillRect(32, 153, 416, 25);
                    overlay.setColor(foreground);
                    overlay.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                    overlay.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    overlay.drawString(Objects.toString(getClientProperty("startup.status"), ""), 32, 171);
                } finally {
                    overlay.dispose();
                }
            }
        };
        label.setBackground(background);
        label.setForeground(foreground);
        label.setOpaque(true);
        label.putClientProperty("startup.status", message);
        label.setSize(image.getIconWidth(), image.getIconHeight());
        return label;
    }
}
