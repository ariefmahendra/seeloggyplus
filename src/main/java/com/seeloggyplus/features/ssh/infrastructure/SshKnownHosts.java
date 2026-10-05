package com.seeloggyplus.features.ssh.infrastructure;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;
import com.seeloggyplus.shared.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Application-managed SSH {@code known_hosts} (trust-on-first-use).
 * <p>
 * Kept outside the DB so the file stays compatible with the standard OpenSSH
 * format and can be inspected by the user.
 */
public final class SshKnownHosts {

    public static final String FILE_NAME = "known_hosts";

    private SshKnownHosts() {
    }

    public static Path defaultFile() {
        return AppPaths.dataFile(FILE_NAME);
    }

    /**
     * Loads (creating an empty file when missing) the known-hosts repository and
     * registers it on the given JSch instance, so trusted keys persist to disk.
     */
    public static HostKeyRepository load(JSch jsch, Path file) throws JSchException {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(file)) {
                Files.createFile(file);
            }
        } catch (IOException e) {
            throw new JSchException("Could not create known_hosts file: " + file, e);
        }
        jsch.setKnownHosts(file.toString());
        return jsch.getHostKeyRepository();
    }

    public static int check(HostKeyRepository repository, String host, HostKey key) {
        // HostKey.getKey() is the base64 blob; the repository API expects raw bytes.
        byte[] raw = Base64.getDecoder().decode(key.getKey());
        return repository.check(host, raw);
    }

    /** Persists a trusted key (used by the "Accept &amp; save" flow). */
    public static void trust(HostKeyRepository repository, HostKey key) throws JSchException {
        repository.add(key, yesUserInfo());
    }

    public static String fingerprint(HostKey key) {
        return key == null ? "" : key.getFingerPrint(new JSch());
    }

    private static UserInfo yesUserInfo() {
        return new UserInfo() {
            @Override public String getPassphrase() { return null; }
            @Override public String getPassword() { return null; }
            @Override public boolean promptPassword(String message) { return false; }
            @Override public boolean promptPassphrase(String message) { return false; }
            @Override public boolean promptYesNo(String message) { return true; }
            @Override public void showMessage(String message) { }
        };
    }
}
