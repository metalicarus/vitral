package com.genosha.vitral.ingest;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API shows about a watched folder.
 *
 * @param lastScanAt    when the last scan finished; null until the first one does
 * @param lastScanError why the last scan could not run, or null when it ran
 * @param photos        photo files found in the folder by the scans so far
 */
public record FolderView(UUID id, String path, String ownerEmail, Instant createdAt, Instant lastScanAt,
                         String lastScanError, long photos) {
}
