package com.seeloggyplus.features.recent.presentation;

import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.shared.model.LogFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RecentFileReadabilityTest {
    private RecentFilesDto entry(String server) {
        LogFile file = new LogFile(); file.setName("app.log"); file.setFilePath("/var/log/app.log");
        file.setRemote(true); file.setSshServerID("id-" + server);
        return new RecentFilesDto(file, null, server, "TAIL");
    }
    @Test void identicalNamesAreDistinguishedBySourceAndSearch() {
        var prod = entry("Production"); var stage = entry("Staging");
        assertEquals("Production", RecentFileTreeCell.sourceLabel(prod));
        assertNotEquals(RecentFileTreeCell.sourceLabel(prod), RecentFileTreeCell.sourceLabel(stage));
        assertTrue(RecentFileTreeCell.matches(prod, "PRODUCTION app.log"));
        assertFalse(RecentFileTreeCell.matches(stage, "production app.log"));
        assertTrue(RecentFileTreeCell.matches(prod, "/var/log"));
    }
    @Test void missingServersKeepTheirIdentity() {
        var missing = entry(null);
        assertTrue(RecentFileTreeCell.sourceLabel(missing).contains("id-null"));
        missing.logFile().setRemote(false);
        assertEquals("Local", RecentFileTreeCell.sourceLabel(missing));
    }
    @Test void sourceLabelNeverMentionsTheOpenMode() {
        assertFalse(RecentFileTreeCell.sourceLabel(entry("Production")).toLowerCase().contains("tail"));
        assertFalse(RecentFileTreeCell.sourceLabel(entry("Production")).toLowerCase().contains("open"));
    }
}
