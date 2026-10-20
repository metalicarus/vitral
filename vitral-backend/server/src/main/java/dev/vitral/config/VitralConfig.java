package dev.vitral.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

/** Settings under the {@code vitral.} prefix in application.properties or environment variables. */
@ConfigMapping(prefix = "vitral")
public interface VitralConfig {

    Session session();

    Login login();

    interface Session {
        /** How long a login session stays valid. */
        @WithDefault("P90D")
        Duration ttl();
    }

    interface Login {
        /** Failed attempts allowed per e-mail before login is temporarily locked. */
        @WithDefault("5")
        int maxFailures();

        /** How long the lock lasts, counted from the first failed attempt. */
        @WithDefault("PT15M")
        Duration lockout();
    }
}
