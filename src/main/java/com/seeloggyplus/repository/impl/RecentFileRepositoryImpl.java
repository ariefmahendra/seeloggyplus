package com.seeloggyplus.repository.impl;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.repository.RecentFileRepository;
import com.seeloggyplus.config.DatabaseConfig;

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
    private final Connection connection = DatabaseConfig.getInstance().getConnection();

    public RecentFileRepositoryImpl() {
    }

    @Override
    public List<RecentFilesDto> findAll() {
        List<RecentFilesDto> recentFiles = new ArrayList<>();
        String sql = "SELECT" +
                "    lf.id AS file_id," +
                "    lf.name AS file_name," +
                "    lf.file_path," +
                "    lf.size," +
                "    lf.modified," +
                "    lf.is_remote," +
                "    lf.ssh_server_id,"+
                "    lf.parsing_config_id,"+
                "    pc.id AS config_id," +
                "    pc.name AS config_name," +
                "    pc.description AS config_description," +
                "    pc.regex_pattern," +
                "    pc.is_default" +
                "FROM" +
                "    recent_files AS rf" +
                "JOIN" +
                "    log_files AS lf ON rf.file_id = lf.id" +
                "LEFT JOIN" +
                "    parsing_configs AS pc ON lf.parsing_config_id = pc.id" +
                "ORDER BY" +
                "    rf.last_opened DESC;";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                recentFiles.add(mapRowToRecentFileDto(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all recent files", e);
        }
        return recentFiles;
    }

    @Override
    public void save(RecentFile recentFile) {
        String sql = "INSERT OR REPLACE INTO recent_files (id, file_id, last_opened) VALUES (?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, recentFile.getId());
            preparedStatement.setString(2, recentFile.getFileId());
            preparedStatement.setString(3, recentFile.getLastOpened().toString());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving recent file: {}", recentFile.getId(), e);
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
        recentFile.setId(rs.getString("id"));
        recentFile.setFileId(rs.getString("file_id"));
        recentFile.setLastOpened(LocalDateTime.parse(rs.getString("last_opened")));
        return recentFile;
    }

    private RecentFilesDto mapRowToRecentFileDto(ResultSet rs) throws SQLException{
        LogFile logFile = new LogFile();
        ParsingConfig parsingConfig = new ParsingConfig();

        logFile.setId(rs.getString("file_id"));
        logFile.setName(rs.getString("file_name"));
        logFile.setFilePath(rs.getString("file_path"));
        logFile.setSize(rs.getString("size"));
        logFile.setModified(rs.getString("modified"));
        logFile.setRemote(rs.getBoolean("is_remote"));
        logFile.setSshServerID(rs.getString("ssh_server_id"));
        logFile.setParsingConfigurationID(rs.getString("parsing_config_id"));

        parsingConfig.setId(rs.getString("config_id"));
        parsingConfig.setName(rs.getString("config_name"));
        parsingConfig.setDescription(rs.getString("config_description"));
        parsingConfig.setRegexPattern(rs.getString("regex_pattern"));

        return new RecentFilesDto(logFile, parsingConfig);
    }
}
