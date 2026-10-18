package com.seeloggyplus.shared.ui;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

final class KeyboardFocusStyles {
    private static final Object SCENE_KEY = new Object();
    private static final Object ROOT_KEY = new Object();
    private static final PseudoClass KEYBOARD_FOCUS = PseudoClass.getPseudoClass("keyboard-focus");
    private boolean keyboard;

    private KeyboardFocusStyles(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case TAB, UP, DOWN, LEFT, RIGHT, HOME, END, SPACE, ENTER -> {
                    keyboard = true;
                    mark(scene.getFocusOwner(), true);
                }
                default -> { }
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            keyboard = false;
            mark(scene.getFocusOwner(), false);
        });
        scene.focusOwnerProperty().addListener((observable, oldOwner, newOwner) -> {
            mark(oldOwner, false);
            mark(newOwner, keyboard);
        });
    }

    static void install(Scene scene) {
        if (scene != null && !scene.getProperties().containsKey(SCENE_KEY)) {
            scene.getProperties().put(SCENE_KEY, new KeyboardFocusStyles(scene));
        }
    }

    static void install(Parent root) {
        install(root.getScene());
        if (root.getProperties().putIfAbsent(ROOT_KEY, Boolean.TRUE) == null) {
            root.sceneProperty().addListener((observable, oldScene, newScene) -> install(newScene));
        }
    }

    private static void mark(Node owner, boolean enabled) {
        if (owner != null) owner.pseudoClassStateChanged(KEYBOARD_FOCUS, enabled && !owner.isDisabled());
    }
}
