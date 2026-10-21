package com.genosha.vitral.asset;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetCursorTest {

    private static final AssetCursor CURSOR =
            new AssetCursor(1720985402000L, UUID.fromString("7c1d2a90-1c2d-4e5f-8a9b-0c1d2e3f4a5b"));

    @Test
    void survivesTheRoundTripAndIsSafeInUrls() {
        String encoded = CURSOR.encode();

        assertEquals(CURSOR, AssetCursor.decode(encoded));
        assertTrue(encoded.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void rejectsAnythingItDidNotProduce() {
        assertThrows(IllegalArgumentException.class, () -> AssetCursor.decode("not base64 at all!"));
        assertThrows(IllegalArgumentException.class, () -> AssetCursor.decode(encode("v1|abc|" + CURSOR.id())));
        assertThrows(IllegalArgumentException.class, () -> AssetCursor.decode(encode("v1|123|not-a-uuid")));
        assertThrows(IllegalArgumentException.class, () -> AssetCursor.decode(encode("v2|123|" + CURSOR.id())));
        assertThrows(IllegalArgumentException.class, () -> AssetCursor.decode(encode("v1|123")));
        assertThrows(IllegalArgumentException.class, () -> AssetCursor.decode(""));
    }

    private static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}