package dev.vitral.user.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTokensTest {

    @Test
    void tokensAreUrlSafeAndUnique() {
        String token = SessionTokens.newToken();

        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
        assertNotEquals(token, SessionTokens.newToken());
    }

    @Test
    void hashIsStableSha256Hex() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", SessionTokens.hash(""));
        assertEquals(SessionTokens.hash("abc"), SessionTokens.hash("abc"));
    }
}
