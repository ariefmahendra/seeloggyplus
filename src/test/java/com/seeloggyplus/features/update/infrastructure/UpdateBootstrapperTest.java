package com.seeloggyplus.features.update.infrastructure;


import com.seeloggyplus.features.update.domain.UpdateException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateBootstrapperTest {

    private static void stageVersion(UpdateLayout layout, String version) throws Exception {
        Path jar = layout.jarFor(version);
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "jar-" + version);
    }

    @Test
    void activatesStagedVersionAndRemembersPrevious() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-layout");
        try {
            UpdateLayout layout = new UpdateLayout(root);
            stageVersion(layout, "0.2.0");
            stageVersion(layout, "0.3.0");
            layout.writeCurrent("0.2.0");

            UpdateBootstrapper bootstrapper = new UpdateBootstrapper(layout);
            bootstrapper.activate("0.3.0");

            assertEquals(Optional.of("0.3.0"), layout.currentVersion());
            assertEquals(Optional.of("0.2.0"), layout.previousVersion());
        } finally {
            UpdateLayout.deleteRecursively(root);
        }
    }

    @Test
    void refusesToActivateUnstagedVersion() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-layout");
        try {
            UpdateBootstrapper bootstrapper = new UpdateBootstrapper(new UpdateLayout(root));
            assertThrows(UpdateException.class, () -> bootstrapper.activate("9.9.9"));
        } finally {
            UpdateLayout.deleteRecursively(root);
        }
    }

    @Test
    void rollsBackWhenActiveVersionIsNotHealthy() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-layout");
        try {
            UpdateLayout layout = new UpdateLayout(root);
            stageVersion(layout, "0.2.0");
            stageVersion(layout, "0.3.0");
            layout.writePrevious("0.2.0");
            layout.writeCurrent("0.3.0"); // no .ok marker written

            UpdateBootstrapper bootstrapper = new UpdateBootstrapper(layout);
            assertTrue(bootstrapper.shouldRollback());

            assertEquals(Optional.of("0.2.0"), bootstrapper.rollback());
            assertEquals(Optional.of("0.2.0"), layout.currentVersion());
        } finally {
            UpdateLayout.deleteRecursively(root);
        }
    }

    @Test
    void doesNotRollbackAfterHealthyStart() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-layout");
        try {
            UpdateLayout layout = new UpdateLayout(root);
            stageVersion(layout, "0.2.0");
            stageVersion(layout, "0.3.0");
            layout.writePrevious("0.2.0");
            layout.writeCurrent("0.3.0");
            new UpdateBootstrapper(layout).markHealthy("0.3.0");

            UpdateBootstrapper bootstrapper = new UpdateBootstrapper(layout);
            assertFalse(bootstrapper.shouldRollback());
        } finally {
            UpdateLayout.deleteRecursively(root);
        }
    }

    @Test
    void confirmsTheRunningActiveVersionBeforeConsideringRollback() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-layout");
        try {
            UpdateLayout layout = new UpdateLayout(root);
            stageVersion(layout, "0.5.1");
            stageVersion(layout, "0.6.3");
            layout.writePrevious("0.5.1");
            layout.writeCurrent("0.6.3"); // first start: no .ok marker yet

            Optional<String> rollback = new UpdateBootstrapper(layout).confirmStartup("0.6.3");

            assertTrue(rollback.isEmpty(), "the version currently running must not roll itself back");
            assertEquals(Optional.of("0.6.3"), layout.currentVersion());
            assertTrue(layout.isHealthy("0.6.3"));
        } finally {
            UpdateLayout.deleteRecursively(root);
        }
    }

    @Test
    void rollsBackAnUnhealthyActiveVersionWhenAnotherVersionIsRunning() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-layout");
        try {
            UpdateLayout layout = new UpdateLayout(root);
            stageVersion(layout, "0.5.1");
            stageVersion(layout, "0.6.3");
            layout.writePrevious("0.5.1");
            layout.writeCurrent("0.6.3");

            Optional<String> rollback = new UpdateBootstrapper(layout).confirmStartup("0.5.1");

            assertEquals(Optional.of("0.5.1"), rollback);
            assertEquals(Optional.of("0.5.1"), layout.currentVersion());
        } finally {
            UpdateLayout.deleteRecursively(root);
        }
    }

    @Test
    void cleanupKeepsCurrentPreviousAndNewest() throws Exception {
        Path root = Files.createTempDirectory("seeloggy-layout");
        try {
            UpdateLayout layout = new UpdateLayout(root);
            for (String version : List.of("0.1.0", "0.2.0", "0.3.0", "0.4.0", "0.5.0")) {
                stageVersion(layout, version);
            }
            layout.writePrevious("0.1.0");
            layout.writeCurrent("0.5.0");

            int removed = layout.cleanup(2);

            assertEquals(2, removed);
            assertTrue(Files.exists(layout.versionDir("0.1.0")), "Previous version must be kept");
            assertTrue(Files.exists(layout.versionDir("0.5.0")), "Current version must be kept");
            assertTrue(Files.exists(layout.versionDir("0.4.0")), "Newest versions must be kept");
            assertFalse(Files.exists(layout.versionDir("0.3.0")));
            assertFalse(Files.exists(layout.versionDir("0.2.0")));
        } finally {
            UpdateLayout.deleteRecursively(root);
        }
    }
}
