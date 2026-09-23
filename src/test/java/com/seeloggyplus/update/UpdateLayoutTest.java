package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The installation root must be resolved from a known location, and must fold the
 * blue/green {@code versions/<v>} directory back to the real install root so
 * subsequent updates do not stage/activate in the wrong folder.
 */
class UpdateLayoutTest {

    @Test
    void versionDirectoryFoldsUpToInstallRoot() {
        Path root = Path.of("C:/apps/seeloggyplus");
        Path versionDir = root.resolve(UpdateLayout.VERSIONS_DIR).resolve("0.5.0");
        assertEquals(root, UpdateLayout.resolveLayoutRoot(versionDir),
                "a running versions/<v> directory must resolve to the install root");
    }

    @Test
    void installRootStaysAsIs() {
        Path root = Path.of("C:/apps/seeloggyplus");
        assertEquals(root, UpdateLayout.resolveLayoutRoot(root));
    }

    @Test
    void unrelatedDirectoryIsNotFolded() {
        Path classesDir = Path.of("C:/work/seeloggyplus/build/classes/java/main");
        assertEquals(classesDir, UpdateLayout.resolveLayoutRoot(classesDir),
                "a classes directory that is not under versions/ must be returned unchanged");
    }

    @Test
    void pathNamedVersionsButNoParentFallsBackToItself() {
        Path lonely = Path.of("versions");
        assertEquals(lonely, UpdateLayout.resolveLayoutRoot(lonely));
    }
}
