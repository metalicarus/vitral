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

    public record User(UUID id, String email, String displayName, String role, Instant createdAt) {

        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }

    /**
     * A watched folder on the server ({@code /api/v1/admin/folders}), whose photos are
     * cataloged where they are for one user.
     *
     * @param lastScanAt    when the last scan finished; null until the first one does
     * @param lastScanError why the last scan could not run, or null when it ran
     * @param photos        photo files the scans found in the folder
     */
    public record Folder(UUID id, String path, String ownerEmail, Instant createdAt, Instant lastScanAt,
                         String lastScanError, long photos) {
    }

    /** {@code POST /api/v1/auth/login} and {@code POST /api/v1/setup}. */
    public record LoginResult(String token, Instant expiresAt, User user) {
    }

    /** Whether a photo's thumbnail exists yet. Unknown values from newer servers read as null. */
    public enum ThumbnailStatus {
        PENDING,
        READY,
        FAILED
    }

    /** Where a photo's date came from, from most to least reliable. Unknown values read as null. */
    public enum TakenAtSource {
        /** Written by the camera. */
        EXIF,
        /** The file's modification time, sent by the uploading client. */
        FILE,
        /** When the server received the file. */
        UPLOAD
    }

    public record Asset(UUID id, String type, String originalFileName, String mimeType, long sizeBytes,
                        Integer width, Integer height, Instant takenAt, TakenAtSource takenAtSource,
                        Instant uploadedAt, ThumbnailStatus thumbnailStatus) {
    }

    /** {@code GET /api/v1/assets}: one page, newest first. {@code nextCursor} is null on the last page. */
    public record AssetPage(List<Asset> items, String nextCursor) {
    }

    /**
     * {@code POST /api/v1/assets/check}: which of the hashes the user already has on the
     * server, and which are missing. Both lists keep the order of the request.
     */
    public record HashCheckResult(List<Existing> existing, List<String> missing) {

        public record Existing(String sha256, UUID id) {
        }
    }

    /** {@code POST /api/v1/assets}: the photo, and whether this upload created it or the user already had it. */
    public record UploadResult(Asset asset, boolean created) {
    }

    /** The body of every error response. */
    record ErrorBody(String error, String message) {
    }
}
