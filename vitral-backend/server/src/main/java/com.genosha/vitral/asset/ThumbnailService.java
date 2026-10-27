package com.genosha.vitral.asset;

import com.genosha.vitral.config.VitralConfig;
import com.genosha.vitral.media.PixelSize;
import com.genosha.vitral.media.Vips;
import com.genosha.vitral.media.VipsException;
import com.genosha.vitral.storage.DerivativeStorage;
import com.genosha.vitral.storage.LibraryStorage;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Generates each photo's preview and thumbnail in the background, so uploads stay fast.
 *
 * <p>The queue lives in memory, but the status lives in the database: on startup every
 * asset still {@code PENDING} is queued again. That covers photos uploaded before this
 * feature existed, photos that were waiting when the server stopped, and photos uploaded
 * while libvips was missing.
 */
@ApplicationScoped
public class ThumbnailService {

    /**
     * Part of the ETag of every generated file. Increase it whenever sizes or quality change,
     * so clients fetch the new files instead of keeping the old ones from their cache.
     */
    public static final int GENERATION_VERSION = 1;
    public static final int PREVIEW_SIZE = 1440;
    public static final int THUMBNAIL_SIZE = 360;
    private static final int PREVIEW_QUALITY = 82;
    private static final int THUMBNAIL_QUALITY = 80;
    private static final Duration VIPS_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_ERROR_LENGTH = 500;

    private static final Logger LOG = Logger.getLogger(ThumbnailService.class);

    @Inject
    VitralConfig config;

    @Inject
    LibraryStorage library;

    @Inject
    DerivativeStorage derivatives;

    /** Assets queued or being processed, so the same one is never handled twice at once. */
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();
    private volatile Vips vips;
    private volatile ExecutorService workers;
    private volatile boolean available;

    void start(@Observes StartupEvent event) {
        vips = new Vips(config.media().vipsDir().orElse(null), VIPS_TIMEOUT);
        available = vips.isAvailable();
        if (!available) {
            LOG.error("libvips was not found (the vipsthumbnail command). Thumbnails will stay pending until it is"
                    + " installed and the server restarts; see 'Desenvolvimento' in the README.");
            return;
        }
        int count = Math.max(1, config.media().workers());
        workers = Executors.newFixedThreadPool(count, Thread.ofPlatform().name("vitral-thumbnails-", 0).daemon().factory());
        try {
            LOG.infof("Thumbnails use %s, with %d worker(s)", vips.version(), count);
        } catch (IOException ignored) {
            // The version is only informative.
        }

        List<UUID> pending = QuarkusTransaction.requiringNew().call(() ->
                Asset.<Asset>list("thumbnailStatus", ThumbnailStatus.PENDING).stream().map(asset -> asset.id).toList());
        if (!pending.isEmpty()) {
            LOG.infof("Generating thumbnails for %d pending asset(s)", pending.size());
            pending.forEach(this::enqueue);
        }
    }

    void stop(@Observes ShutdownEvent event) {
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    /** Whether libvips is installed. Without it, thumbnails stay pending. */
    public boolean isAvailable() {
        return available;
    }

    public void enqueue(UUID assetId) {
        if (!available || workers == null || !queued.add(assetId)) {
            return;
        }
        try {
            workers.execute(() -> {
                try {
                    process(assetId);
                } catch (RuntimeException e) {
                    LOG.errorf(e, "Unexpected error generating thumbnails for asset %s; it stays pending", assetId);
                } finally {
                    queued.remove(assetId);
                }
            });
        } catch (RejectedExecutionException shuttingDown) {
            queued.remove(assetId);
        }
    }

    /** Sends an asset back to the queue, for example when its generated files were deleted. */
    public void requeue(UUID assetId) {
        QuarkusTransaction.requiringNew().run(() -> {
            Asset asset = Asset.findById(assetId);
            if (asset != null) {
                asset.thumbnailStatus = ThumbnailStatus.PENDING;
                asset.thumbnailError = null;
            }
        });
        enqueue(assetId);
    }

    private void process(UUID assetId) {
        Asset asset = QuarkusTransaction.requiringNew().call(() -> Asset.<Asset>findById(assetId));
        if (asset == null || asset.thumbnailStatus != ThumbnailStatus.PENDING) {
            return;
        }
        Path original = library.resolve(asset.relativePath);
        Path previewTemp = null;
        Path thumbnailTemp = null;
        try {
            previewTemp = derivatives.newTempFile(".jpg");
            thumbnailTemp = derivatives.newTempFile(".jpg");
            // The thumbnail is made from the preview, so heavy formats like HEIC are decoded once.
            vips.thumbnail(original, previewTemp, PREVIEW_SIZE, PREVIEW_QUALITY);
            vips.thumbnail(previewTemp, thumbnailTemp, THUMBNAIL_SIZE, THUMBNAIL_QUALITY);
            PixelSize size = asset.width == null || asset.height == null ? vips.size(original) : null;

            derivatives.install(previewTemp, derivatives.preview(asset.ownerId, asset.id));
            derivatives.install(thumbnailTemp, derivatives.thumbnail(asset.ownerId, asset.id));
            markReady(assetId, size);
        } catch (VipsException e) {
            LOG.warnf("Could not generate thumbnails for asset %s: %s", assetId, e.getMessage());
            markFailed(assetId, e.getMessage());
        } catch (IOException e) {
            // libvips could not even run, or the disk failed: not the file's fault, so try again on the next start.
            LOG.errorf(e, "Thumbnail generation for asset %s was interrupted; it stays pending", assetId);
        } finally {
            deleteQuietly(previewTemp);
            deleteQuietly(thumbnailTemp);
        }
    }

    private void markReady(UUID assetId, PixelSize size) {
        QuarkusTransaction.requiringNew().run(() -> {
            Asset asset = Asset.findById(assetId);
            if (asset == null) {
                return;
            }
            asset.thumbnailStatus = ThumbnailStatus.READY;
            asset.thumbnailError = null;
            // Fills in what the metadata could not tell, such as HEIC files without EXIF dimensions.
            if (size != null && (asset.width == null || asset.height == null)) {
                asset.width = size.width();
                asset.height = size.height();
            }
        });
    }

    private void markFailed(UUID assetId, String error) {
        QuarkusTransaction.requiringNew().run(() -> {
            Asset asset = Asset.findById(assetId);
            if (asset != null) {
                asset.thumbnailStatus = ThumbnailStatus.FAILED;
                asset.thumbnailError = error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
            }
        });
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // A leftover scratch file is harmless.
            }
        }
    }
}