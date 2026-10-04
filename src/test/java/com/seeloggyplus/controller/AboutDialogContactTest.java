package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The About dialog must expose the maintainer contact as a clickable mail link
 * that follows the theme instead of the default JavaFX hyperlink colour.
 */
@ExtendWith(ApplicationExtension.class)
class AboutDialogContactTest {

    private static final String EMAIL = "mahend.arief@gmail.com";

    private Stage stage;
    private Parent root;
    private AboutDialogController controller;
    private final List<String> opened = new ArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AboutDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
        controller.setBrowser(opened::add);
        stage.setScene(AppTheme.scene(root));
        stage.show();
        apply();
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setTheme(AppTheme.Theme.GRAPHITE));
    }

    @Test
    void aboutDialogShowsTheContactEmail() {
        onFxThread(() -> {
            Hyperlink contact = contactLink();
            assertEquals(EMAIL, contact.getText(), "the maintainer email must be visible in About");
            assertTrue(contact.getTooltip() != null && contact.getTooltip().getText().contains(EMAIL),
                    "hovering should explain where the link goes");
        });
    }

    @Test
    void clickingTheContactOpensAMailToLink() {
        onFxThread(() -> contactLink().fire());
        assertEquals(List.of("mailto:" + EMAIL), opened,
                "clicking the contact must ask the system to compose an email");
    }

    @Test
    void contactLinkFollowsTheThemeInEveryPalette() {
        onFxThread(() -> {
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                apply();
                Hyperlink contact = contactLink();
                Color fill = (Color) contact.getTextFill();
                Color background = background(contact);
                assertTrue(contrast(fill, background) >= 4.5,
                        "contact link must stay readable in " + theme + " (fill=" + fill + ", background=" + background + ")");
                assertNotEquals(Color.web("#0000ee"), fill,
                        "the default JavaFX link blue must not leak through in " + theme);
            }
        });
    }

    @Test
    void contactRemainsReadableAfterHoverAndFocus() {
        onFxThread(() -> {
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                Hyperlink contact = contactLink();
                contact.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                contact.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
                apply();
                Color fill = (Color) contact.getTextFill();
                assertTrue(contrast(fill, background(contact)) >= 4.5,
                        "hover/focus must keep the link readable in " + theme);
                contact.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
                contact.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), false);
            }
        });
    }

    private Hyperlink contactLink() {
        Hyperlink contact = (Hyperlink) root.lookup("#contactLink");
        assertNotNull(contact, "About must render a #contactLink hyperlink");
        return contact;
    }

    private void apply() {
        root.applyCss();
        root.layout();
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Color background(Node node) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof Region region && region.getBackground() != null) {
                var fills = region.getBackground().getFills();
                for (int i = fills.size() - 1; i >= 0; i--) {
                    Paint paint = fills.get(i).getFill();
                    if (paint instanceof Color color && color.getOpacity() == 1) {
                        return color;
                    }
                }
            }
        }
        throw new AssertionError("no opaque background found for " + node);
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a);
        double second = luminance(b);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static void onFxThread(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }
}
