package com.seeloggyplus.features.files.presentation;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

final class FilePathBreadcrumb extends StackPane {
    record Folder(String name, String path) {}
    private final TextField editor;
    private final HBox segments = new HBox();
    private final MenuButton overflow = new MenuButton();
    private final java.util.ArrayList<Button> folderButtons = new java.util.ArrayList<>();
    private final java.util.ArrayList<MenuButton> folderArrows = new java.util.ArrayList<>();
    private final Consumer<String> navigate;
    private final Function<String, CompletableFuture<List<Folder>>> children;

    FilePathBreadcrumb(TextField editor, Consumer<String> navigate,
            Function<String, CompletableFuture<List<Folder>>> children) {
        this.editor = editor;
        this.navigate = navigate;
        this.children = children;
        getStyleClass().add("file-path-breadcrumb");
        var stylesheet = getClass().getResource("/style/file-path-breadcrumb.css");
        getStylesheets().add(stylesheet.toExternalForm());
        setMinWidth(100);
        setPrefWidth(360);
        setMaxWidth(Double.MAX_VALUE);
        overflow.getStyleClass().add("path-overflow");
        overflow.setAccessibleText("Hidden parent folders");
        HBox dots = new HBox(3);
        dots.setAlignment(javafx.geometry.Pos.CENTER);
        for (int i = 0; i < 3; i++) {
            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(1.5);
            dot.getStyleClass().add("path-overflow-dot");
            dots.getChildren().add(dot);
        }
        overflow.setGraphic(dots);
        overflow.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        overflow.setMinWidth(32);
        overflow.setPrefWidth(32);
        overflow.setMaxWidth(32);
        segments.setMinWidth(0);
        getChildren().addAll(segments, editor);
        editor.setVisible(false);
        editor.setManaged(false);
        segments.setOnMouseClicked(event -> {
            if (event.getTarget() == segments) edit();
        });
        editor.focusedProperty().addListener((o, old, focused) -> {
            if (!focused) finishEditing();
        });
        editor.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) { finishEditing(); event.consume(); }
        });
        editor.textProperty().addListener((o, old, value) -> rebuild(value));
        rebuild(editor.getText());
    }

    void edit() {
        editor.setManaged(true);
        editor.setVisible(true);
        editor.requestFocus();
        editor.selectAll();
    }

    void finishEditing() {
        editor.setVisible(false);
        editor.setManaged(false);
    }

    private void rebuild(String path) {
        for (var node : segments.getChildren()) if (node instanceof MenuButton menu) menu.hide();
        overflow.hide();
        segments.getChildren().clear();
        folderButtons.clear();
        folderArrows.clear();
        segments.getChildren().add(overflow);
        if (path == null || path.isBlank()) return;
        boolean windows = path.matches("^[A-Za-z]:.*") || path.startsWith("\\\\");
        String separator = windows ? "\\" : "/";
        String normalized = path.replace('\\', '/');
        String cumulative = normalized.startsWith("//") ? "\\\\" : normalized.startsWith("/") ? "/" : "";
        if (cumulative.equals("/")) addSegment("/", "/");
        for (String part : normalized.split("/")) {
            if (part.isEmpty()) continue;
            if (!cumulative.isEmpty() && !cumulative.endsWith(separator)) cumulative += separator;
            cumulative += part;
            if (part.matches("[A-Za-z]:")) cumulative += separator;
            addSegment(part, cumulative);
        }
        Region space = new Region();
        space.setMinWidth(0);
        HBox.setHgrow(space, Priority.ALWAYS);
        space.setOnMouseClicked(event -> edit());
        segments.getChildren().add(space);
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        double available = Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset());
        double total = 0;
        for (Button button : folderButtons) total += button.prefWidth(-1) + 28;
        int hidden = 0;
        double visibleWidth = total;
        if (total > available) {
            visibleWidth += 32;
            while (hidden < folderButtons.size() - 1 && visibleWidth > available) {
                visibleWidth -= folderButtons.get(hidden).prefWidth(-1) + 28;
                hidden++;
            }
        }
        boolean collapsed = hidden > 0;
        overflow.setVisible(collapsed);
        overflow.setManaged(collapsed);
        List<String> hiddenPaths = new java.util.ArrayList<>();
        for (int i = hidden - 1; i >= 0; i--) hiddenPaths.add((String) folderButtons.get(i).getUserData());
        List<String> menuPaths = overflow.getItems().stream().map(item -> (String) item.getUserData()).toList();
        if (!hiddenPaths.equals(menuPaths)) {
            overflow.hide();
            overflow.getItems().clear();
            for (int i = hidden - 1; i >= 0; i--) {
                Button button = folderButtons.get(i);
                MenuItem item = new MenuItem(button.getText());
                String path = (String) button.getUserData();
                item.setUserData(path);
                item.setOnAction(event -> navigate.accept(path));
                overflow.getItems().add(item);
            }
        }
        for (int i = 0; i < folderButtons.size(); i++) {
            boolean visible = i >= hidden;
            Button button = folderButtons.get(i);
            MenuButton arrow = folderArrows.get(i);
            button.setVisible(visible);
            button.setManaged(visible);
            arrow.setVisible(visible);
            arrow.setManaged(visible);
            if (!visible) arrow.hide();
            double limit = i == folderButtons.size() - 1
                    ? Math.max(0, available - (collapsed ? 32 : 0) - 28) : Double.MAX_VALUE;
            button.setMaxWidth(limit);
        }
        super.layoutChildren();
    }

    private void addSegment(String name, String path) {
        Button segment = new Button(name);
        segment.getStyleClass().add("path-segment");
        segment.setMinWidth(0);
        segment.setTooltip(new Tooltip(path));
        segment.setUserData(path);
        segment.setOnAction(event -> { finishEditing(); navigate.accept(path); });
        MenuButton arrow = new MenuButton();
        javafx.scene.shape.SVGPath chevron = new javafx.scene.shape.SVGPath();
        chevron.setContent("M 1 1 L 5 5 L 1 9");
        chevron.getStyleClass().add("path-chevron");
        arrow.setGraphic(chevron);
        arrow.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        arrow.getStyleClass().add("path-children");
        arrow.setMinSize(28, 30);
        arrow.setPrefSize(28, 30);
        arrow.setMaxSize(28, 30);
        arrow.setAccessibleText("Subfolders of " + path);
        arrow.setOnShowing(event -> {
            chevron.setRotate(90);
            MenuItem loading = new MenuItem("Loading…");
            loading.setDisable(true);
            arrow.getItems().setAll(loading);
            children.apply(path).whenComplete((folders, error) -> Platform.runLater(() -> {
                if (!arrow.isShowing()) return;
                arrow.getItems().clear();
                if (error != null || folders.isEmpty()) {
                    MenuItem message = new MenuItem(error != null ? "Unable to read folder" : "No subfolders");
                    message.setDisable(true);
                    arrow.getItems().add(message);
                } else {
                    folders.stream().sorted(java.util.Comparator.comparing(Folder::name, String.CASE_INSENSITIVE_ORDER))
                            .forEach(folder -> {
                                MenuItem item = new MenuItem(folder.name());
                                item.setOnAction(action -> navigate.accept(folder.path()));
                                arrow.getItems().add(item);
                            });
                }
            }));
        });
        arrow.setOnHidden(event -> chevron.setRotate(0));
        folderButtons.add(segment);
        folderArrows.add(arrow);
        segments.getChildren().addAll(segment, arrow);
    }
}
