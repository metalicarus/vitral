package dev.vitral.user.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginThrottle throttle = new LoginThrottle(3, Duration.ofMinutes(15), clock);

    @Test
    void locksAfterTooManyFailures() {
        throttle.recordFailure("ana@example.com");
        throttle.recordFailure("ana@example.com");
        assertFalse(throttle.isLocked("ana@example.com"));

        throttle.recordFailure("ana@example.com");
        assertTrue(throttle.isLocked("ana@example.com"));
        assertFalse(throttle.isLocked("bia@example.com"));
    }

    @Test
    void unlocksWhenTheWindowEnds() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("ana@example.com");
        }
        clock.advance(Duration.ofMinutes(16));

        assertFalse(throttle.isLocked("ana@example.com"));
    }

    @Test
    void successfulLoginClearsFailures() {
        throttle.recordFailure("ana@example.com");
        throttle.recordFailure("ana@example.com");
        throttle.reset("ana@example.com");
        throttle.recordFailure("ana@example.com");

        assertFalse(throttle.isLocked("ana@example.com"));
    }
}
