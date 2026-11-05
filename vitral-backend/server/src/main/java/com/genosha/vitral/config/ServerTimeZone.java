package com.genosha.vitral.config;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * The server's time zone, from {@code vitral.time-zone} (or {@code VITRAL_TIME_ZONE}).
 *
 * <p>A blank value means the system time zone, since an empty variable is easy to end up
 * with in Docker setups. An invalid value stops the server at startup with a message that
 * says what to fix, instead of failing later on the first upload.
 */
@Startup
@Singleton
public class ServerTimeZone {

    private final ZoneId zone;

    @Inject
    public ServerTimeZone(VitralConfig config) {
        this(config.timeZone().orElse(null));
    }

    ServerTimeZone(String configured) {
        this.zone = parse(configured);
    }

    public ZoneId zone() {
        return zone;
    }

    static ZoneId parse(String configured) {
        if (configured == null || configured.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured.strip());
        } catch (DateTimeException e) {
            throw new IllegalStateException("vitral.time-zone (VITRAL_TIME_ZONE) is \"" + configured
                    + "\", which is not a valid time zone. Use an IANA name such as America/Sao_Paulo,"
                    + " or leave it empty to use the system time zone.", e);
        }
    }
}