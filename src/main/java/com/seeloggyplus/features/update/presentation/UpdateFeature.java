package com.seeloggyplus.features.update.presentation;

import com.seeloggyplus.features.update.domain.AppVersion;
import com.seeloggyplus.features.update.domain.ReleaseNotes;
import com.seeloggyplus.features.update.domain.UpdatePreferences;
import com.seeloggyplus.features.update.infrastructure.UpdateBootstrapper;
import com.seeloggyplus.features.update.infrastructure.UpdateLayout;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.util.AppDistribution;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

public final class UpdateFeature {

    private static final Logger logger = LoggerFactory.getLogger(UpdateFeature.class);
    private final PreferenceService preferenceService;
    private final BiConsumer<String, String> notesPresenter;

    public UpdateFeature(PreferenceService preferenceService) {
        this(preferenceService, WhatsNewDialog::show);
    }

    UpdateFeature(PreferenceService preferenceService, BiConsumer<String, String> notesPresenter) {
        this.preferenceService = preferenceService;
        this.notesPresenter = notesPresenter;
    }

    public void reconcileUpdateStartup() {
        if (AppDistribution.isStore() || Boolean.getBoolean("seeloggyplus.disableUpdateCheck")
                || Boolean.getBoolean("seeloggyplus.dev")) return;
        try {
            new UpdateBootstrapper(new UpdateLayout(UpdateLayout.installationRoot()))
                    .confirmStartup(AppVersion.current())
                    .ifPresent(version -> logger.warn("Update failed to start; rolled back to version {}", version));
        } catch (Exception e) {
            logger.debug("Legacy update startup reconciliation skipped", e);
        }
    }

    public void showWhatsNewIfNeeded() {
        if (!AppDistribution.isStore() || Boolean.getBoolean("seeloggyplus.disableUpdateCheck")
                || Boolean.getBoolean("seeloggyplus.dev")) return;
        try {
            String current = AppVersion.current();
            String seen = preferenceService.getPreferencesByCode(UpdatePreferences.SEEN_VERSION)
                    .filter(value -> !value.isBlank()).orElse(null);
            if (!ReleaseNotes.shouldShow(current, seen)) return;
            preferenceService.saveOrUpdatePreferences(new Preference(UpdatePreferences.SEEN_VERSION, current));
            if (seen == null) return;
            String notes = ReleaseNotes.load().map(ReleaseNotes::toPlainText).orElse("");
            Platform.runLater(() -> notesPresenter.accept(current, notes));
        } catch (Exception e) {
            logger.debug("What's New dialog skipped", e);
        }
    }
}
