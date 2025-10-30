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
    private final Connection connection = DatabaseConfig.getInstance().getConnection();

    public LogFileRepositoryImpl() {
    }

    @Override
    public void insert(LogFile logFile) throws FatalDatabaseException {
        String sqlStr = "INSERT INTO log_files (id, name, file_path, size, modified, is_remote, ssh_server_id, parsing_configuration_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStr)){
            preparedStatement.setString(1, logFile.getId());
            preparedStatement.setString(2, logFile.getName());
            preparedStatement.setString(3, logFile.getFilePath());
            preparedStatement.setString(4, logFile.getSize());
            preparedStatement.setString(5, logFile.getModified());
            preparedStatement.setBoolean(6, logFile.isRemote());
            preparedStatement.setString(7, logFile.getSshServerID());
            preparedStatement.setString(8, logFile.getParsingConfigurationID());

            preparedStatement.executeUpdate();
        } catch (SQLException e){
            logger.error("Error inserting log file: {}", logFile.getName(), e);
        }
    }

    @Override
    public LogFile findById(String id) throws FatalDatabaseException, NotFoundException {
        String sqlStr = "SELECT id, name, file_path, size, modified, is_remote, ssh_server_id, parsing_configuration_id FROM log_files WHERE id = ?;";
        try(PreparedStatement preparedStatement = connection.prepareStatement(sqlStr)) {
            preparedStatement.setString(1, id);
            var resultSet = preparedStatement.executeQuery();

            if (resultSet.wasNull()) {
                throw new NotFoundException("Log file not found with id: " + id);
            }

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
        }catch (SQLException ex){
            logger.error("Error finding log file by id: {}", id, ex);
        }

        return null;
    }

    @Override
    public void deleteById(String id) throws FatalDatabaseException, NotFoundException {
        String sqlStr = "DELETE FROM log_files WHERE id = ?;";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStr)){
            preparedStatement.setString(1, id);
            int affectedRows = preparedStatement.executeUpdate();
            if (affectedRows == 0) {
                throw new NotFoundException("Log file with id " + id + " not found.");
            }
        } catch (SQLException ex) {
            logger.error("Error deleting log file by id: {}", id, ex);
            throw new FatalDatabaseException("Database error while deleting log file with id " + id, ex);
        }
    }

    @Override
    public void update(LogFile logFile) throws FatalDatabaseException, NotFoundException {
        String sqlStr = "UPDATE log_files SET name = ?, file_path = ?, size = ?, modified = ?, is_remote = ?, ssh_server_id = ?, parsing_configuration_id = ? WHERE id = ?;";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStr)){
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
        } catch (SQLException ex){
            logger.error("Error updating log file: {}", logFile.getName(), ex);
            throw new FatalDatabaseException("Error updating log file: " + logFile.getName(), ex);
        }
    }

    @Override
    public LogFile findByPathAndName(String filePath, String name) throws FatalDatabaseException, NotFoundException {
        String sqlStr = "SELECT id, name, file_path, size, modified, is_remote, ssh_server_id, parsing_configuration_id FROM log_files WHERE file_path = ? AND name = ?;";
        try(PreparedStatement preparedStatement = connection.prepareStatement(sqlStr)) {
            preparedStatement.setString(1, filePath);
            preparedStatement.setString(2, name);
            var resultSet = preparedStatement.executeQuery();

            if (resultSet.wasNull()) {
                throw new NotFoundException("Log file not found with path: " + filePath + " and name: " + name);
            }

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
        }catch (SQLException ex){
            logger.error("Error finding log file by path: {} and name: {}", filePath, name, ex);
            throw new FatalDatabaseException("Error finding log file by path and name", ex);
        }
        return null;
    }
}
