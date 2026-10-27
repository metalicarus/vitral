package com.genosha.vitral.asset;

/** Whether an asset's thumbnail and preview exist yet. */
public enum ThumbnailStatus {
    /** Waiting in the queue, or waiting for libvips to be installed. */
    PENDING,
    /** Both files are ready. */
    READY,
    /** libvips could not process the original, for example because it is corrupted. */
    FAILED
}