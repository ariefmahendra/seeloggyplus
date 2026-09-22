package com.seeloggyplus.update;

import com.seeloggyplus.Main;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the "released app shows vDEV" defect.
 *
 * The released application read {@code /version.properties} off the classpath. When
 * that resource was missing (the generated resource was not reliably packaged), the
 * version silently fell back to "DEV" even for tagged releases. These tests lock in
 * that the Gradle build always generates and packages a real version.
 */
class AppVersionTest {

    @Test
    @DisplayName("AppVersion resolves the version from gradle.properties, never DEV")
    void currentVersionIsResolvedFromGradleProperties() throws Exception {
        File gradleProps = new File("gradle.properties");
        assertTrue(gradleProps.exists(), "gradle.properties must exist at project root");

        Properties props = new Properties();
        try (var in = Files.newInputStream(gradleProps.toPath())) {
            props.load(in);
        }
        String expected = props.getProperty("version");

        assertFalse("DEV".equalsIgnoreCase(AppVersion.current()),
                "Released/current version must not fall back to DEV");
        assertEquals(expected, AppVersion.current(),
                "AppVersion.current() must equal the version declared in gradle.properties");
        assertTrue(AppVersion.current().matches("\\d+(\\.\\d+)+.*"),
                "Version must look like a real version, was: " + AppVersion.current());
    }

    @Test
    @DisplayName("Main.VERSION and AppVersion.current() agree (single source of truth)")
    void mainVersionMatchesAppVersion() {
        assertEquals(Main.VERSION, AppVersion.current(),
                "Main.VERSION must not drift from AppVersion.current()");
    }

    @Test
    @DisplayName("build.gradle generates version.properties into a cached, declared resource dir")
    void buildGradleWiresGeneratedVersionResources() throws Exception {
        File buildGradle = new File("build.gradle");
        assertTrue(buildGradle.exists(), "build.gradle must exist at project root");
        String content = Files.readString(buildGradle.toPath());

        assertTrue(content.contains("sourceSets.main.resources.srcDir(generatedVersionResources)"),
                "The generated version directory must be registered as a resource source so it is "
                        + "always packaged (and cache-correct)");
        assertTrue(content.contains("outputs.dir(generatedVersionResources)"),
                "generateVersionProperties must declare its output directory");
        assertTrue(content.contains("inputs.property('version'"),
                "generateVersionProperties must declare the version input so the build cache "
                        + "cannot serve a stale output");
        assertTrue(content.contains("processResources.dependsOn generateVersionProperties"),
                "processResources must depend on generateVersionProperties");
    }
}
