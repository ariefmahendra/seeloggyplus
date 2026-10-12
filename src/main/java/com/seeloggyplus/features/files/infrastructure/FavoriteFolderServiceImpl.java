package com.seeloggyplus.features.files.infrastructure;

import com.seeloggyplus.features.files.domain.FavoriteFolder;
import com.seeloggyplus.features.files.infrastructure.FavoriteFolderRepository;
import com.seeloggyplus.features.files.infrastructure.FavoriteFolderRepositoryImpl;
import com.seeloggyplus.features.files.application.FavoriteFolderService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class FavoriteFolderServiceImpl implements FavoriteFolderService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteFolderServiceImpl.class);

    private final FavoriteFolderRepository favoriteFolderRepository;

    public FavoriteFolderServiceImpl() {
        this(new FavoriteFolderRepositoryImpl());
    }

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

    @Override
    public void removeFavorite(int id) {
        favoriteFolderRepository.delete(id);
        log.info("Removed favorite folder with ID: {}", id);
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
