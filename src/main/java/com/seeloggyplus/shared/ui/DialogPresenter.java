package com.seeloggyplus.shared.ui;

import javafx.scene.control.Dialog;
import javafx.stage.Stage;

import java.util.Optional;

public interface DialogPresenter {

    void present(Stage dialog);

    <T> Optional<T> present(Dialog<T> dialog);
}
