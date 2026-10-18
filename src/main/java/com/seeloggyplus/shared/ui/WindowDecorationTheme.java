package com.seeloggyplus.shared.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.stage.Window;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;

public final class WindowDecorationTheme implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(WindowDecorationTheme.class);
    private final Consumer<Boolean> adapter;
    private final ListChangeListener<Window> listener = change -> {
        while (change.next()) {
            if (change.wasAdded() && change.getAddedSubList().stream().anyMatch(Stage.class::isInstance)) {
                schedule();
            }
        }
    };
    private boolean installed;
    private boolean pending;
    private boolean failureLogged;

    public WindowDecorationTheme() {
        this(WindowsWindowDecoration::apply);
    }

    WindowDecorationTheme(Consumer<Boolean> adapter) {
        this.adapter = adapter;
    }

    public void install() {
        if (installed) return;
        installed = true;
        Window.getWindows().addListener(listener);
        update(AppTheme.getTheme());
    }

    public void update(AppTheme.Theme theme) {
        if (!installed) return;
        try {
            adapter.accept(theme == AppTheme.Theme.DARK);
        } catch (RuntimeException | LinkageError error) {
            if (!failureLogged) {
                failureLogged = true;
                logger.warn("Native window theme is unavailable; keeping system decoration", error);
            }
        }
    }

    private void schedule() {
        if (pending) return;
        pending = true;
        Platform.runLater(() -> {
            pending = false;
            if (installed) update(AppTheme.getTheme());
        });
    }

    @Override public void close() {
        if (!installed) return;
        installed = false;
        Window.getWindows().removeListener(listener);
    }
}
