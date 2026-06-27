package com.seeloggyplus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for stored credentials.
 * <p>
 * Key is generated once on first use and stored in {@code .data/.keystore} alongside the DB.
 * Encrypted values are Base64-encoded with a {@code ENC:} prefix so plaintext legacy values
 * can be detected and migrated transparently.
 */
public class CredentialEncryptor {

    private static final Logger logger = LoggerFactory.getLogger(CredentialEncryptor.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int AES_KEY_BITS = 256;
    private static final String PREFIX = "ENC:";

    private static CredentialEncryptor instance;
    private final SecretKey secretKey;

    private CredentialEncryptor(SecretKey key) {
        this.secretKey = key;
    }

    public static synchronized CredentialEncryptor getInstance() {
        if (instance == null) {
            instance = new CredentialEncryptor(loadOrCreateKey());
        }
        return instance;
    }

    /**
     * Encrypt a plaintext credential. Returns Base64 string prefixed with "ENC:".
     * Returns null if input is null or blank.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: [12 bytes IV][ciphertext+tag]
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt credential", e);
        }
    }

    /**
     * Decrypt an encrypted credential string.
     * If the value does NOT have the "ENC:" prefix, it's treated as legacy plaintext and returned as-is.
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) return stored;
        if (!stored.startsWith(PREFIX)) {
            // Legacy plaintext — return as-is (caller should re-encrypt on next save)
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Decryption failed — returning null to force re-prompt", e);
            return null;
        }
    }

    /**
     * Check if a stored value is already encrypted.
     */
    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    // -------------------------------------------------------------------------

    private static SecretKey loadOrCreateKey() {
        Path keyPath = Paths.get(".", ".data", ".keystore");
        try {
            if (Files.exists(keyPath)) {
                byte[] keyBytes = Base64.getDecoder().decode(Files.readString(keyPath).trim());
                logger.debug("Loaded encryption key from {}", keyPath.toAbsolutePath());
                return new SecretKeySpec(keyBytes, "AES");
            }

            // Generate new key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_BITS, new SecureRandom());
            SecretKey key = keyGen.generateKey();

            // Save to file
            Files.createDirectories(keyPath.getParent());
            Files.writeString(keyPath, Base64.getEncoder().encodeToString(key.getEncoded()));

            // Restrict permissions on Unix
            try {
                Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows — no POSIX permissions, acceptable for desktop app
            }

            logger.info("Generated new encryption key at {}", keyPath.toAbsolutePath());
            return key;
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            logger.error("Failed to load/create encryption key", e);
            throw new RuntimeException("Cannot initialize credential encryption", e);
        }
    }
}
