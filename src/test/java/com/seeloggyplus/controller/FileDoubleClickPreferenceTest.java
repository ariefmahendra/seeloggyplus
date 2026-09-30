package com.seeloggyplus.controller;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FileDoubleClickPreferenceTest {
    @Test void tailIsExplicitAndUnknownSettingsDefaultToOpen() {
        assertEquals(UnifiedFileManagerDialogController.OpenAction.TAIL, UnifiedFileManagerDialogController.preferredFileAction("TAIL"));
        for (String value : new String[]{null, "", "OPEN", "invalid"})
            assertEquals(UnifiedFileManagerDialogController.OpenAction.OPEN, UnifiedFileManagerDialogController.preferredFileAction(value));
    }
}
