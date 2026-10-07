package com.perera.fishgame.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Turns passwords into hashes that are safe to store (virtual identity).
 *
 * <ul>
 * <li><b>Salt:</b> every password gets its own random 16-byte salt, so two
 * users with the same password have different hashes and pre-computed
 * ("rainbow") tables are useless.</li>
 * <li><b>Slow hash:</b> PBKDF2 with HMAC-SHA256 and many iterations makes each
 * guess expensive for an attacker. The iteration count is stored inside the
 * hash string, so it can be increased later without breaking old accounts.</li>
 * <li><b>Constant-time comparison:</b> {@link MessageDigest#isEqual} does not
 * stop at the first different byte, which avoids timing attacks.</li>
 * </ul>
 *
 * <p>Stored format: {@code pbkdf2-sha256$iterations$saltBase64$hashBase64}.
 * Uses only the Java standard library (no external code).
 */
public final class PasswordHasher {

    /** Number of PBKDF2 iterations used for new passwords. */
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int MAX_ACCEPTED_ITERATIONS = 10_000_000;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /** A lower iteration count is only meant for fast unit tests. */
    public PasswordHasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        this.iterations = iterations;
    }

    /** Creates a salted hash string for the given password. */
    public String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = derive(password, salt, iterations);
        Base64.Encoder enc = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + enc.encodeToString(salt)
                + "$" + enc.encodeToString(key);
    }

    /** True if the password matches the stored hash string. */
    public boolean verify(char[] password, String stored) {
        if (stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals(PREFIX)) {
            return false;
        }
        try {
            int storedIterations = Integer.parseInt(parts[1]);
            if (storedIterations < 1 || storedIterations > MAX_ACCEPTED_ITERATIONS) {
                return false; // refuse absurd values from a tampered file
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, storedIterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false; // not a number / not valid Base64
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}