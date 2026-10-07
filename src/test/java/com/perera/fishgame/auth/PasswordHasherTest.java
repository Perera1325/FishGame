package com.perera.fishgame.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    // A low iteration count keeps the tests fast; the real default is much higher.
    private final PasswordHasher hasher = new PasswordHasher(1000);

    @Test
    void correctPasswordVerifies() {
        String stored = hasher.hash("correct horse".toCharArray());
        assertTrue(hasher.verify("correct horse".toCharArray(), stored));
    }

    @Test
    void wrongPasswordIsRejected() {
        String stored = hasher.hash("correct horse".toCharArray());
        assertFalse(hasher.verify("wrong horse".toCharArray(), stored));
    }

    @Test
    void samePasswordGivesDifferentHashesBecauseOfTheSalt() {
        String a = hasher.hash("same password".toCharArray());
        String b = hasher.hash("same password".toCharArray());
        assertNotEquals(a, b);
    }

    @Test
    void hashDoesNotContainThePassword() {
        String stored = hasher.hash("secretpassword".toCharArray());
        assertFalse(stored.contains("secretpassword"));
    }

    @Test
    void hashRecordsAlgorithmAndIterations() {
        String stored = hasher.hash("anything".toCharArray());
        assertTrue(stored.startsWith("pbkdf2-sha256$1000$"));
    }

    @Test
    void garbageOrTamperedStoredValuesAreRejected() {
        char[] pw = "anything".toCharArray();
        assertFalse(hasher.verify(pw, null));
        assertFalse(hasher.verify(pw, ""));
        assertFalse(hasher.verify(pw, "not a hash"));
        assertFalse(hasher.verify(pw, "pbkdf2-sha256$abc$AAAA$AAAA"));
        assertFalse(hasher.verify(pw, "pbkdf2-sha256$999999999$AAAA$AAAA"));
        assertFalse(hasher.verify(pw, "pbkdf2-sha256$1000$!!!$!!!"));
    }

    @Test
    void zeroIterationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(0));
    }
}