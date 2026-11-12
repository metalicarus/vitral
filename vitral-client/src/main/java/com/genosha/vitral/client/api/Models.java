package com.genosha.vitral.client.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The JSON bodies the client reads from the server, mirroring the server's records.
 * Fields the client does not use are left out; unknown fields are ignored.
 */
public final class Models {

    private Models() {
    }

    /** {@code GET /api/v1/server/info}: what a client needs before logging in. */
    public record ServerInfo(String name, String version, boolean setupRequired) {
    }

    public record User(UUID id, String email, String displayName, String role) {
    }

    /** {@code POST /api/v1/auth/login}. */
    public record LoginResult(String token, Instant expiresAt, User user) {
    }

    /** Whether a photo's thumbnail exists yet. Unknown values from newer servers read as null. */
    public enum ThumbnailStatus {
        PENDING,
        READY,
        FAILED
    }

    public record Asset(UUID id, String type, String originalFileName, String mimeType, long sizeBytes,
                        Integer width, Integer height, Instant takenAt, ThumbnailStatus thumbnailStatus) {
    }

    /** {@code GET /api/v1/assets}: one page, newest first. {@code nextCursor} is null on the last page. */
    public record AssetPage(List<Asset> items, String nextCursor) {
    }

    /** The body of every error response. */
    record ErrorBody(String error, String message) {
    }
}
