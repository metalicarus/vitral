package com.genosha.vitral.asset;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Where a page of the asset list ended: the capture date and id of its last item.
 * The next page starts right after it, so pages never repeat or skip assets even
 * while new ones are being uploaded.
 *
 * <p>Clients get it as an opaque string and must send it back unchanged. The version
 * prefix allows changing the format later without breaking cursors in flight.
 */
public record AssetCursor(long takenAtMillis, UUID id) {

    private static final String VERSION = "v1";

    public String encode() {
        String raw = VERSION + "|" + takenAtMillis + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException if the value was not produced by {@link #encode()} */
    public static AssetCursor decode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Empty cursor");
        }
        String raw = new String(Base64.getUrlDecoder().decode(value.strip()), StandardCharsets.UTF_8);
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Unknown cursor format");
        }
        return new AssetCursor(Long.parseLong(parts[1]), UUID.fromString(parts[2]));
    }
}