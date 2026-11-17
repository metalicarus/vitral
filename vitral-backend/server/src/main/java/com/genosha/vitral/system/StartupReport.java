package com.genosha.vitral.system;

import com.genosha.vitral.config.ServerTimeZone;
import com.genosha.vitral.config.VitralConfig;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;

/** Logs, once at startup, the settings in effect, so a log file says how the server was running. */
@ApplicationScoped
public class StartupReport {

    private static final Logger LOG = Logger.getLogger(StartupReport.class);

    @Inject
    VitralConfig config;

    @Inject
    ServerTimeZone timeZone;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    @ConfigProperty(name = "quarkus.log.file.enabled", defaultValue = "false")
    boolean fileLog;

    @ConfigProperty(name = "quarkus.log.file.path", defaultValue = "")
    String fileLogPath;

    void report(@Observes StartupEvent event) {
        LOG.infof("Vitral %s starting with data in %s, time zone %s", version,
                config.dataDir().toAbsolutePath().normalize(), timeZone.zone());
        LOG.infof("Settings: uploads up to %d MB, %d thumbnail worker(s), watched folders scanned every %s"
                        + " (files settle for %s), trash kept for %s",
                config.upload().maxSize().asLongValue() / (1024 * 1024), config.media().workers(),
                readable(config.imports().scanInterval()), readable(config.imports().settle()),
                readable(config.trash().retention()));
        if (config.backup().enabled()) {
            LOG.infof("Database backups: every %s, keeping the last %d, in %s",
                    readable(config.backup().interval()), config.backup().keep(),
                    config.dataDir().resolve("backups").toAbsolutePath().normalize());
        }
        if (fileLog) {
            LOG.infof("Logs are also written to %s", fileLogPath);
        }
    }

    /** {@code 30 days}, {@code 15 minutes}, {@code 5 seconds}: Duration.toString() would say PT720H. */
    static String readable(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds % 86_400 == 0) {
            return seconds / 86_400 + " day(s)";
        }
        if (seconds % 3_600 == 0) {
            return seconds / 3_600 + " hour(s)";
        }
        if (seconds % 60 == 0) {
            return seconds / 60 + " minute(s)";
        }
        return seconds + " second(s)";
    }
}
