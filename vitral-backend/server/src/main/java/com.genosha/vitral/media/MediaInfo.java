package com.genosha.vitral.media;

import com.genosha.vitral.asset.AssetType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * What the inspector learned from a file's content.
 *
 * @param width          display width in pixels (already swapped for rotated photos), or null if unknown
 * @param height         display height in pixels, or null if unknown
 * @param capturedLocal  capture date and time as written by the camera, usually local time; null if absent
 * @param capturedOffset the UTC offset the camera recorded with it, if any
 */
public record MediaInfo(AssetType type, String mimeType, String extension, Integer width, Integer height,
                        LocalDateTime capturedLocal, ZoneOffset capturedOffset) {
}