package com.genosha.vitral.asset;

import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.common.Timestamps;
import com.genosha.vitral.config.VitralConfig;
import com.genosha.vitral.storage.DerivativeStorage;
import com.genosha.vitral.storage.LibraryStorage;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Favorites, the archive and the trash.
 *
 * <p>Moving a photo to the trash only marks it; it can be restored for
 * {@code vitral.trash.retention} (30 days), after which a periodic cleanup deletes it for
 * good. Deleting for good removes the catalog entry and the generated files. The original is
 * deleted too when it lives in the library (it was uploaded); a photo from a watched folder
 * only leaves the catalog, because Vitral never deletes anything in those folders.
 */
@ApplicationScoped
public class AssetOrganizer {

    private static final Logger LOG = Logger.getLogger(AssetOrganizer.class);
    private static final Duration CLEANUP_INTERVAL = Duration.ofHours(6);

    @Inject
    VitralConfig config;

    @Inject
    LibraryStorage library;

    @Inject
    DerivativeStorage derivatives;

    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("vitral-trash-cleanup").daemon().factory());

    void start(@Observes StartupEvent event) {
        cleanup.scheduleWithFixedDelay(() -> {
            try {
                deleteExpired();
            } catch (RuntimeException e) {
                // Caught so the schedule survives: an escaping exception would cancel it.
                LOG.error("Emptying expired photos from the trash failed; it is tried again later", e);
            }
        }, 1, CLEANUP_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    }

    void stop(@Observes ShutdownEvent event) {
        cleanup.shutdownNow();
    }

    /** Marks a photo as favorite or not, archived or not; null leaves that flag as it is. */
    public Asset update(UUID ownerId, UUID assetId, Boolean favorite, Boolean archived) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Asset asset = owned(ownerId, assetId);
            if (favorite != null) {
                asset.favorite = favorite;
            }
            if (archived != null) {
                asset.archived = archived;
            }
            return asset;
        });
    }

    /** Moves a photo to the trash. Doing it twice keeps the first date, so the 30 days do not restart. */
    public Asset trash(UUID ownerId, UUID assetId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Asset asset = owned(ownerId, assetId);
            if (asset.trashedAt == null) {
                asset.trashedAt = Timestamps.now();
            }
            return asset;
        });
    }

    /** Takes a photo out of the trash, back where it was (timeline or archive). */
    public Asset restore(UUID ownerId, UUID assetId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Asset asset = owned(ownerId, assetId);
            asset.trashedAt = null;
            return asset;
        });
    }

    /** Deletes one photo for good. Only photos already in the trash can be. */
    public void deleteForGood(UUID ownerId, UUID assetId) {
        Asset asset = QuarkusTransaction.requiringNew().call(() -> owned(ownerId, assetId));
        if (asset.trashedAt == null) {
            throw ApiException.conflict("not_in_trash", "Move the photo to the trash before deleting it for good.");
        }
        delete(List.of(assetId));
    }

    /** Deletes for good every photo in the owner's trash. */
    public int empty(UUID ownerId) {
        List<UUID> ids = QuarkusTransaction.requiringNew().call(() ->
                Asset.<Asset>list("ownerId = ?1 and trashedAt is not null", ownerId).stream()
                        .map(asset -> asset.id).toList());
        return delete(ids);
    }

    /** Deletes the photos that have been in the trash longer than the retention period. */
    int deleteExpired() {
        Instant cutoff = Timestamps.now().minus(config.trash().retention());
        List<UUID> ids = QuarkusTransaction.requiringNew().call(() ->
                Asset.<Asset>list("trashedAt < ?1", cutoff).stream().map(asset -> asset.id).toList());
        int deleted = delete(ids);
        if (deleted > 0) {
            LOG.infof("Deleted %d photo(s) that had been in the trash for more than %s", deleted,
                    config.trash().retention());
        }
        return deleted;
    }

    /** What is left to clean up on disk once a row is gone. */
    private record Removed(UUID ownerId, UUID assetId, UUID folderId, String relativePath) {
    }

    private int delete(List<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        List<Removed> removed = QuarkusTransaction.requiringNew().call(() -> {
            List<Asset> assets = Asset.list("id in ?1", ids);
            assets.forEach(Asset::delete);
            return assets.stream().map(a -> new Removed(a.ownerId, a.id, a.folderId, a.relativePath)).toList();
        });
        // Files go only after the rows are gone: a crash in between leaves orphan files, never broken rows.
        for (Removed asset : removed) {
            derivatives.deleteQuietly(asset.ownerId(), asset.assetId());
            if (asset.folderId() == null) {
                library.deleteQuietly(library.resolve(asset.relativePath()));
            }
        }
        return removed.size();
    }

    private static Asset owned(UUID ownerId, UUID assetId) {
        Asset asset = Asset.<Asset>find("id = ?1 and ownerId = ?2", assetId, ownerId).firstResult();
        if (asset == null) {
            throw ApiException.notFound("asset_not_found", "Asset not found.");
        }
        return asset;
    }
}
