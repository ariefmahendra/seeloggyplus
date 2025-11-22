package com.seeloggyplus.repository;

import com.seeloggyplus.model.FavoriteFolder;

import java.util.List;
import java.util.Optional;

public interface FavoriteFolderRepository {
    void setupTable();
    FavoriteFolder save(FavoriteFolder favoriteFolder);
    void delete(int id);
    List<FavoriteFolder> findByLocationId(String locationId);
    Optional<FavoriteFolder> findByPathAndLocationId(String path, String locationId);
}
