package com.seeloggyplus.service;

import com.seeloggyplus.model.FavoriteFolder;

import java.util.List;
import java.util.Optional;

public interface FavoriteFolderService {
    FavoriteFolder addFavorite(String name, String path, String locationId);
    void removeFavorite(int id);
    List<FavoriteFolder> getFavoritesForLocation(String locationId);
    boolean isFavorite(String path, String locationId);
}
