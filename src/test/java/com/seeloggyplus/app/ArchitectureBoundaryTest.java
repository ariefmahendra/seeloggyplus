package com.seeloggyplus.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the package layout:
 * <pre>
 * com.seeloggyplus.app
 * com.seeloggyplus.shared.{model,dto,logs,settings,servers,tail,ssh,database,session,ui,util}
 * com.seeloggyplus.features.&lt;name&gt;.{domain,application,infrastructure,presentation}
 * </pre>
 */
class ArchitectureBoundaryTest {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;");

    private static final String APP = "com.seeloggyplus.app";
    private static final String SHARED = "com.seeloggyplus.shared";
    private static final String FEATURES = "com.seeloggyplus.features";

    /** Shared packages that must stay free of JavaFX so they can run headless. */
    private static final List<String> HEADLESS_PACKAGES = List.of(
            "com.seeloggyplus.shared.model",
            "com.seeloggyplus.shared.dto",
            "com.seeloggyplus.shared.logs",
            "com.seeloggyplus.shared.settings",
            "com.seeloggyplus.shared.servers",
            "com.seeloggyplus.shared.tail",
            "com.seeloggyplus.shared.database",
            "com.seeloggyplus.shared.ssh");

    private record SourceFile(Path file, String packageName, List<String> imports) {
        boolean in(String prefix) {
            return packageName != null && (packageName.equals(prefix) || packageName.startsWith(prefix + "."));
        }
    }

    private static List<SourceFile> sources;

    @BeforeAll
    static void scanSources() {
        Path root = Path.of("src/main/java");
        assertTrue(Files.isDirectory(root), "src/main/java not found; run tests from the project root");
        try (Stream<Path> files = Files.walk(root)) {
            sources = files.filter(path -> path.toString().endsWith(".java"))
                    .map(ArchitectureBoundaryTest::parse)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SourceFile parse(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(text);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : null;
        List<String> imports = new ArrayList<>();
        Matcher importMatcher = IMPORT_PATTERN.matcher(text);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
        return new SourceFile(file, packageName, imports);
    }

    @Test
    @DisplayName("app may depend on anything in the project")
    void appLayerIsUnrestricted() {
        // The composition root wires shared services and feature controllers together,
        // so it deliberately has no import restrictions.
        assertTrue(sources.stream().anyMatch(file -> file.in(APP)),
                "expected application classes in " + APP);
    }

    @Test
    @DisplayName("shared never depends on app or features")
    void sharedDoesNotDependOnAppOrFeatures() {
        List<String> violations = new ArrayList<>();
        violations.addAll(collectImports(List.of(SHARED), APP));
        violations.addAll(collectImports(List.of(SHARED), FEATURES));
        assertNoViolations(violations, "shared packages must not depend on app or feature packages");
    }

    @Test
    @DisplayName("a feature never depends on app or another feature")
    void featuresAreIsolated() {
        List<String> violations = new ArrayList<>();
        for (SourceFile file : sources) {
            if (!file.in(FEATURES)) continue;
            String ownFeature = featureOf(file.packageName());
            for (String imported : file.imports()) {
                if (imported.equals(APP) || imported.startsWith(APP + ".")) {
                    violations.add(file.file() + " imports " + imported);
                    continue;
                }
                if (!imported.startsWith(FEATURES + ".")) continue;
                String importedFeature = featureOf(imported);
                if (ownFeature != null && !ownFeature.equals(importedFeature)) {
                    violations.add(file.file() + " imports " + imported);
                }
            }
        }
        assertNoViolations(violations, "a feature must not import the app layer or another feature");
    }

    @Test
    @DisplayName("feature layers only depend on lower layers")
    void featureLayersRespectDependencies() {
        List<String> violations = new ArrayList<>();
        for (SourceFile file : sources) {
            if (!file.in(FEATURES)) continue;
            String ownFeature = featureOf(file.packageName());
            String ownLayer = layerOf(file.packageName());
            if (ownFeature == null || ownLayer == null) continue;
            for (String imported : file.imports()) {
                if (featureOf(imported) == null || !ownFeature.equals(featureOf(imported))) continue;
                String importedLayer = layerOf(imported);
                if (importedLayer == null) continue;
                if ("domain".equals(ownLayer)) {
                    violations.add(file.file() + " imports " + imported);
                } else if ("application".equals(ownLayer)
                        && ("infrastructure".equals(importedLayer) || "presentation".equals(importedLayer))) {
                    violations.add(file.file() + " imports " + imported);
                }
            }
        }
        assertNoViolations(violations,
                "domain must stay pure; application must not use infrastructure or presentation");
    }

    @Test
    @DisplayName("model stays a pure domain package")
    void modelIsIndependent() {
        List<String> violations = new ArrayList<>();
        for (SourceFile file : sources) {
            if (!file.in("com.seeloggyplus.shared.model")) continue;
            for (String imported : file.imports()) {
                if (imported.startsWith("com.seeloggyplus.")) {
                    violations.add(file.file() + " imports " + imported);
                }
            }
        }
        assertNoViolations(violations, "model must not depend on other application packages");
    }

    @Test
    @DisplayName("headless layers stay free of JavaFX")
    void headlessLayersDoNotImportJavaFx() {
        List<String> violations = collectImports(HEADLESS_PACKAGES, "javafx");
        assertNoViolations(violations, "headless layers must not import JavaFX");
    }

    private static String featureOf(String packageName) {
        if (packageName == null || !packageName.startsWith(FEATURES + ".")) {
            return null;
        }
        String rest = packageName.substring(FEATURES.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static String layerOf(String packageName) {
        String feature = featureOf(packageName);
        if (feature == null) {
            return null;
        }
        String rest = packageName.substring(FEATURES.length() + 1 + feature.length());
        if (!rest.startsWith(".")) {
            return null;
        }
        rest = rest.substring(1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static List<String> collectImports(List<String> packagePrefixes, String importedPrefix) {
        List<String> violations = new ArrayList<>();
        for (SourceFile file : sources) {
            boolean relevant = packagePrefixes.stream().anyMatch(file::in);
            if (!relevant) continue;
            for (String imported : file.imports()) {
                if (imported.equals(importedPrefix) || imported.startsWith(importedPrefix + ".")) {
                    violations.add(file.file() + " imports " + imported);
                }
            }
        }
        return violations;
    }

    private static void assertNoViolations(List<String> violations, String message) {
        assertTrue(violations.isEmpty(), message + "\n" + String.join("\n", violations));
    }
}
