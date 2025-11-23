package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.FavoriteFolder;
import com.seeloggyplus.repository.FavoriteFolderRepository;
import com.seeloggyplus.repository.impl.FavoriteFolderRepositoryImpl;
import com.seeloggyplus.service.FavoriteFolderService;

import java.util.List;

public record FavoriteFolderServiceImpl(FavoriteFolderRepository favoriteFolderRepository) implements FavoriteFolderService {
    public FavoriteFolderServiceImpl() {
        this(new FavoriteFolderRepositoryImpl());
    }

    @Override
    public FavoriteFolder addFavorite(String name, String path, String locationId) {
        if (name == null || path == null || locationId == null) {
            throw new IllegalArgumentException("Favorite name, path, and locationId cannot be null");
        }
        FavoriteFolder favorite = new FavoriteFolder(null, name, path, locationId);
        return favoriteFolderRepository.save(favorite);
    }

    @Override
    public void removeFavorite(int id) {
        favoriteFolderRepository.delete(id);
    }

    @Override
    public List<FavoriteFolder> getFavoritesForLocation(String locationId) {
        return favoriteFolderRepository.findByLocationId(locationId);
    }

    @Override
    public boolean isFavorite(String path, String locationId) {
        return favoriteFolderRepository.findByPathAndLocationId(path, locationId).isPresent();
    }
}
