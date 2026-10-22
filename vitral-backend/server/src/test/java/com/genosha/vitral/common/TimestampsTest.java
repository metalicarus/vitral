package com.genosha.vitral.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimestampsTest {

    @Test
    void nowHasNoMoreThanMillisecondPrecision() {
        assertEquals(0, Timestamps.now().getNano() % 1_000_000);
    }

    @Test
    void dropsWhatTheDatabaseWouldDrop() {
        assertEquals(Instant.parse("2024-07-14T19:32:10.123Z"),
                Timestamps.toStoredPrecision(Instant.parse("2024-07-14T19:32:10.123456789Z")));
        assertNull(Timestamps.toStoredPrecision(null));
    }
}