package com.genosha.vitral.client.ui;

import com.genosha.vitral.client.api.VitralApi;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Downloads and decodes thumbnails in the background and keeps the most recent ones in memory.
 *
 * <p>A few workers fetch them, so scrolling quickly does not open dozens of connections at
 * once. A request whose future was cancelled before its turn came (because its tile scrolled
 * off screen) is skipped without touching the network.
 */
public final class ThumbnailLoader implements AutoCloseable {

    private static final int WORKERS = 4;
    /** About 400 KB each once decoded, so the cache stays under roughly 200 MB. */
    private static final int CACHE_SIZE = 500;

    private final VitralApi api;
    private final ExecutorService workers;
    private final Map<UUID, Image> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Image> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public ThumbnailLoader(VitralApi api) {
        this.api = api;
        this.workers = Executors.newFixedThreadPool(WORKERS,
                Thread.ofPlatform().name("vitral-thumbnails-", 0).daemon().factory());
    }

    /** The thumbnail if it is already in memory, or null. */
    public Image cached(UUID assetId) {
        synchronized (cache) {
            return cache.get(assetId);
        }
    }

    /**
     * Fetches the thumbnail. The future fails with the server's {@code thumbnail_pending} error
     * while the server is still generating it. Cancel the future when the image is no longer needed.
     */
    public CompletableFuture<Image> load(UUID assetId) {
        Image cachedImage = cached(assetId);
        if (cachedImage != null) {
            return CompletableFuture.completedFuture(cachedImage);
        }
        CompletableFuture<Image> future = new CompletableFuture<>();
        try {
            workers.execute(() -> {
                if (future.isDone()) {
                    return;
                }
                try {
                    Image image = decode(api.thumbnail(assetId));
                    synchronized (cache) {
                        cache.put(assetId, image);
                    }
                    future.complete(image);
                } catch (IOException | RuntimeException e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException closed) {
            future.cancel(false);
        }
        return future;
    }

    private static Image decode(byte[] jpeg) throws IOException {
        Image image = new Image(new ByteArrayInputStream(jpeg));
        if (image.isError()) {
            throw new IOException("Could not decode the thumbnail", image.getException());
        }
        return image;
    }

    /** Stops the workers and drops the cached images, for example after logging out. */
    @Override
    public void close() {
        workers.shutdownNow();
        synchronized (cache) {
            cache.clear();
        }
    }
}
