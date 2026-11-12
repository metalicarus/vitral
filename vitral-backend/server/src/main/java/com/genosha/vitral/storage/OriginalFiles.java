package com.genosha.vitral.storage;

import com.genosha.vitral.ingest.WatchedFolder;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds an asset's original on disk: in the library for uploaded files, or in its watched
 * folder for files cataloged where they are.
 *
 * <p>The folders' paths are kept in memory, loaded once at startup (before thumbnails start
 * being generated) and updated as folders are added or removed, so finding a file never
 * touches the database.
 */
@Singleton
public class OriginalFiles {

    private final LibraryStorage library;
    private final Map<UUID, Path> folderRoots = new ConcurrentHashMap<>();

    @Inject
    public OriginalFiles(LibraryStorage library) {
        this.library = library;
    }

    /** Runs before the other startup work, which may already need originals. */
    void loadFolders(@Observes @Priority(1) StartupEvent event) {
        QuarkusTransaction.requiringNew().run(() -> WatchedFolder.<WatchedFolder>listAll()
                .forEach(folder -> register(folder.id, Path.of(folder.path))));
    }

    /**
     * @param folderId     the asset's watched folder, or null for the library
     * @param relativePath the asset's path inside it
     * @throws IllegalArgumentException if the path would escape the folder
     * @throws IllegalStateException    if the folder is unknown, for example just removed
     */
    public Path resolve(UUID folderId, String relativePath) {
        if (folderId == null) {
            return library.resolve(relativePath);
        }
        Path root = folderRoots.get(folderId);
        if (root == null) {
            throw new IllegalStateException("Unknown watched folder " + folderId);
        }
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the watched folder: " + relativePath);
        }
        return path;
    }

    public void register(UUID folderId, Path root) {
        folderRoots.put(folderId, root.toAbsolutePath().normalize());
    }

    public void forget(UUID folderId) {
        folderRoots.remove(folderId);
    }
}
