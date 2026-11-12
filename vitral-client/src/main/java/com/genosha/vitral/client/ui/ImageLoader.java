package com.genosha.vitral.client.ui;

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
 * Downloads and decodes one kind of generated image (thumbnails, previews) in the background
 * and keeps the most recent ones in memory.
 *
 * <p>A few workers fetch them, so scrolling quickly does not open dozens of connections at
 * once. A request whose future was cancelled before its turn came (because its tile scrolled
 * off screen) is skipped without touching the network.
 */
public final class ImageLoader implements AutoCloseable {

    /** Gets an image's bytes from the server, such as {@code VitralApi::thumbnail}. */
    @FunctionalInterface
    public interface Fetcher {
        byte[] fetch(UUID assetId) throws IOException;
    }

    private final Fetcher fetcher;
    private final ExecutorService workers;
    private final Map<UUID, Image> cache;

    /**
     * @param name      names the worker threads, for thread dumps
     * @param cacheSize how many decoded images stay in memory
     */
    public ImageLoader(String name, Fetcher fetcher, int workerCount, int cacheSize) {
        this.fetcher = fetcher;
        this.workers = Executors.newFixedThreadPool(workerCount,
                Thread.ofPlatform().name("vitral-" + name + "-", 0).daemon().factory());
        this.cache = new LinkedHashMap<>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Image> eldest) {
                return size() > cacheSize;
            }
        };
    }

    /** The image if it is already in memory, or null. */
    public Image cached(UUID assetId) {
        synchronized (cache) {
            return cache.get(assetId);
        }
    }

    /**
     * Fetches the image. The future fails with the server's {@code thumbnail_pending} error
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
                    Image image = decode(fetcher.fetch(assetId));
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
            throw new IOException("Could not decode the image", image.getException());
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
