package com.seeloggyplus.ssh;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Trust-on-first-use known_hosts behaviour: unknown hosts are surfaced, trusted
 * keys persist across reloads, and a different key for a known host is refused.
 */
class SshKnownHostsTest {

    @TempDir
    Path tempDir;

    /** A valid SSH public key blob (JSch parses the bytes, so they must be real). */
    private static byte[] newKeyBlob() throws Exception {
        JSch jsch = new JSch();
        KeyPair pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
        return pair.getPublicKeyBlob();
    }

    @Test
    @DisplayName("unknown host -> trusted -> persisted -> changed key refused")
    void trustOnFirstUseFlow() throws Exception {
        Path file = tempDir.resolve("known_hosts");
        String host = "example.test";

        JSch jsch = new JSch();
        HostKeyRepository repository = SshKnownHosts.load(jsch, file);
        assertTrue(Files.exists(file), "known_hosts file must be created on first use");

        HostKey first = new HostKey(host, newKeyBlob());
        assertEquals(HostKeyRepository.NOT_INCLUDED, SshKnownHosts.check(repository, host, first),
                "a host with no saved key must be reported as unknown");

        SshKnownHosts.trust(repository, first);

        // Reload from disk to prove the key was persisted in OpenSSH format.
        JSch reloadedJsch = new JSch();
        HostKeyRepository reloaded = SshKnownHosts.load(reloadedJsch, file);
        assertEquals(HostKeyRepository.OK, SshKnownHosts.check(reloaded, host, first),
                "a trusted key must be accepted after reload");

        HostKey different = new HostKey(host, newKeyBlob());
        assertEquals(HostKeyRepository.CHANGED, SshKnownHosts.check(reloaded, host, different),
                "a different key for a known host must be refused");
    }

    @Test
    @DisplayName("fingerprint is human-readable for a real key blob")
    void fingerprintIsReadable() throws Exception {
        JSch jsch = new JSch();
        HostKey key = new HostKey("host.test", newKeyBlob());

        String fingerprint = SshKnownHosts.fingerprint(key);

        assertNotNull(fingerprint);
        assertFalse(fingerprint.isBlank());
        assertTrue(fingerprint.contains(":"), "fingerprint should use the usual colon notation: " + fingerprint);
    }

    @Test
    @DisplayName("two different hosts do not share trust")
    void trustIsPerHost() throws Exception {
        Path file = tempDir.resolve("known_hosts");
        JSch jsch = new JSch();
        HostKeyRepository repository = SshKnownHosts.load(jsch, file);

        SshKnownHosts.trust(repository, new HostKey("a-" + UUID.randomUUID(), newKeyBlob()));

        JSch other = new JSch();
        HostKeyRepository reloaded = SshKnownHosts.load(other, file);
        assertEquals(HostKeyRepository.NOT_INCLUDED,
                SshKnownHosts.check(reloaded, "b-" + UUID.randomUUID(), new HostKey("b", newKeyBlob())));
    }
}
