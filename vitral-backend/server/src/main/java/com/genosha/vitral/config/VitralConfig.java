package com.genosha.vitral.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

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

    Media media();

    Trash trash();

    Backup backup();

    interface Backup {
        /** Whether the database is copied automatically; backups can still be made on demand when off. */
        @WithDefault("true")
        boolean enabled();

        /** How old the newest copy may get before a new one is made. */
        @WithDefault("P1D")
        Duration interval();

        /** How many copies are kept; older ones are deleted. */
        @WithDefault("7")
        int keep();
    }

    interface Trash {
        /** How long a photo stays in the trash, where it can be restored, before it is deleted for good. */
        @WithDefault("P30D")
        Duration retention();
    }

    /** Watched folders. Named {@code imports} because {@code import} is a Java keyword. */
    @WithName("import")
    Import imports();

    interface Import {
        /** How often every watched folder is scanned in full, on top of the changes seen as they happen. */
        @WithDefault("PT15M")
        Duration scanInterval();

        /**
         * How long a file must stay untouched before it is cataloged, so files still being
         * written by a sync tool are not read half-way. Changes noticed as they happen are
         * also scanned this long after the last one.
         */
        @WithDefault("PT5S")
        Duration settle();
    }

    interface Media {
        /**
         * Folder with the libvips command-line tools (vipsthumbnail, vipsheader).
         * Empty means they are found on the PATH.
         */
        Optional<Path> vipsDir();

        /** How many photos have their thumbnails generated at the same time. */
        @WithDefault("2")
        int workers();
    }

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