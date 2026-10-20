package dev.vitral.user.auth;

import dev.vitral.config.VitralConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks login for an e-mail after too many failed attempts.
 *
 * <p>State lives in memory: it resets when the server restarts, which is acceptable
 * for a single self-hosted instance.
 */
@Singleton
public class LoginThrottle {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(int failures, Instant firstFailure) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;

    @Inject
    public LoginThrottle(VitralConfig config) {
        this(config.login().maxFailures(), config.login().lockout(), Clock.systemUTC());
    }

    LoginThrottle(int maxFailures, Duration lockout, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockout = lockout;
        this.clock = clock;
    }

    public boolean isLocked(String key) {
        Window window = windows.get(key);
        if (window == null) {
            return false;
        }
        if (expired(window)) {
            windows.remove(key, window);
            return false;
        }
        return window.failures() >= maxFailures;
    }

    public void recordFailure(String key) {
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(this::expired);
        }
        windows.compute(key, (k, window) -> window == null || expired(window)
                ? new Window(1, clock.instant())
                : new Window(window.failures() + 1, window.firstFailure()));
    }

    public void reset(String key) {
        windows.remove(key);
    }

    private boolean expired(Window window) {
        return clock.instant().isAfter(window.firstFailure().plus(lockout));
    }
}
