package com.seeloggyplus.ui.cell;

import com.seeloggyplus.dto.RecentFilesDto;
import java.util.function.Supplier;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.ServerManagementService;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecentFileListCell extends ListCell<RecentFilesDto> {
    private static final Logger logger = LoggerFactory.getLogger(RecentFileListCell.class);
    private final ServerManagementService serverManagementService;
    private final Supplier<String> monitoringRemotePathSupplier;

    public RecentFileListCell(ServerManagementService serverManagementService, Supplier<String> monitoringRemotePathSupplier) {
        this.serverManagementService = serverManagementService;
        this.monitoringRemotePathSupplier = monitoringRemotePathSupplier;
    }

    @Override
    protected void updateItem(RecentFilesDto item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
        } else {
            VBox vbox = new VBox(2);
            vbox.setFillWidth(true);
            LogFile logFile = item.logFile();

            String displayName = logFile.getName();
            // Clean up any temporary download prefix (e.g. seeloggyplus-1788673390465-app.log -> app.log)
            if (displayName != null && displayName.matches("^seeloggyplus-\\d+-(.+)$")) {
                displayName = displayName.replaceFirst("^seeloggyplus-\\d+-", "");
            }

            String monitoringRemotePath = monitoringRemotePathSupplier != null ? monitoringRemotePathSupplier.get()
                    : null;
            if (logFile.isRemote()
                    && monitoringRemotePath != null
                    && monitoringRemotePath.equals(logFile.getFilePath())) {
                displayName = displayName + " (Monitoring)";
            }

            Label nameLabel = new Label(displayName);
            nameLabel.setStyle("-fx-font-weight: bold;");
            nameLabel.getStyleClass().add("name-label");
            nameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);

            Label serverLabel = null;
            String serverNameForTooltip = null;
            if (logFile.isRemote()) {
                String serverNameText = "Server: -";
                String serverId = logFile.getSshServerID();
                if (serverId != null && !serverId.isBlank()) {
                    try {
                        SSHServerModel server = serverManagementService.getServerById(serverId);
                        if (server != null) {
                            serverNameText = "Server: " + server.getName();
                            serverNameForTooltip = server.getName();
                        } else {
                            serverNameText = "Server: (not found: " + serverId + ")";
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to load server name for id={}", serverId, e);
                        serverNameText = "Server: (error)";
                    }
                }
                serverLabel = new Label(serverNameText);
                serverLabel.setStyle("-fx-font-size: 11px;");
                serverLabel.getStyleClass().add("server-label");
                serverLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            }

            Label pathLabel = new Label(logFile.getFilePath());
            pathLabel.getStyleClass().add("path-label");
            pathLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);

            Label sizeLabel = new Label(logFile.getSize());
            sizeLabel.getStyleClass().add("size-label");

            // Bind max width to prevent horizontal overflow and scrollbars in ListView
            if (getListView() != null) {
                nameLabel.maxWidthProperty().bind(getListView().widthProperty().subtract(30));
                pathLabel.maxWidthProperty().bind(getListView().widthProperty().subtract(30));
                if (serverLabel != null) {
                    serverLabel.maxWidthProperty().bind(getListView().widthProperty().subtract(30));
                }
            }

            if (serverLabel != null) {
                vbox.getChildren().addAll(nameLabel, serverLabel, pathLabel, sizeLabel);
            } else {
                vbox.getChildren().addAll(nameLabel, pathLabel, sizeLabel);
            }

            // Informative tooltip with full path
            StringBuilder tip = new StringBuilder();
            tip.append("Name: ").append(displayName).append("\n");
            if (serverNameForTooltip != null) {
                tip.append("Server: ").append(serverNameForTooltip).append("\n");
            }
            tip.append("Path: ").append(logFile.getFilePath()).append("\n");
            tip.append("Size: ").append(logFile.getSize() != null ? logFile.getSize() : "-");
            setTooltip(new javafx.scene.control.Tooltip(tip.toString()));

            setGraphic(vbox);
        }
    }
}
