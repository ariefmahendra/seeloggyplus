package com.seeloggyplus.shared.ui;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public final class AppIcons {

    private AppIcons() {
    }

    public static void apply(Stage stage) {
        try {
            Image icon = new Image(Objects.requireNonNull(
                    AppIcons.class.getResourceAsStream("/images/app-icon.png")));
            stage.getIcons().add(icon);
        } catch (Exception ignored) {
        }
    }
}
