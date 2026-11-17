package com.genosha.vitral.asset;

import java.util.Locale;
import java.util.Optional;

/** Which of the owner's photos a listing shows: the {@code view} parameter of GET /api/v1/assets. */
public enum AssetFilter {

    /** Everything except archived and trashed photos. The default. */
    TIMELINE("archived = false and trashedAt is null"),
    /** Favorites, archived or not, outside the trash. */
    FAVORITES("favorite = true and trashedAt is null"),
    ARCHIVE("archived = true and trashedAt is null"),
    TRASH("trashedAt is not null");

    private final String condition;

    AssetFilter(String condition) {
        this.condition = condition;
    }

    /** The HQL condition on Asset that selects this view's photos. */
    String condition() {
        return condition;
    }

    /** {@code timeline}, {@code favorites}, {@code archive} or {@code trash}; empty for anything else. */
    public static Optional<AssetFilter> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(TIMELINE);
        }
        try {
            return Optional.of(valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
