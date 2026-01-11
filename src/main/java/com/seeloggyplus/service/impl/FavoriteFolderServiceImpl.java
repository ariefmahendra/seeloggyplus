package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.FavoriteFolder;
import com.seeloggyplus.repository.FavoriteFolderRepository;
import com.seeloggyplus.repository.impl.FavoriteFolderRepositoryImpl;
import com.seeloggyplus.service.FavoriteFolderService;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Implementation of {@link FavoriteFolderService}.
 * Manages favorite folders using {@link FavoriteFolderRepository}.
 */
@RequiredArgsConstructor
public class FavoriteFolderServiceImpl implements FavoriteFolderService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FavoriteFolderServiceImpl.class);

    private final FavoriteFolderRepository favoriteFolderRepository;

    /**
     * Default constructor for manual instantiation.
     * Initializes with the default repository implementation.
     */
    public FavoriteFolderServiceImpl() {
        this(new FavoriteFolderRepositoryImpl());
    }

    /**
     * Adds a new favorite folder.
     * Validates input before saving.
     *
     * @param name       Display name
     * @param path       Filesystem path
     * @param locationId Location context
     * @throws IllegalArgumentException if any argument is null
     */
    @Override
    public void addFavorite(String name, String path, String locationId) {
        if (name == null || path == null || locationId == null) {
            log.error("Attempted to add favorite with null values: name={}, path={}, locationId={}", name, path, locationId);
            throw new IllegalArgumentException("Favorite name, path, and locationId cannot be null");
        }

        FavoriteFolder favorite = new FavoriteFolder(null, name, path, locationId);
        FavoriteFolder saved = favoriteFolderRepository.save(favorite);
        log.info("Added favorite folder: {} ({}) [{}]", name, path, locationId);
    }

    /**
     * Removes a favorite folder by ID.
     *
     * @param id Favorite ID
     */
    @Override
    public void removeFavorite(int id) {
        favoriteFolderRepository.delete(id);
        log.info("Removed favorite folder with ID: {}", id);
    }

    /**
     * Retrieves favorites for a specific location.
     *
     * @param locationId Location context
     * @return List of favorites
     */
    @Override
    public List<FavoriteFolder> getFavoritesForLocation(String locationId) {
        return favoriteFolderRepository.findByLocationId(locationId);
    }

    /**
     * Checks if a path is already favorite in the given location.
     *
     * @param path       Path to check
     * @param locationId Location context
     * @return true if exists
     */
    @Override
    public boolean isFavorite(String path, String locationId) {
        return favoriteFolderRepository.findByPathAndLocationId(path, locationId).isPresent();
    }
}
