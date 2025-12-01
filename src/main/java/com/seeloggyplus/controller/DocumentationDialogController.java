package com.seeloggyplus.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentationDialogController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationDialogController.class);

    @FXML
    private VBox contentContainer;
    @FXML
    private Button closeButton;

    @FXML
    public void initialize() {
        logger.info("Initializing Documentation Dialog (Lightweight)");
        closeButton.setOnAction(e -> closeDialog());
        buildDocumentationContent();
    }

    private void buildDocumentationContent() {
        // Header
        addHeader("SeeLoggyPlus Documentation");
        addParagraph("Welcome to the official documentation for SeeLoggyPlus, the enterprise-grade log viewer.");

        // Getting Started
        addSectionTitle("Getting Started");
        addParagraph(
                "SeeLoggyPlus allows you to view, filter, and analyze log files from both local and remote sources (via SSH).");

        addSubSectionTitle("Opening a Local File");
        addParagraph("Go to File > Open or press Ctrl+O to select a log file from your computer.");

        addSubSectionTitle("Connecting to a Remote Server");
        addParagraph(
                "Go to Settings > Server Management to configure SSH servers. Once configured, you can tail logs directly from the remote server.");

        // Features
        addSectionTitle("Features");
        addBulletPoint("Real-time Tailing: Follow log files as they grow.");
        addBulletPoint("Smart Filtering: Filter by text, regex, log level, or date range.");
        addBulletPoint("Auto-Prettify: Automatically format JSON and XML log entries.");
        addBulletPoint("Large File Support: Efficiently handle gigabyte-sized log files.");

        // Support
        addSectionTitle("Support");
        addParagraph("For additional support, please contact the IT department.");
    }

    private void addHeader(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 24));
        label.setStyle(
                "-fx-text-fill: #2c3e50; -fx-padding: 0 0 10 0; -fx-border-color: transparent transparent #3498db transparent; -fx-border-width: 0 0 2 0;");
        contentContainer.getChildren().add(label);
    }

    private void addSectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 18));
        label.setStyle("-fx-text-fill: #2980b9; -fx-padding: 20 0 5 0;");
        contentContainer.getChildren().add(label);
    }

    private void addSubSectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 14));
        label.setStyle("-fx-text-fill: #16a085; -fx-padding: 10 0 0 0;");
        contentContainer.getChildren().add(label);
    }

    private void addParagraph(String text) {
        Text textNode = new Text(text);
        textNode.setFont(Font.font("System", 13));
        TextFlow flow = new TextFlow(textNode);
        contentContainer.getChildren().add(flow);
    }

    private void addBulletPoint(String text) {
        Text textNode = new Text("• " + text);
        textNode.setFont(Font.font("System", 13));
        TextFlow flow = new TextFlow(textNode);
        flow.setStyle("-fx-padding: 0 0 0 20;"); // Indent
        contentContainer.getChildren().add(flow);
    }

    private void closeDialog() {
        javafx.stage.Stage stage = (javafx.stage.Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}
