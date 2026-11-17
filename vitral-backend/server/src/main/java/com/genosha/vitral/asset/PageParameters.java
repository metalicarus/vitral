package com.genosha.vitral.asset;

import com.genosha.vitral.common.ApiException;

/**
 * Reads the {@code limit} and {@code cursor} query parameters of the photo listings. They
 * arrive as strings so that bad values get a clear 400 instead of the 404 JAX-RS gives for
 * parameters it cannot convert.
 */
public final class PageParameters {

    private PageParameters() {
    }

    public static int limit(String value) {
        if (value == null || value.isBlank()) {
            return AssetService.DEFAULT_PAGE_SIZE;
        }
        try {
            int limit = Integer.parseInt(value.strip());
            if (limit >= 1 && limit <= AssetService.MAX_PAGE_SIZE) {
                return limit;
            }
        } catch (NumberFormatException ignored) {
            // Falls through to the error below.
        }
        throw ApiException.badRequest("invalid_limit",
                "limit must be a whole number from 1 to " + AssetService.MAX_PAGE_SIZE + ".");
    }

    /** @return null for the first page */
    public static AssetCursor cursor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AssetCursor.decode(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_cursor",
                    "Use the nextCursor value from the previous page, unchanged.");
        }
    }
}
