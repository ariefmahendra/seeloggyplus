package com.seeloggyplus.repository.impl;

import com.seeloggyplus.config.DatabaseConfig;
import com.seeloggyplus.model.FavoriteFolder;
import com.seeloggyplus.repository.FavoriteFolderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FavoriteFolderRepositoryImpl implements FavoriteFolderRepository {

    private static final Logger logger = LoggerFactory.getLogger(FavoriteFolderRepositoryImpl.class);

    @Override
    public void setupTable() {
        String sql = "CREATE TABLE IF NOT EXISTS favorite_folders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "path TEXT NOT NULL," +
                "locationId TEXT NOT NULL," +
                "UNIQUE(path, locationId)" +
                ");";

        try (Statement stmt = DatabaseConfig.getInstance().getConnection().createStatement()) {
            stmt.execute(sql);
            logger.info("Favorite Folders table created or already exists.");
        } catch (SQLException e) {
            logger.error("Error creating favorite_folders table", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public FavoriteFolder save(FavoriteFolder favoriteFolder) {
        String sql = "INSERT INTO favorite_folders(name, path, locationId) VALUES(?, ?, ?)";
        Connection conn = DatabaseConfig.getInstance().getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, favoriteFolder.getName());
            pstmt.setString(2, favoriteFolder.getPath());
            pstmt.setString(3, favoriteFolder.getLocationId());
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        favoriteFolder.setId(generatedKeys.getInt(1));
                    }
                }
            }
            logger.info("Saved favorite folder: {}", favoriteFolder.getName());
            return favoriteFolder;
        } catch (SQLException e) {
            logger.error("Error saving favorite folder: " + favoriteFolder.getName(), e);
            // Handle UNIQUE constraint violation
            if (e.getErrorCode() == 19) { // SQLite constraint violation
                return null; // Or throw a custom exception
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM favorite_folders WHERE id = ?";
        try (PreparedStatement pstmt = DatabaseConfig.getInstance().getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            logger.info("Deleted favorite folder with id: {}", id);
        } catch (SQLException e) {
            logger.error("Error deleting favorite folder with id: " + id, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<FavoriteFolder> findByLocationId(String locationId) {
        List<FavoriteFolder> favorites = new ArrayList<>();
        String sql = "SELECT * FROM favorite_folders WHERE locationId = ? ORDER BY name";
        try (PreparedStatement pstmt = DatabaseConfig.getInstance().getConnection().prepareStatement(sql)) {
            pstmt.setString(1, locationId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                favorites.add(new FavoriteFolder(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("path"),
                        rs.getString("locationId")
                ));
            }
        } catch (SQLException e) {
            logger.error("Error finding favorite folders for location: " + locationId, e);
            throw new RuntimeException(e);
        }
        return favorites;
    }

    @Override
    public Optional<FavoriteFolder> findByPathAndLocationId(String path, String locationId) {
        String sql = "SELECT * FROM favorite_folders WHERE path = ? AND locationId = ?";
        try (PreparedStatement pstmt = DatabaseConfig.getInstance().getConnection().prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.setString(2, locationId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new FavoriteFolder(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("path"),
                        rs.getString("locationId")
                ));
            }
        } catch (SQLException e) {
            logger.error("Error finding favorite folder by path and location", e);
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }
}
