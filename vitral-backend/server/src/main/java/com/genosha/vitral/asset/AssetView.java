package com.genosha.vitral.asset;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API exposes about an asset. The location on disk stays private.
 *
 * @param camera   how the photo was taken, or null when the file says nothing about it
 * @param location where the photo was taken, or null when it has no GPS position
 */
public record AssetView(UUID id, AssetType type, String originalFileName, String mimeType, long sizeBytes,
                        String sha256, Integer width, Integer height, Instant takenAt,
                        TakenAtSource takenAtSource, Instant uploadedAt, ThumbnailStatus thumbnailStatus,
                        Camera camera, Location location) {

    /** Every field may be null; the record itself is null only when all of them would be. */
    public record Camera(String make, String model, String lens, Double fNumber, Double exposureSeconds,
                         Integer iso, Double focalLength) {
    }

    /** Decimal degrees; negative latitudes are south, negative longitudes west. */
    public record Location(double latitude, double longitude) {
    }

    public static AssetView of(Asset asset) {
        return new AssetView(asset.id, asset.type, asset.originalFileName, asset.mimeType, asset.sizeBytes,
                asset.sha256, asset.width, asset.height, asset.takenAt, asset.takenAtSource, asset.uploadedAt,
                asset.thumbnailStatus, camera(asset), location(asset));
    }

    private static Camera camera(Asset asset) {
        Camera camera = new Camera(asset.cameraMake, asset.cameraModel, asset.lensModel, asset.fNumber,
                asset.exposureSeconds, asset.iso, asset.focalLength);
        return camera.equals(new Camera(null, null, null, null, null, null, null)) ? null : camera;
    }

    private static Location location(Asset asset) {
        return asset.latitude == null || asset.longitude == null ? null : new Location(asset.latitude, asset.longitude);
    }
}
