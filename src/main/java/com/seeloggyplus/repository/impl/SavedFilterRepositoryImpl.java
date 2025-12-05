package com.seeloggyplus.repository.impl;

import com.seeloggyplus.config.DatabaseConfig;
import com.seeloggyplus.model.SavedFilter;
import com.seeloggyplus.repository.SavedFilterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SavedFilterRepositoryImpl implements SavedFilterRepository {

    private static final Logger logger = LoggerFactory.getLogger(SavedFilterRepositoryImpl.class);
    private final Connection connection = DatabaseConfig.getInstance().getConnection();

    @Override
    public void save(SavedFilter filter) {
        String sql = "INSERT INTO saved_filters (id, name, search_text, is_regex, is_case_sensitive, log_level, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, filter.getId());
            stmt.setString(2, filter.getName());
            stmt.setString(3, filter.getSearchText());
            stmt.setBoolean(4, filter.isRegex());
            stmt.setBoolean(5, filter.isCaseSensitive());
            stmt.setString(6, filter.getLogLevel());
            stmt.setString(7, filter.getCreatedAt());
            stmt.executeUpdate();
            logger.info("Saved filter: {}", filter.getName());
        } catch (SQLException e) {
            logger.error("Error saving filter", e);
        }
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM saved_filters WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
            logger.info("Deleted filter: {}", id);
        } catch (SQLException e) {
            logger.error("Error deleting filter", e);
        }
    }

    @Override
    public List<SavedFilter> findAll() {
        List<SavedFilter> list = new ArrayList<>();
        String sql = "SELECT * FROM saved_filters ORDER BY created_at DESC";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToFilter(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all filters", e);
        }
        return list;
    }

    @Override
    public Optional<SavedFilter> findByName(String name) {
        String sql = "SELECT * FROM saved_filters WHERE name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToFilter(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding filter by name", e);
        }
        return Optional.empty();
    }

    private SavedFilter mapResultSetToFilter(ResultSet rs) throws SQLException {
        return new SavedFilter(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("search_text"),
                rs.getBoolean("is_regex"),
                rs.getBoolean("is_case_sensitive"),
                rs.getString("log_level"),
                rs.getString("created_at")
        );
    }
}
