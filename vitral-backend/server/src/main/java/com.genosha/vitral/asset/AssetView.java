package com.genosha.vitral.asset;

import java.time.Instant;
import java.util.UUID;

/** What the API exposes about an asset. The location on disk stays private. */
public record AssetView(UUID id, AssetType type, String originalFileName, String mimeType, long sizeBytes,
                        String sha256, Integer width, Integer height, Instant takenAt,
                        TakenAtSource takenAtSource, Instant uploadedAt) {

    public static AssetView of(Asset asset) {
        return new AssetView(asset.id, asset.type, asset.originalFileName, asset.mimeType, asset.sizeBytes,
                asset.sha256, asset.width, asset.height, asset.takenAt, asset.takenAtSource, asset.uploadedAt);
    }
}