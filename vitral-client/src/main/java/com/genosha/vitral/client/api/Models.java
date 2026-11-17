package com.genosha.vitral.client.api;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
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

    /** @param disabled a disabled account cannot log in */
    public record User(UUID id, String email, String displayName, String role, Instant createdAt, boolean disabled) {

        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }

    /**
     * A device logged into the account ({@code GET /api/v1/auth/sessions}).
     *
     * @param current whether it is the session making the request, this app
     */
    public record DeviceSession(UUID id, String deviceName, Instant createdAt, Instant lastUsedAt, Instant expiresAt,
                                boolean current) {
    }

    /** A copy of the server's catalog database ({@code /api/v1/admin/backups}). */
    public record Backup(String name, long sizeBytes, Instant createdAt) {
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

    /**
     * @param camera    how the photo was taken, or null when the file says nothing about it
     * @param location  where it was taken, or null when it has no GPS position
     * @param archived  kept out of the timeline, shown only in the archive
     * @param trashedAt when it went to the trash, or null when it is not there
     */
    public record Asset(UUID id, String type, String originalFileName, String mimeType, long sizeBytes,
                        Integer width, Integer height, Instant takenAt, TakenAtSource takenAtSource,
                        Instant uploadedAt, ThumbnailStatus thumbnailStatus, Camera camera, Location location,
                        boolean favorite, boolean archived, Instant trashedAt) {
    }

    /** Which photos a listing shows (the {@code view} parameter of {@code GET /api/v1/assets}). */
    public enum LibraryView {
        /** Everything not archived nor in the trash. */
        TIMELINE,
        /** Favorites outside the trash, archived or not. */
        FAVORITES,
        ARCHIVE,
        TRASH;

        public String parameter() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Whether the photo belongs here, as the server decides it; used after changing a photo. */
        public boolean contains(Asset asset) {
            boolean trashed = asset.trashedAt() != null;
            return switch (this) {
                case TIMELINE -> !asset.archived() && !trashed;
                case FAVORITES -> asset.favorite() && !trashed;
                case ARCHIVE -> asset.archived() && !trashed;
                case TRASH -> trashed;
            };
        }
    }

    /**
     * An album ({@code /api/v1/albums}).
     *
     * @param coverAssetId the photo to show as its cover, or null when it is empty
     * @param photos       how many photos it has, not counting those in the trash
     * @param contains     whether it has the photo asked about with {@code containing}; null otherwise
     */
    public record Album(UUID id, String name, UUID coverAssetId, long photos, Instant createdAt, Instant updatedAt,
                        Boolean contains) {
    }

    /** How many photos a request added to or removed from an album. */
    public record PhotosChanged(int changed) {
    }

    /** {@code POST /api/v1/assets/trash/empty}. */
    public record EmptyTrashResult(int deleted) {
    }

    /**
     * Camera, lens and exposure from the photo's EXIF. Any field may be null.
     *
     * @param fNumber         aperture, such as 4.0 for f/4
     * @param exposureSeconds shutter time, such as 0.004 for 1/250 s
     * @param focalLength     millimeters
     */
    public record Camera(String make, String model, String lens, Double fNumber, Double exposureSeconds,
                         Integer iso, Double focalLength) {
    }

    /** Decimal degrees; negative latitudes are south, negative longitudes west. */
    public record Location(double latitude, double longitude) {
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
