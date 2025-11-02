package com.seeloggyplus.repository.impl;

import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.repository.ParsingConfigRepository;
import com.seeloggyplus.config.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParsingConfigRepositoryImpl implements ParsingConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(ParsingConfigRepositoryImpl.class);
    private final Connection connection = DatabaseConfig.getInstance().getConnection();

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
        }
    }

    @Override
    public Optional<ParsingConfig> findDefault() {
        // Default config feature has been removed - each file has its own associated config
        // Return empty Optional for backward compatibility
        logger.debug("findDefault() called but default config feature is no longer used");
        return Optional.empty();
    }

    private ParsingConfig mapRowToParsingConfig(ResultSet rs) throws SQLException {
        ParsingConfig config = new ParsingConfig();
        config.setId(rs.getString("id"));
        config.setName(rs.getString("name"));
        config.setDescription(rs.getString("description"));
        config.setRegexPattern(rs.getString("regex_pattern"));
        config.setTimestampFormat(rs.getString("timestamp_format"));

        // IMPORTANT: Validate pattern to extract group names and set isValid flag
        config.validatePattern();

        return config;
    }
}
