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
        } else {
            VBox vbox = new VBox(2);
            LogFile logFile = item.logFile();

            String displayName = logFile.getName();
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

            Label serverLabel = null;
            if (logFile.isRemote()) {
                String serverNameText = "Server: -";
                String serverId = logFile.getSshServerID();
                if (serverId != null && !serverId.isBlank()) {
                    try {
                        SSHServerModel server = serverManagementService.getServerById(serverId);
                        if (server != null) {
                            serverNameText = "Server: " + server.getName();
                        } else {
                            serverNameText = "Server: (not found: " + serverId + ")";
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to load server name for id={}", serverId, e);
                        serverNameText = "Server: (error)";
                    }
                }
                serverLabel = new Label(serverNameText);
                serverLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
                serverLabel.getStyleClass().add("server-label");
            }

            Label pathLabel = new Label(logFile.getFilePath());
            pathLabel.getStyleClass().add("path-label");
            Label sizeLabel = new Label(logFile.getSize());
            sizeLabel.getStyleClass().add("size-label");

            if (serverLabel != null) {
                vbox.getChildren().addAll(nameLabel, serverLabel, pathLabel, sizeLabel);
            } else {
                vbox.getChildren().addAll(nameLabel, pathLabel, sizeLabel);
            }

            setGraphic(vbox);
        }
    }
}
