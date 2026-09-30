package com.seeloggyplus.controller;

import com.seeloggyplus.model.FavoriteFolder;
import com.seeloggyplus.model.Preference;
import com.seeloggyplus.service.FavoriteFolderService;
import com.seeloggyplus.service.impl.FavoriteFolderServiceImpl;
import com.seeloggyplus.service.impl.PreferenceServiceImpl;
import com.seeloggyplus.util.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout regression for the File Management left panel: compact icon action bar,
 * the server tree owning the vertical space, and the Favorite folders section
 * collapsing when empty instead of showing a big empty box.
 */
@ExtendWith(ApplicationExtension.class)
class FileManagerLeftPanelLayoutTest {

    private static final String LOCAL = "local";

    private Stage stage;
    private UnifiedFileManagerDialogController controller;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        // Deterministic start: local drive, no leftover server preference.
        new PreferenceServiceImpl().saveOrUpdatePreferences(new Preference("file_manager_last_location", LOCAL));
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("action buttons form a compact icon bar above the tree")
    void actionButtonsAreCompact() throws Exception {
        Button newGroup = field("newGroupButton");
        Button favorite = field("favoriteCurrentButton");
        Button manage = field("manageServersButton");

        for (Button button : List.of(newGroup, favorite, manage)) {
            assertTrue(button.getParent() instanceof HBox,
                    "action buttons must share the compact toolbar row, not stack full-width");
            assertNotNull(button.getTooltip(), "icon-only buttons need a tooltip");
            assertTrue(button.getText() == null || button.getText().isBlank(),
                    "action buttons should be icon-only");
        }

        TreeView<?> tree = field("locationTree");
        assertEquals(Priority.ALWAYS, VBox.getVgrow(tree),
                "the server tree is the primary content and owns the free space");
    }

    @Test
    @DisplayName("Favorite folders section collapses when empty and expands when it has entries")
    void favoritesSectionCollapsesWhenEmpty() throws Exception {
        FavoriteFolderService favorites = new FavoriteFolderServiceImpl();
        TitledPane pane = field("favoritesPane");
        ListView<?> favoritesList = field("favoritesListView");

        // Start from a clean slate for the local location.
        for (FavoriteFolder existing : favorites.getFavoritesForLocation(LOCAL)) {
            favorites.removeFavorite(existing.getId());
        }
        reloadFavorites();
        assertTrue(favoritesList.getItems().isEmpty());
        assertFalse(pane.isExpanded(), "an empty favorites list must stay collapsed");

        String path = "C:/tmp/fav-" + UUID.randomUUID().toString().substring(0, 8);
        favorites.addFavorite("fav-" + UUID.randomUUID().toString().substring(0, 8), path, LOCAL);
        try {
            reloadFavorites();
            assertFalse(favoritesList.getItems().isEmpty(), "the added favorite must be listed");
            assertTrue(pane.isExpanded(), "the section must expand when it has entries");
        } finally {
            for (FavoriteFolder remaining : favorites.getFavoritesForLocation(LOCAL)) {
                favorites.removeFavorite(remaining.getId());
            }
            reloadFavorites();
        }
    }

    private void reloadFavorites() throws Exception {
        Field cacheField = UnifiedFileManagerDialogController.class.getDeclaredField("cachedFavoritesLocationId");
        cacheField.setAccessible(true);
        cacheField.set(controller, null);
        java.lang.reflect.Method load = UnifiedFileManagerDialogController.class
                .getDeclaredMethod("loadFavoritesForCurrentLocation");
        load.setAccessible(true);
        WaitForAsyncUtils.asyncFx(() -> {
            try {
                load.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).get();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) throws Exception {
        Field field = UnifiedFileManagerDialogController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(controller);
    }
}
