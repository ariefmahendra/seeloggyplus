package com.seeloggyplus.util;

import com.seeloggyplus.model.ParsingConfig;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * Utility class untuk menampilkan dialog-dialog di aplikasi
 */
public class DialogUtils {

    private static final Logger logger = LoggerFactory.getLogger(DialogUtils.class);

    /**
     * Menampilkan dialog untuk memilih parsing configuration
     *
     * @return Optional berisi ParsingConfig yang dipilih
     */
    public static Optional<ParsingConfig> showParsingConfigurationSelectionDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                DialogUtils.class.getResource("/fxml/ParsingConfigurationSelectionDialog.fxml")
            );

            DialogPane dialogPane = loader.load();

            Dialog<ParsingConfig> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("Select Parsing Configuration");
            dialog.initModality(Modality.APPLICATION_MODAL);

            logger.info("Showing parsing configuration selection dialog");
            return dialog.showAndWait();

        } catch (IOException e) {
            logger.error("Error loading parsing configuration selection dialog", e);
            return Optional.empty();
        }
    }
}

