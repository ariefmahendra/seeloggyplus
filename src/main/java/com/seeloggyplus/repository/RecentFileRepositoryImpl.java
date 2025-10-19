package com.seeloggyplus.repository;

import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.service.DatabaseService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecentFileRepositoryImpl implements RecentFileRepository {

    private static final Logger logger = LoggerFactory.getLogger(RecentFileRepositoryImpl.class);
    private final Connection connection = DatabaseService.getInstance().getConnection();

    public RecentFileRepositoryImpl() {
        String createRecentFileTable = "CREATE TABLE IF NOT EXISTS recent_files ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "file_path TEXT NOT NULL UNIQUE,"
                + "file_name TEXT NOT NULL,"
                + "last_opened TEXT NOT NULL,"
                + "file_size INTEGER,"
                + "is_remote BOOLEAN NOT NULL DEFAULT 0,"
                + "remote_host TEXT,"
                + "remote_port INTEGER,"
                + "remote_user TEXT,"
                + "remote_path TEXT,"
                + "parsing_config_id INTEGER,"
                + "FOREIGN KEY (parsing_config_id) REFERENCES parsing_configs(id)"
                + ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createRecentFileTable);
            logger.info("Recent files table created or already exists.");
        } catch (SQLException e) {
            logger.error("Failed to create recent_files table.", e);
        }
    }

    @Override
    public List<RecentFile> findAll() {
        List<RecentFile> recentFiles = new ArrayList<>();
        String sql = "SELECT * FROM recent_files ORDER BY last_opened DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                recentFiles.add(mapRowToRecentFile(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all recent files", e);
        }
        return recentFiles;
    }

    @Override
    public void save(RecentFile recentFile) {
        String sql = "INSERT OR REPLACE INTO recent_files (file_path, file_name, last_opened, file_size, is_remote, remote_host, remote_port, remote_user, remote_path, parsing_config_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, recentFile.getFilePath());
            pstmt.setString(2, recentFile.getFileName());
            pstmt.setString(3, recentFile.getLastOpened().toString());
            pstmt.setLong(4, recentFile.getFileSize());
            pstmt.setBoolean(5, recentFile.isRemote());
            pstmt.setString(6, recentFile.getRemoteHost());
            pstmt.setInt(7, recentFile.getRemotePort());
            pstmt.setString(8, recentFile.getRemoteUser());
            pstmt.setString(9, recentFile.getRemotePath());
            if (recentFile.getParsingConfig() != null) {
                pstmt.setInt(10, recentFile.getParsingConfig().getId());
            } else {
                pstmt.setNull(10, java.sql.Types.INTEGER);
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving recent file: " + recentFile.getFileName(), e);
        }
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM recent_files";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            logger.error("Error deleting all recent files", e);
        }
    }

    private RecentFile mapRowToRecentFile(ResultSet rs) throws SQLException {
        RecentFile recentFile = new RecentFile();
        recentFile.setFilePath(rs.getString("file_path"));
        recentFile.setFileName(rs.getString("file_name"));
        recentFile.setLastOpened(LocalDateTime.parse(rs.getString("last_opened")));
        recentFile.setFileSize(rs.getLong("file_size"));
        recentFile.setRemote(rs.getBoolean("is_remote"));
        recentFile.setRemoteHost(rs.getString("remote_host"));
        recentFile.setRemotePort(rs.getInt("remote_port"));
        recentFile.setRemoteUser(rs.getString("remote_user"));
        recentFile.setRemotePath(rs.getString("remote_path"));
        int parsingConfigId = rs.getInt("parsing_config_id");
        if (!rs.wasNull()) {
            new ParsingConfigRepositoryImpl().findById(parsingConfigId).ifPresent(recentFile::setParsingConfig);
        }
        return recentFile;
    }
}
