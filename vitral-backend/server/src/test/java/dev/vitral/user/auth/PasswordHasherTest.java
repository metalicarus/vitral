package dev.vitral.user.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void acceptsTheRightPasswordAndRejectsOthers() {
        String hash = hasher.hash("correct horse battery staple");

        assertTrue(hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertTrue(hasher.verify("correct horse battery staple", hash));
        assertFalse(hasher.verify("Correct horse battery staple", hash));
        assertFalse(hasher.verify("", hash));
    }

    @Test
    void usesARandomSaltForEveryHash() {
        assertNotEquals(hasher.hash("same password"), hasher.hash("same password"));
    }

    @Test
    void rejectsMalformedHashesInsteadOfThrowing() {
        assertFalse(hasher.verify("password", "not-a-hash"));
        assertFalse(hasher.verify("password", "$argon2i$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA"));
        assertFalse(hasher.verify("password", "$argon2id$v=19$m=abc,t=2,p=1$c2FsdA$aGFzaA"));
        assertFalse(hasher.verify("password", "$argon2id$v=19$m=19456,t=2,p=1$%%%$aGFzaA"));
        assertFalse(hasher.verify("password", null));
        assertFalse(hasher.verify(null, hasher.hash("password")));
    }
}
