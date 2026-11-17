package com.genosha.vitral.album;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API shows about an album.
 *
 * @param coverAssetId the photo to show as the cover, or null for an empty album
 * @param photos       how many photos it has, not counting those in the trash
 * @param contains     whether it has the photo asked about with {@code ?containing=}; null otherwise
 */
public record AlbumView(UUID id, String name, UUID coverAssetId, long photos, Instant createdAt, Instant updatedAt,
                        Boolean contains) {
}
