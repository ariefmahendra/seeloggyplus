package com.seeloggyplus.shared.util;

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
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.security.NoSuchAlgorithmException;

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

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt credential", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) return stored;
        if (!stored.startsWith(PREFIX)) {
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

    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    private static SecretKey loadOrCreateKey() {
        Path keyPath = AppPaths.dataFile(".keystore");
        try {
            if (Files.exists(keyPath)) {
                byte[] keyBytes = Base64.getDecoder().decode(Files.readString(keyPath).trim());
                logger.debug("Loaded encryption key from {}", keyPath.toAbsolutePath());
                return new SecretKeySpec(keyBytes, "AES");
            }

            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_BITS, new SecureRandom());
            SecretKey key = keyGen.generateKey();

            Files.createDirectories(keyPath.getParent());
            Files.writeString(keyPath, Base64.getEncoder().encodeToString(key.getEncoded()));

            try {
                Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
            }

            logger.info("Generated new encryption key at {}", keyPath.toAbsolutePath());
            return key;
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Failed to load/create encryption key", e);
            throw new RuntimeException("Cannot initialize credential encryption", e);
        }
    }
}
