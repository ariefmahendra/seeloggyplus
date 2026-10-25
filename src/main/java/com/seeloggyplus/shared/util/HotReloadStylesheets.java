package com.seeloggyplus.shared.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

final class HotReloadStylesheets implements AutoCloseable {

    private final int retainedCopies;
    private final List<Path> files = new ArrayList<>();
    private Path directory;

    HotReloadStylesheets(int retainedCopies) {
        if (retainedCopies < 1) throw new IllegalArgumentException("At least one stylesheet copy must be retained");
        this.retainedCopies = retainedCopies;
    }

    synchronized void prepare() throws IOException {
        if (directory == null) {
            directory = Files.createTempDirectory("seeloggy-hotreload-", directoryPermissions());
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Private hot-reload directory is unavailable");
        }
    }

    synchronized Path copy(Path source) throws IOException {
        prepare();
        String base = source.getFileName().toString().replaceFirst("\\.css$", "");
        Path target = Files.createTempFile(directory, "seeloggy-hotreload-" + base + "-", ".css", filePermissions());
        try (var input = Files.newInputStream(source);
             var output = Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            input.transferTo(output);
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
        files.add(target);
        while (files.size() > retainedCopies) {
            Files.deleteIfExists(files.getFirst());
            files.removeFirst();
        }
        return target;
    }

    private static FileAttribute<?>[] directoryPermissions() throws IOException {
        var views = FileSystems.getDefault().supportedFileAttributeViews();
        if (views.contains("posix")) {
            return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------"))};
        }
        if (!views.contains("acl")) throw new IOException("Private directory permissions are unsupported");
        var owner = FileSystems.getDefault().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        var entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT).build();
        return new FileAttribute<?>[]{new FileAttribute<List<AclEntry>>() {
            @Override
            public String name() {
                return "acl:acl";
            }

            @Override
            public List<AclEntry> value() {
                return List.of(entry);
            }
        }};
    }

    private static FileAttribute<?>[] filePermissions() {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------"))};
        }
        return new FileAttribute<?>[0];
    }

    @Override
    public synchronized void close() throws IOException {
        if (directory == null) return;
        if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            for (Path file : List.copyOf(files)) {
                Files.deleteIfExists(file);
                files.remove(file);
            }
        }
        Files.deleteIfExists(directory);
        directory = null;
        files.clear();
    }
}
