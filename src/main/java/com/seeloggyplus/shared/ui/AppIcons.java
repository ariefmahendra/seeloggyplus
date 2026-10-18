package com.seeloggyplus.shared.ui;

import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class AppIcons {

    private static final Logger logger = LoggerFactory.getLogger(AppIcons.class);

    private AppIcons() {
    }

    public static void apply(Stage stage) {
        try {
            Image icon = new Image(Objects.requireNonNull(
                    AppIcons.class.getResourceAsStream("/images/app-icon.png")));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            logger.debug("Could not load the application icon", e);
        }
    }
}
