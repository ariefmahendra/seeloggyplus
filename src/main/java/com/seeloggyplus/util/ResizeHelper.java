package com.seeloggyplus.util;

import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class ResizeHelper {
    private static final double RESIZE_MARGIN = 5.0; // Margin around the window for resizing

    public static void addResizeListener(Stage stage) {
        ResizeListener listener = new ResizeListener(stage);
        stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                listener.addListeners(newScene);
            }
        });
        // If scene is already set
        if (stage.getScene() != null) {
            listener.addListeners(stage.getScene());
        }
    }

    private static class ResizeListener {
        private final Stage stage;
        private Cursor cursorEvent = Cursor.DEFAULT;
        private double startX = 0;
        private double startY = 0;
        private double startScreenX = 0;
        private double startScreenY = 0;
        private double startWidth = 0;
        private double startHeight = 0;

        public ResizeListener(Stage stage) {
            this.stage = stage;
        }

        public void addListeners(Scene scene) {
            scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::handleMouseMoved);
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleMousePressed);
            scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
            scene.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleMouseReleased);
        }

        private void handleMouseMoved(MouseEvent event) {
            // Allow resize handling even if "pseudo" maximized.
            // The constraint of "if (stage.isMaximized()) return" is removed
            // because we want resizing to implicitly "restore" the window.

            double mouseEventX = event.getSceneX();
            double mouseEventY = event.getSceneY();
            double sceneWidth = stage.getScene().getWidth();
            double sceneHeight = stage.getScene().getHeight();

            Cursor cursorType = Cursor.DEFAULT;

            boolean inLeft = mouseEventX < RESIZE_MARGIN;
            boolean inRight = mouseEventX > sceneWidth - RESIZE_MARGIN;
            boolean inTop = mouseEventY < RESIZE_MARGIN;
            boolean inBottom = mouseEventY > sceneHeight - RESIZE_MARGIN;

            if (inLeft && inTop) {
                cursorType = Cursor.NW_RESIZE;
            } else if (inRight && inTop) {
                cursorType = Cursor.NE_RESIZE;
            } else if (inLeft && inBottom) {
                cursorType = Cursor.SW_RESIZE;
            } else if (inRight && inBottom) {
                cursorType = Cursor.SE_RESIZE;
            } else if (inLeft) {
                cursorType = Cursor.W_RESIZE;
            } else if (inRight) {
                cursorType = Cursor.E_RESIZE;
            } else if (inTop) {
                cursorType = Cursor.N_RESIZE;
            } else if (inBottom) {
                cursorType = Cursor.S_RESIZE;
            }

            cursorEvent = cursorType;
            if (!sceneIsCursor(stage.getScene(), cursorType)) {
                stage.getScene().setCursor(cursorType);
            }
        }

        private void handleMousePressed(MouseEvent event) {

            // If we are in a resize zone, consume the event so other handlers (like drag)
            // don't trigger.
            if (!Cursor.DEFAULT.equals(cursorEvent)) {
                startX = event.getSceneX();
                startY = event.getSceneY();
                startScreenX = event.getScreenX();
                startScreenY = event.getScreenY();
                startWidth = stage.getWidth();
                startHeight = stage.getHeight();
                event.consume();
            }
        }

        private void handleMouseDragged(MouseEvent event) {
            if (Cursor.DEFAULT.equals(cursorEvent)) {
                return; // Not in resize mode
            }

            // Consume event to prevent drag or selection while resizing
            event.consume();

            double mouseEventX = event.getSceneX();
            double mouseEventY = event.getSceneY();
            double sceneWidth = stage.getScene().getWidth();
            double sceneHeight = stage.getScene().getHeight();

            if (cursorEvent.equals(Cursor.W_RESIZE) || cursorEvent.equals(Cursor.NW_RESIZE)
                    || cursorEvent.equals(Cursor.SW_RESIZE)) {
                double deltaX = startScreenX - event.getScreenX();
                double newWidth = startWidth + deltaX;
                if (newWidth > stage.getMinWidth()) {
                    stage.setWidth(newWidth);
                    stage.setX(startScreenX - deltaX);
                }
            }

            if (cursorEvent.equals(Cursor.E_RESIZE) || cursorEvent.equals(Cursor.NE_RESIZE)
                    || cursorEvent.equals(Cursor.SE_RESIZE)) {
                double deltaX = event.getScreenX() - startScreenX;
                double newWidth = startWidth + deltaX;
                if (newWidth > stage.getMinWidth()) {
                    stage.setWidth(newWidth);
                }
            }

            if (cursorEvent.equals(Cursor.N_RESIZE) || cursorEvent.equals(Cursor.NW_RESIZE)
                    || cursorEvent.equals(Cursor.NE_RESIZE)) {
                double deltaY = startScreenY - event.getScreenY();
                double newHeight = startHeight + deltaY;
                if (newHeight > stage.getMinHeight()) {
                    stage.setHeight(newHeight);
                    stage.setY(startScreenY - deltaY);
                }
            }

            if (cursorEvent.equals(Cursor.S_RESIZE) || cursorEvent.equals(Cursor.SW_RESIZE)
                    || cursorEvent.equals(Cursor.SE_RESIZE)) {
                double deltaY = event.getScreenY() - startScreenY;
                double newHeight = startHeight + deltaY;
                if (newHeight > stage.getMinHeight()) {
                    stage.setHeight(newHeight);
                }
            }
        }

        private void handleMouseReleased(MouseEvent event) {
            // Optional: reset cursor or state if needed
        }

        private boolean sceneIsCursor(Scene scene, Cursor cursor) {
            return scene.getCursor() != null && scene.getCursor().equals(cursor);
        }
    }
}
