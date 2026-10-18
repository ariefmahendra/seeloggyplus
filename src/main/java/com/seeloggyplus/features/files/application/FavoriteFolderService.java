package com.seeloggyplus.features.files.application;

import com.seeloggyplus.features.files.domain.FavoriteFolder;

import java.util.List;

public interface FavoriteFolderService {

    void addFavorite(String name, String path, String locationId);

    void removeFavorite(int id);

    List<FavoriteFolder> getFavoritesForLocation(String locationId);

    boolean isFavorite(String path, String locationId);
}
