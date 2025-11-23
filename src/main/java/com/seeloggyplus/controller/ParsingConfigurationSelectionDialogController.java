package com.seeloggyplus.controller;

import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.service.ParsingConfigService;
import com.seeloggyplus.service.impl.ParsingConfigServiceImpl;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller untuk ParsingConfigurationSelectionDialog
 * Menampilkan daftar parsing configuration yang tersedia untuk dipilih
 */
public class ParsingConfigurationSelectionDialogController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ParsingConfigurationSelectionDialogController.class);

    @FXML
    private TextField searchField;

    @FXML
    private TableView<ParsingConfig> configTableView;

    @FXML
    private TableColumn<ParsingConfig, String> nameColumn;

    @FXML
    private TableColumn<ParsingConfig, String> descriptionColumn;

    @FXML
    private TableColumn<ParsingConfig, String> groupsColumn;

    @FXML
    private TableColumn<ParsingConfig, String> statusColumn;

    @FXML
    private Label detailNameLabel;

    @FXML
    private Label detailDescriptionLabel;

    @FXML
    private TextArea detailPatternTextArea;


    private ParsingConfigService parsingConfigService;
    private ObservableList<ParsingConfig> configList;
    private FilteredList<ParsingConfig> filteredList;
    /**
     * -- GETTER --
     *  Get selected parsing configuration
     */
    @Getter
    private ParsingConfig selectedConfig;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            parsingConfigService = new ParsingConfigServiceImpl();
            initializeTableColumns();
            loadConfigurations();
            setupEventHandlers();
            setupSearch();
        } catch (Exception e) {
            logger.error("Error initializing ParsingConfigurationSelectionDialogController", e);
            showError("Failed to initialize dialog", e.getMessage());
        }
    }

    /**
     * Inisialisasi kolom-kolom dalam table view
     */
    private void initializeTableColumns() {
        // Kolom Name
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        // Kolom Description
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Kolom Groups - menampilkan jumlah group
        groupsColumn.setCellValueFactory(cellData -> {
            int groupCount = cellData.getValue().getGroupNames() != null ? cellData.getValue().getGroupNames().size() : 0;
            return new SimpleStringProperty(String.valueOf(groupCount));
        });

        // Kolom Status - menampilkan valid atau error
        statusColumn.setCellValueFactory(cellData -> {
            boolean isValid = cellData.getValue().isValid();
            String status = isValid ? "✓ Valid" : "✗ Invalid";
            return new SimpleStringProperty(status);
        });

        // Styling untuk status column
        statusColumn.setCellFactory(column -> new TableCell<ParsingConfig, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("Valid")) {
                        setStyle("-fx-text-fill: #27ae60;");
                    } else {
                        setStyle("-fx-text-fill: #e74c3c;");
                    }
                }
            }
        });
    }

    /**
     * Load semua parsing configurations dari service
     */
    private void loadConfigurations() {
        try {
            List<ParsingConfig> configs = parsingConfigService.findAll();
            configList = FXCollections.observableArrayList(configs);
            filteredList = new FilteredList<>(configList, p -> true);
            configTableView.setItems(filteredList);

            logger.info("Loaded {} parsing configurations", configs.size());

            // Show message if no configurations available
            if (configs.isEmpty()) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("No Parsing Configurations");
                    alert.setHeaderText("No parsing configurations available");
                    alert.setContentText("Please create a parsing configuration first.\n\n" +
                            "Go to Settings → Parsing Configuration to create one.");
                    alert.showAndWait();
                });
            }
        } catch (Exception e) {
            logger.error("Error loading configurations", e);
            showError("Error loading configurations", e.getMessage());
        }
    }

    /**
     * Setup event handlers untuk table selection
     */
    private void setupEventHandlers() {
        // Handle table row selection
        configTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedConfig = newVal;
                displayConfigDetails(newVal);
            }
        });

        // Select first row by default if available
        if (!configTableView.getItems().isEmpty()) {
            configTableView.getSelectionModel().selectFirst();
        }
    }

    /**
     * Setup search/filter functionality
     */
    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredList.setPredicate(config -> {
                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newVal.toLowerCase();
                return config.getName().toLowerCase().contains(lowerCaseFilter) ||
                       (config.getDescription() != null && config.getDescription().toLowerCase().contains(lowerCaseFilter));
            });
        });
    }

    /**
     * Menampilkan detail dari parsing configuration yang dipilih
     */
    private void displayConfigDetails(ParsingConfig config) {
        detailNameLabel.setText(config.getName());
        detailDescriptionLabel.setText(config.getDescription() != null ? config.getDescription() : "-");
        detailPatternTextArea.setText(config.getRegexPattern() != null ? config.getRegexPattern() : "-");
    }

    /**
     * Validate selected configuration before returning
     */
    public boolean isValidSelection() {
        if (selectedConfig == null) {
            return false;
        }

        if (!selectedConfig.isValid()) {
            showWarning("Invalid Configuration",
                "The selected configuration is invalid:\n" + selectedConfig.getValidationError());
            return false;
        }

        return true;
    }

    /**
     * Show error dialog
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show warning dialog
     */
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
