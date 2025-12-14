package com.seeloggyplus.util;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WindowSnapHandler {

    private static final Logger logger = LoggerFactory.getLogger(WindowSnapHandler.class);

    private final Stage stage;
    private final Node titleBar;
    private final FontAwesomeIconView maximizeIcon;

    private double xOffset = 0;
    private double yOffset = 0;

    private boolean isSnapped = false;
    private double preSnapX = 0;
    private double preSnapY = 0;
    private double preSnapWidth = 0;
    private double preSnapHeight = 0;

    private boolean isMaximizedState = false;

    private Stage ghostWindow;

    private enum SnapPosition {
        NONE, TOP, LEFT, RIGHT
    }

    private SnapPosition currentSnapPosition = SnapPosition.NONE;
    private Rectangle2D currentSnapWorkArea = null;
    private boolean isAnimating = false;

    public WindowSnapHandler(Stage stage, Node titleBar, FontAwesomeIconView maximizeIcon) {
        this.stage = stage;
        this.titleBar = titleBar;
        this.maximizeIcon = maximizeIcon;
        initialize();
    }

    private void initialize() {
        ResizeHelper.addResizeListener(stage);
        setupListeners();
        setupStageListeners();
    }

    private void setupListeners() {
        titleBar.setOnMousePressed(this::handleMousePressed);
        titleBar.setOnMouseDragged(this::handleMouseDragged);
        titleBar.setOnMouseReleased(this::handleMouseReleased);
        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                toggleMaximize();
            }
        });
    }

    private void setupStageListeners() {
        stage.widthProperty().addListener((obs, oldVal, newVal) -> checkResized());
        stage.heightProperty().addListener((obs, oldVal, newVal) -> checkResized());
    }

    private void checkResized() {
        if (isAnimating)
            return;
        if (isMaximizedState) {
            Rectangle2D workArea = getWorkArea(stage.getX(), stage.getY());
            if (Math.abs(stage.getWidth() - workArea.getWidth()) > 10 ||
                    Math.abs(stage.getHeight() - workArea.getHeight()) > 10) {
                isMaximizedState = false;
                updateMaximizeIcon(false);
            }
        }

        if (isSnapped && currentSnapPosition == SnapPosition.RIGHT && currentSnapWorkArea != null) {
            double expectedX = currentSnapWorkArea.getMaxX() - stage.getWidth();
            if (Math.abs(stage.getX() - expectedX) > 1.0) {
                stage.setX(expectedX);
            }
        }
    }

    private void handleMousePressed(MouseEvent event) {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

    private void handleMouseDragged(MouseEvent event) {
        if (event.getClickCount() > 1)
            return;

        if (isMaximizedState || isSnapped || stage.isMaximized()) {
            double currentWidth = stage.getWidth();
            double ratioX = xOffset / currentWidth;
            toggleMaximize(false);
            xOffset = stage.getWidth() * ratioX;
        }

        stage.setX(event.getScreenX() - xOffset);
        stage.setY(event.getScreenY() - yOffset);

        detectSnap(event.getScreenX(), event.getScreenY());
    }

    private void handleMouseReleased(MouseEvent event) {
        applySnap();
    }

    public void minimize() {
        stage.setIconified(true);
    }

    public void close() {
        stage.close();
    }

    public void toggleMaximize() {
        toggleMaximize(false);
    }

    public void toggleMaximize(boolean animate) {
        double centerX = stage.getX() + (stage.getWidth() / 2);
        double centerY = stage.getY() + (stage.getHeight() / 2);
        Rectangle2D workArea = getWorkArea(centerX, centerY);

        if (isMaximizedState) {
            double targetX, targetY, targetW, targetH;

            if (isSnapped) {
                targetX = (preSnapWidth > 0) ? preSnapX : (workArea.getMinX() + 50);
                targetY = (preSnapHeight > 0) ? preSnapY : (workArea.getMinY() + 50);
                targetW = (preSnapWidth > 0) ? preSnapWidth : 1000;
                targetH = (preSnapHeight > 0) ? preSnapHeight : 700;
            } else {
                if (preSnapWidth < 100) {
                    targetX = (workArea.getWidth() - 1000) / 2 + workArea.getMinX();
                    targetY = (workArea.getHeight() - 700) / 2 + workArea.getMinY();
                    targetW = 1000;
                    targetH = 700;
                } else {
                    targetX = preSnapX;
                    targetY = preSnapY;
                    targetW = preSnapWidth;
                    targetH = preSnapHeight;
                }
            }

            Runnable completion = () -> {
                isMaximizedState = false;
                isSnapped = false;
                if (stage.isMaximized())
                    stage.setMaximized(false);
                updateMaximizeIcon(false);
                if (stage.getScene().getRoot() != null) {
                    stage.getScene().getRoot().getStyleClass().remove("maximized");
                }
            };

            stage.setX(targetX);
            stage.setY(targetY);
            stage.setWidth(targetW);
            stage.setHeight(targetH);
            completion.run();

        } else {
            // MAXIMIZE
            preSnapX = stage.getX();
            preSnapY = stage.getY();
            preSnapWidth = stage.getWidth();
            preSnapHeight = stage.getHeight();

            Runnable completion = () -> {
                isMaximizedState = true;
                updateMaximizeIcon(true);
                if (stage.getScene().getRoot() != null) {
                    stage.getScene().getRoot().getStyleClass().add("maximized");
                }
            };

            stage.setX(workArea.getMinX());
            stage.setY(workArea.getMinY());
            stage.setWidth(workArea.getWidth());
            stage.setHeight(workArea.getHeight());
            completion.run();
        }
    }

    private void updateMaximizeIcon(boolean isMax) {
        if (maximizeIcon != null) {
            maximizeIcon.setGlyphName(isMax ? "COMPRESS" : "SQUARE_ALT");
        }
    }

    private void createGhostWindow() {
        if (ghostWindow != null)
            return;
        ghostWindow = new Stage();
        ghostWindow.initStyle(StageStyle.TRANSPARENT);
        StackPane root = new StackPane();
        root.setStyle(
                "-fx-background-color: rgba(100, 100, 100, 0.3); -fx-border-color: #3b82f6; -fx-border-width: 2;");
        Scene scene = new Scene(root);
        scene.setFill(null);
        ghostWindow.setScene(scene);
        ghostWindow.setAlwaysOnTop(true);
    }

    private void detectSnap(double mouseX, double mouseY) {
        Rectangle2D workArea = getWorkArea(mouseX, mouseY);
        currentSnapWorkArea = workArea;

        if (ghostWindow == null)
            createGhostWindow();

        double snapMargin = 10;
        double minWidth = stage.getMinWidth() > 0 ? stage.getMinWidth() : 0;
        double minHeight = stage.getMinHeight() > 0 ? stage.getMinHeight() : 0;

        boolean nearTop = mouseY <= workArea.getMinY() + snapMargin;
        boolean nearLeft = mouseX <= workArea.getMinX() + snapMargin;
        boolean nearRight = mouseX >= workArea.getMaxX() - snapMargin;

        if (nearTop) {
            currentSnapPosition = SnapPosition.TOP;
            ghostWindow.setX(workArea.getMinX());
            ghostWindow.setY(workArea.getMinY());
            ghostWindow.setWidth(Math.max(workArea.getWidth(), minWidth));
            ghostWindow.setHeight(Math.max(workArea.getHeight(), minHeight));
            ghostWindow.show();
        } else if (nearLeft) {
            currentSnapPosition = SnapPosition.LEFT;
            double targetWidth = workArea.getWidth() / 2;
            double actualWidth = Math.max(targetWidth, minWidth);
            ghostWindow.setX(workArea.getMinX());
            ghostWindow.setY(workArea.getMinY());
            ghostWindow.setWidth(actualWidth);
            ghostWindow.setHeight(Math.max(workArea.getHeight(), minHeight));
            ghostWindow.show();
        } else if (nearRight) {
            currentSnapPosition = SnapPosition.RIGHT;
            // Fix: Align strict to MaxX to avoid bleed to next monitor
            // Respect minWidth to prevent spillover if window cannot shrink enough
            double targetWidth = workArea.getWidth() / 2;
            // Subtract small buffer (1px) to avoid rounding errors causing pixel bleed
            if (targetWidth > 1)
                targetWidth -= 1;

            double actualWidth = Math.max(targetWidth, minWidth);

            ghostWindow.setX(workArea.getMaxX() - actualWidth);
            ghostWindow.setY(workArea.getMinY());
            ghostWindow.setWidth(actualWidth);
            ghostWindow.setHeight(Math.max(workArea.getHeight(), minHeight));
            ghostWindow.show();
        } else {
            currentSnapPosition = SnapPosition.NONE;
            ghostWindow.hide();
        }
    }

    private void applySnap() {
        if (ghostWindow != null)
            ghostWindow.hide();

        if (currentSnapPosition != SnapPosition.NONE && currentSnapWorkArea != null) {

            if (!isSnapped && !isMaximizedState) {
                preSnapX = stage.getX();
                preSnapY = stage.getY();
                preSnapWidth = stage.getWidth();
                preSnapHeight = stage.getHeight();
            }

            double minWidth = stage.getMinWidth() > 0 ? stage.getMinWidth() : 0;
            double minHeight = stage.getMinHeight() > 0 ? stage.getMinHeight() : 0;

            double finalX = stage.getX();
            double finalY = stage.getY();
            double finalWidth = stage.getWidth();
            double finalHeight = stage.getHeight();

            switch (currentSnapPosition) {
                case TOP:
                    finalX = currentSnapWorkArea.getMinX();
                    finalY = currentSnapWorkArea.getMinY();
                    finalWidth = Math.max(currentSnapWorkArea.getWidth(), minWidth);
                    finalHeight = Math.max(currentSnapWorkArea.getHeight(), minHeight);
                    break;

                case LEFT: {
                    double targetWidth = currentSnapWorkArea.getWidth() / 2;
                    double actualWidth = Math.max(targetWidth, minWidth);
                    finalX = currentSnapWorkArea.getMinX();
                    finalY = currentSnapWorkArea.getMinY();
                    finalWidth = actualWidth;
                    finalHeight = Math.max(currentSnapWorkArea.getHeight(), minHeight);
                }
                    break;

                case RIGHT: {
                    double targetWidth = currentSnapWorkArea.getWidth() / 2;
                    if (targetWidth > 1)
                        targetWidth -= 1;
                    double actualWidth = Math.max(targetWidth, minWidth);

                    finalX = currentSnapWorkArea.getMaxX() - actualWidth;
                    finalY = currentSnapWorkArea.getMinY();
                    finalWidth = actualWidth;
                    finalHeight = Math.max(currentSnapWorkArea.getHeight(), minHeight);
                }
                    break;
                default:
                    return;
            }

            stage.setX(finalX);
            stage.setY(finalY);
            stage.setWidth(finalWidth);
            stage.setHeight(finalHeight);

            isSnapped = true;
            if (currentSnapPosition == SnapPosition.TOP) {
                isMaximizedState = true;
                updateMaximizeIcon(true);
                if (stage.getScene().getRoot() != null) {
                    stage.getScene().getRoot().getStyleClass().add("maximized");
                }
            } else {
                isMaximizedState = false;
                updateMaximizeIcon(false);
                if (stage.getScene().getRoot() != null) {
                    stage.getScene().getRoot().getStyleClass().remove("maximized");
                }
            }
        }
    }

    private Rectangle2D getWorkArea(double x, double y) {
        // 1. Try exact containment first
        for (Screen screen : Screen.getScreens()) {
            if (screen.getBounds().contains(x, y)) {
                return screen.getVisualBounds();
            }
        }

        // 2. If mouse is slightly off-bounds (e.g. negative coordinates edge case),
        // find closest screen
        return Screen.getScreens().stream()
                .min((s1, s2) -> {
                    double d1 = distanceToCenter(s1.getBounds(), x, y);
                    double d2 = distanceToCenter(s2.getBounds(), x, y);
                    return Double.compare(d1, d2);
                })
                .orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    private double distanceToCenter(Rectangle2D bounds, double x, double y) {
        double centerX = bounds.getMinX() + bounds.getWidth() / 2;
        double centerY = bounds.getMinY() + bounds.getHeight() / 2;
        return Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2);
    }
}
