package com.seeloggyplus.repository.impl;

import com.seeloggyplus.config.DatabaseConfig;
import com.seeloggyplus.exceptions.database.FatalDatabaseException;
import com.seeloggyplus.exceptions.database.NotFoundException;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.repository.LogFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class LogFileRepositoryImpl implements LogFileRepository {

    private static final Logger logger = LoggerFactory.getLogger(LogFileRepositoryImpl.class);

    public LogFileRepositoryImpl() {
    }

    private Connection getConnection() {
        return DatabaseConfig.getInstance().getConnection();
    }

    @Override
    public void insert(LogFile logFile) throws FatalDatabaseException {
        String sqlStr = "INSERT INTO log_files (id, name, file_path, size, modified, is_remote, ssh_server_id, parsing_configuration_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        Connection conn = getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sqlStr)){
            preparedStatement.setString(1, logFile.getId());
            preparedStatement.setString(2, logFile.getName());
            preparedStatement.setString(3, logFile.getFilePath());
            preparedStatement.setString(4, logFile.getSize());
            preparedStatement.setString(5, logFile.getModified());
            preparedStatement.setBoolean(6, logFile.isRemote());
            preparedStatement.setString(7, logFile.getSshServerID());
            preparedStatement.setString(8, logFile.getParsingConfigurationID());

            preparedStatement.executeUpdate();
            logger.info("Successfully inserted log file: {}", logFile.getName());
        } catch (SQLException e){
            logger.error("Error inserting log file: {}", logFile.getName(), e);
            throw new FatalDatabaseException("Failed to insert log file: " + logFile.getName(), e);
        }
    }

    @Override
    public LogFile findById(String id) throws FatalDatabaseException, NotFoundException {
        String sqlStr = "SELECT id, name, file_path, size, modified, is_remote, ssh_server_id, parsing_configuration_id FROM log_files WHERE id = ?;";
        Connection conn = getConnection();
        try(PreparedStatement preparedStatement = conn.prepareStatement(sqlStr)) {
            preparedStatement.setString(1, id);
            var resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                return new LogFile(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("file_path"),
                        resultSet.getString("size"),
                        resultSet.getString("modified"),
                        resultSet.getBoolean("is_remote"),
                        resultSet.getString("ssh_server_id"),
                        resultSet.getString("parsing_configuration_id")
                );
            }

            throw new NotFoundException("Log file not found with id: " + id);
        } catch (SQLException ex){
            logger.error("Error finding log file by id: {}", id, ex);
            throw new FatalDatabaseException("Database error while finding log file with id: " + id, ex);
        }
    }

    @Override
    public void deleteById(String id) throws FatalDatabaseException, NotFoundException {
        String sqlStr = "DELETE FROM log_files WHERE id = ?;";
        Connection conn = getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sqlStr)){
            preparedStatement.setString(1, id);
            int affectedRows = preparedStatement.executeUpdate();
            if (affectedRows == 0) {
                throw new NotFoundException("Log file with id " + id + " not found.");
            }
            logger.info("Successfully deleted log file with id: {}", id);
        } catch (SQLException ex) {
            logger.error("Error deleting log file by id: {}", id, ex);
            throw new FatalDatabaseException("Database error while deleting log file with id " + id, ex);
        }
    }

    @Override
    public void update(LogFile logFile) throws FatalDatabaseException, NotFoundException {
        String sqlStr = "UPDATE log_files SET name = ?, file_path = ?, size = ?, modified = ?, is_remote = ?, ssh_server_id = ?, parsing_configuration_id = ? WHERE id = ?;";
        Connection conn = getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sqlStr)){
            preparedStatement.setString(1, logFile.getName());
            preparedStatement.setString(2, logFile.getFilePath());
            preparedStatement.setString(3, logFile.getSize());
            preparedStatement.setString(4, logFile.getModified());
            preparedStatement.setBoolean(5, logFile.isRemote());
            preparedStatement.setString(6, logFile.getSshServerID());
            preparedStatement.setString(7, logFile.getParsingConfigurationID());
            preparedStatement.setString(8, logFile.getId());

            int rowsAffected = preparedStatement.executeUpdate();

            if (rowsAffected == 0) {
                throw new NotFoundException("LogFile with ID " + logFile.getId() + " not found.");
            }
            logger.info("Successfully updated log file: {}", logFile.getName());
        } catch (SQLException ex){
            logger.error("Error updating log file: {}", logFile.getName(), ex);
            throw new FatalDatabaseException("Error updating log file: " + logFile.getName(), ex);
        }
    }

    @Override
    public LogFile findByPathAndName(String filePath, String name) throws FatalDatabaseException, NotFoundException {
        String sqlStr = "SELECT id, name, file_path, size, modified, is_remote, ssh_server_id, parsing_configuration_id FROM log_files WHERE file_path = ? AND name = ?;";
        Connection conn = getConnection();
        try(PreparedStatement preparedStatement = conn.prepareStatement(sqlStr)) {
            preparedStatement.setString(1, filePath);
            preparedStatement.setString(2, name);
            var resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                return new LogFile(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("file_path"),
                        resultSet.getString("size"),
                        resultSet.getString("modified"),
                        resultSet.getBoolean("is_remote"),
                        resultSet.getString("ssh_server_id"),
                        resultSet.getString("parsing_configuration_id")
                );
            }

            throw new NotFoundException("Log file not found with path: " + filePath + " and name: " + name);
        } catch (SQLException ex){
            logger.error("Error finding log file by path: {} and name: {}", filePath, name, ex);
            throw new FatalDatabaseException("Error finding log file by path and name", ex);
        }
    }

    @Override
    public void deleteAll() throws FatalDatabaseException {
        String sqlStr = "DELETE FROM log_files;";
        Connection conn = getConnection();
        try (PreparedStatement preparedStatement = conn.prepareStatement(sqlStr)){
            preparedStatement.executeUpdate();
            logger.info("Successfully deleted all log files");
        } catch (SQLException ex){
            logger.error("Error deleting all log files", ex);
            throw new FatalDatabaseException("Error deleting all log files", ex);
        }
    }
}
