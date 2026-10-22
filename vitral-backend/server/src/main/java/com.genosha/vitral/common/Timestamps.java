package com.genosha.vitral.common;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Timestamps at the precision the database keeps: milliseconds.
 *
 * <p>Every timestamp that gets saved must come from here. Otherwise the response given
 * right after saving (built from the object in memory, with nanoseconds) would differ
 * from every later response (read back from the database).
 */
public final class Timestamps {

    private Timestamps() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /** For timestamps that come from outside, such as a file date sent by a client. */
    public static Instant toStoredPrecision(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MILLIS);
    }
}