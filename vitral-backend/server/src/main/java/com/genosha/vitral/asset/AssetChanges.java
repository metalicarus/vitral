package com.genosha.vitral.asset;

/**
 * Body of PATCH /api/v1/assets/{id}. Fields left out (null) keep their value.
 *
 * @param favorite marks or unmarks the photo as a favorite
 * @param archived moves the photo to the archive, out of the timeline, or back
 */
public record AssetChanges(Boolean favorite, Boolean archived) {
}
