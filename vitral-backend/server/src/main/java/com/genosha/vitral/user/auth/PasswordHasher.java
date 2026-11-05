package com.genosha.vitral.user.auth;

import jakarta.inject.Singleton;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes passwords with Argon2id and stores them in the PHC string format:
 * {@code $argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>}.
 *
 * <p>The parameters follow the OWASP minimum recommendation. They are written into every
 * hash, so they can be raised later without invalidating existing passwords.
 */
@Singleton
public class PasswordHasher {

    private static final int MEMORY_KB = 19_456;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    /** Upper bound when reading stored hashes, so a corrupted value cannot exhaust memory. */
    private static final int MAX_MEMORY_KB = 1_048_576;

    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getDecoder();

    private final SecureRandom random = new SecureRandom();
    private volatile String dummyHash;

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, MEMORY_KB, ITERATIONS, PARALLELISM, HASH_BYTES);
        return "$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s".formatted(
                MEMORY_KB, ITERATIONS, PARALLELISM,
                B64_ENCODER.encodeToString(salt), B64_ENCODER.encodeToString(hash));
    }

    public boolean verify(String password, String encoded) {
        if (password == null || encoded == null) {
            return false;
        }
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 6 || !parts[0].isEmpty()
                    || !"argon2id".equals(parts[1]) || !"v=19".equals(parts[2])) {
                return false;
            }
            int memory = 0;
            int iterations = 0;
            int parallelism = 0;
            for (String param : parts[3].split(",")) {
                String[] pair = param.split("=", 2);
                if (pair.length != 2) {
                    return false;
                }
                int value = Integer.parseInt(pair[1]);
                switch (pair[0]) {
                    case "m" -> memory = value;
                    case "t" -> iterations = value;
                    case "p" -> parallelism = value;
                    default -> {
                        return false;
                    }
                }
            }
            if (memory <= 0 || memory > MAX_MEMORY_KB || iterations <= 0 || parallelism <= 0) {
                return false;
            }
            byte[] salt = B64_DECODER.decode(parts[4]);
            byte[] expected = B64_DECODER.decode(parts[5]);
            if (expected.length == 0) {
                return false;
            }
            byte[] actual = derive(password, salt, memory, iterations, parallelism, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            // Malformed number or Base64: treat as a non-matching hash.
            return false;
        }
    }

    /**
     * Spends about the same time as a real check. Used when the account does not exist,
     * so response times do not reveal which e-mails are registered.
     */
    public void simulateVerify(String password) {
        String hash = dummyHash;
        if (hash == null) {
            hash = hash("vitral-timing-equalizer");
            dummyHash = hash;
        }
        verify(password == null ? "" : password, hash);
    }

    private static byte[] derive(String password, byte[] salt, int memoryKb, int iterations,
                                 int parallelism, int length) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memoryKb)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] out = new byte[length];
        generator.generateBytes(password.toCharArray(), out);
        return out;
    }
}
