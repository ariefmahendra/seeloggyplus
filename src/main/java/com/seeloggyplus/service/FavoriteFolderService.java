package com.seeloggyplus.service;

import com.seeloggyplus.model.FavoriteFolder;

import java.util.List;

/**
 * Service interface for managing favorite folders.
 * Provides operations to add, remove, and retrieve favorite folders
 * based on location contexts (e.g., Local, SSH).
 */
public interface FavoriteFolderService {

    /**
     * Adds a new folder to favorites.
     *
     * @param name       The display name of the favorite
     * @param path       The absolute path of the folder
     * @param locationId The location context ID (e.g., "LOCAL", "SSH:user@host")
     */
    void addFavorite(String name, String path, String locationId);

    /**
     * Removes a favorite folder by its ID.
     *
     * @param id The ID of the favorite folder to remove
     */
    void removeFavorite(int id);

    /**
     * Retrieves all favorite folders for a specific location.
     *
     * @param locationId The location context ID
     * @return List of favorite folders for the given location
     */
    List<FavoriteFolder> getFavoritesForLocation(String locationId);

    /**
     * Checks if a specific path is already marked as a favorite in the given
     * location.
     *
     * @param path       The absolute path to check
     * @param locationId The location context ID
     * @return true if the folder is a favorite, false otherwise
     */
    boolean isFavorite(String path, String locationId);
}
