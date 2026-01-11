package com.seeloggyplus.service;

import com.seeloggyplus.model.FavoriteFolder;

import java.util.List;

public interface FavoriteFolderService {
    FavoriteFolder addFavorite(String name, String path, String locationId);

    void removeFavorite(int id);

    List<FavoriteFolder> getFavoritesForLocation(String locationId);

    boolean isFavorite(String path, String locationId);
}
