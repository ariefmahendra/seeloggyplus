package com.seeloggyplus.ui.cell;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import java.util.Locale;
import java.util.function.Supplier;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.VBox;

/**
 * Tree cell for the Recent Files panel.
 * <p>
 * The panel is grouped by source: one expandable node per server (or {@code Local})
 * with the recent files of that source as children. File rows intentionally show
 * only the source + file identity — the old "Open/Tail" suffix was removed because
 * it made the list harder to scan.
 */
public class RecentFileTreeCell extends TreeCell<RecentFileTreeCell.RecentNode> {

    public enum Kind { ROOT, SERVER, FILE }

    /** One node of the recent-files tree. */
    public static final class RecentNode {
        private final Kind kind;
        private final String serverName;
        private final RecentFilesDto file;

        private RecentNode(Kind kind, String serverName, RecentFilesDto file) {
            this.kind = kind;
            this.serverName = serverName;
            this.file = file;
        }

        public static RecentNode root() {
            return new RecentNode(Kind.ROOT, null, null);
        }

        public static RecentNode server(String serverName) {
            return new RecentNode(Kind.SERVER, serverName, null);
        }

        public static RecentNode file(RecentFilesDto file) {
            return new RecentNode(Kind.FILE, null, file);
        }

        public Kind getKind() {
            return kind;
        }

        public boolean isFile() {
            return kind == Kind.FILE;
        }

        public boolean isServer() {
            return kind == Kind.SERVER;
        }

        public String getServerName() {
            return serverName;
        }

        public RecentFilesDto getFile() {
            return file;
        }
    }

    private final Supplier<String> monitoringRemotePathSupplier;

    public RecentFileTreeCell(Supplier<String> monitoringRemotePathSupplier) {
        this.monitoringRemotePathSupplier = monitoringRemotePathSupplier;
    }

    public static String sourceLabel(RecentFilesDto item) {
        if (item == null || item.logFile() == null) return "Unavailable";
        if (!item.logFile().isRemote()) return "Local";
        if (item.serverName() != null && !item.serverName().isBlank()) return item.serverName();
        return "Unavailable server: " + java.util.Objects.toString(item.logFile().getSshServerID(), "unknown");
    }

    public static boolean matches(RecentFilesDto item, String query) {
        if (query == null || query.isBlank()) return true;
        if (item == null || item.logFile() == null) return false;
        String text = sourceLabel(item) + " " + item.logFile().getName() + " " + item.logFile().getFilePath();
        String haystack = text.toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .allMatch(haystack::contains);
    }

    /** Human-readable file name with the temporary download prefix stripped. */
    public static String displayName(LogFile logFile) {
        String name = logFile == null ? null : logFile.getName();
        if (name != null && name.matches("^seeloggyplus-\\d+-(.+)$")) {
            return name.replaceFirst("^seeloggyplus-\\d+-", "");
        }
        return name;
    }

    @Override
    protected void updateItem(RecentNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null || item.getKind() == Kind.ROOT) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
            return;
        }
        if (item.isServer()) {
            setText(item.getServerName());
            setTooltip(new Tooltip(item.getServerName()));
            setGraphic(null);
            return;
        }
        RecentFilesDto dto = item.getFile();
        LogFile logFile = dto.logFile();
        String displayName = displayName(logFile);

        VBox vbox = new VBox(2);
        vbox.setFillWidth(true);

        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-font-weight: bold;");
        nameLabel.getStyleClass().add("name-label");
        nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        Label pathLabel = new Label(logFile.getFilePath());
        pathLabel.getStyleClass().add("path-label");
        pathLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);

        Label sizeLabel = new Label(logFile.getSize());
        sizeLabel.getStyleClass().add("size-label");

        if (getTreeView() != null) {
            nameLabel.maxWidthProperty().bind(getTreeView().widthProperty().subtract(60));
            pathLabel.maxWidthProperty().bind(getTreeView().widthProperty().subtract(60));
        }

        vbox.getChildren().addAll(nameLabel, pathLabel, sizeLabel);

        StringBuilder tip = new StringBuilder();
        tip.append("Name: ").append(displayName).append("\n");
        tip.append("Server: ").append(sourceLabel(dto)).append("\n");
        tip.append("Path: ").append(logFile.getFilePath()).append("\n");
        tip.append("Size: ").append(logFile.getSize() != null ? logFile.getSize() : "-");
        if (monitoringRemotePathSupplier != null && monitoringRemotePathSupplier.get() != null
                && monitoringRemotePathSupplier.get().equals(logFile.getFilePath())) {
            tip.append("\nWatching for changes");
        }
        setTooltip(new Tooltip(tip.toString()));
        setText(null);
        setGraphic(vbox);
    }
}
