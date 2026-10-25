package com.seeloggyplus.shared.util;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CredentialEncryptorTest {

    private static final class CountingRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;
        private final AtomicInteger requests = new AtomicInteger();

        @Override
        public void nextBytes(byte[] bytes) {
            requests.incrementAndGet();
            super.nextBytes(bytes);
        }
    }

    private static SecretKey key() throws Exception {
        var generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    @Test
    void requestsFreshIvBytesFromTheSameEntropySourceForEveryEncryption() throws Exception {
        var random = new CountingRandom();
        var encryptor = new CredentialEncryptor(key(), random);
        String first = encryptor.encrypt("sensitive value");
        String second = encryptor.encrypt("sensitive value");
        assertEquals(2, random.requests.get());
        assertNotEquals(first, second);
        assertEquals("sensitive value", encryptor.decrypt(first));
        assertEquals("sensitive value", encryptor.decrypt(second));
    }

    @Test
    void preservesTheExistingIvCiphertextAndTagStorageFormat() throws Exception {
        var secret = key();
        var random = new SecureRandom();
        var encryptor = new CredentialEncryptor(secret, random);
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        var reference = Cipher.getInstance("AES/GCM/NoPadding");
        reference.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(128, iv));
        byte[] ciphertext = reference.doFinal("old credential 日本語".getBytes(StandardCharsets.UTF_8));
        byte[] stored = Arrays.copyOf(iv, iv.length + ciphertext.length);
        System.arraycopy(ciphertext, 0, stored, iv.length, ciphertext.length);
        assertEquals("old credential 日本語", encryptor.decrypt("ENC:" + Base64.getEncoder().encodeToString(stored)));
        byte[] encoded = Base64.getDecoder().decode(encryptor.encrypt("new credential").substring(4));
        reference.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(128, Arrays.copyOf(encoded, 12)));
        assertEquals("new credential", new String(reference.doFinal(Arrays.copyOfRange(encoded, 12, encoded.length)),
                StandardCharsets.UTF_8));
    }

    @Test
    void concurrentEncryptionKeepsIndependentIvsAndDecryptableValues() throws Exception {
        var random = new CountingRandom();
        var encryptor = new CredentialEncryptor(key(), random);
        var ivs = new HashSet<String>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 32; i++) futures.add(executor.submit(() -> encryptor.encrypt("parallel credential")));
            for (var future : futures) {
                String stored = future.get(5, TimeUnit.SECONDS);
                byte[] bytes = Base64.getDecoder().decode(stored.substring(4));
                assertTrue(ivs.add(Base64.getEncoder().encodeToString(Arrays.copyOf(bytes, 12))));
                assertEquals("parallel credential", encryptor.decrypt(stored));
            }
        }
        assertEquals(32, random.requests.get());
    }

    @Test
    void emptyAndLegacyPlaintextValuesKeepTheirBehavior() throws Exception {
        var random = new CountingRandom();
        var encryptor = new CredentialEncryptor(key(), random);
        assertNull(encryptor.encrypt(null));
        assertEquals(" ", encryptor.encrypt(" "));
        assertEquals("legacy value", encryptor.decrypt("legacy value"));
        assertEquals(0, random.requests.get());
    }
}
