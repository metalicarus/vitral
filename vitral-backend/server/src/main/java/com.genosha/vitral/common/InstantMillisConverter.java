package com.genosha.vitral.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;

/**
 * Stores every {@link Instant} as UTC epoch milliseconds (an INTEGER column).
 *
 * <p>SQLite has no native timestamp type, and the JDBC driver's handling of
 * date-time objects varies. Plain numbers are unambiguous, sort correctly and
 * are easy to read in the sqlite3 shell with {@code datetime(col / 1000, 'unixepoch')}.
 */
@Converter(autoApply = true)
public class InstantMillisConverter implements AttributeConverter<Instant, Long> {

    @Override
    public Long convertToDatabaseColumn(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    @Override
    public Instant convertToEntityAttribute(Long millis) {
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }
}