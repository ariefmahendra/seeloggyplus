package com.seeloggyplus.app;

import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.features.update.infrastructure.Hashing;
import com.seeloggyplus.features.update.infrastructure.UpdateBootstrapper;
import com.seeloggyplus.features.update.infrastructure.UpdateLayout;
import com.seeloggyplus.shared.database.DatabaseConfig;
import com.seeloggyplus.shared.database.DatabaseMigrator;
import com.seeloggyplus.shared.util.CredentialEncryptor;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;

public final class ReleaseCompatibilityVerifier {
    private static final String PREVIOUS = "0.7.0";
    private static final List<String> TABLES = List.of("parsing_configs", "ssh_servers", "preferences",
            "log_files", "recent_files", "favorite_folders", "server_groups");

    public static void main(String[] args) throws Exception {
        Path legacyJar = Path.of(args[0]).toAbsolutePath();
        Path zip = Path.of(args[1]).toAbsolutePath();
        String version = args[2];
        Path root = Files.createTempDirectory(Path.of("build"), "release compatibility ").toAbsolutePath();
        Path data = root.resolve(".data");
        Files.createDirectories(data);
        System.setProperty("seeloggyplus.dataDir", data.toString());
        System.setProperty("seeloggyplus.disableUpdateCheck", "true");
        Files.createDirectories(root.resolve("versions/" + PREVIOUS));
        Files.copy(legacyJar, root.resolve("versions/" + PREVIOUS + "/seeloggyplus.jar"));
        Files.writeString(root.resolve("current"), PREVIOUS);
        Files.writeString(root.resolve("launcher.properties"), "max.memory.gb=2\n");
        Files.writeString(data.resolve("known_hosts"), "example.test ssh-ed25519 synthetic-fixture\n");
        verifyEntries(zip);
        try (var legacy = new URLClassLoader(new java.net.URL[] {legacyJar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class.forName("org.sqlite.JDBC", true, legacy);
            Class<?> oldDatabase = legacy.loadClass("com.seeloggyplus.config.DatabaseConfig");
            Object oldInstance = oldDatabase.getMethod("getInstance").invoke(null);
            Connection oldConnection = (Connection) oldDatabase.getMethod("getConnection").invoke(oldInstance);
            Class<?> oldEncryption = legacy.loadClass("com.seeloggyplus.util.CredentialEncryptor");
            Object oldEncryptor = oldEncryption.getMethod("getInstance").invoke(null);
            String encrypted = (String) oldEncryption.getMethod("encrypt", String.class).invoke(oldEncryptor, "synthetic password");
            seed(oldConnection, encrypted);
            Map<String, List<String>> original = snapshot(oldConnection);
            oldConnection.close();
            byte[] key = Files.readAllBytes(data.resolve(".keystore"));
            byte[] hosts = Files.readAllBytes(data.resolve("known_hosts"));
            byte[] database = Files.readAllBytes(data.resolve("seeloggyplus.db"));
            Class<?> oldAsset = legacy.loadClass("com.seeloggyplus.update.UpdateAsset");
            Class<?> oldCoordinator = legacy.loadClass("com.seeloggyplus.update.UpdateCoordinator");
            Class<?> oldProgress = legacy.loadClass("com.seeloggyplus.update.UpdateCoordinator$ProgressListener");
            Object coordinator = oldCoordinator.getConstructor(Path.class, Path.class).newInstance(root, data.resolve("updates"));
            var install = oldCoordinator.getMethod("install", oldAsset, String.class, oldProgress, BooleanSupplier.class);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/package.zip", exchange -> {
                exchange.sendResponseHeaders(200, Files.size(zip));
                try (var output = exchange.getResponseBody()) { Files.copy(zip, output); }
            });
            server.start();
            try {
                String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/package.zip";
                Object asset = oldAsset.getConstructor(String.class, long.class, String.class)
                        .newInstance(url, Files.size(zip), Hashing.sha256(zip));
                Object result = install.invoke(coordinator, asset, version, null, (BooleanSupplier) () -> false);
                require((boolean) result.getClass().getMethod("success").invoke(result), "Old updater rejected candidate: " + result);
                require(java.util.Arrays.equals(database, Files.readAllBytes(data.resolve("seeloggyplus.db"))), "Updater modified database");
                var layout = new UpdateLayout(root);
                require(layout.currentVersion().orElseThrow().equals(version), "Candidate was not activated");
                require(layout.previousVersion().orElseThrow().equals(PREVIOUS), "Previous version was lost");
                var bootstrapper = new UpdateBootstrapper(layout);
                require(bootstrapper.shouldRollback(), "Unhealthy candidate must support rollback");
                require(bootstrapper.rollback().orElseThrow().equals(PREVIOUS), "Failed startup rollback failed");
                bootstrapper.activate(version);
                Connection current = DatabaseConfig.getInstance().getConnection();
                DatabaseMigrator.migrate(current, data.resolve("seeloggyplus.db"), false);
                require(original.equals(snapshot(current)), "Database content changed during upgrade");
                require("synthetic password".equals(CredentialEncryptor.getInstance().decrypt(encrypted)), "Stored credentials no longer decrypt");
                var preferences = new PreferenceServiceImpl();
                var startup = new HashMap<String, String>();
                for (var preference : preferences.getListPreferences()) startup.put(preference.getCode(), preference.getValue());
                ThemePreferenceMigration.normalize(startup, preferences);
                ThemePreferenceMigration.normalize(startup, preferences);
                require("light".equals(preferences.getPreferencesByCode("app_theme").orElseThrow()), "Legacy theme did not normalize");
                require(original.equals(snapshot(current)), "Theme migration changed unrelated user data");
                require(java.util.Arrays.equals(key, Files.readAllBytes(data.resolve(".keystore"))), "Encryption key was replaced");
                require(java.util.Arrays.equals(hosts, Files.readAllBytes(data.resolve("known_hosts"))), "Trusted hosts were replaced");
                require(Files.readString(root.resolve("launcher.properties")).equals("max.memory.gb=2\n"), "Launcher preferences were overwritten");
                smoke(layout.jarFor(version), root, data);
                String javaName = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
                Path bundledJava = layout.versionDir(version).resolve("jre/bin").resolve(javaName);
                if (Files.exists(bundledJava)) require(Files.isExecutable(bundledJava), "Bundled runtime lost execute permission after legacy staging");
                requirePreserved(original, snapshot(current));
                bootstrapper.confirmStartup(version);
                require(!bootstrapper.shouldRollback(), "Healthy candidate was not confirmed");
                Object badAsset = oldAsset.getConstructor(String.class, long.class, String.class)
                        .newInstance(url, Files.size(zip), "0".repeat(64));
                Object bad = install.invoke(coordinator, badAsset, version + ".invalid", null, (BooleanSupplier) () -> false);
                require(!(boolean) bad.getClass().getMethod("success").invoke(bad), "Invalid checksum accepted");
                require(layout.currentVersion().orElseThrow().equals(version), "Failed download changed active version");
                require(!Files.exists(layout.versionDir(version + ".invalid")), "Failed download staged an installation");
                bootstrapper.rollback();
                smoke(layout.jarFor(PREVIOUS), root, data);
                requirePreserved(original, snapshot(current));
                require("synthetic password".equals(oldEncryption.getMethod("decrypt", String.class).invoke(oldEncryptor,
                        CredentialEncryptor.getInstance().encrypt("synthetic password"))), "Rolled-back version cannot decrypt credentials");
                current.close();
                Path freshRoot = Files.createTempDirectory(Path.of("build"), "fresh install ").toAbsolutePath();
                new com.seeloggyplus.features.update.infrastructure.UpdateInstaller()
                        .stage(zip, freshRoot.resolve("versions"), version, null);
                smoke(freshRoot.resolve("versions").resolve(version).resolve("seeloggyplus.jar"), freshRoot, freshRoot.resolve(".data"));
            } finally { server.stop(0); }
        }
        System.out.println("RELEASE COMPATIBILITY PASS: 0.7.0 updater, real ZIP, legacy data, credentials, health and rollback: " + zip.getFileName());
    }

    private static void seed(Connection connection, String password) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO preferences(code,value) VALUES ('app_theme','graphite'),('app_font_size','14'),('ssh_download_directory','custom downloads')");
            statement.executeUpdate("INSERT INTO server_groups(name,sort_order) VALUES ('Production',0)");
            statement.executeUpdate("INSERT INTO parsing_configs(id,name,regex_pattern,timestamp_format) VALUES ('parser','Parser','.*','yyyy-MM-dd')");
            statement.executeUpdate("INSERT INTO ssh_servers(id,name,host,port,username,created_at,favorite,sort_order,group_name,auth_type,key_path,save_password) VALUES ('server','Server','example.test',22,'demo','2026-10-01',1,0,'Production','PASSWORD','/keys/demo',1)");
            statement.executeUpdate("INSERT INTO log_files(id,name,file_path,ssh_server_id,parsing_configuration_id) VALUES ('file','application.log','/var/log/application.log','server','parser')");
            statement.executeUpdate("INSERT INTO recent_files(id,file_id,last_opened,mode) VALUES ('recent','file','2026-10-01','TAIL')");
            statement.executeUpdate("INSERT INTO favorite_folders(name,path,locationId) VALUES ('Logs','/var/log','server')");
        }
        try (var statement = connection.prepareStatement("UPDATE ssh_servers SET password=?,key_passphrase=? WHERE id='server'")) {
            statement.setString(1, password); statement.setString(2, password); statement.executeUpdate();
        }
    }

    private static Map<String, List<String>> snapshot(Connection connection) throws Exception {
        var result = new java.util.TreeMap<String, List<String>>();
        for (String table : TABLES) {
            var rows = new ArrayList<String>();
            try (var statement = connection.createStatement(); var query = statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
                while (query.next()) {
                    if (table.equals("preferences") && query.getString(1).equals("app_theme")) continue;
                    var row = new ArrayList<String>();
                    for (int column = 1; column <= query.getMetaData().getColumnCount(); column++) row.add(query.getString(column));
                    rows.add(row.toString());
                }
            }
            result.put(table, rows);
        }
        try (var statement = connection.createStatement(); var query = statement.executeQuery("PRAGMA integrity_check")) {
            require(query.next() && query.getString(1).equals("ok"), "Database integrity check failed");
        }
        return result;
    }

    private static void verifyEntries(Path zip) throws Exception {
        Set<String> allowed = Set.of("seeloggyplus.jar", "launcher.bat", "launcher.sh", "launcher.properties", "launcher_readme.md", "version.properties");
        try (var archive = new ZipFile(zip.toFile())) {
            for (var entry : archive.stream().filter(entry -> !entry.isDirectory()).toList()) {
                String path = entry.getName().toLowerCase(java.util.Locale.ROOT);
                String name = path.substring(path.lastIndexOf('/') + 1);
                require(allowed.contains(name) || path.contains("/jre/") || path.contains("/lib/"), "Legacy whitelist rejects " + path);
                require(!path.contains("/help/") && !path.contains("/.data/"), "Package includes offline documentation or user data");
                if (name.equals("launcher.properties")) {
                    var defaults = new java.util.Properties();
                    try (var input = archive.getInputStream(entry)) { defaults.load(input); }
                    require("4".equals(defaults.getProperty("max.memory.gb")), "Package contains developer memory settings");
                    require(defaults.getProperty("java.home", "").isBlank(), "Package contains a developer Java path");
                }
            }
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static void requirePreserved(Map<String, List<String>> original, Map<String, List<String>> current) {
        for (var entry : original.entrySet()) require(current.get(entry.getKey()).containsAll(entry.getValue()), "User data lost: " + entry.getKey());
    }

    private static void smoke(Path jar, Path root, Path data) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        String binary = windows ? "java.exe" : "java";
        Path javaExecutable = jar.getParent().resolve("jre/bin").resolve(binary);
        if (!Files.isRegularFile(javaExecutable) || !Files.isExecutable(javaExecutable))
            javaExecutable = Path.of(System.getProperty("java.home"), "bin", binary);
        Path monocle = Path.of(Class.forName("com.sun.glass.ui.monocle.MonoclePlatformFactory")
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        String launcher;
        try (var archive = new java.util.jar.JarFile(jar.toFile())) {
            launcher = archive.getManifest().getMainAttributes().getValue("Main-Class");
            require(launcher != null && !launcher.isBlank(), "Packaged main class is missing");
        }
        Path log = root.resolve("startup-smoke.log");
        Process process = new ProcessBuilder(javaExecutable.toString(), "-Dglass.platform=Monocle", "-Dmonocle.platform=Headless",
                "-Dprism.order=sw", "-Djava.awt.headless=true", "-Dseeloggyplus.disableUpdateCheck=true",
                "-Dseeloggyplus.dataDir=" + data, "-Dseeloggyplus.installRoot=" + root,
                "-Dseeloggyplus.launcherProperties=" + root.resolve("launcher.properties"),
                "-cp", jar + java.io.File.pathSeparator + monocle, launcher)
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
            while (System.nanoTime() < deadline && process.isAlive()) {
                String output = Files.readString(log);
                if (output.contains("SeeLoggyPlus application started successfully")) {
                    require(!output.contains("Failed to start application"), "Startup failed: " + output);
                    System.out.println("PACKAGED STARTUP PASS: " + jar + " using " + javaExecutable);
                    return;
                }
                Thread.sleep(200);
            }
            throw new IllegalStateException("Packaged startup failed: " + Files.readString(log));
        } finally {
            process.destroy();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly().waitFor();
        }
    }
}
