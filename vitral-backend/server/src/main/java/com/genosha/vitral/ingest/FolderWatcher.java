package com.genosha.vitral.ingest;

import com.genosha.vitral.config.VitralConfig;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decides when watched folders are scanned: at startup, every {@code vitral.import.scan-interval},
 * and shortly after a change is noticed in one of them.
 *
 * <p>Changes are noticed through the operating system ({@link WatchService}, inotify on Linux),
 * which watches one directory at a time; every directory a scan visits is watched from then
 * on. A burst of changes, such as a sync tool writing many photos, leads to a single scan,
 * {@code vitral.import.settle} after the last change. When the system cannot watch more
 * directories, the periodic scans still find everything, just later.
 */
@ApplicationScoped
public class FolderWatcher {

    private static final Logger LOG = Logger.getLogger(FolderWatcher.class);

    @Inject
    FolderScanner scanner;

    @Inject
    VitralConfig config;

    /** Runs the scans, one at a time, so they never compete for the database. */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("vitral-folder-scans").daemon().factory());
    private final Map<UUID, ScheduledFuture<?>> pendingScans = new ConcurrentHashMap<>();
    private final Map<WatchKey, UUID> watchedDirectories = new ConcurrentHashMap<>();
    private final Map<Path, WatchKey> keysByDirectory = new ConcurrentHashMap<>();
    private final AtomicBoolean warnedAboutLimit = new AtomicBoolean();
    private volatile WatchService watchService;

    void start(@Observes StartupEvent event) {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Thread.ofPlatform().name("vitral-folder-events").daemon().start(this::listen);
        } catch (IOException e) {
            LOG.warn("Changes in watched folders cannot be noticed as they happen; they are found by the periodic"
                    + " scans instead", e);
        }
        Duration interval = config.imports().scanInterval();
        scheduler.scheduleWithFixedDelay(this::scanAll, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void stop(@Observes ShutdownEvent event) {
        scheduler.shutdownNow();
        WatchService service = watchService;
        if (service != null) {
            try {
                service.close();
            } catch (IOException ignored) {
                // Shutting down anyway.
            }
        }
    }

    /**
     * Scans the folder after {@code delay}. A scan already waiting for the same folder is
     * replaced, so a burst of requests leads to one scan.
     */
    public void requestScan(UUID folderId, Duration delay) {
        pendingScans.compute(folderId, (id, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return scheduler.schedule(() -> {
                pendingScans.remove(id);
                runScan(id);
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    /** Stops watching a folder that was removed. */
    public void forget(UUID folderId) {
        ScheduledFuture<?> pending = pendingScans.remove(folderId);
        if (pending != null) {
            pending.cancel(false);
        }
        watchedDirectories.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(folderId)) {
                entry.getKey().cancel();
                keysByDirectory.values().remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void scanAll() {
        try {
            List<UUID> folders = QuarkusTransaction.requiringNew().call(() ->
                    WatchedFolder.<WatchedFolder>listAll().stream().map(folder -> folder.id).toList());
            folders.forEach(this::runScan);
        } catch (RuntimeException e) {
            // Caught so the periodic schedule survives: an escaping exception would cancel it.
            LOG.error("The periodic scan of watched folders failed; it runs again at the next interval", e);
        }
    }

    private void runScan(UUID folderId) {
        try {
            FolderScanner.ScanResult result = scanner.scan(folderId, directory -> watch(folderId, directory));
            if (result.deferred() > 0) {
                // Files too recent to read yet are not changing anymore by then.
                requestScan(folderId, config.imports().settle());
            }
        } catch (RuntimeException e) {
            LOG.errorf(e, "Scanning watched folder %s failed; it is tried again on the next scan", folderId);
        }
    }

    private void watch(UUID folderId, Path directory) {
        WatchService service = watchService;
        if (service == null || keysByDirectory.containsKey(directory)) {
            return;
        }
        try {
            WatchKey key = directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            keysByDirectory.put(directory, key);
            watchedDirectories.put(key, folderId);
        } catch (IOException e) {
            if (warnedAboutLimit.compareAndSet(false, true)) {
                LOG.warnf("Cannot watch %s for changes (%s). On Linux, raising fs.inotify.max_user_watches helps;"
                        + " meanwhile, new photos there are found by the periodic scans.", directory, e.getMessage());
            }
        }
    }

    /** Waits for change notifications and turns them into scans. */
    private void listen() {
        while (true) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (ClosedWatchServiceException | InterruptedException stopped) {
                return;
            }
            // Only whether something changed matters; the scan works out what.
            key.pollEvents();
            UUID folderId = watchedDirectories.get(key);
            if (folderId != null) {
                requestScan(folderId, config.imports().settle());
            }
            if (!key.reset()) {
                // The directory itself was deleted or became unreadable.
                watchedDirectories.remove(key);
                keysByDirectory.values().remove(key);
            }
        }
    }
}
