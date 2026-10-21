package com.genosha.vitral.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import io.quarkus.runtime.configuration.MemorySize;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Settings under the {@code vitral.} prefix in application.properties or environment variables. */
@ConfigMapping(prefix = "vitral")
public interface VitralConfig {

    /** Where the database, the photo library and temporary uploads live. */
    Path dataDir();

    /**
     * Time zone used when a photo carries none, as an IANA name such as America/Sao_Paulo.
     * Missing or blank means the system time zone. Read it through {@link ServerTimeZone}.
     */
    Optional<String> timeZone();

    Session session();

    Login login();

    Upload upload();

    interface Upload {
        /** Largest file accepted in one request. */
        @WithDefault("4G")
        MemorySize maxSize();
    }

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