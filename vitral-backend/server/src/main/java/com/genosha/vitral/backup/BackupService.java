package com.genosha.vitral.backup;

import com.genosha.vitral.config.VitralConfig;
import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Copies the catalog (vitral.db) to {@code <data-dir>/backups}, automatically every
 * {@code vitral.backup.interval} and on demand, keeping the last {@code vitral.backup.keep}.
 *
 * <p>The copy is made by SQLite itself ({@code VACUUM INTO}), so it is consistent even with
 * the server running and writing, unlike copying the file (ADR 0002). It holds users, albums,
 * favorites and the whole catalog, but not the photos: those live in plain folders and are
 * backed up like any other files.
 */
@ApplicationScoped
public class BackupService {

    private static final Logger LOG = Logger.getLogger(BackupService.class);
    /** Names like vitral-2026-11-17T16-30-00Z.db; nothing else in the folder is ever read or deleted. */
    static final Pattern NAME = Pattern.compile("vitral-\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}Z\\.db");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'");
    /** How often the automatic schedule checks whether a new copy is due. */
    private static final Duration CHECK_EVERY = Duration.ofHours(1);

    @Inject
    VitralConfig config;

    @Inject
    AgroalDataSource dataSource;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("vitral-backup").daemon().factory());
    private final Object lock = new Object();

    /** One copy of the database. */
    public record Backup(String name, long sizeBytes, Instant createdAt) {
    }

    void start(@Observes StartupEvent event) {
        if (!config.backup().enabled()) {
            LOG.info("Automatic database backups are off (vitral.backup.enabled=false)");
            return;
        }
        // The first check waits a few minutes, so a backup never slows down the startup.
        scheduler.scheduleWithFixedDelay(this::backupIfDue, 3, CHECK_EVERY.toMinutes(), TimeUnit.MINUTES);
    }

    void stop(@Observes ShutdownEvent event) {
        scheduler.shutdownNow();
    }

    /** Makes a copy now. */
    public Backup create() {
        synchronized (lock) {
            Path dir = folder();
            Instant now = Instant.now();
            String name = "vitral-" + STAMP.format(LocalDateTime.ofInstant(now, ZoneOffset.UTC)) + ".db";
            Path target = dir.resolve(name);
            Path partial = dir.resolve(name + ".partial");
            long started = System.nanoTime();
            try {
                Files.createDirectories(dir);
                Files.deleteIfExists(partial);
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement vacuum = connection.prepareStatement("VACUUM INTO ?")) {
                    vacuum.setString(1, partial.toString());
                    vacuum.execute();
                }
                // Under its final name only once complete, so a half-written copy is never listed.
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (SQLException | IOException e) {
                deleteQuietly(partial);
                throw new IllegalStateException("The database backup failed: " + e.getMessage(), e);
            }
            Backup backup = describe(target);
            LOG.infof("Database backed up to %s (%d KB in %d ms)", target, backup.sizeBytes() / 1024,
                    (System.nanoTime() - started) / 1_000_000);
            prune(config.backup().keep());
            return backup;
        }
    }

    /** The copies, newest first. */
    public List<Backup> list() {
        Path dir = folder();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> NAME.matcher(file.getFileName().toString()).matches())
                    .map(BackupService::describe)
                    .sorted(Comparator.comparing(Backup::name).reversed())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The file of one copy, or empty when there is no copy with that name. */
    public Optional<Path> file(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path file = folder().resolve(name);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** Deletes the oldest copies, keeping {@code keep}. */
    void prune(int keep) {
        List<Backup> backups = list();
        for (Backup old : backups.subList(Math.min(Math.max(keep, 1), backups.size()), backups.size())) {
            deleteQuietly(folder().resolve(old.name()));
            LOG.infof("Old database backup %s deleted (keeping the last %d)", old.name(), keep);
        }
    }

    private void backupIfDue() {
        try {
            Optional<Backup> newest = list().stream().findFirst();
            Duration interval = config.backup().interval();
            if (newest.isEmpty() || newest.get().createdAt().plus(interval).isBefore(Instant.now())) {
                create();
            }
        } catch (RuntimeException e) {
            // Caught so the schedule survives: an escaping exception would cancel it.
            LOG.error("The automatic database backup failed; it is tried again in an hour", e);
        }
    }

    private Path folder() {
        return config.dataDir().resolve("backups");
    }

    private static Backup describe(Path file) {
        try {
            return new Backup(file.getFileName().toString(), Files.size(file),
                    Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A leftover file is only taking space.
        }
    }
}
