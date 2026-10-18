package com.seeloggyplus.features.logs.infrastructure.persistence;

import com.seeloggyplus.features.logs.application.port.ParsingConfigRepository;
import com.seeloggyplus.shared.database.FatalDatabaseException;
import com.seeloggyplus.shared.model.ParsingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ParsingConfigRepositoryImpl implements ParsingConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(ParsingConfigRepositoryImpl.class);
    private final Connection connection;

    public ParsingConfigRepositoryImpl(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    @Override
    public Optional<ParsingConfig> findById(String id) {
        String sql = "SELECT * FROM parsing_configs WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, id);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToParsingConfig(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding parsing config by id: {}", id, e);
            throw new FatalDatabaseException("Error finding parsing config by id: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ParsingConfig> findAll() {
        List<ParsingConfig> configs = new ArrayList<>();
        String sql = "SELECT * FROM parsing_configs";
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                configs.add(mapRowToParsingConfig(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all parsing configs", e);
            throw new FatalDatabaseException("Error finding all parsing configs", e);
        }
        return configs;
    }

    @Override
    public void save(ParsingConfig config) {
        String sql = "INSERT INTO parsing_configs(id, name, description, regex_pattern, timestamp_format) VALUES(?,?,?,?,?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, config.getId());
            preparedStatement.setString(2, config.getName());
            preparedStatement.setString(3, config.getDescription());
            preparedStatement.setString(4, config.getRegexPattern());
            preparedStatement.setString(5, config.getTimestampFormat());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving parsing config: {}", config.getName(), e);
            throw new FatalDatabaseException("Error saving parsing config: " + config.getName(), e);
        }
    }

    @Override
    public void update(ParsingConfig config) {
        String sql = "UPDATE parsing_configs SET name = ?, description = ?, regex_pattern = ?, timestamp_format = ? WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, config.getName());
            preparedStatement.setString(2, config.getDescription());
            preparedStatement.setString(3, config.getRegexPattern());
            preparedStatement.setString(4, config.getTimestampFormat());
            preparedStatement.setString(5, config.getId());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating parsing config: {}", config.getName(), e);
            throw new FatalDatabaseException("Error updating parsing config: " + config.getName(), e);
        }
    }

    @Override
    public void delete(ParsingConfig config) {
        String sql = "DELETE FROM parsing_configs WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, config.getId());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting parsing config: {}", config.getName(), e);
            throw new FatalDatabaseException("Error deleting parsing config: " + config.getName(), e);
        }
    }

    @Override
    public Optional<ParsingConfig> findDefault() {
        logger.debug("findDefault() called but default config feature is no longer used");
        return Optional.empty();
    }

    private ParsingConfig mapRowToParsingConfig(ResultSet rs) throws SQLException {
        ParsingConfig config = new ParsingConfig(rs.getString("name"), rs.getString("regex_pattern"));
        config.setId(rs.getString("id"));
        config.setDescription(rs.getString("description"));
        config.setTimestampFormat(rs.getString("timestamp_format"));
        return config;
    }
}
