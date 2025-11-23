package com.seeloggyplus.repository.impl;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.model.RecentFile;
import com.seeloggyplus.repository.RecentFileRepository;
import com.seeloggyplus.config.DatabaseConfig;
import com.seeloggyplus.util.FileUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecentFileRepositoryImpl implements RecentFileRepository {
    private static final Logger logger = LoggerFactory.getLogger(RecentFileRepositoryImpl.class);

    public RecentFileRepositoryImpl() {
    }

    private Connection getConnection() {
        return DatabaseConfig.getInstance().getConnection();
    }

    @Override
    public List<RecentFilesDto> findAll() {
        List<RecentFilesDto> recentFiles = new ArrayList<>();
        String sql = "SELECT " +
                "lf.id AS file_id, " +
                "lf.name AS file_name, " +
                "lf.file_path, " +
                "lf.size, " +
                "lf.modified, " +
                "lf.is_remote, " +
                "lf.ssh_server_id, " +
                "lf.parsing_configuration_id AS parsing_config_id, " +
                "pc.id AS config_id, " +
                "pc.name AS config_name, " +
                "pc.description AS config_description, " +
                "pc.regex_pattern, " +
                "pc.timestamp_format " +
                "FROM recent_files rf " +
                "JOIN log_files lf ON rf.file_id = lf.id " +
                "LEFT JOIN parsing_configs pc ON lf.parsing_configuration_id = pc.id " +
                "ORDER BY rf.last_opened DESC";

        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement();
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
        Connection conn = getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, recentFile.getId());
            preparedStatement.setString(2, recentFile.getFileId());

            // Handle null lastOpened with current timestamp as fallback
            String lastOpenedStr = recentFile.getLastOpened() != null
                ? recentFile.getLastOpened().toString()
                : java.time.LocalDateTime.now().toString();
            preparedStatement.setString(3, lastOpenedStr);

            preparedStatement.executeUpdate();
            logger.info("Successfully saved recent file: {}", recentFile.getFileId());
        } catch (SQLException e) {
            logger.error("Error saving recent file: {}", recentFile.getId(), e);
        }
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM recent_files";
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            logger.error("Error deleting all recent files", e);
        }
    }

    @Override
    public RecentFilesDto findById(String id) {
        RecentFilesDto recentFileDto = null;
        String sql = "SELECT " +
                "lf.id AS file_id, " +
                "lf.name AS file_name, " +
                "lf.file_path, " +
                "lf.size, " +
                "lf.modified, " +
                "lf.is_remote, " +
                "lf.ssh_server_id, " +
                "lf.parsing_configuration_id AS parsing_config_id, " +
                "pc.id AS config_id, " +
                "pc.name AS config_name, " +
                "pc.description AS config_description, " +
                "pc.regex_pattern, " +
                "pc.timestamp_format " +
                "FROM recent_files rf " +
                "JOIN log_files lf ON rf.file_id = lf.id " +
                "LEFT JOIN parsing_configs pc ON lf.parsing_configuration_id = pc.id " +
                "WHERE rf.id = ?";

        Connection conn = getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, id);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    recentFileDto = mapRowToRecentFileDto(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding recent file by id: {}", id, e);
        }
        return recentFileDto;
    }

    @Override
    public Optional<RecentFile> findByFileId(String fileId) {
        RecentFile recentFile = null;
        String sql = "SELECT id, file_id, last_opened FROM recent_files WHERE file_id = ?";

        Connection conn = getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, fileId);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    recentFile = mapRowToRecentFile(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding recent file by fileId: {}", fileId, e);
        }
        return Optional.ofNullable(recentFile);
    }

    @Override
    public void deleteById(String id) {
        String sql = "DELETE FROM recent_files WHERE id = ?";

        Connection connection = getConnection();
        try(PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, id);
            preparedStatement.execute();
        } catch (SQLException e){
            logger.error("Error deleting recent file by id: {}", id, e);
        }
    }

    @Override
    public void deleteByFileId(String fileId) {
        String sql = "DELETE FROM recent_files WHERE file_id = ?";

        Connection connection = getConnection();
        try(PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, fileId);
            preparedStatement.execute();
        } catch (SQLException e){
            logger.error("Error deleting recent file by fileId: {}", fileId, e);
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

        // Populate LogFile from ResultSet
        logFile.setId(rs.getString("file_id"));
        logFile.setName(rs.getString("file_name"));
        logFile.setFilePath(rs.getString("file_path"));

        // Convert size from bytes to human readable format
        String sizeInBytes = rs.getString("size");
        logFile.setSize(FileUtils.formatFileSize(sizeInBytes));

        logFile.setModified(rs.getString("modified"));
        logFile.setRemote(rs.getBoolean("is_remote"));
        logFile.setSshServerID(rs.getString("ssh_server_id"));
        logFile.setParsingConfigurationID(rs.getString("parsing_config_id"));

        // Handle ParsingConfig - check if it exists in database (LEFT JOIN may return null)
        ParsingConfig parsingConfig = null;
        String configId = rs.getString("config_id");

        if (configId != null && !configId.isEmpty()) {
            // Config exists - populate it
            parsingConfig = new ParsingConfig();
            parsingConfig.setId(configId);
            parsingConfig.setName(rs.getString("config_name"));
            parsingConfig.setDescription(rs.getString("config_description"));
            parsingConfig.setRegexPattern(rs.getString("regex_pattern"));
            parsingConfig.setTimestampFormat(rs.getString("timestamp_format"));

            // IMPORTANT: Validate pattern to extract group names and compile regex
            parsingConfig.validatePattern();
        }

        return new RecentFilesDto(logFile, parsingConfig);
    }
}
